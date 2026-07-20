#!/usr/bin/env python3
"""Independent, arbitrary-precision (MPFR-class) oracle for the seven
correctly-rounded transcendental functions JGDMS-STD-011 Sec 7.5 ratifies
(sin, cos, tan, asin, acos, atan, atan2): generates the
`eval-transcendental.json` conformance vectors consumed by `CorpusRunnerTest`.

WHY THIS SCRIPT EXISTS (STD-011 Sec 13 item 4 / Sec 7.5)
---------------------------------------------------------
STD-011 Sec 7.5 ratified *correct rounding required* for these seven
functions specifically so the corpus does not need a reference
implementation as oracle (G9): "the nearest binary64 to the exact real
result" is independently computable from the mathematical definition alone,
via arbitrary-precision arithmetic, with no dependency on any particular
platform libm (glibc/musl/MSVC/Apple/fdlibm all legitimately differ from
each other and from this oracle in the last bit for at least some inputs --
that divergence is exactly the gap Sec 7.4 describes and Sec 7.5 closes by
requiring correct rounding instead of tolerating it).

This script's expected values MUST NOT be sourced from running the Java
`jgdms-cel` evaluator (or any other implementation) and recording what it
returns (G12: bind to a known-good independent source, not to
self-consistency) -- everything below is computed from mpmath's
arbitrary-precision `mpf` type, a general-purpose multiprecision library
independent of both the Java (T3) and any future Rust (T4) evaluator.

METHOD (the "how" a future re-generator or Rust-side reproduction needs)
-------------------------------------------------------------------------
For an input `x` (or `(y, x)` for atan2) and a target function `f`:

1. Compute `f(x)` at `start_dps` decimal digits of precision (60 by
   default -- ~199 bits, versus binary64's 53-bit mantissa).
2. Round that high-precision result to the nearest binary64
   (Python's `float(mpf_value)` performs round-to-nearest-even on the
   mpmath value, which -- because 199 bits vastly exceeds 53 -- is
   indistinguishable from rounding the *exact* mathematical value unless
   the exact value happens to fall within roughly 2^-146 of a binary64
   rounding boundary, a "hard to round" case that is vanishingly rare for
   generic inputs but not provably absent for an arbitrary one).
3. **Double-rounding guard**: recompute at `2 * dps` digits and round again.
   If the two roundings agree, accept the result -- two independent
   precisions agreeing on the same 53-bit rounding is strong evidence
   neither happened to land in a rounding-boundary blind spot. If they
   disagree, double the precision again and retry, up to `max_dps`. This
   is the guard the SOW's T5 task explicitly requires ("recomputing at
   higher precision" to verify the value is not within 1/2-ulp ambiguity
   of a rounding boundary) -- implemented here as `correctly_rounded()`.
4. If `max_dps` is exhausted without two consecutive agreements, the script
   raises rather than silently emitting an unverified value -- a vector
   this oracle cannot confidently certify is not written to the corpus at
   all (fail closed, matching this whole project's posture elsewhere).

`atan2`'s signed-zero/infinity boundary table (Sec 7.2 row 15's
"atan2(+-0, +x) = +-0, atan2(+-0, -x) = +-pi, etc.") is asserted directly
from the IEEE-754/C99 `atan2` convention rather than trusted to
`mpmath.atan2`, because mpmath's own zero value does not reliably preserve
a distinguished sign the way IEEE double zero does -- the *magnitude* of
those results (0, pi, pi/2, 3pi/4, pi/4) still comes from this same
correctly-rounded oracle machinery.

USAGE
-----
    pip install mpmath   # only third-party dependency; pure-Python, no C toolchain
    python3 transcendental_oracle.py [output.json]

With no argument, writes to
`../vectors/eval-transcendental.json` relative to this file (i.e. this
repository's actual corpus location) -- the layout `CORPUS-FORMAT.md` Sec 1
documents. Every emitted vector carries `"requires": ["conformant-transcendentals"]`
and `"provenance": "oracle"`.

If `mpmath` is unavailable in a given environment and cannot be installed,
the *procedure* above is the exact fallback documentation: any arbitrary-
precision library (MPFR bindings, `decimal` extended with a manual
Taylor/CORDIC implementation, a CAS) that can (a) evaluate these six
functions to >=60 significant decimal digits and (b) round the result to
binary64 with the double-rounding guard in step 3 produces an equally valid
oracle. The corpus format (`CORPUS-FORMAT.md`) does not care which tool
produced the hex bit patterns, only that they are independent of both
evaluators under test.
"""
import json
import os
import random
import struct
import sys

try:
    import mpmath
    from mpmath import mp
except ImportError:
    sys.stderr.write(
        "ERROR: mpmath is not installed. Run: pip install mpmath\n"
        "(or, per this script's own docstring, substitute any arbitrary-\n"
        "precision library implementing the same correctly-rounded procedure.)\n"
    )
    raise


# ============================================================================
# binary64 <-> bits, and a minimal, self-contained DER encoder for this
# script's own use (deliberately duplicated from -- not imported from -- the
# corpus's other generator tooling, so this oracle script stands alone and
# is independently runnable/reproducible per the SOW's requirement).
# ============================================================================

def double_bits(x: float) -> int:
    return struct.unpack(">Q", struct.pack(">d", x))[0]


def bits_to_double(bits: int) -> float:
    return struct.unpack(">d", struct.pack(">Q", bits & 0xFFFFFFFFFFFFFFFF))[0]


def _minimal_length(n):
    if n <= 0x7F:
        return bytes([n])
    out = []
    v = n
    while v > 0:
        out.insert(0, v & 0xFF)
        v >>= 8
    return bytes([0x80 | len(out)] + out)


def _tlv(tag_bytes, content):
    return tag_bytes + _minimal_length(len(content)) + content


def _ctx(number, constructed, content=b""):
    b = 0x80 | (0x20 if constructed else 0x00) | number
    return _tlv(bytes([b]), content)


def _minimal_int_content(value):
    if value == 0:
        return b"\x00"
    n = 1
    while True:
        lo = -(1 << (8 * n - 1))
        hi = (1 << (8 * n - 1)) - 1
        if lo <= value <= hi:
            return value.to_bytes(n, "big", signed=True)
        n += 1


def _universal_int(content):
    return _tlv(bytes([0x02]), content)


def _universal_seq(*children):
    return _tlv(bytes([0x30]), b"".join(children))


def lit_double_bits(bits):
    return _ctx(2, False, bits.to_bytes(8, "big"))


def call_node(function_id, *args):
    return _ctx(26, True, _universal_int(_minimal_int_content(function_id)) + _universal_seq(*args))


def predicate_record(expr_bytes):
    context = _ctx(0, False, b"")
    fmt = _universal_int(_minimal_int_content(1))
    return _universal_seq(fmt, context, expr_bytes)


# Appendix B Sec B.8.3 pinned wire ids for the seven correctly-rounded functions.
FUNCTION_IDS = {
    "sin": 15, "cos": 16, "tan": 17, "asin": 18, "acos": 19, "atan": 20, "atan2": 21,
}


# ============================================================================
# The correctly-rounded oracle core
# ============================================================================

def correctly_rounded(fn, *py_args, start_dps=60, max_dps=4000):
    """Returns the correctly-rounded binary64 result of `fn` (an mpmath
    function) applied to `py_args` (Python floats, converted to exact
    high-precision mpf values), with the double-rounding guard described in
    this module's docstring. Raises RuntimeError if the guard cannot
    converge by `max_dps` digits (never emits an unverified value)."""
    dps = start_dps
    prev = None
    while dps <= max_dps:
        mp.dps = dps
        mp_args = [mpmath.mpf(a) for a in py_args]
        val = fn(*mp_args)
        d = float(val)
        if prev is not None and d == prev:
            return d
        prev = d
        dps *= 2
    raise RuntimeError("could not confirm a correctly-rounded double for %s%r "
                        "within max_dps=%d (double-rounding ambiguity)" % (fn, py_args, max_dps))


def vec(id_, description, function_name, arg_bits_list, expected_bits, provenance="oracle"):
    args = [lit_double_bits(b) for b in arg_bits_list]
    expr = call_node(FUNCTION_IDS[function_name], *args)
    return {
        "id": "eval-transcendental/" + id_,
        "category": "eval",
        "description": description,
        "provenance": provenance,
        "requires": ["conformant-transcendentals"],
        "wireHex": predicate_record(expr).hex(),
        "candidate": {"namespaceChain": [], "fields": {}},
        "expected": {"outcome": "value", "type": "DOUBLE", "value": "0x%016x" % (expected_bits & 0xFFFFFFFFFFFFFFFF)},
    }


# ============================================================================
# Vector construction
# ============================================================================

def build_unary_vectors(name, fn, domain_check=None, sign_preserving_zero=False):
    """`fn` is the mpmath unary function; `domain_check(x) -> bool` restricts
    the pseudorandom sample to the function's actual domain (asin/acos).

    `sign_preserving_zero` (STD-011 T3-phase-2 correction): sin/tan/asin/atan
    are each odd (f(-x) = -f(x)) with f(0) = 0, so continuity alone pins
    f(-0.0) = -0.0 -- independently confirmed against Python's own `math`
    module (libm), and both `java.lang.Math` and `java.lang.StrictMath` (all
    three agree). mpmath's `mpf` type does NOT track IEEE signed zero at all
    (`mpmath.mpf(-0.0)` silently becomes plain `mpf('0.0')`,
    `mpmath.sign(mpmath.mpf(-0.0))` is `0.0`, and `float(mpmath.mpf(-0.0))`
    is `+0.0`, not `-0.0`) -- so routing the "zero-" hard case through
    `correctly_rounded()` for these functions previously emitted a silently
    wrong (+0.0) expected value. `cos`/`acos` are NOT sign-preserving at
    zero (`cos(+-0) = 1`, `acos(+-0) = pi/2` regardless of sign) and must
    NOT set this flag."""
    out = []
    hard_doubles = {
        "zero+": 0.0,
        "zero-": -0.0,
        "one": 1.0,
        "neg-one": -1.0,
        "half": 0.5,
        "neg-half": -0.5,
        "pi/2-nearest": bits_to_double(0x3FF921FB54442D18),  # nearest double to pi/2
        "pi-nearest": bits_to_double(0x400921FB54442D18),    # nearest double to pi
        "3pi/2-nearest": 3.0 * bits_to_double(0x3FF921FB54442D18),
        "2pi-nearest": 2.0 * bits_to_double(0x400921FB54442D18),
        "neg-pi/2-nearest": -bits_to_double(0x3FF921FB54442D18),
        "huge-1e300": 1e300,
        "huge-neg-1e300": -1e300,
        # STD-011 T3-phase-2 addition: intermediate-magnitude reduction-stress points
        # (argument reduction mod pi/2 against a huge-magnitude argument is the classic
        # correctly-rounded-sin/cos/tan failure mode -- these exercise reduction at a
        # spread of exponents, not just the single 1e300 extreme already above).
        "huge-1e15": 1e15,
        "huge-1e50": 1e50,
        "huge-1e100": 1e100,
        "huge-1e200": 1e200,
        "huge-2pow100": 2.0 ** 100,
        "huge-neg-2pow100": -(2.0 ** 100),
        "largest-finite": bits_to_double(0x7FEFFFFFFFFFFFFF),
        "neg-largest-finite": bits_to_double(0xFFEFFFFFFFFFFFFF),
        "smallest-normal": bits_to_double(0x0010000000000000),
        "smallest-subnormal": bits_to_double(0x0000000000000001),
        "neg-smallest-subnormal": bits_to_double(0x8000000000000001),
        "largest-subnormal": bits_to_double(0x000FFFFFFFFFFFFF),
    }
    for label, x in hard_doubles.items():
        if domain_check is not None and not domain_check(x):
            continue
        if sign_preserving_zero and label in ("zero+", "zero-"):
            out.append(vec(
                f"{name}-hard-{label}",
                f"{name}({x!r}) [{label}]: sign-preserving exact case (STD-011 Sec 7.2 -- "
                f"{name} is odd, f(0)=0, so continuity pins f(-0.0)=-0.0). NOT routed through "
                f"mpmath (mpf loses IEEE signed-zero sign -- see build_unary_vectors docstring); "
                f"independently confirmed against Python's math module and Java's Math/StrictMath.",
                name, [double_bits(x)], double_bits(x),
                provenance="spec-sign-preserving-zero",
            ))
            continue
        try:
            d = correctly_rounded(fn, x)
        except RuntimeError as e:
            sys.stderr.write("SKIPPING (oracle could not certify): %s(%r): %s\n" % (name, x, e))
            continue
        out.append(vec(
            f"{name}-hard-{label}",
            f"{name}({x!r}) [{label}], correctly rounded (STD-011 Sec 7.5).",
            name, [double_bits(x)], double_bits(d),
        ))
    return out


def build_random_vectors(name, fn, rng, count, sampler):
    out = []
    for i in range(count):
        x = sampler(rng)
        try:
            d = correctly_rounded(fn, x)
        except RuntimeError as e:
            sys.stderr.write("SKIPPING random vector (oracle could not certify): %s(%r): %s\n" % (name, x, e))
            continue
        out.append(vec(
            f"{name}-random-{i:02d}",
            f"{name}({x!r}), correctly rounded; pseudorandom input, seed pinned in this "
            f"module's build_all() (reproducible).",
            name, [double_bits(x)], double_bits(d),
        ))
    return out


def build_atan2_vectors(rng, random_count):
    out = []
    # ---- IEEE-754/C99 signed-zero and quadrant boundary table (Sec 7.2 row 15) ----
    pi = correctly_rounded(lambda: mpmath.pi)
    pi_2 = correctly_rounded(lambda: mpmath.pi / 2)
    three_pi_4 = correctly_rounded(lambda: 3 * mpmath.pi / 4)

    def zero_case(label, y_bits, x_bits, expected_bits):
        out.append(vec(f"atan2-zero-{label}",
                        f"atan2 IEEE-754 zero/quadrant boundary case [{label}] (Sec 7.2 row 15).",
                        "atan2", [y_bits, x_bits], expected_bits))

    POS0, NEG0 = 0x0000000000000000, 0x8000000000000000
    ONE = double_bits(1.0)
    NEG_ONE = double_bits(-1.0)
    zero_case("+0,+x", POS0, ONE, POS0)
    zero_case("-0,+x", NEG0, ONE, NEG0)
    zero_case("+0,-x", POS0, NEG_ONE, double_bits(pi))
    zero_case("-0,-x", NEG0, NEG_ONE, double_bits(-pi))
    zero_case("+y,+0", ONE, POS0, double_bits(pi_2))
    zero_case("-y,+0", NEG_ONE, POS0, double_bits(-pi_2))
    zero_case("+y,-0", ONE, NEG0, double_bits(pi_2))
    zero_case("-y,-0", NEG_ONE, NEG0, double_bits(-pi_2))

    # ---- double-zero cases (both arguments zero): C99 F.10.1.4 --------------
    # atan2(+-0, +0) = +-0; atan2(+-0, -0) = +-pi. Board-review fix list item 6
    # (numerics seat): these four cells were previously missing from the
    # table above, which only covered one-zero/one-nonzero combinations.
    zero_case("+0,+0", POS0, POS0, POS0)
    zero_case("-0,+0", NEG0, POS0, NEG0)
    zero_case("+0,-0", POS0, NEG0, double_bits(pi))
    zero_case("-0,-0", NEG0, NEG0, double_bits(-pi))

    # ---- hard finite cases (large args, argument-reduction stress) ----
    hard_pairs = {
        "huge-both": (1e300, 1e300),
        "huge-y-small-x": (1e300, 1.0),
        "small-y-huge-x": (1.0, 1e300),
        "equal-magnitude-45deg": (1.0, 1.0),
        "third-quadrant": (-1.0, -1.0),
        "subnormal-both": (bits_to_double(1), bits_to_double(2)),
    }
    for label, (y, x) in hard_pairs.items():
        d = correctly_rounded(mpmath.atan2, y, x)
        out.append(vec(f"atan2-hard-{label}",
                        f"atan2({y!r}, {x!r}) [{label}], correctly rounded.",
                        "atan2", [double_bits(y), double_bits(x)], double_bits(d)))

    # ---- pseudorandom pairs ----
    for i in range(random_count):
        y = rng.uniform(-1e6, 1e6)
        x = rng.uniform(-1e6, 1e6)
        d = correctly_rounded(mpmath.atan2, y, x)
        out.append(vec(f"atan2-random-{i:02d}",
                        f"atan2({y!r}, {x!r}), correctly rounded; pseudorandom, seed pinned.",
                        "atan2", [double_bits(y), double_bits(x)], double_bits(d)))
    return out


def build_all(seed=20260720):
    """`seed` is recorded here (not merely "some seed was used") so this
    module's random vectors are byte-for-byte reproducible by anyone re-
    running it -- required for a corpus that must be able to explain, years
    later, exactly how every one of its bit patterns was derived."""
    rng = random.Random(seed)
    out = []

    out += build_unary_vectors("sin", mpmath.sin, sign_preserving_zero=True)
    out += build_unary_vectors("cos", mpmath.cos)
    out += build_unary_vectors("tan", mpmath.tan, sign_preserving_zero=True)
    out += build_unary_vectors("asin", mpmath.asin, domain_check=lambda x: abs(x) <= 1.0, sign_preserving_zero=True)
    out += build_unary_vectors("acos", mpmath.acos, domain_check=lambda x: abs(x) <= 1.0)
    out += build_unary_vectors("atan", mpmath.atan, sign_preserving_zero=True)

    out += build_random_vectors("sin", mpmath.sin, rng, 10, lambda r: r.uniform(-1e6, 1e6))
    out += build_random_vectors("cos", mpmath.cos, rng, 10, lambda r: r.uniform(-1e6, 1e6))
    out += build_random_vectors("tan", mpmath.tan, rng, 10, lambda r: r.uniform(-1e6, 1e6))
    out += build_random_vectors("asin", mpmath.asin, rng, 5, lambda r: r.uniform(-1.0, 1.0))
    out += build_random_vectors("acos", mpmath.acos, rng, 5, lambda r: r.uniform(-1.0, 1.0))
    out += build_random_vectors("atan", mpmath.atan, rng, 5, lambda r: r.uniform(-1e6, 1e6))

    out += build_atan2_vectors(rng, random_count=5)

    return out


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "vectors", "eval-transcendental.json")
    vectors = build_all()
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(vectors, f, indent=2, sort_keys=True)
        f.write("\n")
    sys.stderr.write("Wrote %d transcendental conformance vectors to %s\n" % (len(vectors), out_path))


if __name__ == "__main__":
    main()
