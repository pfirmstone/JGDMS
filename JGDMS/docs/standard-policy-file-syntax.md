# JGDMS-STD-004 — Policy File Syntax Standard

**Version:** 1.0  
**Status:** Draft  
**Scope:** Syntax rules for JGDMS policy files, including digest-based code identity and
the `META-INF/Permissions.list` JAR manifest convention.

---

## Purpose

This standard defines the **complete, authoritative grammar** for JGDMS security policy
files.  It extends the classic J2SE policy-file syntax in two important directions:

1. **Digest-based code identity** — a `grant` clause can identify code by its
   cryptographic hash rather than (or in addition to) a URL.  This removes the need for
   a trusted naming service and makes grants tamper-evident.

2. **`META-INF/Permissions.list`** — a JAR-resident declaration of the permissions the
   JAR's code requires, written in the same syntax as policy-file `permission` entries.
   The Bytecode Analysis Engine (BAE, JGDMS-STD-002) reads this manifest and cross-checks
   it against actual bytecode behaviour; policy authors use it as a machine-readable
   specification to drive grant generation.

Both extensions are backwards-compatible: a policy engine that supports only the classic
syntax ignores `digest` clauses and ignores the JAR manifest file.

---

## Definitions

| Term | Meaning |
|------|---------|
| `CodeSource` | `java.security.CodeSource` — a (URL, certificate-set) pair that identifies where code was loaded from. |
| `DigestCodeSource` | `java.security.DigestCodeSource` — a `CodeSource` sub-type (introduced in JDK 24 / DirtyChai) whose identity is primarily a set of `MessageDigest` values rather than a URL. |
| HTTPMD URL | A URL with scheme `httpmd:` whose path encodes a message-digest algorithm and hex value; serves as an integrity-verified codebase URL (pre-existing, JGDMS 2.0+). |
| Permissions.list | The file `META-INF/Permissions.list` inside a JAR archive; declares the permissions the JAR requires, in policy-file `permission` syntax. |
| BAE | Bytecode Analysis Engine (JGDMS-STD-002) — reads `Permissions.list` at analysis time. |
| ProtectionDomain | `java.security.ProtectionDomain` — the runtime container that associates a class with its `CodeSource` and the permissions granted to it. |
| SCAP | Safe Codebase Audit Pipeline (JGDMS-STD-002). |
| principal | A `java.security.Principal` carried in the executing `Subject`. |
| hex digest | A lowercase hexadecimal string encoding a `MessageDigest` byte array. |

---

## 1. Base Syntax (existing, unchanged)

### 1.1 Keystore clause

```
keystore "<url>" [, "<keystore-type>"];
```

Specifies the keystore used to resolve `signedby` aliases.  Only the first
successfully loaded keystore is used; subsequent entries are ignored.

### 1.2 Grant clause

```
grant [signedby "<alias-list>"] [, codebase "<URL>"]
      [, principal [<PrincipalClass>] "<principal-name>"] ...
{
    permission <PermissionClass> ["<target>"] [, "<actions>"]
               [, signedby "<alias-list>"];
    ...
};
```

*   **`signedby`** — comma-separated list of keystore alias names.  Code must have
    been signed by all named certificates.
*   **`codebase`** — the URL from which the code was loaded.  Property expansion
    (`${key}`) is performed when `policy.expandProperties` is `true`.
*   **`principal`** — restricts the grant to code executing within a `Subject` that
    holds the named principal.  Both class and name may be the wildcard `*`.
*   **`permission`** — a fully-qualified permission class name, an optional target
    string, optional actions string, and an optional `signedby` constraint on the
    permission *class* itself.

Order of `signedby`, `codebase`, and `principal` fields within a `grant` header
is not significant.

---

## 2. HTTPMD Codebase URL (integrity-verified, pre-existing)

An HTTPMD URL embeds a message-digest directly in the URL path, allowing the policy
engine to verify JAR integrity without a separate PKI step.

### 2.1 URL format

```
httpmd://<host>[:<port>]/<path>;<algorithm>=<hex-digest>[,<comment>]
httpmd:<relative-path>;<algorithm>=<hex-digest>[,<comment>]
```

*   `<algorithm>` — a JCA `MessageDigest` algorithm name in any case
    (e.g. `sha-256`, `SHA-256`, `md5`).  Implementations **must** support at
    least `SHA-256`; use of `md5` or `sha-1` is deprecated.
*   `<hex-digest>` — lowercase hexadecimal encoding of the digest bytes.
*   `<comment>` — optional free-text comment after the digest; ignored by the engine.

### 2.2 Grant example

```
grant codebase "httpmd://cdn.example.com/lib/myservice-dl.jar;SHA-256=a3b4c5d6..." {
    permission java.net.SocketPermission "api.internal:8443", "connect";
};
```

### 2.3 Semantics

When the policy engine evaluates an `implies` call for a `CodeSource` whose URL is
an HTTPMD URL, it **downloads the JAR** (if not already cached), computes the named
digest, and compares it against the embedded value.  A mismatch causes `implies` to
return `false` as if no grant exists.

---

## 3. Digest Grant Clause (new in JGDMS-STD-004)

### 3.1 Motivation

HTTPMD URLs tie digest verification to a URL-based codebase.  For code distributed
through a content-addressed store, or loaded via the SCAP pipeline (where the
download URL is ephemeral), it is useful to express grants **by hash alone** — or by
hash combined with a URL for human readability.

`java.security.DigestCodeSource` (JDK 24 / DirtyChai) supports this model.  A
`digest` clause in the grant header maps directly to a `DigestCodeSource`-backed
`ProtectionDomain`.

### 3.2 Syntax

```
grant [signedby "<alias-list>"] [, codebase "<URL>"]
      [, digest "<algorithm>" "<hex-digest>"]
      [, principal [<PrincipalClass>] "<principal-name>"] ...
{
    permission <PermissionClass> ["<target>"] [, "<actions>"]
               [, signedby "<alias-list>"];
    ...
};
```

The `digest` field is a new, **optional** token in the grant header.

| Token | Type | Description |
|-------|------|-------------|
| `digest` | keyword | Introduces a digest constraint on the grant. |
| `"<algorithm>"` | quoted string | JCA `MessageDigest` algorithm name (e.g. `"SHA-256"`). Case-insensitive. |
| `"<hex-digest>"` | quoted string | Lowercase hex encoding of the computed digest.  Length must match the algorithm's output size. |

Multiple `digest` fields **may** appear in a single grant header to require agreement
across two independent algorithms:

```
grant codebase "https://repo.example.com/lib/proxy-1.2.jar",
      digest "SHA-256" "a3b4c5d6e7f8...",
      digest "SHA-512" "001122334455..." {
    permission java.net.SocketPermission "backend.internal:9090", "connect";
};
```

### 3.3 Semantics

At policy-load time the engine resolves each `digest` field to a
`java.security.MessageDigest` instance (algorithm + bytes) and creates a
`DigestCodeSource` whose identity is the set of those digests (and, if present,
the `codebase` URL).

During `implies` evaluation the engine computes the digest of the code being checked
and compares it against the stored values.  **All** specified digests must match.

If a `codebase` URL is also present, the URL is used as an additional (non-digest)
identity constraint: the code's `CodeSource` URL must imply the grant's URL, **and**
all digest values must match.

If only `digest` is present (no `codebase`), the grant applies to any code whose
content matches the digest, regardless of where it was loaded from.

### 3.4 Algorithm requirements

| Level | Algorithm | Requirement |
|-------|-----------|-------------|
| Mandatory | `SHA-256` | Implementations MUST support |
| Recommended | `SHA-512` | Implementations SHOULD support |
| Deprecated | `SHA-1`, `MD5` | Implementations MUST accept but SHOULD emit a warning |

### 3.5 Policy tool support

The `computedigest` tool (`org.apache.river.tool.ComputeDigest`) generates the
`hex-digest` value for a given JAR and algorithm:

```
java -jar computedigest.jar SHA-256 /path/to/myservice-dl.jar
```

The BAE pipeline (JGDMS-STD-002, Host 4) automatically computes `SHA-256` for every
downloaded JAR and includes it in the `AnalysisRequest.getContentHash()` field, which
is subsequently carried in `JarAnalysisReport.contentHash`.  Policy-generation tools
**should** use this value directly when emitting `digest` grant clauses for code that
has passed SCAP analysis.

### 3.6 Complete example

```
// Policy file example demonstrating digest-based grants

keystore "file://${user.home}/.jgdms/keystore.jks", "JKS";

// Classic codebase-only grant (unchanged)
grant codebase "file://${jsk.home}/lib/jsk-platform.jar" {
    permission java.security.AllPermission "", "";
};

// HTTPMD codebase grant (existing feature)
grant codebase "httpmd://files.example.com/service-dl.jar;SHA-256=aabbcc..." {
    permission java.net.SocketPermission "registry.example.com:4160", "connect";
};

// Digest-only grant (new in JGDMS-STD-004)
// Grants permissions to any code whose SHA-256 matches, wherever it was loaded from.
grant digest "SHA-256" "deadbeef0102..." {
    permission java.io.FilePermission "${java.io.tmpdir}/-", "read,write,delete";
};

// URL + digest grant (URL for human readability; hash for tamper-evidence)
grant codebase "https://nexus.corp/releases/proxy-2.0.jar",
      digest "SHA-256" "11223344aabb..." {
    permission net.jini.security.AuthenticationPermission
        "javax.security.auth.x500.X500Principal \"CN=backend,O=Corp\"",
        "connect";
    permission java.net.SocketPermission "backend.corp:443", "connect,resolve";
};

// JWT principal + digest grant (code + caller identity)
grant digest "SHA-256" "99aabb1122...",
      principal net.jini.security.JwtPrincipal "role:data-analyst" {
    permission java.net.SocketPermission "warehouse.internal:5432", "connect";
};
```

---

## 4. `META-INF/Permissions.list`

### 4.1 Purpose

`META-INF/Permissions.list` is an optional file inside a JAR archive that declares
the permissions the code in that JAR requires at runtime.  It is the JAR-side
counterpart to the policy file: the policy file is the system administrator's view
of what code may do; `Permissions.list` is the developer's view of what code *needs*
to do.

This file serves three roles:

| Role | Consumer | Outcome |
|------|----------|---------|
| **BAE audit signal** | `JarAnalyzer` (JGDMS-STD-002) | Upgrades `BLOCKING_GUARDED` → `BLOCKING_DECLARED` (DANGEROUS) when a blocking sink's required permission is declared. Prevents a JAR that already requests a dangerous permission from receiving an INCONCLUSIVE verdict. |
| **Policy generation input** | Policy generation tools | Tools extract the declared permissions to generate minimal, correct `grant` clauses automatically. |
| **Human documentation** | Developers / auditors | Self-documents the JAR's security requirements alongside its code. |

### 4.2 File location

The file **must** appear at exactly the following path within the JAR:

```
META-INF/Permissions.list
```

The path is matched case-insensitively in the BAE pipeline
(`"META-INF/PERMISSIONS.LIST".equals(entryName)` with `toUpperCase` normalisation
applied to the JAR entry name before comparison).

> **Note:** Although the BAE currently uses `META-INF/PERMISSIONS.LIST`
> (all-caps) for matching, the canonical path for new JARs is
> `META-INF/Permissions.list` (mixed case).  Tools that generate the file
> **should** use `META-INF/Permissions.list`; tools that read it **must** accept
> both forms.

### 4.3 Syntax

```
# This is a comment — ignored by all consumers.
# Blank lines are also ignored.

permission <PermissionClass> ["<target>"] [, "<actions>"];
permission <PermissionClass> ["<target>"] [, "<actions>"];
...
```

*   The file is UTF-8 encoded.
*   Each non-blank, non-comment line is a **permission entry** using standard Java
    policy-file permission syntax.
*   Lines are trimmed of leading and trailing whitespace before processing.
*   Comments begin with `#` as the first non-whitespace character on the line.
*   A single permission entry **must** fit on one line; multi-line continuations are
    not supported.
*   Lines are included verbatim in `JarAnalysisReport.getDeclaredPermissions()`;
    they are sorted lexicographically before being signed by the BAE engine.

### 4.4 Example `META-INF/Permissions.list`

```
# Permissions required by com.example.myservice-dl.jar
# Generated by: mvn jgdms:generate-permissions-list

# Network access to the backend API
permission java.net.SocketPermission "api.corp.internal:8443", "connect,resolve";

# Read the service configuration file
permission java.io.FilePermission "${user.home}/.myservice/config.properties", "read";

# JERI authentication
permission net.jini.security.AuthenticationPermission
    "javax.security.auth.x500.X500Principal \"CN=client,O=Corp\"", "connect";

# Required by the virtual-thread scheduler path
permission java.lang.RuntimePermission "createVirtualThread";
```

> **Note:** Although the last example contains `RuntimePermission "createVirtualThread"`,
> the BAE will treat this as `BLOCKING_DECLARED` (DANGEROUS) if the JAR's bytecode
> reachability graph shows an unguarded `Thread::ofVirtual` call.  Declaring a dangerous
> permission does **not** whitelist it; it makes the danger explicit and escalates the
> BAE verdict.

### 4.5 Matching rules

The BAE matches a blocking sink against `Permissions.list` using the
`SINK_TO_PERMISSION_CLASS` registry (see JGDMS-STD-002 §BAE).  Two match forms exist:

| Match form | `SINK_TO_PERMISSION_CLASS` value | Match criteria |
|---|---|---|
| Class-only | `"java.net.SocketPermission"` | Line starts with `permission java.net.SocketPermission` followed by a non-identifier character |
| Class+action | `"java.lang.RuntimePermission#createVirtualThread"` | Line starts with `permission java.lang.RuntimePermission` **and** contains the quoted string `"createVirtualThread"` |

The class+action form prevents false positives where a broad permission class
(e.g. `RuntimePermission`) has many unrelated action names with very different
security implications.

### 4.6 Relationship to the grant clause

A `Permissions.list` entry and a policy-file `permission` entry use identical syntax
but are evaluated differently:

| | `META-INF/Permissions.list` | Policy-file `permission` |
|---|---|---|
| **Location** | Inside the JAR | In the system/application policy file |
| **Author** | JAR developer | System administrator |
| **Effect** | Declares intent (read by BAE + tools) | Grants permissions at runtime |
| **Enforcement** | Informational for the BAE; ignored by `SecurityManager` | Enforced by `AccessController` |

A `Permissions.list` entry that has no corresponding `grant` in the policy file is
not an error; the permission is simply not granted.  A well-operated system will
eventually reconcile the two: SCAP analysis produces a `JarAnalysisReport` whose
`declaredPermissions` field drives automated policy generation.

---

## 5. Grammar Reference

The following ABNF grammar extends
[RFC 5234](https://www.rfc-editor.org/rfc/rfc5234) to describe the complete
JGDMS-STD-004 policy file syntax.

```abnf
policy-file    = *(keystore-clause / grant-clause / comment / whitespace)

; ── Keystore ──────────────────────────────────────────────────────────────────
keystore-clause = "keystore" SP quoted-string
                  [ "," SP quoted-string ] ";"

; ── Grant ─────────────────────────────────────────────────────────────────────
grant-clause   = "grant" SP grant-header SP "{" *(permission-entry) "}" ";"

grant-header   = [grant-field *("," SP grant-field)]

grant-field    = signedby-field
               / codebase-field
               / digest-field           ; NEW in JGDMS-STD-004
               / principal-field

signedby-field  = "signedby" SP quoted-string
codebase-field  = "codebase" SP quoted-string
digest-field    = "digest" SP quoted-string SP quoted-string
                  ; first string = algorithm, second = hex digest
principal-field = "principal" SP [class-name SP] quoted-string

; ── Permission entry ──────────────────────────────────────────────────────────
permission-entry = "permission" SP class-name
                   [SP quoted-string]
                   ["," SP quoted-string]
                   ["," SP signedby-field]
                   ";"

; ── Shared tokens ─────────────────────────────────────────────────────────────
class-name     = 1*(ALPHA / DIGIT / "." / "_" / "$")
quoted-string  = DQUOTE *(%x20-21 / %x23-7E) DQUOTE
                 ; any printable ASCII inside double-quotes, except DQUOTE itself
hex-string     = 1*(HEXDIG)     ; lowercase preferred; case-insensitive on input
SP             = 1*WSP
comment        = ("/*" ... "*/") / ("//" ... EOL) / ("#" ... EOL)
```

### 5.1 Property expansion

Where `policy.expandProperties=true` (the default), any token of the form `${key}`
inside a double-quoted string is replaced by the value of the Java system property
`key`.  The special token `${/}` expands to `file.separator`.  If the property is
not defined, the enclosing clause is silently dropped.

Property expansion applies to: `keystore` URLs, `codebase` URLs, `principal` names,
and `permission` target strings.  It does **not** apply to `digest` hex values.

---

## 6. Permissions.list File Grammar

```abnf
permissions-list = *(comment-line / blank-line / permission-line)

permission-line = "permission" SP class-name
                  [SP quoted-string]
                  ["," SP quoted-string]
                  ";" EOL

comment-line   = "#" *VCHAR EOL
blank-line     = EOL
```

Lines are trimmed before matching; leading/trailing whitespace is not significant.

---

## 7. Policy Engine Behaviour

### 7.1 Loading order

1. The engine reads all policy files referenced by `java.security.policy` (or
   configured via `policy.url.N` security properties).
2. For each `grant` clause the engine creates a `PermissionGrant` record.
3. A `grant` with a `digest` field creates a `PermissionGrant` backed by a
   `DigestCodeSource` (or an equivalent engine-internal representation if
   `java.security.DigestCodeSource` is not available on the running JVM; in
   that case the `digest` constraint is treated as unsatisfiable and the grant
   is never matched — **fail-secure**).

### 7.2 Implies evaluation

For each `AccessController.checkPermission` call, the engine iterates over all
`PermissionGrant` records and calls `implies(ProtectionDomain pd)`:

| Grant field present | Match condition |
|---|---|
| `codebase` only | `grant.codebase.implies(pd.codeSource.location)` |
| `digest` only | `pd.codeSource instanceof DigestCodeSource` AND all digests match |
| `codebase` + `digest` | Both conditions must hold |
| `signedby` only | All certificates in the grant are in `pd.codeSource.certificates` |
| `principal` | All specified principals are in the executing `Subject` |

Multiple conditions are ANDed.

### 7.3 Fail-secure on missing DigestCodeSource

If the JVM does not provide `java.security.DigestCodeSource`:

*   `digest`-only grants are **silently dropped** at load time.
*   A warning is emitted to the system security logger
    (`java.security.policy` logger, level `WARNING`).
*   `codebase`-only and HTTPMD grants continue to function normally.

This ensures that a deployment on an older JVM continues to operate; the only
consequence is that digest-hardened grants fall back to no-grant (restrictive).

### 7.4 HTTPMD vs digest clause

| Feature | HTTPMD codebase URL | `digest` grant field |
|---|---|---|
| Digest algorithm embedded in | URL path | Grant clause |
| URL required | Yes (the URL is the transport mechanism) | No |
| JVM support | JGDMS 2.0+ | JDK 24 / DirtyChai + JGDMS-STD-004 |
| Best for | Code loaded over HTTP where URL is stable | Code in a content-addressed store; SCAP-verified JARs |

---

## 8. Integration with JGDMS-STD-002 (SCAP Pipeline)

The digest-based grant clause and `META-INF/Permissions.list` are designed to work
together with the SCAP pipeline:

```
JAR published         BAE analyses         VerdictRegistry       Policy admin
on CDN / Maven   →    bytecode         →   stores SAFE verdict →  generates policy
                       reads Permissions.list                       with digest grants
                       computes SHA-256
```

1. **BAE (Host 2)** reads `META-INF/Permissions.list` from the JAR and records
   `declaredPermissions` in the signed `JarAnalysisReport`.
2. **VerdictRegistry (Host 3)** issues a `SAFE` `RegistryVerdict` that includes
   the `contentHash` (SHA-256 hex) and `declaredPermissions`.
3. **Policy admin tooling** (out of scope for this standard) reads the
   `RegistryVerdict` and emits `grant` clauses of the form:
   ```
   grant digest "SHA-256" "<contentHash>" {
       // one entry per line in declaredPermissions
       permission ...;
   };
   ```
4. **Policy provider** (`ConcurrentPolicyFile` + `DynamicPolicyProvider`) loads the
   generated file.  At runtime, `implies` checks code identity by digest, so a
   JAR whose content changes (even if its URL does not) will fail the check.

This pipeline guarantees that **only code whose bytecode has been audited and whose
identity matches the audit record** receives the permissions declared in
`Permissions.list`.

---

## 9. Relationship to Other Standards

| Standard | Relationship |
|---|---|
| JGDMS-STD-001 (@AtomicSerial) | Independent. Applies to all Serializable classes; does not affect policy syntax. |
| JGDMS-STD-002 (SCAP Pipeline) | Complementary. SCAP produces `contentHash` and `declaredPermissions` that feed this standard's digest grant and `Permissions.list` features. |
| JGDMS-STD-003 (Multi-Subject Identity) | Complementary. `principal` fields in grant clauses reference the identity model defined in STD-003 (JWT, SPIFFE, X.500). |

---

## 10. Normative Checklist for Implementors

A conforming JGDMS policy engine (parser + provider) **MUST**:

- [x] Accept all grant-header field orders (codebase, signedby, digest, principal).
- [x] Parse `digest "<algorithm>" "<hex>"` in grant headers.
- [x] Support multiple `digest` fields in a single grant header.
- [x] Create `DigestCodeSource`-backed `ProtectionDomain`s when `digest` is present
      and `java.security.DigestCodeSource` is available.
- [x] Silently drop `digest`-only grants (with a `WARNING` log entry) when
      `DigestCodeSource` is not available.
- [x] Reject (log + skip) a `digest` field whose hex value length is inconsistent
      with the named algorithm's output size.
- [x] Treat digest comparison as case-insensitive hex.

A conforming JAR build tool **MUST**:

- [x] Place `META-INF/Permissions.list` (mixed case) in the JAR when the developer
      specifies declared permissions.
- [x] Use one `permission <ClassName> ["target"] [, "actions"];` entry per line.
- [x] Encode the file as UTF-8.

A conforming BAE implementation **MUST**:

- [x] Read `META-INF/Permissions.list` (case-insensitive JAR entry match).
- [x] Include sorted `declaredPermissions` in the canonical signing bytes of
      `JarAnalysisReport`.
- [x] Upgrade `BLOCKING_GUARDED` → `BLOCKING_DECLARED` when a blocking sink's
      guarding permission (or className#action pair) is declared in `Permissions.list`.

---

## 11. Non-Goals

*   This standard does **not** define a `deny` clause.  `deny` is under consideration
    as a future extension; if added, it will be addressed in a revision of this
    document.
*   This standard does **not** redefine the HTTPMD URL format; that is governed by the
    `net.jini.url.httpmd` package and the original Jini 2.0 specification.
*   This standard does **not** specify how policy files are discovered or loaded; that
    remains the responsibility of the `PolicyFileProvider` / `ConcurrentPolicyFile`
    configuration.

---

*Draft written 2026-05-11.  Status will advance to Active once the `digest` grant
 field is implemented in `DefaultPolicyScanner` and `DefaultPolicyParser` and the
 `META-INF/Permissions.list` BAE integration is verified end-to-end against
 JGDMS-STD-002.*
