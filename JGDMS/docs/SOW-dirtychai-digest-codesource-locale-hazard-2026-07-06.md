# SOW — Remove the locale-service dependency from the SecureClassLoader digest-stamping path

**Status:** ADVISE-ONLY (DirtyChai is human-authored under a no-AI-contribution policy — this is planning input; a human maintainer writes the source + JavaDoc).
**Date:** 2026-07-06
**Component:** DirtyChai OpenJDK 27 — `java.base` security / class-loading bootstrap.
**Author:** Claude (diagnosis from the P1 SPIFFE-under-SM validation run).

---

## 1. Problem statement

DirtyChai digest-stamps every codesource under an active `SecurityManager`: `SecureClassLoader.getProtectionDomain` promotes each `file:` codesource to a `DigestCodeSource`, and `DigestCodeSource.computeDigest` reads the codesource to compute its digest.

For a `file:` codesource that resolves to a **directory** (an exploded classpath / module dir, e.g. Maven `-cp target/classes`), `computeDigest` opens it via `url.openConnection().getInputStream()`. `sun.net.www.protocol.file.FileURLConnection.getInputStream()` then sorts the directory listing with a **locale-sensitive `Collator`**:

```
directoryListing.sort(Collator.getInstance())        // FileURLConnection.java:247
```

`Collator.getInstance()` lazily initializes the CLDR locale provider (`sun.util.cldr.CLDRLocaleProviderAdapter.<init>`). That init is **not SM-safe** when it happens lazily under an active `SecurityManager`: it throws

```
java.util.ServiceConfigurationError: Locale provider adapter "CLDR" cannot be instantiated
  at sun.util.cldr.CLDRLocaleProviderAdapter.<init>
  at java.text.Collator.getInstance
  at sun.net.www.protocol.file.FileURLConnection.getInputStream
  at java.security.DigestCodeSource.computeDigest
  at java.security.SecureClassLoader.getProtectionDomain     <-- fires on the FIRST class load
```

Because this fires at the *first* class load, the VM cannot bootstrap **any** program under `-Djava.security.manager=default` — it aborts before application code runs.

### Why it is normally invisible
CDS/AppCDS masks it. The product image (`mixed mode, sharing`) archives the CLDR adapter, `Collator`, and locale classes at dump time, so at runtime they are already materialized and **never run `<init>` under the active SM**. The failure only appears when sharing is off: `-Xshare:off`, archive-less/intermediate JDK trees, custom archives, or archive invalidation. (It was discovered by launching the archive-less intermediate `build/.../jdk` tree instead of the product `build/.../images/jdk` image; a 3-line non-JGDMS program reproduces it — `-Djava.security.manager=allow` works, `=default` dies.)

### Not a code regression
Git history confirms nothing recent touched `DigestCodeSource`, `SecureClassLoader`, `FileURLConnection`, `Collator`, or the CLDR adapter. The path has always been SM-unsafe; CDS has always hidden it. This SOW makes the JVM robust regardless of CDS state.

---

## 2. Impact / risk

- **Robustness:** any DirtyChai deployment running without a valid CDS archive cannot boot under an SM. The SM's viability should not depend on an unrelated performance feature (CDS). This is fragile and surprising.
- **Bootstrap hazard:** a locale / `ServiceLoader` initialization on the class-load-critical path is a latent recursion/failure risk beyond just CLDR.
- **Digest canonicality (secondary — verify):** the digest identity is derived by reading a directory whose listing is ordered by a *locale-sensitive* `Collator`. If that ordering influences the digest bytes, the codesource identity could differ across locales — undesirable for a value used in `LoadClassPermission`-per-digest / codesource identity. Confirm during the work whether the sort order feeds the digest or is merely incidental to reading the stream.

---

## 3. Objective

Remove the locale-service (`Collator`/CLDR) dependency from the digest-stamping path so `DigestCodeSource.computeDigest` works under an active `SecurityManager` **regardless of CDS state**, for `file:` directory codesources — while producing a **stable, locale-independent** digest.

---

## 4. Scope

**In scope**
- `DigestCodeSource.computeDigest` handling of `file:`-scheme codesources, specifically the **directory** case.
- The mechanism by which directory entries are enumerated and fed to the digest.

**Out of scope (noted, not required)**
- General CLDR/`Collator` SM-safety across the JDK.
- CDS configuration changes. (Keeping CDS on is *not* the fix — it only masks the hazard.)
- Optional defense-in-depth: wrapping any unavoidable locale init in `doPrivileged`. The primary fix removes the need.

---

## 5. Approach / tasks

1. **Establish the failing baseline.** Run an archive-less tree (or `-Xshare:off` on the product image) with a trivial program under `-Djava.security.manager=default`; observe the `CLDR ServiceConfigurationError`.
2. **Locate the code.** `DigestCodeSource.computeDigest` `file:` handling (~`DigestCodeSource.java:965`) and the existing `jrt:`/`jmod:` branch that digests module entries via `ModuleReader` — the pattern to mirror.
3. **Replace the `file:` directory path.** Resolve the URL to a `Path`; if it is a directory, enumerate entries directly (`Files.walk` / recursive `File.listFiles()`), order entry names with a **locale-independent** comparator (String natural/code-point order, or byte order), read each file's bytes, and feed them to the digest in that deterministic order. **No `URLConnection.getInputStream()` on a directory; no `Collator`.** Use plain loops — avoid streams/lambdas that could force the lambda metafactory during bootstrap.
4. **Preserve digest stability.** Confirm the new ordering yields a digest stable across runs and locales. Decide with the maintainers whether a change in the produced digest *value* is acceptable (expected: yes — these digests are per-run identity, not persisted grants) or whether the previous byte-input order must be replicated for compatibility.
5. **Verify SM-safe.** Rerun the archive-less / `-Xshare:off` baseline under `-Djava.security.manager=default`: boots, no CLDR init, first class load completes.
6. **Verify no regression with CDS on.** Product image (`mixed mode, sharing`) still boots and digests correctly.
7. **Optional hardening.** Audit `computeDigest` and neighbours for any *other* locale / `ServiceLoader` / lambda-on-first-use dependency on the class-load-critical path; wrap any unavoidable init in `doPrivileged`.

---

## 6. Acceptance criteria

- [ ] Archive-less DirtyChai (or `-Xshare:off`) boots any program under `-Djava.security.manager=default` — no `Collator`/CLDR init, no `ServiceConfigurationError`.
- [ ] `computeDigest` produces stable, locale-independent digests for `file:` directory codesources — **verified identical across at least two locales** (e.g. `en_AU` and a non-Latin locale such as `tr_TR` or `zh_CN`).
- [ ] No dependency on `Collator` / `FileURLConnection` directory listing remains on the digest path (grep-clean).
- [ ] Existing DirtyChai SM/jtreg suites pass with sharing **ON and OFF**.
- [ ] JGDMS `SslEngineRoundTripTest`-under-SM (the P1 validation) passes with sharing **ON and OFF**.

---

## 7. Risks / considerations

- **Digest-value change** could break any persisted digest-keyed grants — confirm none are persisted before changing input ordering.
- **Directory traversal semantics:** symlinks, hidden files, ordering stability, and large directories (perf). Ensure deterministic, bounded traversal.
- **Bootstrap safety of the fix itself:** it lives in `java.base` bootstrap — introduce no new bootstrap-unsafe dependency (no streams/lambdas forcing the metafactory at a bad moment; plain loops + `java.nio.file` already loaded).
- **`tr_TR` dotted-i caveat:** a locale-sensitive sort could reorder entries differently under Turkish locale — exactly the class of bug the locale-independent comparator eliminates; use it as a test locale.

---

## 8. References (file:line — DirtyChai HEAD `3cac7b7fcf4d`)

- `src/java.base/share/classes/java/security/SecureClassLoader.java:354` — promotes `file:` codesource → `DigestCodeSource` under SM.
- `src/java.base/share/classes/java/security/DigestCodeSource.java:965` — `computeDigest` opens the URL connection; the `jrt:`/`jmod:` branch (via `ModuleReader`) is the pattern to mirror for the `file:` directory case.
- `src/java.base/share/classes/sun/net/www/protocol/file/FileURLConnection.java:247` — `directoryListing.sort(Collator.getInstance())`, the locale dependency to remove from the path.
