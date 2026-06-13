# Agent Context: SPIFFE Discovery Format Provider

**Task:** Implement `net.jini.discovery.spiffe.SHA256withECDSA` and
`net.jini.discovery.spiffe.SHA512withECDSA` — two new SPIFFE-native multicast
discovery format providers — and the DER unicast discovery response format for
JGDMS/DirtyChai, enabling SPIFFE-attested embedded devices to participate as
Jini Lookup Services.

**Status:** Design complete (JGDMS-STD-006 v0.5-DRAFT §7.7.7–7.7.8). Implementation
not started.

**Version:** 1.2 — added §13 Discovery Infrastructure Integration covering
`DiscoveryCredentialProvider` SPI, bilateral format registration requirement,
`multicastAnnouncementConstraints`, and `ConstrainableLookupLocator` unicast path.
Source basis: `AbstractLookupDiscovery.java`, `AbstractLookupLocatorDiscovery.java`,
`AbstractLookupDiscoveryManager.java`, `ConstrainableLookupLocator.java`.

**Prepared from:** Extended architectural discussion with Peter Firmstone,
session context `Discussion_with_Claude_about_Subjects_Access_Control_Architecture
_and_DER_JERI_AtomicSerial.txt`.

---

## 1. Background and Motivation

JGDMS uses the Jini discovery protocol for service discovery. The existing multicast
discovery formats (`net.jini.discovery.x500.SHA256withDSA`,
`net.jini.discovery.x500.SHA256withRSA`, `net.jini.discovery.x500.SHA512withECDSA`)
all use a static trust-store model: the verifier looks up the signer's leaf
certificate in a local trust store. SPIFFE SVIDs rotate hourly, making leaf-cert
pinning impractical. SPIFFE deployments also benefit from distinct, clearly-named
format providers that signal their CA-chain trust model.

Two new formats are required:

| Format name | Signature algorithm | Target curve | Use case |
|---|---|---|---|
| `net.jini.discovery.spiffe.SHA256withECDSA` | SHA-256withECDSA | P-256 | Embedded sidecar Registrars; SPIFFE SVIDs with P-256 keys |
| `net.jini.discovery.spiffe.SHA512withECDSA` | SHA-512withECDSA | P-384 | Server-grade JVM Registrars; SPIFFE SVIDs with P-384 keys |

**Reference implementation:** `org.apache.river.discovery.x500.sha512withecdsa`
(`Constants.java`, `Client.java`, `Server.java`) is the structural template for
package layout, OSGi annotations, and the `X500Client`/`X500Server` delegation
pattern. It is **not** extended, modified, or used as a base class. The SPIFFE
formats extend `SpiffeX500Provider` (new), which overrides the trust model entirely.

**Why two formats share the same infrastructure but have different format names:**
The `net.jini.discovery.spiffe.*` namespace signals the CA-chain trust model to
operators configuring `Discovery.getProtocol2()`. Clients and servers negotiate
format by name; a node that only understands trust-store verification must not
accidentally accept a packet whose signature was intended for CA-chain validation
(and vice versa, since the packet structure differs — see §5.1).

**Known issue in the reference implementation** (`x500.SHA512withECDSA`):
- `KEY_ALGORITHM = "ECDSA"` — standard Java `ECPrivateKey.getAlgorithm()` returns
  `"EC"`, not `"ECDSA"`. This may cause `getPrivateCredentials()` to find no
  credentials for SPIFFE SVIDs. **The SPIFFE implementations use `"EC"` and must
  verify this works against DirtyChai's credential model (§8.2).**
- `KEY_ALGORITHM_OID = "1.2.840.10045.4.3.4"` — this is the `ecdsa-with-SHA512`
  signature OID, not the `ecPublicKey` key OID (`1.2.840.10045.2.1`). Both existing
  x500 formats (DSA and ECDSA) follow this signature-OID pattern, so it is
  apparently intentional in `X500Provider`. Do not "fix" it — replicate the pattern.
- `Server.java` Javadoc says "SHA1withDSA" — copy-paste error; not relevant to the
  SPIFFE implementation.
- `MAX_SIGNATURE_LEN = 128` covers P-384 (max 104 bytes) but not P-521 (max 139
  bytes). SPIFFE SVIDs typically use P-256 or P-384. **See §4.1 for correct values.**

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
      X500Provider.java          — X500 credential/trust infrastructure; READ ONLY
      X500Client.java            — multicast client superclass; READ ONLY
      X500Server.java            — multicast server superclass; READ ONLY
      X500Constraints.java       — constraint processing; READ ONLY, no changes needed
    x500/
      sha512withecdsa/           — REFERENCE ONLY: Constants, Client, Server
                                   Structural template; do not modify or extend
```

**New packages to create** (all new files, nothing extended from x500.sha512withecdsa):

```
  spiffe/
    internal/
      SpiffeX500Provider.java    — NEW: extends X500Provider; CA-based trust
      SpiffeMulticastClient.java — NEW: extends X500Client; reads cert from packet
      SpiffeMulticastServer.java — NEW: extends X500Server; writes cert into packet
    sha256withecdsa/
      Constants.java             — NEW: spiffe.SHA256withECDSA format constants
      Client.java                — NEW: MulticastClient wrapper
      Server.java                — NEW: MulticastServer wrapper
    sha512withecdsa/
      Constants.java             — NEW: spiffe.SHA512withECDSA format constants
      Client.java                — NEW: MulticastClient wrapper
      Server.java                — NEW: MulticastServer wrapper
  ssl/
    der/                         — NEW: DER unicast response (Phase 3)
      DerUnicastClient.java
      DerUnicastServer.java
```

---

## 4. Phase 1 — SPIFFE Format Constants, Client and Server Wrappers

Create the two format constant classes and their thin wrapper clients/servers.
These extend `SpiffeMulticastClient` and `SpiffeMulticastServer` (Phase 2), not
the base `X500Client`/`X500Server` directly. Phase 1 and Phase 2 are therefore
co-dependent — implement the Constants in Phase 1, but wire the Client/Server
wrappers once `SpiffeMulticastClient`/`SpiffeMulticastServer` exist in Phase 2.

### 4.1 Constants

**`spiffe/sha256withecdsa/Constants.java`** — for P-256 / embedded devices:

```java
package org.apache.river.discovery.spiffe.sha256withecdsa;

class Constants {
    static final String FORMAT_NAME         = "net.jini.discovery.spiffe.SHA256withECDSA";
    static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    static final int    MAX_SIGNATURE_LEN   = 72;    // P-256 DER max (32+32+8 overhead)
    // [OPEN] Verify: standard Java ECPrivateKey.getAlgorithm() returns "EC".
    // Confirm this matches DirtyChai SPIFFE credential model before committing.
    static final String KEY_ALGORITHM       = "EC";
    // ecdsa-with-SHA256 OID per RFC 5758 (consistent with x500 format OID pattern)
    static final String KEY_ALGORITHM_OID   = "1.2.840.10045.4.3.2";
    private Constants() {}
}
```

**`spiffe/sha512withecdsa/Constants.java`** — for P-384 / server nodes:

```java
package org.apache.river.discovery.spiffe.sha512withecdsa;

class Constants {
    static final String FORMAT_NAME         = "net.jini.discovery.spiffe.SHA512withECDSA";
    static final String SIGNATURE_ALGORITHM = "SHA512withECDSA";
    static final int    MAX_SIGNATURE_LEN   = 104;   // P-384 DER max; use 140 for P-521
    static final String KEY_ALGORITHM       = "EC";  // [OPEN] verify as above
    // ecdsa-with-SHA512 OID per RFC 5758 (same as existing x500.SHA512withECDSA)
    static final String KEY_ALGORITHM_OID   = "1.2.840.10045.4.3.4";
    private Constants() {}
}
```

**Why `MAX_SIGNATURE_LEN = 72` for P-256:** A P-256 ECDSA DER signature encodes
two 32-byte integers (r, s). With DER INTEGER tags, lengths, and SEQUENCE overhead,
the maximum is `2 + 2 + 33 + 2 + 33 = 72` bytes (33 = 32 bytes + 1 leading zero
if high bit set). This is tighter than the 128 in the reference implementation and
matters for the MTU budget calculation (§5.1).

### 4.2 Client and Server wrappers

Each is a thin wrapper delegating to `SpiffeMulticastClient`/`SpiffeMulticastServer`
with the appropriate `Constants`. Follow the exact structure of
`x500.sha512withecdsa.Client` and `x500.sha512withecdsa.Server`:

- Same `@Requirement`/`@Capability` OSGi annotations (mandatory for service loader)
- Same `MulticastClient`/`MulticastServer` delegation pattern
- Private static inner `ClientImpl extends SpiffeMulticastClient` (not `X500Client`)
- Private static inner `ServerImpl extends SpiffeMulticastServer` (not `X500Server`)
- Javadoc referencing the correct format name (do NOT copy the SHA1withDSA typo
  from the reference `Server.java`)

Both SHA-256 and SHA-512 variants are identical in structure; only `Constants`
references differ.

---

## 5. Phase 2 — SpiffeX500Provider and SPIFFE Multicast Implementation

This is the architecturally significant phase. `SpiffeX500Provider` replaces the
static trust-store cert lookup with PKIX CA-chain validation against the SPIRE
trust bundle. `SpiffeMulticastServer` and `SpiffeMulticastClient` add the leaf
certificate to the packet so receivers can validate the chain.

Both `spiffe.SHA256withECDSA` and `spiffe.SHA512withECDSA` share the same
`SpiffeX500Provider`, `SpiffeMulticastClient`, and `SpiffeMulticastServer`
infrastructure — they differ only in which `Constants` they pass to the constructor.

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

### 8.2 `KEY_ALGORITHM = "EC"` vs `"ECDSA"` — verify early

The existing reference implementation `x500.sha512withecdsa` uses
`KEY_ALGORITHM = "ECDSA"`. Standard Java `ECPrivateKey.getAlgorithm()` returns
`"EC"`. The SPIFFE implementations use `"EC"` on the assumption that DirtyChai
SPIFFE credentials follow the standard. **Verify this is correct by checking
`SpiffeCredentialManager.getSubject().getPrivateCredentials(X500PrivateCredential.class)`
and calling `getAlgorithm()` on the returned private key.** If the result is
`"ECDSA"`, update both `Constants.java` files before implementing any
credential-dependent code. This check takes minutes and prevents a hard-to-diagnose
failure where `getPrivateCredentials()` silently returns nothing.

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
| 1a | `spiffe/sha256withecdsa/Constants.java` | Trivial | Nothing |
| 1b | `spiffe/sha512withecdsa/Constants.java` | Trivial | Nothing |
| 2a | `SpiffeX500Provider.validateSpiffeCert()` + `refreshTrustBundle()` | Medium | SECURITY_MODEL.md §8.3 |
| 2b | `SpiffeX500Provider` `SvidRotationListener` registration | Low | 2a |
| 2c | `SpiffeMulticastServer` — cert-carrying announcement/request encoder | Medium | 2a |
| 2d | `SpiffeMulticastClient` — cert-reading/validating announcement decoder | Medium | 2a |
| 2e | `spiffe/sha256withecdsa/Client.java`, `Server.java` wrappers | Trivial | 1a, 2c, 2d |
| 2f | `spiffe/sha512withecdsa/Client.java`, `Server.java` wrappers | Trivial | 1b, 2c, 2d |
| 2g | Verify `KEY_ALGORITHM = "EC"` works with DirtyChai SPIFFE credentials | Low | 2e or 2f |
| 2h | Integration test: both formats announce/decode round-trip with PKIX | High | 2e, 2f |
| 3a | `DerUnicastServer.writeUnicastResponse()` — DER encoder | High | STD-006 §7.7.8 |
| 3b | `DerUnicastClient.readUnicastResponse()` — DER decoder | High | 3a |
| 3c | Integration test: unicast discovery full round-trip with DER response | High | 3a, 3b |

Note: the `KEY_ALGORITHM` verification (2g) is low-effort but should be done early
— if "EC" doesn't match DirtyChai's SPIFFE credential key algorithm string, Constants
needs updating before any credential-dependent code can be tested.

---

## 10. Open Questions (resolve before or during implementation)

1. **`SpiffeCredentialManager.getTrustBundle()`** — confirm the exact method name
   that exposes the `X509Certificate[]` trust bundle. If no public method exists,
   add `getTrustBundle()` to `SpiffeCredentialManager`.

2. **`SvidRotationListener`** — confirm it is a `@FunctionalInterface` or identify
   the correct registration method.

3. **`KEY_ALGORITHM = "EC"` vs `"ECDSA"`** — verify via `getAlgorithm()` on an
   actual DirtyChai SPIFFE credential. The existing `x500.SHA512withECDSA` uses
   `"ECDSA"` but standard Java `ECPrivateKey.getAlgorithm()` returns `"EC"`.
   **Check before implementing credential selection code.** Update both
   `Constants.java` files if `"ECDSA"` turns out to be correct.

4. **`KEY_ALGORITHM_OID` pattern** — both existing x500 formats use signature
   algorithm OIDs (not key algorithm OIDs). The SPIFFE implementations follow the
   same pattern (§4.1). Confirm `X500Provider.getCertificate0()` uses this OID
   compatibly with the `SpiffeX500Provider` CA-chain override, or confirm it is
   unused in that path.

5. **SPIFFE Subject DN in SPIRE config** — document the SPIRE registration entry
   format that ensures SVIDs include a non-empty Subject DN.

6. **Datagram MTU constraint** — measure actual cert size for P-256 and P-384 SVIDs
   from the test SPIRE instance. Verify cert + principal name + signature fits within
   the configured datagram buffer. Values needed: P-256 cert bytes, P-384 cert bytes.

7. **`UnicastResponse` constructor for DER** — confirm whether `UnicastResponse`
   can be constructed from an already-built `ServiceRegistrar` proxy stub, or
   whether a subtype is needed.

8. **`JeriEndpointRecord` → JERI stub on the client** — confirm the factory path
   for constructing `SslEndpoint` + `AtomicILFactory` from `host:port:spiffeId`.

9. **`AuthenticationPermission` for `SpiffePrincipal`** — confirm the policy grant
   syntax for the SPIFFE workload principal in the service policy file.

---

## 11. Acceptance Criteria

- [ ] Both `net.jini.discovery.spiffe.SHA256withECDSA` and
  `net.jini.discovery.spiffe.SHA512withECDSA` are registered via OSGi service
  loader and returned by `Discovery.getProtocol2()`.
- [ ] A Jini Registrar with a SPIFFE P-256 SVID successfully signs and sends
  multicast announcements using `spiffe.SHA256withECDSA` that a client verifies
  using only the SPIRE trust bundle (no leaf cert in trust store).
- [ ] A Jini Registrar with a SPIFFE P-384 SVID successfully signs and sends
  multicast announcements using `spiffe.SHA512withECDSA` that a client verifies
  using only the SPIRE trust bundle.
- [ ] SVID rotation during an active multicast session causes zero announcement
  verification failures for both formats.
- [ ] Unicast discovery returns a DER-encoded `UnicastResponseRecord` that a client
  decodes into a usable `ServiceRegistrar` proxy stub.
- [ ] The `writeClassAnnotationCerts`/`readAnnotationCertsGrantPerm` code paths are
  unreachable from both new format classes.
- [ ] Empty Subject DN produces a clear logged error, not a cryptic exception,
  for both formats.
- [ ] `checkAuthenticationPermission` is called on every credential used for signing.
- [ ] The existing `net.jini.discovery.x500.SHA512withECDSA` format is unmodified
  and continues to pass its existing tests.

---

## 13. Discovery Infrastructure Integration

This section covers how the SPIFFE format providers connect to the broader discovery
infrastructure in `AbstractLookupDiscovery`, `AbstractLookupLocatorDiscovery`,
`AbstractLookupDiscoveryManager`, and `ConstrainableLookupLocator`. The format
providers themselves do not call into this infrastructure, but understanding how
they are invoked is essential for correct deployment and testing.

### 13.1 Format Loading — Bilateral Requirement

`AbstractLookupDiscovery` loads format providers once at construction:

```java
private final Discovery protocol2 = Discovery.getProtocol2(null);  // line 209
```

`Discovery.getProtocol2(null)` uses the Java service loader to find all registered
`DiscoveryFormatProvider` implementations. Format routing is by format name prefix
embedded in each packet. A packet announcing `net.jini.discovery.spiffe.SHA512withECDSA`
will be **silently discarded** on any `AbstractLookupDiscovery` instance that does
not have the `spiffe.sha512withecdsa.Client` provider registered.

**This is a bilateral deployment requirement:** both the announcing Registrar and
all receiving clients must have the relevant SPIFFE format providers registered.
The OSGi `@Capability`/`@Requirement` annotations on the `Client` and `Server`
classes (see §4.2 and the reference implementation) are what trigger service loader
registration at deployment time. Verify these are present and correct — missing
annotations are a silent failure mode that produces no error, only ignored packets.

### 13.2 DiscoveryCredentialProvider SPI — Client Credential Injection

`AbstractLookupDiscovery` has a `DiscoveryCredentialProvider` configured via
`Configuration`:

```java
discoveryCredentialProvider = (DiscoveryCredentialProvider) config.getEntry(
    ..., "discoveryCredentialProvider",
    DiscoveryCredentialProvider.class,
    NoOpDiscoveryCredentialProvider.INSTANCE);
```

When sending multicast requests, `getDiscoverySubject()` calls
`discoveryCredentialProvider.getSubject()`, and the result is passed to
`runWithDiscoverySubject(discoverySubject, action)` which runs the encoding action.

**Currently `runWithDiscoverySubject` is a no-op** — it calls `action.run()` directly
without wrapping in `Subject.callAs(subject, action)`. The SPIFFE format providers
work regardless because the WorkerSubject is already in the AccessControlContext from
the service bootstrap `doAsPrivileged`. `getPrivateCredentials()` retrieves it from
the ACC.

If a client needs to sign multicast requests with a Subject *other* than the process
WorkerSubject (e.g. in a user-driven client application), a
`SpiffeDiscoveryCredentialProvider` implementing `DiscoveryCredentialProvider` and
returning `SpiffeCredentialManager.getSubject()` would be the correct approach —
but only if `runWithDiscoverySubject` is also updated to use `Subject.callAs`.
**This is out of scope for the current implementation; document it as a future
enhancement rather than a blocking item.**

### 13.3 Constraint Enforcement — multicastAnnouncementConstraints

The `multicastAnnouncementConstraints` field in `AbstractLookupDiscovery` controls
what constraints are applied when decoding incoming multicast announcements. After
decoding, `checkAnnouncementConstraints(ann)` calls `ann.checkConstraints()` which
is implemented by the format provider's inner announcement class (see
`X500Client.X500MulticastAnnouncement.checkConstraints()` in `X500Client.java`).

The `SpiffeMulticastClient` implementation of `checkConstraints()` (§5.3) performs:
1. Principal constraint checking (`cons.checkServerPrincipal(p)`)
2. Leaf certificate chain validation against the SPIRE trust bundle
3. Signature verification

For a deployment that requires SPIFFE authentication on all multicast announcements,
the client configuration sets `multicastAnnouncementConstraints` to include
`ServerMinPrincipal` with a `SpiffePrincipal`. Announcements from non-SPIFFE
Registrars will then fail constraint checking and be discarded. This is the operator
lever for enforcing SPIFFE-only discovery zones.

### 13.4 Unicast Discovery via ConstrainableLookupLocator

For directed unicast discovery to a known embedded sidecar Registrar (rather than
waiting for multicast), a client constructs a `ConstrainableLookupLocator` with
constraints targeting the device:

```java
new ConstrainableLookupLocator(
    "device-hostname", 4160,
    new BasicMethodConstraints(
        new InvocationConstraints(
            new ServerMinPrincipal(
                new SpiffePrincipal("spiffe://jgdms.example.org/device/sensor-01")),
            null)));
```

`AbstractLookupLocatorDiscovery` extracts these constraints (lines 236–240) and
passes them to `getRegistrar(ic, context)` which calls the unicast discovery path.
The `DerUnicastClient` (Phase 3) would then connect to the device, send the
handshake, and decode the DER `UnicastResponseRecord`.

The `ConstrainableLookupLocator` is the correct way to target a specific embedded
device by SPIFFE ID. Ensure the `DerUnicastClient` correctly handles the
`ServerMinPrincipal(SpiffePrincipal)` constraint in its `getEndpoint()` method —
the SPIFFE ID in the constraint must be matched against the SVID presented during
TLS. This is handled by the existing `SslEndpoint` SPIFFE-aware TLS layer, not by
the unicast response DER decoder.

### 13.5 Announcement Timer and Format Continuity

`AbstractLookupDiscovery` tracks the last announcement time per Registrar
(`AnnouncementTimerThread`). After 3× the announcement interval without a packet,
the Registrar is considered unreachable and discarded. For an embedded sidecar
Registrar, ensure the announcement interval is configured appropriately for the
network and that the device reliably sends on schedule. SVID rotation (§2.2) does
not interrupt announcements since signing always uses the current credential.

### 13.6 Key Files Added to §7 (Read Before Starting)

| File | Why |
|---|---|
| `AbstractLookupDiscovery.java` | Format loading (line 209), credential provider (lines 282/1419), `Requestor` thread (line 710), `decodeMulticastAnnouncement` (line 2758), `checkAnnouncementConstraints` (line 2797), `getDiscovery()` routing (line 2957) |
| `AbstractLookupLocatorDiscovery.java` | Unicast constraint extraction (lines 236–240), `getRegistrar` invocation path |
| `ConstrainableLookupLocator.java` | How clients target specific Registrars by address + SPIFFE principal constraint; `@AtomicSerial` implementation shows correct pattern |

---

## 12. Relationship to Standards Documents

| Standard | Relevance |
|---|---|
| JGDMS-STD-006 v0.5-DRAFT §7.7.7 | Normative wire types: `MulticastAnnouncementRecord`, `MulticastRequestRecord`; format variants table; SPIFFE Subject DN requirement |
| JGDMS-STD-006 v0.5-DRAFT §7.7.8 | `UnicastResponseRecord`; deprecation of `writeClassAnnotationCerts`; extension points |
| JGDMS-STD-006 §8 | Codebase annotations deprecated; the `writeClassAnnotationCerts` path does not appear in DER format |
| JGDMS-STD-003 | WorkerSubject is the ambient process identity; `getPrivateCredentials()` retrieves it via ACC |
| SECURITY_MODEL.md §8.3 | `SpiffeCredentialManager`, trust bundle, `SvidRotationListener` |
| blog-post-3a-identity-model.md | `WorkerSubject` baking into ACC via `doAsPrivileged`; `LocalPrincipalProvider` |
