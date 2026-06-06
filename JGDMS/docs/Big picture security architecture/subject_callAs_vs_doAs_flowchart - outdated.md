# Subject.callAs (User Subject) vs Subject.doAs (Worker Process Subject)

This document illustrates the two complementary Subject-propagation mechanisms used in JGDMS and
how they are consumed together by the security infrastructure.

---

## Flowchart

```mermaid
flowchart TB
    subgraph USER["👤 User Subject  —  LoginContext → Subject.callAs()  (JDK 21 ScopedValue)"]
        direction TB
        U1["User authenticates via LoginContext\n→ userSubject obtained\n(KerberosPrincipal / X500Principal)"]
        U2["Client / dispatcher calls\nSubject.callAs(userSubject, action)"]
        U3["JDK 21 ScopedValue bound to\ncalling thread's scope\n(Subject.CURRENT scoped var)"]
        U4["Action and any nested calls see\nSubject.current() → userSubject"]
        U5["Scope exits (return / exception)\n→ ScopedValue automatically cleared\n(no manual cleanup needed)"]
        U1 --> U2 --> U3 --> U4 --> U5
    end

    subgraph WORKER["⚙️ Worker Process Subject  —  Global SPIFFE Subject / Subject.getSubject(acc)\n(AccessControlContext)"]
        direction TB
        W1["SPIFFE infrastructure provisions\nglobal process worker Subject\n(SpiffePrincipal / X.509 SVID)\nat process start — not LoginContext"]
        W2["Subject.doAsPrivileged / callAs wraps\nservice bootstrap — embeds SPIFFE Subject\ninto the AccessControlContext (ACC)"]
        W3["Worker / executor threads inherit\nthe ACC from their parent context"]
        W4["Subject.getSubject(\n  AccessController.getContext()\n) → workerSubject"]
        W1 --> W2 --> W3 --> W4
    end

    subgraph RETRIEVE["🔐 Reading both Subjects\n(must be inside AccessController.doPrivileged —\nboth calls trigger a permission check)"]
        direction LR
        R1["AccessController.doPrivileged(\n  () -> new Subject[]{\n    Subject.current(),         // user\n    Subject.getSubject(acc)    // worker\n  }\n)"]
    end

    subgraph GRANT["📜 Security.getCurrentPrincipals()\ncalled by Security.grant(Class, Permission[])"]
        direction TB
        G1{{"user == null?"}}
        G2{{"worker == null?"}}
        G3["Return worker principals only"]
        G4["Return user principals only"]
        G5["Return UNION of\nuser ∪ worker principals\n\nPOLP: grant applies only when the caller\nsimultaneously holds BOTH identities"]
        G1 -- yes --> G3
        G1 -- no  --> G2
        G2 -- yes --> G4
        G2 -- no  --> G5
    end

    subgraph SSL["🔒 SslEndpointImpl.getCallContext()\n(outbound TLS — X.509 / SPIFFE)"]
        direction TB
        S1{{"Subject.getSubject(acc)\nhas X500Principal\nor SpiffePrincipal?"}}
        S2["Use ACC Subject\n(SPIFFE workload / service identity)"]
        S3{{"SpiffeSubjectHolder.get()\nnon-null?"}}
        S4["Use SPIFFE holder Subject"]
        S5{{"Subject.current()\nhas X500Principal\nor SpiffePrincipal?"}}
        S6["Use Subject.current()\n(legacy callAs-only app fallback)"]
        S7["No usable Subject\n(anonymous / unauthenticated)"]
        S1 -- yes --> S2
        S1 -- no  --> S3
        S3 -- yes --> S4
        S3 -- no  --> S5
        S5 -- yes --> S6
        S5 -- no  --> S7
    end

    subgraph KRB["🎫 KerberosEndpoint.newRequest()\n(GSS-API / Kerberos)"]
        direction TB
        K1{{"Subject.current()\nhas KerberosPrincipal?"}}
        K2["Use Subject.current()\n(interactive user Kerberos identity)\nGSS credential via Subject.doAs"]
        K3{{"Subject.getSubject(acc)\nhas KerberosPrincipal?"}}
        K4["Use ACC Subject\n(service / workload Kerberos identity)"]
        K5["No Kerberos Subject available"]
        K1 -- yes --> K2
        K1 -- no  --> K3
        K3 -- yes --> K4
        K3 -- no  --> K5
    end

    USER   --> RETRIEVE
    WORKER --> RETRIEVE
    RETRIEVE --> GRANT
    RETRIEVE --> SSL
    RETRIEVE --> KRB
```

---

## Summary table

| Aspect | `Subject.callAs()` — User Subject | `Subject.getSubject(acc)` — Worker Subject |
|---|---|---|
| **Storage mechanism** | JDK 21 `ScopedValue` (thread-local, lexically scoped) | `AccessControlContext` (inherited by child threads) |
| **Propagates to child threads?** | No — scope is strictly per-calling-thread | Yes — child threads inherit the parent ACC |
| **Lifetime** | Lexical scope of the `callAs` lambda | Lifetime of any thread that holds the parent ACC |
| **Who sets it** | `LoginContext` authenticates the user; `Subject.callAs` binds per-request (e.g. `BasicInvocationDispatcher` on dispatch; event tasks in `RegistrarImpl.pendingEvent`) | **SPIFFE infrastructure** provisions a global process SVID (`SpiffePrincipal` / X.509) at process start — `Subject.doAsPrivileged` embeds it into the ACC |
| **Read API** | `Subject.current()` | `Subject.getSubject(AccessController.getContext())` |
| **Must be inside `doPrivileged`?** | Yes — triggers a permission check | Yes — triggers a permission check |
| **Typical principal types** | `KerberosPrincipal`, `X500Principal` (human user) | `SpiffePrincipal`, `X500Principal` (SPIFFE workload SVID) |
| **TLS credential priority (SSL)** | Checked last (legacy fallback only if has X500/SPIFFE) | Checked first |
| **Kerberos credential priority** | Checked first (user wins) | Checked second |
| **`Security.grant` behaviour** | Principals consulted by `Security.getCurrentPrincipals()` | Principals consulted by `Security.getCurrentPrincipals()` |
| **When both present** | Principal sets are **unioned** — grant fires only when the caller simultaneously holds *both* the user identity and the workload identity, enforcing Principle of Least Privilege (POLP) |

---

## Relevant source locations

| File | What it does |
|---|---|
| `jgdms-jeri/.../BasicInvocationDispatcher.java` | Calls `Subject.callAs(clientSubject, action)` on every inbound RPC dispatch (`invokeWithClientSubject`) |
| `jgdms-jeri/.../BasicInvocationHandler.java` | Reads `Subject.current()` (`getUserPrincipals`) for outbound calls |
| `jgdms-service-support/.../AbstractJiniService.java` | Wraps service bootstrap with `Subject.doAsPrivileged` to embed the SPIFFE worker Subject into the ACC |
| `jgdms-platform/.../Security.java:1233–1277` | `getCurrentPrincipals()` — unions user + worker principals inside a single `doPrivileged` |
| `jgdms-jeri/.../SslEndpointImpl.java:278–325` | ACC Subject (SPIFFE) → SPIFFE holder → `Subject.current()` priority chain for TLS |
| `jgdms-jeri/.../KerberosEndpoint.java:638–655` | `Subject.current()` → ACC Subject priority chain for Kerberos GSS |
| `services/reggie/.../RegistrarImpl.java` | `EventReg` captures `Subject.current()` in constructor; `pendingEvent()` re-applies it via `Subject.callAs` |
