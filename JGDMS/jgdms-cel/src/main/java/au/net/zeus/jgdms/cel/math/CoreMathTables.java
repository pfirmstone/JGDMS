/*
 * CoreMathTables: Java port of the constant tables of CORE-MATH's binary64 sine,
 * src/binary64/sin/sin.c at commit 018fa97cbdacf59924b899a9dfa422d532cb2bb7. The same
 * tables are copied unchanged into src/binary64/cos/cos.c at that commit (cos.c says so in
 * its header; every table's text was compared between the two files and is identical), so
 * this one class serves both ports and carries both files' copyright lines.
 *
 * DERIVATION: this file is a Java port, that is a modified and derived work, of the constant tables of
 * src/binary64/sin/sin.c (repeated in src/binary64/cos/cos.c) of the CORE-MATH project at commit
 * 018fa97cbdacf59924b899a9dfa422d532cb2bb7. It is not part of CORE-MATH and is not endorsed by
 * its authors. The copyright and permission notice below are the upstream's, reproduced without change.
 *
 * Copyright (c) 2026 Paul Zimmermann and Alexei Sibidanov
 * Copyright (c) 2026 Paul Zimmermann
 *
 * This file is part of the CORE-MATH project
 * (https://core-math.gitlabpages.inria.fr/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package au.net.zeus.jgdms.cel.math;

/**
 * The constant tables CORE-MATH's binary64 {@code sin} and {@code cos} share: the 1/(2*pi) bit
 * table of the Payne-Hanek reduction, the two double-double sine/cosine tables of the fast path,
 * the 128-bit fixed-point tables and polynomials of the accurate path, and the two-word
 * constants of the moderate-argument reduction.
 * <p>
 * <b>Bit-exact.</b> Every value is a literal copy of the C source's: a {@code double} as the same
 * hexadecimal floating-point literal, a 64-bit word as the same hexadecimal digits. A rounded
 * decimal would change a table entry and, silently, a result. Two checks guard them. {@code CoreMathConstantsTest}
 * re-reads {@code sin.c} and {@code cos.c} and compares every entry here to them, bit for bit; it needs the
 * CORE-MATH clone, so it is skipped without it (and FAILS if the full tier is requested with
 * {@code -Dcore.math.full=true}). {@code CoreMathTablesDigestTest} needs nothing: it hashes a canonical
 * rendering of every table (SHA-256) and compares the digest, committed as a test resource, so a changed
 * bit fails on every machine.
 * <p>
 * <b>Layout.</b> The C {@code double U1[128][4]} is flattened row-major: {@code U1[4*j + c]}.
 * A C {@code u128} table is an array of {@link U128}. The arrays are private; see the accessors at the end.
 */
final class CoreMathTables {

    private CoreMathTables() {}

    /** C: sin.c:563 {@code pih}: high word of -pi/2^14 (the fast path's reduction constant). */
    static final double PIH = -0x1.921fb54442d18p-13;

    /** C: sin.c:564 {@code pil}: low word of -pi/2^14. */
    static final double PIL = -0x1.1a62633145c07p-67;

    /** C: sin.c:127-149 `_T[20]`: 1/(2*pi) ~ T[0]/2^0 + T[1]/2^64 + ... (T[0] = 0, entry 20 unused in the C). */
    private static final long[] T = {
        0L,
        0x28be60db9391054aL,
        0x7f09d5f47d4d3770L,
        0x36d8a5664f10e410L,
        0x7f9458eaf7aef158L,
        0x6dc91b8e909374b8L,
        0x1924bba82746487L,
        0x3f877ac72c4a69cfL,
        0xba208d7d4baed121L,
        0x3a671c09ad17df90L,
        0x4e64758e60d4ce7dL,
        0x272117e2ef7e4a0eL,
        0xc7fe25fff7816603L,
        0xfbcbc462d6829b47L,
        0xdb4d9fb3c9f2c26dL,
        0xd3d18fd9a797fa8bL,
        0x5d49eeb1faf97c5eL,
        0xcf41ce7de294a4baL,
        0x9afed7ec47e35742L,
        0x1580cc11bf1edaeaL,
    };

    /** C: sin.c:297-426 `U1[128][4]`, flattened row-major: U1[4*j+0..3] = sh, sl, ch, cl of sin/cos(j*pi/2^7). */
    private static final double[] U1 = {
        0x0p+0, 0x0p+0, 0x1p+0, 0x0p+0,
        0x1.92155f7a3667ep-6, -0x1.b1d63091a013p-64, 0x1.ffd886084cd0dp-1, -0x1.1354d4556e4cbp-55,
        0x1.91f65f10dd814p-5, -0x1.912bd0d569a9p-61, 0x1.ff621e3796d7ep-1, -0x1.c57bc2e24aa15p-57,
        0x1.2d52092ce19f6p-4, -0x1.9a088a8bf6b2cp-59, 0x1.fe9cdad01883ap-1, 0x1.521ecd0c67e35p-57,
        0x1.917a6bc29b42cp-4, -0x1.e2718d26ed688p-60, 0x1.fd88da3d12526p-1, -0x1.87df6378811c7p-55,
        0x1.f564e56a9730ep-4, 0x1.a2704729ae56dp-59, 0x1.fc26470e19fd3p-1, 0x1.1ec8668ecaceep-55,
        0x1.2c8106e8e613ap-3, 0x1.13000a89a11ep-58, 0x1.fa7557f08a517p-1, -0x1.7a0a8ca13571fp-55,
        0x1.5e214448b3fc6p-3, 0x1.531ff779ddac6p-57, 0x1.f8764fa714ba9p-1, 0x1.ab256778ffcb6p-56,
        0x1.8f8b83c69a60bp-3, -0x1.26d19b9ff8d82p-57, 0x1.f6297cff75cbp-1, 0x1.562172a361fd3p-56,
        0x1.c0b826a7e4f63p-3, -0x1.af1439e521935p-62, 0x1.f38f3ac64e589p-1, -0x1.d7bafb51f72e6p-56,
        0x1.f19f97b215f1bp-3, -0x1.42deef11da2c4p-57, 0x1.f0a7efb9230d7p-1, 0x1.52c7adc6b4989p-56,
        0x1.111d262b1f677p-2, 0x1.824c20ab7aa9ap-56, 0x1.ed740e7684963p-1, 0x1.e82c791f59cc2p-56,
        0x1.294062ed59f06p-2, -0x1.5d28da2c4612dp-56, 0x1.e9f4156c62ddap-1, 0x1.760b1e2e3f81ep-55,
        0x1.4135c94176601p-2, 0x1.0c97c4afa2518p-56, 0x1.e6288ec48e112p-1, -0x1.16b56f2847754p-57,
        0x1.58f9a75ab1fddp-2, -0x1.efdc0d58cf62p-62, 0x1.e212104f686e5p-1, -0x1.014c76c126527p-55,
        0x1.7088530fa459fp-2, -0x1.44b19e0864c5dp-56, 0x1.ddb13b6ccc23cp-1, 0x1.83c37c6107db3p-55,
        0x1.87de2a6aea963p-2, -0x1.72cedd3d5a61p-57, 0x1.d906bcf328d46p-1, 0x1.457e610231ac2p-56,
        0x1.9ef7943a8ed8ap-2, 0x1.6da81290bdbabp-57, 0x1.d4134d14dc93ap-1, -0x1.4ef5295d25af2p-55,
        0x1.b5d1009e15ccp-2, 0x1.5b362cb974183p-57, 0x1.ced7af43cc773p-1, -0x1.e7b6bb5ab58aep-58,
        0x1.cc66e9931c45ep-2, 0x1.6850e59c37f8fp-58, 0x1.c954b213411f5p-1, -0x1.2fb761e946603p-58,
        0x1.e2b5d3806f63bp-2, 0x1.e0d891d3c6841p-58, 0x1.c38b2f180bdb1p-1, -0x1.6e0b1757c8d07p-56,
        0x1.f8ba4dbf89abap-2, -0x1.2ec1fc1b776b8p-60, 0x1.bd7c0ac6f952ap-1, -0x1.825a732ac700ap-55,
        0x1.073879922ffeep-1, -0x1.a5a014347406cp-55, 0x1.b728345196e3ep-1, -0x1.bc69f324e6d61p-55,
        0x1.11eb3541b4b23p-1, -0x1.ef23b69abe4f1p-55, 0x1.b090a581502p-1, -0x1.926da300ffccep-55,
        0x1.1c73b39ae68c8p-1, 0x1.b25dd267f66p-55, 0x1.a9b66290ea1a3p-1, 0x1.9f630e8b6dac8p-60,
        0x1.26d054cdd12dfp-1, -0x1.5da743ef3770cp-55, 0x1.a29a7a0462782p-1, -0x1.128bb015df175p-56,
        0x1.30ff7fce17035p-1, -0x1.efcc626f74a6fp-57, 0x1.9b3e047f38741p-1, -0x1.30ee286712474p-55,
        0x1.3affa292050b9p-1, 0x1.e3e25e3954964p-56, 0x1.93a22499263fbp-1, 0x1.3d419a920df0bp-55,
        0x1.44cf325091dd6p-1, 0x1.8076a2cfdc6b3p-57, 0x1.8bc806b151741p-1, -0x1.2c5e12ed1336dp-55,
        0x1.4e6cabbe3e5e9p-1, 0x1.3c293edceb327p-57, 0x1.83b0e0bff976ep-1, -0x1.6f420f8ea3475p-56,
        0x1.57d69348cecap-1, -0x1.75720992bfbb2p-55, 0x1.7b5df226aafafp-1, -0x1.0f537acdf0ad7p-56,
        0x1.610b7551d2cdfp-1, -0x1.251b352ff2a37p-56, 0x1.72d0837efff96p-1, 0x1.0d4ef0f1d915cp-55,
        0x1.6a09e667f3bcdp-1, -0x1.bdd3413b26456p-55, 0x1.6a09e667f3bcdp-1, -0x1.bdd3413b26456p-55,
        0x1.72d0837efff96p-1, 0x1.0d4ef0f1d915cp-55, 0x1.610b7551d2cdfp-1, -0x1.251b352ff2a37p-56,
        0x1.7b5df226aafafp-1, -0x1.0f537acdf0ad7p-56, 0x1.57d69348cecap-1, -0x1.75720992bfbb2p-55,
        0x1.83b0e0bff976ep-1, -0x1.6f420f8ea3475p-56, 0x1.4e6cabbe3e5e9p-1, 0x1.3c293edceb327p-57,
        0x1.8bc806b151741p-1, -0x1.2c5e12ed1336dp-55, 0x1.44cf325091dd6p-1, 0x1.8076a2cfdc6b3p-57,
        0x1.93a22499263fbp-1, 0x1.3d419a920df0bp-55, 0x1.3affa292050b9p-1, 0x1.e3e25e3954964p-56,
        0x1.9b3e047f38741p-1, -0x1.30ee286712474p-55, 0x1.30ff7fce17035p-1, -0x1.efcc626f74a6fp-57,
        0x1.a29a7a0462782p-1, -0x1.128bb015df175p-56, 0x1.26d054cdd12dfp-1, -0x1.5da743ef3770cp-55,
        0x1.a9b66290ea1a3p-1, 0x1.9f630e8b6dac8p-60, 0x1.1c73b39ae68c8p-1, 0x1.b25dd267f66p-55,
        0x1.b090a581502p-1, -0x1.926da300ffccep-55, 0x1.11eb3541b4b23p-1, -0x1.ef23b69abe4f1p-55,
        0x1.b728345196e3ep-1, -0x1.bc69f324e6d61p-55, 0x1.073879922ffeep-1, -0x1.a5a014347406cp-55,
        0x1.bd7c0ac6f952ap-1, -0x1.825a732ac700ap-55, 0x1.f8ba4dbf89abap-2, -0x1.2ec1fc1b776b8p-60,
        0x1.c38b2f180bdb1p-1, -0x1.6e0b1757c8d07p-56, 0x1.e2b5d3806f63bp-2, 0x1.e0d891d3c6841p-58,
        0x1.c954b213411f5p-1, -0x1.2fb761e946603p-58, 0x1.cc66e9931c45ep-2, 0x1.6850e59c37f8fp-58,
        0x1.ced7af43cc773p-1, -0x1.e7b6bb5ab58aep-58, 0x1.b5d1009e15ccp-2, 0x1.5b362cb974183p-57,
        0x1.d4134d14dc93ap-1, -0x1.4ef5295d25af2p-55, 0x1.9ef7943a8ed8ap-2, 0x1.6da81290bdbabp-57,
        0x1.d906bcf328d46p-1, 0x1.457e610231ac2p-56, 0x1.87de2a6aea963p-2, -0x1.72cedd3d5a61p-57,
        0x1.ddb13b6ccc23cp-1, 0x1.83c37c6107db3p-55, 0x1.7088530fa459fp-2, -0x1.44b19e0864c5dp-56,
        0x1.e212104f686e5p-1, -0x1.014c76c126527p-55, 0x1.58f9a75ab1fddp-2, -0x1.efdc0d58cf62p-62,
        0x1.e6288ec48e112p-1, -0x1.16b56f2847754p-57, 0x1.4135c94176601p-2, 0x1.0c97c4afa2518p-56,
        0x1.e9f4156c62ddap-1, 0x1.760b1e2e3f81ep-55, 0x1.294062ed59f06p-2, -0x1.5d28da2c4612dp-56,
        0x1.ed740e7684963p-1, 0x1.e82c791f59cc2p-56, 0x1.111d262b1f677p-2, 0x1.824c20ab7aa9ap-56,
        0x1.f0a7efb9230d7p-1, 0x1.52c7adc6b4989p-56, 0x1.f19f97b215f1bp-3, -0x1.42deef11da2c4p-57,
        0x1.f38f3ac64e589p-1, -0x1.d7bafb51f72e6p-56, 0x1.c0b826a7e4f63p-3, -0x1.af1439e521935p-62,
        0x1.f6297cff75cbp-1, 0x1.562172a361fd3p-56, 0x1.8f8b83c69a60bp-3, -0x1.26d19b9ff8d82p-57,
        0x1.f8764fa714ba9p-1, 0x1.ab256778ffcb6p-56, 0x1.5e214448b3fc6p-3, 0x1.531ff779ddac6p-57,
        0x1.fa7557f08a517p-1, -0x1.7a0a8ca13571fp-55, 0x1.2c8106e8e613ap-3, 0x1.13000a89a11ep-58,
        0x1.fc26470e19fd3p-1, 0x1.1ec8668ecaceep-55, 0x1.f564e56a9730ep-4, 0x1.a2704729ae56dp-59,
        0x1.fd88da3d12526p-1, -0x1.87df6378811c7p-55, 0x1.917a6bc29b42cp-4, -0x1.e2718d26ed688p-60,
        0x1.fe9cdad01883ap-1, 0x1.521ecd0c67e35p-57, 0x1.2d52092ce19f6p-4, -0x1.9a088a8bf6b2cp-59,
        0x1.ff621e3796d7ep-1, -0x1.c57bc2e24aa15p-57, 0x1.91f65f10dd814p-5, -0x1.912bd0d569a9p-61,
        0x1.ffd886084cd0dp-1, -0x1.1354d4556e4cbp-55, 0x1.92155f7a3667ep-6, -0x1.b1d63091a013p-64,
        0x1p+0, 0x0p+0, 0x0p+0, 0x0p+0,
        0x1.ffd886084cd0dp-1, -0x1.1354d4556e4cbp-55, -0x1.92155f7a3667ep-6, 0x1.b1d63091a013p-64,
        0x1.ff621e3796d7ep-1, -0x1.c57bc2e24aa15p-57, -0x1.91f65f10dd814p-5, 0x1.912bd0d569a9p-61,
        0x1.fe9cdad01883ap-1, 0x1.521ecd0c67e35p-57, -0x1.2d52092ce19f6p-4, 0x1.9a088a8bf6b2cp-59,
        0x1.fd88da3d12526p-1, -0x1.87df6378811c7p-55, -0x1.917a6bc29b42cp-4, 0x1.e2718d26ed688p-60,
        0x1.fc26470e19fd3p-1, 0x1.1ec8668ecaceep-55, -0x1.f564e56a9730ep-4, -0x1.a2704729ae56dp-59,
        0x1.fa7557f08a517p-1, -0x1.7a0a8ca13571fp-55, -0x1.2c8106e8e613ap-3, -0x1.13000a89a11ep-58,
        0x1.f8764fa714ba9p-1, 0x1.ab256778ffcb6p-56, -0x1.5e214448b3fc6p-3, -0x1.531ff779ddac6p-57,
        0x1.f6297cff75cbp-1, 0x1.562172a361fd3p-56, -0x1.8f8b83c69a60bp-3, 0x1.26d19b9ff8d82p-57,
        0x1.f38f3ac64e589p-1, -0x1.d7bafb51f72e6p-56, -0x1.c0b826a7e4f63p-3, 0x1.af1439e521935p-62,
        0x1.f0a7efb9230d7p-1, 0x1.52c7adc6b4989p-56, -0x1.f19f97b215f1bp-3, 0x1.42deef11da2c4p-57,
        0x1.ed740e7684963p-1, 0x1.e82c791f59cc2p-56, -0x1.111d262b1f677p-2, -0x1.824c20ab7aa9ap-56,
        0x1.e9f4156c62ddap-1, 0x1.760b1e2e3f81ep-55, -0x1.294062ed59f06p-2, 0x1.5d28da2c4612dp-56,
        0x1.e6288ec48e112p-1, -0x1.16b56f2847754p-57, -0x1.4135c94176601p-2, -0x1.0c97c4afa2518p-56,
        0x1.e212104f686e5p-1, -0x1.014c76c126527p-55, -0x1.58f9a75ab1fddp-2, 0x1.efdc0d58cf62p-62,
        0x1.ddb13b6ccc23cp-1, 0x1.83c37c6107db3p-55, -0x1.7088530fa459fp-2, 0x1.44b19e0864c5dp-56,
        0x1.d906bcf328d46p-1, 0x1.457e610231ac2p-56, -0x1.87de2a6aea963p-2, 0x1.72cedd3d5a61p-57,
        0x1.d4134d14dc93ap-1, -0x1.4ef5295d25af2p-55, -0x1.9ef7943a8ed8ap-2, -0x1.6da81290bdbabp-57,
        0x1.ced7af43cc773p-1, -0x1.e7b6bb5ab58aep-58, -0x1.b5d1009e15ccp-2, -0x1.5b362cb974183p-57,
        0x1.c954b213411f5p-1, -0x1.2fb761e946603p-58, -0x1.cc66e9931c45ep-2, -0x1.6850e59c37f8fp-58,
        0x1.c38b2f180bdb1p-1, -0x1.6e0b1757c8d07p-56, -0x1.e2b5d3806f63bp-2, -0x1.e0d891d3c6841p-58,
        0x1.bd7c0ac6f952ap-1, -0x1.825a732ac700ap-55, -0x1.f8ba4dbf89abap-2, 0x1.2ec1fc1b776b8p-60,
        0x1.b728345196e3ep-1, -0x1.bc69f324e6d61p-55, -0x1.073879922ffeep-1, 0x1.a5a014347406cp-55,
        0x1.b090a581502p-1, -0x1.926da300ffccep-55, -0x1.11eb3541b4b23p-1, 0x1.ef23b69abe4f1p-55,
        0x1.a9b66290ea1a3p-1, 0x1.9f630e8b6dac8p-60, -0x1.1c73b39ae68c8p-1, -0x1.b25dd267f66p-55,
        0x1.a29a7a0462782p-1, -0x1.128bb015df175p-56, -0x1.26d054cdd12dfp-1, 0x1.5da743ef3770cp-55,
        0x1.9b3e047f38741p-1, -0x1.30ee286712474p-55, -0x1.30ff7fce17035p-1, 0x1.efcc626f74a6fp-57,
        0x1.93a22499263fbp-1, 0x1.3d419a920df0bp-55, -0x1.3affa292050b9p-1, -0x1.e3e25e3954964p-56,
        0x1.8bc806b151741p-1, -0x1.2c5e12ed1336dp-55, -0x1.44cf325091dd6p-1, -0x1.8076a2cfdc6b3p-57,
        0x1.83b0e0bff976ep-1, -0x1.6f420f8ea3475p-56, -0x1.4e6cabbe3e5e9p-1, -0x1.3c293edceb327p-57,
        0x1.7b5df226aafafp-1, -0x1.0f537acdf0ad7p-56, -0x1.57d69348cecap-1, 0x1.75720992bfbb2p-55,
        0x1.72d0837efff96p-1, 0x1.0d4ef0f1d915cp-55, -0x1.610b7551d2cdfp-1, 0x1.251b352ff2a37p-56,
        0x1.6a09e667f3bcdp-1, -0x1.bdd3413b26456p-55, -0x1.6a09e667f3bcdp-1, 0x1.bdd3413b26456p-55,
        0x1.610b7551d2cdfp-1, -0x1.251b352ff2a37p-56, -0x1.72d0837efff96p-1, -0x1.0d4ef0f1d915cp-55,
        0x1.57d69348cecap-1, -0x1.75720992bfbb2p-55, -0x1.7b5df226aafafp-1, 0x1.0f537acdf0ad7p-56,
        0x1.4e6cabbe3e5e9p-1, 0x1.3c293edceb327p-57, -0x1.83b0e0bff976ep-1, 0x1.6f420f8ea3475p-56,
        0x1.44cf325091dd6p-1, 0x1.8076a2cfdc6b3p-57, -0x1.8bc806b151741p-1, 0x1.2c5e12ed1336dp-55,
        0x1.3affa292050b9p-1, 0x1.e3e25e3954964p-56, -0x1.93a22499263fbp-1, -0x1.3d419a920df0bp-55,
        0x1.30ff7fce17035p-1, -0x1.efcc626f74a6fp-57, -0x1.9b3e047f38741p-1, 0x1.30ee286712474p-55,
        0x1.26d054cdd12dfp-1, -0x1.5da743ef3770cp-55, -0x1.a29a7a0462782p-1, 0x1.128bb015df175p-56,
        0x1.1c73b39ae68c8p-1, 0x1.b25dd267f66p-55, -0x1.a9b66290ea1a3p-1, -0x1.9f630e8b6dac8p-60,
        0x1.11eb3541b4b23p-1, -0x1.ef23b69abe4f1p-55, -0x1.b090a581502p-1, 0x1.926da300ffccep-55,
        0x1.073879922ffeep-1, -0x1.a5a014347406cp-55, -0x1.b728345196e3ep-1, 0x1.bc69f324e6d61p-55,
        0x1.f8ba4dbf89abap-2, -0x1.2ec1fc1b776b8p-60, -0x1.bd7c0ac6f952ap-1, 0x1.825a732ac700ap-55,
        0x1.e2b5d3806f63bp-2, 0x1.e0d891d3c6841p-58, -0x1.c38b2f180bdb1p-1, 0x1.6e0b1757c8d07p-56,
        0x1.cc66e9931c45ep-2, 0x1.6850e59c37f8fp-58, -0x1.c954b213411f5p-1, 0x1.2fb761e946603p-58,
        0x1.b5d1009e15ccp-2, 0x1.5b362cb974183p-57, -0x1.ced7af43cc773p-1, 0x1.e7b6bb5ab58aep-58,
        0x1.9ef7943a8ed8ap-2, 0x1.6da81290bdbabp-57, -0x1.d4134d14dc93ap-1, 0x1.4ef5295d25af2p-55,
        0x1.87de2a6aea963p-2, -0x1.72cedd3d5a61p-57, -0x1.d906bcf328d46p-1, -0x1.457e610231ac2p-56,
        0x1.7088530fa459fp-2, -0x1.44b19e0864c5dp-56, -0x1.ddb13b6ccc23cp-1, -0x1.83c37c6107db3p-55,
        0x1.58f9a75ab1fddp-2, -0x1.efdc0d58cf62p-62, -0x1.e212104f686e5p-1, 0x1.014c76c126527p-55,
        0x1.4135c94176601p-2, 0x1.0c97c4afa2518p-56, -0x1.e6288ec48e112p-1, 0x1.16b56f2847754p-57,
        0x1.294062ed59f06p-2, -0x1.5d28da2c4612dp-56, -0x1.e9f4156c62ddap-1, -0x1.760b1e2e3f81ep-55,
        0x1.111d262b1f677p-2, 0x1.824c20ab7aa9ap-56, -0x1.ed740e7684963p-1, -0x1.e82c791f59cc2p-56,
        0x1.f19f97b215f1bp-3, -0x1.42deef11da2c4p-57, -0x1.f0a7efb9230d7p-1, -0x1.52c7adc6b4989p-56,
        0x1.c0b826a7e4f63p-3, -0x1.af1439e521935p-62, -0x1.f38f3ac64e589p-1, 0x1.d7bafb51f72e6p-56,
        0x1.8f8b83c69a60bp-3, -0x1.26d19b9ff8d82p-57, -0x1.f6297cff75cbp-1, -0x1.562172a361fd3p-56,
        0x1.5e214448b3fc6p-3, 0x1.531ff779ddac6p-57, -0x1.f8764fa714ba9p-1, -0x1.ab256778ffcb6p-56,
        0x1.2c8106e8e613ap-3, 0x1.13000a89a11ep-58, -0x1.fa7557f08a517p-1, 0x1.7a0a8ca13571fp-55,
        0x1.f564e56a9730ep-4, 0x1.a2704729ae56dp-59, -0x1.fc26470e19fd3p-1, -0x1.1ec8668ecaceep-55,
        0x1.917a6bc29b42cp-4, -0x1.e2718d26ed688p-60, -0x1.fd88da3d12526p-1, 0x1.87df6378811c7p-55,
        0x1.2d52092ce19f6p-4, -0x1.9a088a8bf6b2cp-59, -0x1.fe9cdad01883ap-1, -0x1.521ecd0c67e35p-57,
        0x1.91f65f10dd814p-5, -0x1.912bd0d569a9p-61, -0x1.ff621e3796d7ep-1, 0x1.c57bc2e24aa15p-57,
        0x1.92155f7a3667ep-6, -0x1.b1d63091a013p-64, -0x1.ffd886084cd0dp-1, 0x1.1354d4556e4cbp-55,
    };

    /** C: sin.c:432-561 `U2[128][4]`, flattened row-major: U2[4*j+0..3] = sh, sl, ch, cl of sin/cos(j*pi/2^14). */
    private static final double[] U2 = {
        0x0p+0, 0x0p+0, 0x1p+0, 0x0p+0,
        0x1.921fb51aeb57cp-13, -0x1.a6e1d4916c435p-67, 0x1.ffffff621619cp-1, -0x1.7507dbbbd8fe6p-55,
        0x1.921fb49ee4ea6p-12, 0x1.e894d744a453ep-66, 0x1.fffffd8858675p-1, -0x1.79f0e54748eabp-55,
        0x1.2d97c6dc23a75p-11, -0x1.1c27727253849p-66, 0x1.fffffa72c6e9dp-1, 0x1.ffa0e2a0e5c29p-56,
        0x1.921fb2aecb36p-11, 0x1.876157e566b4cp-65, 0x1.fffff62161a34p-1, -0x1.136dcb1f9b9c4p-57,
        0x1.f6a79d8965ebap-11, -0x1.a2a951670a396p-65, 0x1.fffff09428963p-1, 0x1.2f505fba9c0bp-55,
        0x1.2d97c396f8497p-10, -0x1.45cb4cc3d0fb1p-66, 0x1.ffffe9cb1bc62p-1, 0x1.0fe9fa3478ec9p-56,
        0x1.5fdbb7af33fbbp-10, 0x1.c0c37a5d5101dp-65, 0x1.ffffe1c63b373p-1, 0x1.a50f155e87e56p-55,
        0x1.921faaee6472ep-10, -0x1.ee52e284a9df8p-64, 0x1.ffffd88586ee6p-1, 0x1.1af64f173ae5bp-55,
        0x1.c4639d358815ap-10, 0x1.0ffa0e86b4dccp-69, 0x1.ffffce08fef16p-1, 0x1.92e420a03bf59p-58,
        0x1.f6a78e659d4b6p-10, 0x1.cb8a9b355ac8p-64, 0x1.ffffc250a346ap-1, 0x1.d099c02280c58p-56,
        0x1.1475bf2fd13e2p-9, -0x1.4ae6ded9797ccp-63, 0x1.ffffb55c73f56p-1, 0x1.ee2ae1596963ap-55,
        0x1.2d97b6824b087p-9, -0x1.9dae69dd4f97fp-63, 0x1.ffffa72c7105bp-1, -0x1.564ec4a452371p-55,
        0x1.46b9ad1abb397p-9, -0x1.ac1b1950e9dc6p-64, 0x1.ffff97c09a803p-1, -0x1.961ec991858f1p-56,
        0x1.5fdba2e9a1066p-9, 0x1.f1ed8abaaa608p-64, 0x1.ffff8718f06e7p-1, 0x1.793938ee3fc9ap-57,
        0x1.78fd97df7ba51p-9, -0x1.2f2580c9ba856p-63, 0x1.ffff753572dacp-1, -0x1.5df4d689b3227p-57,
        0x1.921f8becca4bap-9, 0x1.2ba407bcab5b2p-63, 0x1.ffff621621d02p-1, -0x1.6acfcebc82813p-56,
        0x1.ab417f020c31p-9, 0x1.7fb6c9ae3781ap-65, 0x1.ffff4dbafd5a6p-1, -0x1.d017de84ebc3fp-55,
        0x1.c463710fc08c9p-9, -0x1.f621bbef801cfp-64, 0x1.ffff38240586p-1, -0x1.f949383d834ep-61,
        0x1.dd85620666965p-9, 0x1.5a1a4ecd8345ap-65, 0x1.ffff21513a606p-1, 0x1.ee48b39cc31cfp-56,
        0x1.f6a751d67d871p-9, -0x1.66bdef183ff59p-63, 0x1.ffff09429bf7ap-1, -0x1.cabd3ded13a63p-55,
        0x1.07e4a038424c1p-8, 0x1.7e6886ed9bec7p-63, 0x1.fffeeff82a5a7p-1, 0x1.6efc1cf23e273p-55,
        0x1.147596e27d81fp-8, -0x1.bbfe9edba714ap-62, 0x1.fffed571e5989p-1, 0x1.151c0e15503cfp-55,
        0x1.21068ce230028p-8, 0x1.6ce44ca79798dp-63, 0x1.fffeb9afcdc25p-1, 0x1.32c5af9456b9fp-57,
        0x1.2d97822f996bcp-8, 0x1.3e5a15ed6aa3ep-62, 0x1.fffe9cb1e2e8dp-1, -0x1.10f44663fd601p-55,
        0x1.3a2876c2f95cp-8, 0x1.03f5805a36f7ep-63, 0x1.fffe7e78251dep-1, 0x1.89b8834c8800cp-55,
        0x1.46b96a948f72p-8, -0x1.3fbff884c87dap-65, 0x1.fffe5f0294744p-1, 0x1.5e809bdc855fcp-55,
        0x1.534a5d9c9b4dp-8, -0x1.8653d3b3f3bf5p-62, 0x1.fffe3e5130ff5p-1, 0x1.8d94f3d6ca2d6p-57,
        0x1.5fdb4fd35c8cbp-8, -0x1.4b73d73e9b437p-62, 0x1.fffe1c63fad33p-1, 0x1.429d08eb02c47p-55,
        0x1.6c6c413112d15p-8, -0x1.c66913985b2a8p-63, 0x1.fffdf93af204ep-1, -0x1.4ade22d9f9483p-56,
        0x1.78fd31adfdbbap-8, -0x1.03bf7bee2893dp-64, 0x1.fffdd4d616aap-1, -0x1.422710e8c8595p-55,
        0x1.858e21425cecfp-8, -0x1.5af2befb31c3cp-63, 0x1.fffdaf3568d9p-1, 0x1.99c62dc3a22acp-58,
        0x1.921f0fe670071p-8, 0x1.ab967fe6b7a9bp-64, 0x1.fffd8858e8a92p-1, 0x1.359c71883bcf7p-55,
        0x1.9eaffd9276ac8p-8, -0x1.558e4e5ccafc7p-62, 0x1.fffd604096326p-1, -0x1.7d6bdd1c1f436p-60,
        0x1.ab40ea3eb0803p-8, -0x1.7fe2a8e15f257p-67, 0x1.fffd36ec718d7p-1, -0x1.5c59ffcfba7e4p-56,
        0x1.b7d1d5e35d25dp-8, 0x1.9cfe67f192cd7p-62, 0x1.fffd0c5c7ad3dp-1, -0x1.19d99906d21e9p-55,
        0x1.c462c078bc41bp-8, 0x1.9f6db9a6ada3cp-62, 0x1.fffce090b21fcp-1, -0x1.0389138f7e5efp-55,
        0x1.d0f3a9f70d78cp-8, -0x1.a08e48b732df4p-65, 0x1.fffcb389178c4p-1, 0x1.2883b1fe7d578p-56,
        0x1.dd84925690709p-8, -0x1.0441c939bd6dap-62, 0x1.fffc8545ab352p-1, 0x1.61157c68f984bp-55,
        0x1.ea15798f84cf7p-8, -0x1.bd41d0fe0c595p-62, 0x1.fffc55c66d36fp-1, -0x1.ab98d6fcf9cd6p-58,
        0x1.f6a65f9a2a3c6p-8, -0x1.de8c48783f3aep-62, 0x1.fffc250b5daefp-1, -0x1.13b48657081cdp-55,
        0x1.019ba237602f9p-7, -0x1.2479c7145927dp-61, 0x1.fffbf3147cbb3p-1, -0x1.6a078c1216e21p-55,
        0x1.07e41402c3701p-7, -0x1.93e58a2a04d27p-67, 0x1.fffbbfe1ca7a8p-1, -0x1.698b82e41cfaep-56,
        0x1.0e2c852b5eb46p-7, -0x1.9b8495e4ec41dp-61, 0x1.fffb8b73470c8p-1, -0x1.bc4e4be396442p-55,
        0x1.1474f5ad51d17p-7, -0x1.a0fb850f9330dp-62, 0x1.fffb55c8f2917p-1, 0x1.6af540576eb51p-55,
        0x1.1abd6584bc9cbp-7, 0x1.21b927d9670ccp-61, 0x1.fffb1ee2cd2a9p-1, -0x1.3ee83959a5f1dp-56,
        0x1.2105d4adbeecp-7, 0x1.4e6ed5742dcf7p-61, 0x1.fffae6c0d6f9ap-1, -0x1.102fd2cb5b72p-56,
        0x1.274e43247895ap-7, -0x1.46ca3539434eap-63, 0x1.fffaad6310214p-1, 0x1.b8629443a759ap-55,
        0x1.2d96b0e509703p-7, -0x1.1e9131ff52dc9p-63, 0x1.fffa72c978c4fp-1, -0x1.22cb000328f91p-55,
        0x1.33df1deb9152dp-7, 0x1.f5fb935dbf892p-62, 0x1.fffa36f41108bp-1, 0x1.55d1c829905c6p-57,
        0x1.3a278a3430152p-7, -0x1.2c26d82855518p-63, 0x1.fff9f9e2d9118p-1, 0x1.102f90f3495ep-57,
        0x1.406ff5bb058f1p-7, 0x1.6e66ff4bcd327p-61, 0x1.fff9bb95d105p-1, 0x1.7fb680db05f83p-55,
        0x1.46b8607c31993p-7, 0x1.91d1ac2a2ced6p-61, 0x1.fff97c0cf909bp-1, -0x1.b418b1f88cf02p-57,
        0x1.4d00ca73d40c8p-7, -0x1.fae7469c8dbd1p-61, 0x1.fff93b485146bp-1, -0x1.43e02406fb373p-55,
        0x1.5349339e0cc25p-7, -0x1.ec016ea8e8f27p-64, 0x1.fff8f947d9e3fp-1, -0x1.2cefd02ebe75cp-59,
        0x1.59919bf6fb94bp-7, -0x1.1ca7261e51e18p-62, 0x1.fff8b60b930a3p-1, 0x1.97dbe1fdcab9p-56,
        0x1.5fda037ac05e1p-7, -0x1.ff59bf4b574eep-61, 0x1.fff871937ce2fp-1, -0x1.38dae49f0be32p-57,
        0x1.66226a257af95p-7, 0x1.3cd7a66dfafc8p-61, 0x1.fff82bdf97986p-1, -0x1.507b1e2913e3bp-57,
        0x1.6c6acff34b421p-7, 0x1.b03798bbe7197p-63, 0x1.fff7e4efe3558p-1, 0x1.ebe425d873a16p-57,
        0x1.72b334e051144p-7, 0x1.caf2df425ef35p-62, 0x1.fff79cc460462p-1, -0x1.6c8d94412b92ap-55,
        0x1.78fb98e8ac4c8p-7, -0x1.555202816f3a5p-61, 0x1.fff7535d0e96bp-1, -0x1.c5727ff41299bp-56,
        0x1.7f43fc087cc7dp-7, 0x1.00a2a3a85d253p-61, 0x1.fff708b9ee748p-1, -0x1.7ff8f5cefe85ap-60,
        0x1.858c5e3be264p-7, -0x1.ee9c7105192d8p-62, 0x1.fff6bcdb000dap-1, -0x1.65348e4f5cd16p-57,
        0x1.8bd4bf7efcff3p-7, 0x1.66640846e6109p-63, 0x1.fff66fc04390dp-1, 0x1.77f5704f2756cp-55,
        0x1.921d1fcdec784p-7, 0x1.9878ebe836d9dp-61, 0x1.fff62169b92dbp-1, 0x1.5dda3c81fbd0dp-55,
        0x1.98657f24d0aeap-7, 0x1.11d4d1d1806d2p-61, 0x1.fff5d1d761149p-1, 0x1.e7b86f31e2875p-63,
        0x1.9eaddd7fc9825p-7, -0x1.5adb5adfd3669p-62, 0x1.fff581093b768p-1, -0x1.3eeee9513ae4cp-55,
        0x1.a4f63adaf6d3ep-7, -0x1.f838d8dd726dcp-64, 0x1.fff52eff48855p-1, -0x1.514532e82de3fp-57,
        0x1.ab3e973278849p-7, 0x1.35b145a18353cp-61, 0x1.fff4dbb98873ap-1, 0x1.857663dc92252p-55,
        0x1.b186f2826e765p-7, -0x1.6b3b32921e88cp-61, 0x1.fff48737fb74ep-1, -0x1.d462b0c1681e7p-58,
        0x1.b7cf4cc6f88b8p-7, -0x1.32c6a4623533p-62, 0x1.fff4317aa1bd2p-1, -0x1.6a0039355b637p-55,
        0x1.be17a5fc36a75p-7, 0x1.c404548edfc51p-63, 0x1.fff3da817b814p-1, -0x1.2b464e14730cp-55,
        0x1.c45ffe1e48ad9p-7, 0x1.4060e4bd32e79p-63, 0x1.fff3824c88f6fp-1, -0x1.ed820f3fe698p-55,
        0x1.caa855294e82bp-7, 0x1.eca8fa79834dep-66, 0x1.fff328dbca549p-1, -0x1.6ca1af321dc1cp-55,
        0x1.d0f0ab19680bdp-7, -0x1.e9c612f8a4102p-64, 0x1.fff2ce2f3fd15p-1, -0x1.62ccf8bdb122fp-56,
        0x1.d738ffeab52ecp-7, -0x1.c110bc5257c8cp-62, 0x1.fff27246e9a52p-1, -0x1.16d6346f3f55fp-59,
        0x1.dd81539955d2p-7, -0x1.eaf6d880c7f01p-61, 0x1.fff21522c808bp-1, 0x1.a190e2d2eaf6ep-56,
        0x1.e3c9a62169dcbp-7, 0x1.6666333e35e32p-61, 0x1.fff1b6c2db358p-1, -0x1.f4664a9ad833cp-56,
        0x1.ea11f77f1136ep-7, -0x1.7a617506aacb8p-62, 0x1.fff157272365bp-1, 0x1.44ef64386476p-57,
        0x1.f05a47ae6bc91p-7, -0x1.0935bb936be66p-64, 0x1.fff0f64fa0d45p-1, -0x1.ac9acb9bb8d2dp-56,
        0x1.f6a296ab997cbp-7, -0x1.f2943d8fe7033p-61, 0x1.fff0943c53bd1p-1, -0x1.47399f361d158p-55,
        0x1.fceae472ba3bcp-7, -0x1.3a08b1804396p-64, 0x1.fff030ed3c5c7p-1, -0x1.2504b1dc047e8p-55,
        0x1.0199987ff6f89p-6, 0x1.8daccef2e2556p-60, 0x1.ffefcc625aefbp-1, 0x1.fbb0dd6af1603p-59,
        0x1.04bdbe27aa444p-6, 0x1.0c87a06c6956dp-60, 0x1.ffef669bafb4ep-1, -0x1.b5a4e5318177bp-58,
        0x1.07e1e32e86f72p-6, -0x1.f40145dde0463p-60, 0x1.ffeeff993aeacp-1, -0x1.99db334be163bp-58,
        0x1.0b0607929d07ap-6, 0x1.f989b780d54e7p-61, 0x1.ffee975afcd0ep-1, -0x1.2df82b9320ecdp-55,
        0x1.0e2a2b51fc6cep-6, -0x1.2ca118aaa212fp-61, 0x1.ffee2de0f5a78p-1, 0x1.9daa217acc1bp-58,
        0x1.114e4e6ab51e2p-6, 0x1.10d53d5e0ffc5p-62, 0x1.ffedc32b25afcp-1, -0x1.bb89d7b0d4487p-64,
        0x1.147270dad7133p-6, 0x1.8769e00e018p-63, 0x1.ffed57398d2b7p-1, -0x1.0bb0db6384cf3p-55,
        0x1.179692a072443p-6, 0x1.d40c977f2fe27p-60, 0x1.ffecea0c2c5d2p-1, -0x1.7a22a10e4cb4fp-55,
        0x1.1abab3b996a9dp-6, -0x1.e21a2ef2391d3p-60, 0x1.ffec7ba303882p-1, 0x1.b209ec7100e1ap-56,
        0x1.1dded424543cep-6, 0x1.2ecdb3d03a70bp-61, 0x1.ffec0bfe12f0ap-1, 0x1.8b1483b4090edp-56,
        0x1.2102f3debaf6fp-6, -0x1.45a1ea37e10eap-62, 0x1.ffeb9b1d5adb7p-1, 0x1.d5b363c2f437p-55,
        0x1.242712e6dad1cp-6, 0x1.0317d8ce1d1b9p-60, 0x1.ffeb2900db8e4p-1, 0x1.18f6a66301d6p-57,
        0x1.274b313ac3c7ap-6, 0x1.b29e49acf7e7ep-60, 0x1.ffeab5a8954f6p-1, 0x1.0652cfe4cebfap-55,
        0x1.2a6f4ed885d35p-6, -0x1.5a8a13d8f9966p-60, 0x1.ffea41148866p-1, 0x1.b77e2735c69f9p-55,
        0x1.2d936bbe30efdp-6, 0x1.b5f91ee371d64p-61, 0x1.ffe9cb44b51a1p-1, 0x1.5b43366df667p-56,
        0x1.30b787e9d518dp-6, 0x1.b620c940df42ap-60, 0x1.ffe954391bb43p-1, 0x1.ddd5377beef1ap-56,
        0x1.33dba359824a6p-6, -0x1.6c4020b5f32a8p-61, 0x1.ffe8dbf1bc7dep-1, -0x1.cc391831cbcdp-55,
        0x1.36ffbe0b4880ep-6, 0x1.4b88026f4e07bp-61, 0x1.ffe8626e97c13p-1, 0x1.cdb27525fcaf7p-56,
        0x1.3a23d7fd37b96p-6, -0x1.dd9e849a0a5d5p-60, 0x1.ffe7e7afadc94p-1, -0x1.db1225b06b02cp-55,
        0x1.3d47f12d5ff12p-6, 0x1.add51eab25dd4p-60, 0x1.ffe76bb4fee1ap-1, -0x1.0c43dae9e89c6p-57,
        0x1.406c0999d1263p-6, -0x1.cf7cbeea1ddcfp-61, 0x1.ffe6ee7e8b56ep-1, 0x1.7ecac48a7b7bcp-58,
        0x1.439021409b56cp-6, 0x1.81352c74c7ae2p-62, 0x1.ffe6700c53764p-1, -0x1.4f502302afd49p-55,
        0x1.46b4381fce81bp-6, 0x1.ff9f89fb65be3p-60, 0x1.ffe5f05e578dbp-1, -0x1.b71f7469ecc13p-56,
        0x1.49d84e357aa66p-6, -0x1.9143373e36698p-61, 0x1.ffe56f7497ecp-1, -0x1.ddf92a8d40b3p-55,
        0x1.4cfc637fafc48p-6, -0x1.a3541bb15d5bap-60, 0x1.ffe4ed4f14e0ap-1, 0x1.e48478b2f0ba1p-56,
        0x1.502077fc7ddc5p-6, 0x1.25236a8347d96p-60, 0x1.ffe469edcebbfp-1, 0x1.8fc7557d6bc53p-55,
        0x1.53448ba9f4eebp-6, 0x1.e6adde30ec695p-61, 0x1.ffe3e550c5cfp-1, -0x1.58fb3ae72192ep-55,
        0x1.56689e8624fcep-6, -0x1.b967a2d3ee5b3p-61, 0x1.ffe35f77fa6b8p-1, -0x1.a7661b121429ap-57,
        0x1.598cb08f1e089p-6, 0x1.56f9c34c216b4p-60, 0x1.ffe2d8636ce41p-1, 0x1.b4243168ab5a4p-57,
        0x1.5cb0c1c2f0142p-6, 0x1.c2c42ac4d436cp-60, 0x1.ffe250131d8cp-1, 0x1.ed2d09d5789d3p-55,
        0x1.5fd4d21fab226p-6, -0x1.0c0a91c37851cp-61, 0x1.ffe1c6870cb77p-1, 0x1.89aa14768323ep-55,
        0x1.62f8e1a35f369p-6, -0x1.44ab93e20133fp-60, 0x1.ffe13bbf3abb3p-1, 0x1.6721eee289b1dp-55,
        0x1.661cf04c1c548p-6, 0x1.3d6cc7ce6ff57p-60, 0x1.ffe0afbba7ecep-1, 0x1.6ecd981464044p-57,
        0x1.6940fe17f280bp-6, -0x1.84bf8988cc42p-60, 0x1.ffe0227c54a2dp-1, 0x1.eeed1d70e13e2p-55,
        0x1.6c650b04f1bfep-6, 0x1.53e382471fa0cp-69, 0x1.ffdf940141344p-1, -0x1.a63fc248c2dacp-55,
        0x1.6f8917112a179p-6, 0x1.b6de0ad6e9445p-60, 0x1.ffdf044a6df8fp-1, -0x1.798cfb498d8b1p-55,
        0x1.72ad223aab8ddp-6, -0x1.cf17a0ad13b4ap-60, 0x1.ffde7357db499p-1, 0x1.09337deb3ff5ap-59,
        0x1.75d12c7f8629p-6, -0x1.07d8473495aaep-60, 0x1.ffdde129897f9p-1, 0x1.44e8786dedb6bp-55,
        0x1.78f535ddc9f04p-6, 0x1.b194ad9b1aa97p-61, 0x1.ffdd4dbf78f52p-1, 0x1.216679a26323dp-55,
        0x1.7c193e5386eb4p-6, 0x1.725118c2860e7p-63, 0x1.ffdcb919aa053p-1, -0x1.55c1ce37ae6f6p-56,
        0x1.7f3d45decd222p-6, 0x1.2fe8cfe80809dp-60, 0x1.ffdc23381d0b6p-1, 0x1.00d15f9603fedp-57,
        0x1.82614c7dac9dbp-6, 0x1.e95f011ac18c6p-63, 0x1.ffdb8c1ad2643p-1, 0x1.e79471744a0cbp-56,
        0x1.8585522e35674p-6, -0x1.d5a766c5894c1p-60, 0x1.ffdaf3c1ca6cep-1, -0x1.9c0f0834fa422p-56,
        0x1.88a956ee7788ap-6, 0x1.19dfbd12ff726p-61, 0x1.ffda5a2d05835p-1, 0x1.6dbc405ff8e25p-55,
        0x1.8bcd5abc830c7p-6, -0x1.f7d609f14a311p-60, 0x1.ffd9bf5c84066p-1, -0x1.35167a6ce828dp-55,
        0x1.8ef15d9667fdap-6, -0x1.668dc9ba82a7p-60, 0x1.ffd9235046557p-1, -0x1.c3c6e16616fb2p-56,
    };

    /** C: sin.c:158-164 `PS[]` (U128(l,h) little-endian macro order kept by U128.lh). */
    private static final U128[] PS = {
        U128.lh(0xc4c6628b80dbed80L,0xc90fdaa22168c234L), // degree 1
        U128.lh(0xabb8e5506ca45b03L,0x14abbce625be52beL), // degree 3, implicit - sign
        U128.lh(0x44c54995bb97635L,0xa335e33bad570eL),    // degree 5
        U128.lh(0x265a5999d06c4L,0),                     // degree 7, implicit - sign
    };

    /** C: sin.c:171-177 `PC[]`. */
    private static final U128[] PC = {
        U128.lh(0xffffffffffffffffL,0xffffffffffffffffL), // degree 0
        U128.lh(0x2b71366cbcf0b096L,0x4ef4f326f9177969L), // degree 2, implicit - sign
        U128.lh(0xd4c9076e3db78fedL,0x40f07c206d6b0ecL),  // degree 4
        U128.lh(0xeec6962368fb7c41L,0x155d3c7e3cbef9L),   // degree 6, implicit - sign
        U128.lh(0x3c3e845b5510L,0),                      // degree 8
    };

    /** C: sin.c:618-652 `S1u[33]`. */
    private static final U128[] S1U = {
        U128.lh(0x0L,0x0L),
        U128.lh(0x76a17954b2b7c517L,0xc8fb2f886ec09f3L),
        U128.lh(0xd8e72d912977ee71L,0x1917a6bc29b42be1L),
        U128.lh(0xc002a2684781f080L,0x259020dd1cc27444L),
        U128.lh(0x9732300393f33614L,0x31f17078d34c156cL),
        U128.lh(0x90887712e9dc9663L,0x3e33f2f642be355eL),
        U128.lh(0xd725d3b9ed35fbaaL,0x4a5018bb567c16a2L),
        U128.lh(0x408fca9cc277fc1fL,0x563e69d6ac7f73f8L),
        U128.lh(0x98916152cf7eee1cL,0x61f78a9abaa58b46L),
        U128.lh(0x9b165cba0c171818L,0x6d744027857300adL),
        U128.lh(0x362474f1a105878fL,0x78ad74e01bd8ec78L),
        U128.lh(0xbfd79717f2880abfL,0x839c3cc917ff6cb4L),
        U128.lh(0xbba4cfecbff54867L,0x8e39d9cd73464364L),
        U128.lh(0x19cec845ac87a5c6L,0x987fbfe70b81a708L),
        U128.lh(0x3b5167ee359a234eL,0xa267992848eeb0c0L),
        U128.lh(0x1becda8089c1a94cL,0xabeb49a46764fd15L),
        U128.lh(0x597d89b3754abe9fL,0xb504f333f9de6484L),
        U128.lh(0xac85320f528d6d5dL,0xbdaef913557d76f0L),
        U128.lh(0x43da25d99267326bL,0xc5e40358a8ba05a7L),
        U128.lh(0x23af31db7179a4aaL,0xcd9f023f9c3a059eL),
        U128.lh(0xf630e8b6dac83e69L,0xd4db3148750d1819L),
        U128.lh(0x2c19b63253da43fcL,0xdb941a28cb71ec87L),
        U128.lh(0xf4e8a8372f8c5810L,0xe1c5978c05ed8691L),
        U128.lh(0x125129529d48a92fL,0xe76bd7a1e63b9786L),
        U128.lh(0x7e610231ac1d6181L,0xec835e79946a3145L),
        U128.lh(0x67127db35b287316L,0xf1090827b43725fdL),
        U128.lh(0x163c5c7f03b718c5L,0xf4fa0ab6316ed2ecL),
        U128.lh(0xc7adc6b4988891bbL,0xf853f7dc9186b952L),
        U128.lh(0x2172a361fd2a722fL,0xfb14be7fbae58156L),
        U128.lh(0xeae6bd951c1dabbeL,0xfd3aabf84528b50bL),
        U128.lh(0x41390efdc726e9efL,0xfec46d1e89292cf0L),
        U128.lh(0x421e8edaaf59453eL,0xffb10f1bcb6bef1dL),
        U128.lh(0xffffffffffffffffL,0xffffffffffffffffL),
    };

    /** C: sin.c:657-722 `S2u[64]`. */
    private static final U128[] S2U = {
        U128.lh(0x0L,0x0L),
        U128.lh(0xc3b0abf2b35a62f8L,0x3243f655d966c0L),
        U128.lh(0x11ad1d7b5620834aL,0x6487eabb991cb6L),
        U128.lh(0xc4a32c4560d01e70L,0x96cbdb41258434L),
        U128.lh(0x57480f7956b64707L,0xc90fc5f66525d2L),
        U128.lh(0x328421cf8014d698L,0xfb53a8eb3ec385L),
        U128.lh(0xf96857b5aa8f6208L,0x12d97822f996bc4L),
        U128.lh(0xd230a30592f25049L,0x15fdb4fd35c8caaL),
        U128.lh(0xab967fe6b7a9b037L,0x1921f0fe6700711L),
        U128.lh(0x7db6e69ab68f0d10L,0x1c462c078bc41b6L),
        U128.lh(0x85cede1f0314607bL,0x1f6a65f9a2a3c58L),
        U128.lh(0x7c11ebc1b33cafb5L,0x228e9eb5aa3a2d9L),
        U128.lh(0xc2dd9c015a46e767L,0x25b2d61ca12e05dL),
        U128.lh(0x8e8d6151676ae94dL,0x28d70c0f863326cL),
        U128.lh(0x53205a54588f0abL,0x2bfb406f580bc10L),
        U128.lh(0x556febf4862da970L,0x2f1f731d15898f5L),
        U128.lh(0xc3c75f41b6ce7aa8L,0x3243a3f9bd8f08cL),
        U128.lh(0xad8a2d0c1a9e3a8cL,0x3567d2e64f10929L),
        U128.lh(0x80c1c97a65cf2291L,0x388bffc3c915b22L),
        U128.lh(0xa8493bf9c07f7765L,0x3bb02a732aba3f0L),
        U128.lh(0x6b5e1380c7e6bddbL,0x3ed452d5732f950L),
        U128.lh(0xbfeba221fb9d05beL,0x41f878cba1bdc60L),
        U128.lh(0xed3c01c030008bbL,0x451c9c36b5c4cc3L),
        U128.lh(0xe97857207bc589daL,0x4840bcf7aebdbbaL),
        U128.lh(0xafc8f71b8eb233eeL,0x4b64daef8c3bf4dL),
        U128.lh(0x2617b65f5a2a8f5aL,0x4e88f5ff4dee562L),
        U128.lh(0xf9f89fb65be2a455L,0x51ad0e07f3a06dfL),
        U128.lh(0x356ef187634a531dL,0x54d122ea7d3bacfL),
        U128.lh(0x9fab71e43d71e2b7L,0x57f53487eac8977L),
        U128.lh(0xa9f1c1238fd05d5L,0x5b1942c13c6ff80L),
        U128.lh(0x8ca56cd8d54b97f0L,0x5e3d4d77727c10dL),
        U128.lh(0xa58993a76b3f4ffeL,0x6161548b8d59ce2L),
        U128.lh(0x4e29cf6e5fed0679L,0x648557de8d99f7eL),
        U128.lh(0xf1fc3edb7a0f1757L,0x67a9575173f263aL),
        U128.lh(0x51b86c83422cbab4L,0x6acd52c5413f26dL),
        U128.lh(0x4e68e062eb177e2dL,0x6df14a1af683c83L),
        U128.lh(0x9c28010f1c93a252L,0x71153d3394ec722L),
        U128.lh(0x5bcb8fd41cb1096fL,0x74392bf01dcf247L),
        U128.lh(0x9ac20c033d7f5cc3L,0x775d163192ace62L),
        U128.lh(0xb8654aa824578975L,0x7a80fbd8f532f78L),
        U128.lh(0xb00590e675e4e556L,0x7da4dcc7473c03fL),
        U128.lh(0x46f0804dae5f13baL,0x80c8b8dd8ad153dL),
        U128.lh(0x1db725856ff8c284L,0x83ec8ffcc22bfe5L),
        U128.lh(0xa3f67ad05a5be69eL,0x87106205efb61b6L),
        U128.lh(0xede5b1068d174beaL,0x8a342eda160bf5aL),
        U128.lh(0x6aed92d34c17df54L,0x8d57f65a37fd3c2L),
        U128.lh(0x7c8c5732d89e910bL,0x907bb867588e342L),
        U128.lh(0xecc93966728e412dL,0x939f74e27af8eb2L),
        U128.lh(0x437b2dd49d5fca3cL,0x96c32baca2ae68bL),
        U128.lh(0xf9a60c93317892c4L,0x99e6dca6d357dffL),
        U128.lh(0x8a318ba775fd7b04L,0x9d0a87b210d7e1fL),
        U128.lh(0x5f3d645e787b94f2L,0xa02e2caf5f4b8efL),
        U128.lh(0x9b56007d16d4ad5aL,0xa351cb7fc30bc88L),
        U128.lh(0xbdcd0d6bb4b9cd3eL,0xa675640440ae634L),
        U128.lh(0x217954ed6093c44cL,0xa998f61ddd0758aL),
        U128.lh(0x55213c653bfb79b7L,0xacbc81ad9d29f88L),
        U128.lh(0x4cd34d2751c2e1daL,0xafe00694866a1b4L),
        U128.lh(0x6b7029d39efd7682L,0xb30384b39e5d534L),
        U128.lh(0x63a95642f565102fL,0xb626fbebeadc1ecL),
        U128.lh(0xefb8391d83d6da18L,0xb94a6c1e7203198L),
        U128.lh(0x5f10bfca3d646401L,0xbc6dd52c3a342ebL),
        U128.lh(0xf9530f050886d756L,0xbf9136f64a17ca4L),
        U128.lh(0x35bfac0f965612e2L,0xc2b4915da89e0b2L),
        U128.lh(0xc6718c1dfd2aa612L,0xc5d7e4435cfff45L),
    };

    /** C: sin.c:732-797 `C2u[64]`. */
    private static final U128[] C2U = {
        U128.lh(0xffffffffffffffffL,0xffffffffffffffffL),
        U128.lh(0x491a703231e0a12eL,0xfffffb10b0d19f76L),
        U128.lh(0xec9e2e75cb525f2cL,0xffffec42c3773235L),
        U128.lh(0x6276b75b91de105eL,0xffffd3963882d553L),
        U128.lh(0x3031437d7eccb9dfL,0xffffb10b10e80e95L),
        U128.lh(0x858425d8b397dee6L,0xffff84a14dfbcc6aL),
        U128.lh(0x177338053fd93920L,0xffff4e58f17465deL),
        U128.lh(0x3a11d60588d8b96eL,0xffff0e31fd699a85L),
        U128.lh(0x38e310779edfec68L,0xfffec42c7454926bL),
        U128.lh(0xedd8e1034213f22bL,0xfffe7048590fddf8L),
        U128.lh(0x96f351efc65556cfL,0xfffe1285aed775d8L),
        U128.lh(0xea80aedd6a19710fL,0xfffdaae47948bad5L),
        U128.lh(0x69fff9ae0dedb047L,0xfffd3964bc6275baL),
        U128.lh(0xf3a703b987eca44aL,0xfffcbe067c84d725L),
        U128.lh(0x928db07a0e70ba36L,0xfffc38c9be717763L),
        U128.lh(0x8d800bed6653dcbaL,0xfffba9ae874b563aL),
        U128.lh(0xb47903f7a19f8ee2L,0xfffb10b4dc96dabbL),
        U128.lh(0xecc7b9244a48eb19L,0xfffa6ddcc439d30aL),
        U128.lh(0xfbe18032d0016082L,0xfff9c126447b7424L),
        U128.lh(0x90e2d2eaf6da4d1eL,0xfff90a91640459a1L),
        U128.lh(0x8cc193c5d508e13fL,0xfff84a1e29de8571L),
        U128.lh(0x89332d07a713477eL,0xfff77fcc9d755f99L),
        U128.lh(0x9e4938f661aa140cL,0xfff6ab9cc695b5e8L),
        U128.lh(0x66c785e86dfbb75fL,0xfff5cd8ead6dbbabL),
        U128.lh(0x43366df666fd54ffL,0xfff4e5a25a8d095bL),
        U128.lh(0xdbb49f29fa872a83L,0xfff3f3d7d6e49c49L),
        U128.lh(0xe08b96133ecce0bdL,0xfff2f82f2bc6d648L),
        U128.lh(0x98a31bcda3def20L,0xfff1f2a862e77d4eL),
        U128.lh(0x5428ed0647c9e5d1L,0xfff0e343865bbb13L),
        U128.lh(0x807b6e7a4a723daeL,0xffefca00a09a1cb3L),
        U128.lh(0xccf344c647917821L,0xffeea6dfbc7a9242L),
        U128.lh(0xf0f7cb05bde024f1L,0xffed79e0e5366e63L),
        U128.lh(0x5657552366961732L,0xffec4304266865d9L),
        U128.lh(0x9195e99fbca2f10aL,0xffeb02498c0c8f12L),
        U128.lh(0x191df31aaa6f7f45L,0xffe9b7b1228061b6L),
        U128.lh(0x3b57790bf77b6b2dL,0xffe8633af682b627L),
        U128.lh(0x53aa9423bb0adc21L,0xffe704e71533c508L),
        U128.lh(0x3e71f7d99688082aL,0xffe59cb58c1526b9L),
        U128.lh(0xbe28fbec7cfb8a6L,0xffe42aa66909d2d2L),
        U128.lh(0xf1ed54343fe7be24L,0xffe2aeb9ba561f99L),
        U128.lh(0x7d209f32d42d864eL,0xffe128ef8e9fc17aL),
        U128.lh(0x8e6ee05573d420L,0xffdf9947f4edca6fL),
        U128.lh(0x44bd28b8d85b530aL,0xffddffc2fca8a970L),
        U128.lh(0x75a8951fc304b914L,0xffdc5c60b59a29dcL),
        U128.lh(0x4fd8f038449ec436L,0xffdaaf212fed72dbL),
        U128.lh(0x8c9611f0b1d8f023L,0xffd8f8047c2f06beL),
        U128.lh(0x8d3cd437dc7fa9d2L,0xffd7370aab4cc25eL),
        U128.lh(0x45bd035edb0a65f5L,0xffd56c33ce95dc73L),
        U128.lh(0x664649b4d541b9c5L,0xffd3977ff7bae4e9L),
        U128.lh(0xc42aac754bedcfdeL,0xffd1b8ef38cdc433L),
        U128.lh(0x1fd552bf146b4a6L,0xffcfd081a441ba99L),
        U128.lh(0x76f487bb853a6989L,0xffcdde374ceb5f7dL),
        U128.lh(0x5595ca3f421ae09cL,0xffcbe2104600a0a9L),
        U128.lh(0x11b369083a7a62e5L,0xffc9dc0ca318c18bL),
        U128.lh(0x5c2a6019679e41fL,0xffc7cc2c782c5a76L),
        U128.lh(0x579207cfe424dcb7L,0xffc5b26fd99557ddL),
        U128.lh(0x1c676208aa3be545L,0xffc38ed6dc0ef98bL),
        U128.lh(0xbc8d54e81d94f831L,0xffc1616194b5d1d3L),
        U128.lh(0x965827f33d906c7cL,0xffbf2a101907c4c5L),
        U128.lh(0xe0aa07fcb29eef39L,0xffbce8e27ee40754L),
        U128.lh(0xccfed60a91097c48L,0xffba9dd8dc8b1e83L),
        U128.lh(0xe907d9a298ab1febL,0xffb848f3489ede86L),
        U128.lh(0xbfdfce09aea7ac02L,0xffb5ea31da2269e5L),
        U128.lh(0xbadfe70a1ef51116L,0xffb38194a87a3097L),
    };

    // ------------------------------------------------------------------------------------------
    // Read access. The arrays are private: they are Java arrays, so a package-visible array could
    // be written by any class in this package (or a class put into it on the classpath; the module
    // has no module-info) and every later result would change silently. Element accessors are what
    // the ports call (small static methods the JIT inlines); the *Copy methods give the tests and
    // the digest check a snapshot that cannot write back.
    // ------------------------------------------------------------------------------------------

    /** {@code _T[i]}, 0 <= i < 20. */
    static long t(int i) {
        return T[i];
    }

    /** {@code U1[4*j + c]}: element {@code c} of row {@code j} (sh, sl, ch, cl), as the C's {@code U1[j][c]}. */
    static double u1(int flatIndex) {
        return U1[flatIndex];
    }

    /** {@code U2[4*j + c]}, as {@link #u1}. */
    static double u2(int flatIndex) {
        return U2[flatIndex];
    }

    /** {@code PS[i]}, 0 <= i < 4. */
    static U128 ps(int i) {
        return PS[i];
    }

    /** {@code PC[i]}, 0 <= i < 5. */
    static U128 pc(int i) {
        return PC[i];
    }

    /** {@code S1u[i]}, 0 <= i < 33. */
    static U128 s1u(int i) {
        return S1U[i];
    }

    /** {@code S2u[i]}, 0 <= i < 64. */
    static U128 s2u(int i) {
        return S2U[i];
    }

    /** {@code C2u[i]}, 0 <= i < 64. */
    static U128 c2u(int i) {
        return C2U[i];
    }

    /** A copy of {@code _T}. */
    static long[] tCopy() {
        return T.clone();
    }

    /** A copy of the flattened {@code U1}. */
    static double[] u1Copy() {
        return U1.clone();
    }

    /** A copy of the flattened {@code U2}. */
    static double[] u2Copy() {
        return U2.clone();
    }

    /** A copy of {@code PS} ({@link U128} is immutable). */
    static U128[] psCopy() {
        return PS.clone();
    }

    /** A copy of {@code PC}. */
    static U128[] pcCopy() {
        return PC.clone();
    }

    /** A copy of {@code S1u}. */
    static U128[] s1uCopy() {
        return S1U.clone();
    }

    /** A copy of {@code S2u}. */
    static U128[] s2uCopy() {
        return S2U.clone();
    }

    /** A copy of {@code C2u}. */
    static U128[] c2uCopy() {
        return C2U.clone();
    }
}
