# Design sketch: reading the authenticated server principal of a bootstrap proxy

**Status:** proposal for board review, not implemented. 2026-10-20, written with AI assistance
(Claude). Context: `DESIGN-dynamic-grant-solutions-2026-10-18.md` (decisions section): the server
identity for grants, the isolation pool key and the `BootstrapPermission` subject is taken from the
bootstrap proxy's **own** authenticated connection, through a handler whose class is checked, then
pinned as a single principal. Today `PreferredProxyCodebaseProvider.resolve` instead reads the
`ServerSubject` from the unmarshalling stream's context, which belongs to whoever delivered the
proxy (`PreferredProxyServerIdentityTest`, `PreferredProxyRelayStreamTest`).

## What exists
- `SslConnection.populateContext` adds a `ServerSubject` (X500 + SPIFFE principals, guarded by
  `ContextPermission("net.jini.io.context.ServerSubject.getServerSubject")`). `AtomicInvocationHandler.
  createMarshalInputStream` calls `request.populateContext(context)` for the response stream only.
- `KerberosEndpoint`'s connection `populateContext` is a no-op: no `ServerSubject`, so Kerberos
  clients get no identity today. Other transports (TCP, HTTP, UDS) add none either.
- `BasicInvocationHandler.invokeRemoteMethod` gets its request with
  `oe.newCall(constraints)` → `iter.next()` → `OutboundRequest`, and can `request.abort()`.

## Proposal
1. **A probe on the handler.** New `public final Set<Principal> authenticateServer(Method m) throws
   IOException` on `BasicInvocationHandler` (inherited by `AtomicInvocationHandler`):
   - constraints = `getClientConstraints().getConstraints(m)` plus `ServerAuthentication.YES`;
   - `iter = getObjectEndpoint().newCall(constraints)`; `request = iter.next()` (this establishes or
     reuses the connection and performs the handshake); `request.populateContext(ctx)`; find the
     `ServerSubject` in `ctx`; copy its principals; `request.abort()` without sending a method;
   - throws `UnsupportedConstraintException`/`IOException` if the transport cannot authenticate the
     server or provides no `ServerSubject`. Never returns an empty set as success.
   The method is `final` so a subclass cannot override it, but the class is public and non-final:
   mobile code can subclass `BasicInvocationHandler` and the provider must not trust the result
   unless the handler's **class** is exactly `BasicInvocationHandler` or `AtomicInvocationHandler`
   (`getClass() ==`, and the loader of that class is the platform's).
2. **Kerberos.** `KerberosEndpoint`'s connection adds a `ServerSubject` holding the authenticated
   `KerberosPrincipal` after mutual authentication, mirroring `SslConnection`.
3. **Provider use (not part of this step's API):** `resolve` checks the handler class, calls
   `authenticateServer` with the `getClassAnnotation` `Method` under the provider's privileges,
   picks the single pin principal by the transport's type (a `SpiffePrincipal` for SSL, a
   `KerberosPrincipal` for Kerberos), applies `ServerMinPrincipal(pin)` with
   `ServerAuthentication.YES` to the bootstrap proxy via `setConstraints` before any further call,
   and uses that principal for the loader record, the isolation pool key and the embedded
   principals. No identity (no `ServerSubject`, unauthenticated transport) means no verified grant.

## Alternatives considered
- **Record the `ServerSubject` of the last real call in the handler** and expose a getter: avoids a
  probe but is racy across threads/calls and ties identity to whichever call ran last.
- **Expose the subject from the unmarshalled return value's stream context:** that is the current
  behaviour and the thing being replaced.
- **A new `Endpoint` method instead of a handler method:** touches every transport.

## Questions for the board
Security: can a hostile handler subclass, endpoint, or proxy make the accessor lie, or make the
provider trust a lie; is the permission model right (who may call it; information disclosure);
downgrade or constraint-stripping; is the identity the probe sees the identity later calls use
(connection reuse, rebinding, TOCTOU, the pin); anonymous/non-TLS. API/compatibility/transports:
API shape and binary/source compatibility; what each transport can supply; `RemoteMethodControl`
and serialization interplay; the Kerberos change. Correctness/implementation: does the probe
sequence really work without sending a method (state machine, handshake timing, resource leaks,
abort semantics, threading, timeouts); a concrete test plan.
