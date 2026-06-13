# Agent Context: SPIFFE Discovery Format Provider

**Task:** Implement the `net.jini.discovery.x500.SHA256withECDSA` multicast discovery
format and the DER unicast discovery response format for JGDMS/DirtyChai, enabling
SPIFFE-attested embedded devices to participate as Jini Lookup Services.

**Status:** Design complete (JGDMS-STD-006 v0.4-DRAFT §7.7). Implementation not started.

**Prepared from:** Extended architectural discussion with Peter Firmstone,
session context file `Discussion_with_Claude_about_Subjects_Access_Control_Architecture
_and_DER_JERI_AtomicSerial.txt`.

---

## 1. Background and Motivation

JGDMS uses the Jini discovery protocol for service discovery. The existing multicast
discovery formats (`net.jini.discovery.x500.SHA256withDSA`,
`net.jini.discovery.x500.SHA256withRSA`) require DSA or RSA keys. SPIFFE SVIDs —
the process identity mechanism used by JGDMS/DirtyChai — use EC (P-256 or P-384)
keys. Without an ECDSA format, a SPIFFE-attested Registrar cannot sign multicast
announcements using its own SVID credential.

Additionally, the existing unicast discovery response carries a Java-serialized
`ServiceRegistrar` proxy plus an inline certificate-grant block
(`writeClassAnnotationCerts`/`readAnnotationCertsGrantPerm`). Both are incompatible
with the DER wire format (STD-006) and the codebase annotation deprecation.

The goal is:
1. A new `net.jini.discovery.x500.SHA256withECDSA` multicast format supporting SVID
   credentials.
2. CA-based SPIFFE trust verification replacing static leaf-cert trust store lookup.
3. A DER unicast response format replacing the Java-serialized proxy + cert-grant block.
4. Integration with `SpiffeCredentialManager` for automatic trust material refresh on
   SVID rotation.

---

## 2. Key Design Decisions (do not relitigate)

### 2.1 CA-based trust, not leaf-cert pinning

`X500Provider.getCertificate(p)` currently searches a static trust store for a
certificate matching the given `X500Principal`. For SPIFFE this is wrong: the SVID
leaf cert rotates hourly and clients cannot maintain a current copy. The correct
model is:

- The **trust anchor** is the SPIRE CA certificate (`X509Certificate[]` trust bundle
  from `SpiffeCredentialManager`).
- Verification is PKIX chain validation against the CA cert, not direct lookup of
  the leaf cert.
- The signer's leaf certificate must therefore travel with the multicast packet
  so the verifier can validate the chain.

This means the multicast packet format for the SHA256withECDSA format is slightly
different from DSA/RSA: it includes the encoded leaf certificate alongside the
X500Principal name and signature. The verifier reconstructs the chain and validates
it against the SPIRE trust bundle. **See §5.1 for the updated packet structure.**

### 2.2 SVID rotation has no impact when trust is CA-based

When `SpiffeCredentialManager` rotates the SVID:
- **Signing side**: `getPrivateCredentials()` reads the current `X500PrivateCredential`
  from the WorkerSubject — it always has the current key. No change needed.
- **Verification side**: The verifier checks the signer's leaf cert against the SPIRE
  CA cert. As long as the CA cert is stable (it is — SPIRE CAs rotate on the order
  of months, not hours), verification works transparently across SVID rotations.

The `SvidRotationListener` mechanism (already used by `SpiffePolicyFile`) can be
reused to notify the discovery provider when the trust bundle itself changes (rare).

### 2.3 The cert-grant mechanism is removed

`EndpointBasedServer.writeClassAnnotationCerts` and
`EndpointBasedClient.readAnnotationCertsGrantPerm` are the in-band cert-grant
mechanism used to establish proxy JAR trust during unicast discovery. This mechanism
is deprecated along with codebase annotations (§8 of STD-006). Do NOT port it to
the new DER format. Proxy trust is established via the normal
`VerdictRegistry` + `DigestGrant` + `LoadClassPermission` path.

### 2.4 ProxyDescriptor replaces the serialized proxy

The unicast response in the DER format carries a `ProxyDescriptor` (STD-006 §7.7.4)
instead of a Java-serialized `ServiceRegistrar` proxy. The two CHOICE arms are:
- `JeriEndpointRecord` — the device speaks JERI DER natively (give clients a JERI
  stub endpoint directly).
- `ServiceSpecRecord` — the device speaks another protocol; a proxy factory generates
  the JAR.

For the initial embedded sidecar Registrar use case, `JeriEndpointRecord` is
expected. The proxy factory path is deferred.

### 2.5 SPIFFE Subject DN — critical deployment requirement

The X500 multicast protocol identifies signers by their `X500Principal` (the Subject
DN of the certificate). SPIFFE SVIDs MAY have an empty Subject DN (the SPIFFE ID is
in the URI SAN, not the Subject DN). If the Subject DN is empty, `X500Principal`
identification breaks.

**Deployment requirement**: SPIRE registration entries for embedded Registrars (and
all JVM Registrars using this format) MUST be configured to include a non-empty
Subject DN in issued SVIDs. The recommended form is `CN=<spiffe-id-path-component>`,
e.g. `CN=lookup` for `spiffe://jgdms.example.org/host/lookup`.

This must be documented in the Registrar deployment guide. An implementation-side
guard should log a clear error (not a cryptic NPE) if `getSubjectX500Principal()`
returns an empty DN.

---

## 3. Repository Structure

The discovery providers live in `jgdms-discovery-providers`. The relevant existing
packages and their locations:

```
jgdms-discovery-providers/src/main/java/
  org/apache/river/discovery/
    internal/
      BaseProvider.java          — base; getFormatName()
      EndpointBasedProvider.java — abstract endpoint/unicast base
      EndpointBasedClient.java   — unicast client; override readUnicastResponse()
      EndpointBasedServer.java   — unicast server; override writeUnicastResponse()
      X500Provider.java          — X500 credential/trust infrastructure; MODIFY
      X500Client.java            — multicast client superclass
      X500Server.java            — multicast server superclass
      X500Constraints.java       — constraint processing; no changes needed
    x500/
      sha256withdsa/             — template: Constants, Client, Server
      sha256withrsa/             — second template
    ssl/
      sha224/                    — template for endpoint-based unicast
    plaintext/
      Server.java                — simplest unicast server; study for structure
```

**New packages to create:**

```
  x500/
    sha256withecdsa/             — NEW: Constants, Client, Server (thin wrappers)
  spiffe/                        — NEW: SPIFFE-aware trust verification subclasses
    SpiffeX500Provider.java      — extends X500Provider; CA-based trust
    SpiffeMulticastClient.java   — extends X500Client; uses SpiffeX500Provider
    SpiffeMulticastServer.java   — extends X500Server; uses SpiffeX500Provider
  ssl/
    der/                         — NEW: DER unicast response format
      DerUnicastClient.java      — extends EndpointBasedClient
      DerUnicastServer.java      — extends EndpointBasedServer
```

---

## 4. Phase 1 — SHA256withECDSA Multicast Format (thin wrappers)

This is the easiest phase and should be done first to establish the basic ECDSA
wiring before tackling the trust model change.

### 4.1 New Constants

Create
`org/apache/river/discovery/x500/sha256withecdsa/Constants.java`:

```java
package org.apache.river.discovery.x500.sha256withecdsa;

class Constants {
    static final String FORMAT_NAME        = "net.jini.discovery.x500.SHA256withECDSA";
    static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    // P-256 DER signature: max 72 bytes. Use 104 for P-384 support.
    static final int    MAX_SIGNATURE_LEN  = 72;
    // PrivateKey.getAlgorithm() returns "EC" for all elliptic curve keys
    static final String KEY_ALGORITHM      = "EC";
    // ecPublicKey OID — used by X509CertSelector.setSubjectPublicKeyAlgID
    static final String KEY_ALGORITHM_OID  = "1.2.840.10045.2.1";
    private Constants() {}
}
```

### 4.2 Client and Server wrappers

Identical structure to `sha256withdsa/Client.java` and `sha256withdsa/Server.java`.
Reference those files directly; only `Constants` reference and class names change.
Include the same `@Requirement`/`@Capability` OSGi annotations.

### 4.3 Important: X500Provider key algorithm check

`X500Provider.getPrivateCredentials()` filters by:
```java
if (keyAlgorithm.equals(cred.getPrivateKey().getAlgorithm()) && ...)
```

For EC keys `getAlgorithm()` returns `"EC"`. With `Constants.KEY_ALGORITHM = "EC"`
this will correctly select ECDSA credentials from the WorkerSubject. **Verify this
in a unit test before moving to Phase 2.**

---

## 5. Phase 2 — CA-Based Trust Verification (SpiffeX500Provider)

This is the architecturally significant phase. The goal is to replace
`X500Provider.getCertificate(p)` with PKIX chain validation against the SPIRE trust
bundle.

### 5.1 Updated multicast packet structure

The standard X500 packet is:
```
[4B: data length][plaintext data...][principal name UTF-8][signature DER]
```

For SPIFFE we need to include the signer's leaf certificate so the verifier can
validate the chain. The updated packet for the SPIFFE format adds the DER-encoded
leaf certificate after the principal name:

```
[4B: data length][plaintext data...][principal name UTF-8]
[2B: cert length][cert DER bytes][signature DER]
```

This changes the `SigningBufferFactory` behaviour. The `SpiffeMulticastServer`
(announcement encoder) must write the cert; the `SpiffeMulticastClient`
(announcement decoder) must read and validate it.

**Note**: This is a format-level change, not in the existing `X500Provider` base.
Create `SpiffeMulticastServer`/`SpiffeMulticastClient` as subclasses that override
the signing and decoding methods. The existing SHA256withDSA/RSA formats are
unchanged.

### 5.2 SpiffeX500Provider

```java
package org.apache.river.discovery.spiffe;

public abstract class SpiffeX500Provider extends X500Provider {
    
    // Trust material: volatile for safe publication across SvidRotationListener
    // notifications. SpiffeCredentialManager.getTrustBundle() returns X509Certificate[].
    private volatile X509Certificate[] trustBundle;
    
    // SvidRotationListener registered with SpiffeCredentialManager to refresh
    // trustBundle when SPIRE rotates the CA (rare, but must be handled).
    private final SvidRotationListener rotationListener = () -> refreshTrustBundle();
    
    protected SpiffeX500Provider(String formatName, String signatureAlgorithm,
                                  int maxSignatureLength, String keyAlgorithm,
                                  String keyAlgorithmOID) {
        super(formatName, signatureAlgorithm, maxSignatureLength,
              keyAlgorithm, keyAlgorithmOID);
        refreshTrustBundle();
        SpiffeCredentialManager.getInstance().addSvidRotationListener(rotationListener);
    }
    
    private void refreshTrustBundle() {
        SpiffeCredentialManager mgr = SpiffeCredentialManager.getInstance();
        if (mgr != null) {
            trustBundle = mgr.getTrustBundle();
        }
    }
    
    /**
     * Overrides X500Provider.getCertificate() to use PKIX chain validation
     * against the SPIRE trust bundle rather than direct trust store lookup.
     *
     * The leaf certificate arrives in the multicast packet (written by
     * SpiffeMulticastServer). This method validates it against the trust bundle
     * and returns it if valid.
     *
     * @param principal the signer's X500Principal (from the packet)
     * @param leafCert  the signer's leaf certificate (from the packet)
     * @return the validated leaf certificate, or null if validation fails
     */
    protected Certificate validateSpiffeCert(X500Principal principal,
                                              X509Certificate leafCert)
        throws CertificateException, GeneralSecurityException
    {
        // Guard: Subject DN must not be empty (deployment requirement §2.5)
        if (principal.getName().isEmpty()) {
            throw new CertificateException(
                "SPIFFE SVID has empty Subject DN. Configure SPIRE to include " +
                "a non-empty Subject DN in issued SVIDs. See deployment guide.");
        }
        
        // Verify principal name matches the cert Subject DN
        if (!principal.equals(leafCert.getSubjectX500Principal())) {
            throw new CertificateException(
                "Principal name mismatch: " + principal + " vs " +
                leafCert.getSubjectX500Principal());
        }
        
        X509Certificate[] bundle = trustBundle;
        if (bundle == null || bundle.length == 0) {
            throw new CertificateException("SPIRE trust bundle not available");
        }
        
        // PKIX validation against SPIRE CA trust bundle
        CertPathValidator validator =
            CertPathValidator.getInstance("PKIX");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath certPath = cf.generateCertPath(
            Collections.singletonList(leafCert));
        
        Set<TrustAnchor> anchors = new HashSet<>();
        for (X509Certificate ca : bundle) {
            anchors.add(new TrustAnchor(ca, null));
        }
        PKIXParameters params = new PKIXParameters(anchors);
        params.setRevocationEnabled(false); // SVIDs are short-lived; no CRL/OCSP
        
        validator.validate(certPath, params);
        return leafCert; // throws CertPathValidatorException if invalid
    }
}
```

**[OPEN]** Confirm the exact API for `SpiffeCredentialManager.getTrustBundle()` —
the `SECURITY_MODEL.md` confirms the trust bundle is `X509Certificate[]` held by
`SpiffeCredentialManager`, but the method name needs to be verified against the
actual source.

**[OPEN]** Confirm `SpiffeCredentialManager.addSvidRotationListener(listener)` as
the registration API. `SvidRotationListener` is used by `SpiffePolicyFile`; confirm
whether it is a functional interface or has multiple methods.

### 5.3 SpiffeMulticastServer and SpiffeMulticastClient

These classes need to:
- Write/read the leaf certificate bytes in the packet (§5.1 packet structure)
- Call `validateSpiffeCert(principal, leafCert)` instead of `getCertificate(p)`

Override the `sign()` path in `SigningBufferFactory` to also write the leaf cert.
Override the `decodeMulticastAnnouncement`/`decodeMulticastRequest` to read the
cert before reading the signature.

The `maxSignatureLength` in the `BufferInfo` reservation arithmetic must also
account for `2 + maxCertLength` bytes. P-256 leaf certs are typically 400–600 bytes.
Use a configurable `MAX_CERT_LEN` constant (e.g. 1024 bytes) and validate
at read time.

**Important**: The standard Jini multicast datagram is limited. Check that the
packet fits within the configured datagram buffer size with the extra cert bytes.
For small UDP MTUs (~1500 bytes) this may be tight. Consider whether the cert
should only be included on the FIRST announcement after a rotation, with subsequent
announcements omitting it (relying on the verifier's cert cache). Flag this as
an [OPEN] if MTU constraints are an issue.

---

## 6. Phase 3 — DER Unicast Response Format

The DER unicast response replaces the Java-serialized proxy + cert-grant block.
The extension points are `protected` overrideable methods already in place.

### 6.1 DerUnicastServer

Extends `EndpointBasedServer`. Override:

```java
@Override
protected void writeUnicastResponse(OutputStream out,
                                    UnicastResponse response,
                                    Collection context)
    throws IOException
{
    // DO NOT call writeClassAnnotationCerts — deprecated and removed.
    // DO NOT call Plaintext.writeUnicastResponse — Java serialized format.
    
    // Write DER-encoded UnicastResponseRecord per STD-006 §7.7.3:
    //   host (UTF8String)
    //   port (INTEGER)
    //   groups (SEQUENCE OF UTF8String) — ORDER-SIGNIFICANT
    //   serviceId (OCTET STRING 16 bytes)
    //   proxy (ProxyDescriptor CHOICE)
    
    ServiceRegistrar reg = response.getRegistrar();
    // Extract host/port/groups from response
    // Build ProxyDescriptor from reg (JeriEndpointRecord for now)
    // Encode as DER and write to out
    
    out.flush();
}
```

**[OPEN]** The `UnicastResponse` API — confirm what fields are directly available
(host, port, groups, serviceID, registrar proxy reference). These drive the
`UnicastResponseRecord` fields.

**[OPEN]** The `ProxyDescriptor` construction — for a JVM JGDMS Registrar, the
proxy is a JERI endpoint. For an embedded device, it would be a `ServiceSpecRecord`.
The initial implementation should support `JeriEndpointRecord` only, with
`ServiceSpecRecord` deferred.

### 6.2 DerUnicastClient

Extends `EndpointBasedClient`. Override:

```java
@Override
protected UnicastResponse readUnicastResponse(InputStream in,
                                               ClassLoader defaultLoader,
                                               boolean verifyCodebaseIntegrity,
                                               ClassLoader verifierLoader,
                                               Collection context)
    throws IOException, ClassNotFoundException
{
    // DO NOT call readAnnotationCertsGrantPerm — deprecated and removed.
    // DO NOT call Plaintext.readUnicastResponse — Java serialized format.
    
    // Read DER-encoded UnicastResponseRecord per STD-006 §7.7.3.
    // Decode ProxyDescriptor CHOICE.
    // For JeriEndpointRecord: construct a JERI stub to the described endpoint.
    // Return UnicastResponse wrapping the constructed ServiceRegistrar proxy.
}
```

**[OPEN]** Constructing a `ServiceRegistrar` proxy from a `JeriEndpointRecord`
at the client side — this requires building a JERI endpoint from host/port/spiffeId
and constructing an `InvocationHandler`. Confirm the right factory path in JGDMS
for this (likely `SslEndpoint` + `AtomicILFactory`).

---

## 7. Key Files to Read Before Starting

| File | Why |
|---|---|
| `X500Provider.java` | Base class; understand `getPrivateCredentials()`, `getCertificate()`, `verify()`, and `SigningBufferFactory` fully before modifying |
| `X500Client.java` | Multicast client; study `decodeMulticastAnnouncement()` packet reading path |
| `X500Server.java` | Multicast server; study `encodeMulticastAnnouncement()` signing path |
| `EndpointBasedServer.java` | Unicast server; the `writeUnicastResponse()` and `writeClassAnnotationCerts()` methods — understand what is being replaced |
| `EndpointBasedClient.java` | Unicast client; the `readUnicastResponse()` and `readAnnotationCertsGrantPerm()` methods |
| `Constants.java` (sha256withdsa) | Template for new Constants class |
| `SECURITY_MODEL.md` §8.3 | `SpiffeCredentialManager` internals; trust bundle access; `SvidRotationListener` |
| `blog-post-3a-identity-model.md` | `WorkerSubject` ambient identity; `SpiffeCredentialManager.start()` and `LocalPrincipalProvider` registration |
| `JGDMS-STD-006-DER-WireFormat-v0.4-DRAFT.md` §7.7 | Normative ASN.1 for `MulticastAnnouncementRecord`, `MulticastRequestRecord`, `UnicastResponseRecord`, `ProxyDescriptor` |

---

## 8. Known Traps and Gotchas

### 8.1 Empty Subject DN (CRITICAL)

SPIFFE SVIDs MAY have an empty Subject DN. If `cert.getSubjectX500Principal().getName()`
returns `""`, the existing `X500Principal`-based identification and lookup will fail
silently or with a cryptic exception. Add an explicit guard with a clear error
message (§5.2 guard comment). This is a deployment configuration issue but must
be caught early.

### 8.2 `getPrivateCredentials()` and `Subject.current()`

`X500Provider.getPrivateCredentials()` uses `Subject.getSubject(acc)` — the legacy
ACC-based subject retrieval. For the JGDMS SPIFFE model the `WorkerSubject` IS in
the ACC (embedded via `doAsPrivileged` at bootstrap), so this works. Do NOT change
this to `Subject.current()` — that carries the UserSubject, not the WorkerSubject,
and UserSubjects do not have `X500PrivateCredential`.

### 8.3 `MAX_SIGNATURE_LEN` arithmetic in SigningBufferFactory

`BufferInfo` in `X500Provider` reserves `principalName.length + maxSignatureLength`
bytes at the tail of each datagram buffer. For the SPIFFE format with an inline
cert, the reservation must grow to `principalName.length + maxCertLength + 2 +
maxSignatureLength`. P-256 certs are typically 400–600 bytes; use 1024 as a safe
maximum and verify it fits within the datagram MTU with a header space check.

### 8.4 Revocation must be disabled for PKIX validation

SPIFFE SVIDs are short-lived (~1 hour). They have no CRL distribution points and
no OCSP responder. `PKIXParameters.setRevocationEnabled(false)` is mandatory.
Without this the PKIX validator will attempt CRL fetching and fail or hang.

### 8.5 `writeClassAnnotationCerts` / `readAnnotationCertsGrantPerm` MUST NOT be called

These methods appear in `EndpointBasedServer` / `EndpointBasedClient`. The
`writeUnicastResponse` override in `DerUnicastServer` must NOT call either. If any
code path calls `writeClassAnnotationCerts`, the DER format will silently include
legacy cert-grant data that clients won't know how to interpret. Read the base
class implementations carefully to ensure the DER override path is clean.

### 8.6 `SvidRotationListener` is for trust bundle refresh, not SVID rotation

The SVID private key rotates hourly but the trust bundle (SPIRE CA cert) is stable
for months. Register the `SvidRotationListener` to refresh the trust bundle, but
understand that in normal operation the listener fires but the CA cert does not
change — refreshing the bundle is a no-op. Only rare SPIRE CA rotations would
cause the bundle to actually change.

### 8.7 OSGi annotations

Both `Client.java` and `Server.java` for multicast formats carry `@Requirement`
and `@Capability` annotations for OSGi service loader registration. Do not omit
these in the new classes.

### 8.8 `checkAuthenticationPermission` must be called

`X500Client.encodeMulticastRequest()` and `X500Server.encodeMulticastAnnouncement()`
both call `checkAuthenticationPermission(p, "connect"/"accept")` before accepting
a credential for use. This permission check must be preserved in any SPIFFE subclass.
The `AuthenticationPermission` for the SPIFFE workload identity must be granted in
the service's policy file. Confirm the policy grant for
`net.jini.security.AuthenticationPermission` covers `SpiffePrincipal` targets.

---

## 9. Implementation Sequence

| Phase | Work item | Complexity | Depends on |
|---|---|---|---|
| 1a | `sha256withecdsa/Constants.java` | Trivial | Nothing |
| 1b | `sha256withecdsa/Client.java`, `Server.java` | Trivial | 1a |
| 1c | Unit test: ECDSA credential selection from WorkerSubject | Low | 1a, 1b |
| 2a | `SpiffeX500Provider.validateSpiffeCert()` | Medium | SECURITY_MODEL.md §8.3 |
| 2b | `SpiffeX500Provider.refreshTrustBundle()` + `SvidRotationListener` | Medium | 2a |
| 2c | `SpiffeMulticastServer` — cert-carrying announcement encoder | Medium | 2a, 2b |
| 2d | `SpiffeMulticastClient` — cert-reading/validating announcement decoder | Medium | 2a, 2b |
| 2e | Integration test: ECDSA multicast announce/decode round-trip with PKIX | High | 2c, 2d |
| 3a | `DerUnicastServer.writeUnicastResponse()` — DER encoder | High | STD-006 §7.7 |
| 3b | `DerUnicastClient.readUnicastResponse()` — DER decoder | High | 3a |
| 3c | Integration test: unicast discovery full round-trip with DER response | High | 3a, 3b |

---

## 10. Open Questions (resolve before or during implementation)

1. **`SpiffeCredentialManager.getTrustBundle()`** — confirm the exact method name
   that exposes the `X509Certificate[]` trust bundle. If no public method exists,
   add `getTrustBundle()` to `SpiffeCredentialManager`.

2. **`SvidRotationListener`** — confirm it is a `@FunctionalInterface` or identify
   the correct registration method.

3. **SPIFFE Subject DN in SPIRE config** — document the SPIRE registration entry
   format that ensures SVIDs include a non-empty Subject DN.

4. **Datagram MTU constraint** — measure actual cert size for P-256 SVIDs from the
   test SPIRE instance and verify the cert + principal name + signature fits within
   the configured datagram buffer size.

5. **`UnicastResponse` constructor for DER** — confirm whether `UnicastResponse`
   can be constructed with an already-built `ServiceRegistrar` proxy stub, or
   whether the DER client needs to return a different type.

6. **`AuthenticationPermission` for `SpiffePrincipal`** — confirm the policy grant
   syntax for the SPIFFE workload principal in the service policy file.

7. **P-384 support** — `MAX_SIGNATURE_LEN = 72` covers P-256 only. Decide whether
   to also support P-384 (max 104 bytes) in the initial implementation.

---

## 11. Acceptance Criteria

- [ ] `net.jini.discovery.x500.SHA256withECDSA` format is registered via OSGi
  service loader and returned by `Discovery.getProtocol2()`.
- [ ] A Jini Registrar launched with a SPIFFE SVID (P-256 key, non-empty Subject DN)
  successfully signs and sends multicast announcements that a client verifies using
  only the SPIRE trust bundle (no leaf cert in trust store).
- [ ] SVID rotation during an active multicast session causes zero announcement
  verification failures.
- [ ] Unicast discovery returns a DER-encoded `UnicastResponseRecord` that a client
  decodes into a usable `ServiceRegistrar` proxy stub.
- [ ] The `writeClassAnnotationCerts`/`readAnnotationCertsGrantPerm` code paths are
  unreachable in the new format classes.
- [ ] Empty Subject DN produces a clear logged error, not a cryptic exception.
- [ ] `checkAuthenticationPermission` is called on every credential used for signing.

---

## 12. Relationship to Standards Documents

| Standard | Relevance |
|---|---|
| JGDMS-STD-006 v0.4-DRAFT §7.7 | Normative wire types: `MulticastAnnouncementRecord`, `MulticastRequestRecord`, `UnicastResponseRecord`, `ProxyDescriptor`, `JeriEndpointRecord`, `ServiceSpecRecord` |
| JGDMS-STD-006 §8 | Codebase annotations deprecated; the `writeClassAnnotationCerts` path does not appear in DER format |
| JGDMS-STD-003 | WorkerSubject is the ambient process identity; `getPrivateCredentials()` retrieves it via ACC |
| SECURITY_MODEL.md §8.3 | `SpiffeCredentialManager`, trust bundle, `SvidRotationListener` |
| blog-post-3a-identity-model.md | `WorkerSubject` baking into ACC via `doAsPrivileged`; `LocalPrincipalProvider` |
