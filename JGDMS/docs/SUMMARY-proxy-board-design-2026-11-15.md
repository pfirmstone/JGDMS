# Summary: the proxy-board design (reading aid)

**AS-OF 2026-11-15.** Written by Claude Sonnet 5.5 (session 757702ae) at the operator's request. **Revised the same day by Claude Fable 5.2** (session 018wGHqgexvwKfVXuCVJMAzR) at the operator's request, after a review of the design with the operator. **A reading aid, not an authority.** The authority for every ruled statement is the claim register (`E:\Claude\claim_register\rulings\JGDMS\INDEX.md`, entries RL-JGDMS-0037 to RL-JGDMS-0050); this file cites IDs and does not restate their status, so read `show <ID>` for the operator's own words and any later change.

**Tags used below.**

- **RL-JGDMS-nnnn**: a ruling in the register.
- **PROPOSED (Pn)**: the assistant's suggestion in memo section 16.1, not a ruling. The memo is `DESIGN-class-free-lookup-and-registrar-split-2026-10-31.md`.
- **STATED**: something the operator said in the review session of 2026-11-15. Each is restated here in the operator's words or close to them. They were entered in the claim register later the same day (RL-JGDMS-0052 to RL-JGDMS-0072 and RL-DIRTYCHAI-0005 to RL-DIRTYCHAI-0014), and the register governs where the two differ.
- **PROPOSED (Fn)**: a suggestion made in that review, not a ruling. F1 to F19 are listed in section 9, because they are written down nowhere else.
- **FOUND**: something read in code or observed in a test run on 2026-11-15, with how it was established.

**Purpose of the design** (operator, earlier in this project): it lets parties, such as companies that know each other, connect with limited trust.

**What was read for the revision.** By the reviser: the fourteen rulings, parts of DirtyChai's `Thread`, `ThreadBuilders`, `ForkJoinPool`, `ForkJoinTask`, `VirtualThread` and `jvm.cpp`, and `SECURITY_MODEL.md` section 11. By agents the reviser dispatched, whose reports were not re-verified line by line: memo sections 15 and 16, `PROXY_HOST_BOARD_REQUIREMENTS.md`, `DIGEST_GRANT_PLAN.md`, `JGDMS-STD-002` v1.3, `SOW-BAE-Timing-Sidechannel-Denial.md`, and the Atomic DER decoder with `PreferredProxyCodebaseProvider` (three places of that trace were checked by the reviser). **Still not read:** the bytecode analysis engine's code.

## 1. The picture

```
        PRIVATE NETWORK                          PUBLIC NETWORK
 +-----------------------+
 |  TRUSTED HOST         |   host calls the proxy              +-----------------+
 |  (trusted client JVM) |-----------------------------------> | PROXY BOARD     |
 |                       |   (the board never calls the host)  | runs the proxy  |
 |  holds the marshalled |                                     | (downloaded     |
 |  proxy from the       |                                     |  code)          |
 |  SafeServiceRegistrar |                                     +--------+--------+
 |                       |                                              | board calls out
 |                       |   host pulls events (method call)            v
 |                       |-------------------------+            +-----------------+
 |                       |                         |            | SERVICE SERVER  |
 +-----------+-----------+                         |            | (origin; e.g.   |
             | commands                            v            |  Outrigger)     |
             v                          +--------------------+  +--------+--------+
 +-----------------------+              | EVENT MAILBOX NODE |<----------+ events delivered
 | NODE CONTROLLER       |  power,      | own SPIFFE role,   |   (the one inbound path)
 | (commanded by host;   |  links,      | no downloaded code |
 |  independent of nodes)|  images      +--------------------+
 +-----------------------+
```

The board has two links, one on each network, and the host treats every node as hostile (`PROXY_HOST_BOARD_REQUIREMENTS.md` H1, H2). The first version of this file said the board "must not forward between them". The board document does not prevent that: H2 and H3 record that a compromised node can bridge its two links, and the switch limits what it can then reach. It is an accepted risk, not an enforced property (F8).

The host contacts only the lookup service, never the service's server (same document, H3).

The controller is independent of every node and can power all nodes off if the host's heartbeat is lost (H4). The board document calls it independent of the host as well, but the host commands it and its heartbeat governs it, so the host is in the controller's trust path (F8).

Not in the diagram: the verdict registry. **STATED:** the verdict registry needs to be on the private network, and should be secured as though that network were public. The board document mentions neither the registry nor an event mailbox node (P5).

## 2. Who starts each connection

| From | To | Why | Source |
|---|---|---|---|
| Host | Proxy board | The host invokes methods on the proxy. The board never calls the host. No call-back listeners. | RL-JGDMS-0037 |
| Proxy board | Service server | Outbound over the public network; the board's proxy code talks to its origin. | RL-JGDMS-0037; board document H3 |
| Host | Event mailbox node | The host pulls events by method call. The host exports no listener. | RL-JGDMS-0038 |
| Service server | Event mailbox node | Outrigger delivers events to the mailbox. This is the only inbound path to a node. | RL-JGDMS-0038, RL-JGDMS-0039. The board document has no inbound case yet (P5). |
| Host | Controller | The host commands the controller over a dedicated authenticated link. | board document H4 |
| Host | Verdict registry | The host fetches the signed verdict for a digest and watches for a change. No node has a route to the registry. | STATED (section 4); PROPOSED F10, F11 |

**STATED:** the link between host and node is Atomic DER over TLS.

## 3. Events and leases

- Leases are renewed by the host through the board to the service server (RL-JGDMS-0038).
- Events cannot use a listener the host exports, because that would reverse the call direction (RL-JGDMS-0037). An event mailbox relays them, on a board separate from every board running untrusted proxy code (RL-JGDMS-0038). The host pulls them.
- The event mailbox node has its own unique SPIFFE role and policy and runs no untrusted or downloaded code (RL-JGDMS-0039). One such node serves a host and may process events from several partially trusted services (RL-JGDMS-0040).
- The host can move the mail service to another proxy board and must re-register the event listeners when it does (RL-JGDMS-0041). It can also power the node down and back on, or swap it with a busy node (RL-JGDMS-0042).
- The host may use two mailbox nodes, split the services between them, and on an attack move the non-attacking services to the node not under attack (RL-JGDMS-0043).
- **PROPOSED:** events arrive through an untrusted relay, so they are unverified claims unless the origin signs them (P1); the host detects sequence gaps after a move and re-reads the space (P2); the mailbox node is stateless and boots from a signed image (P3); per-origin identity, opaque payloads and quotas on a shared node (P4).
- **PROPOSED (F1):** the same holds for everything else that comes back through a board. A return value, an exception or a reported lease renewal is the node's claim, not the service's, unless the origin signed it. Atomic DER is canonical, so the origin can sign the encoded bytes and the host can check them without trusting the node's re-encoding.

## 4. Loading a proxy

**Ruled.**

- The host holds the marshalled proxy from the `SafeServiceRegistrar` (RL-JGDMS-0047).
- Before a proxy is loaded, its digest, its code and its serial form are stored (RL-JGDMS-0046).
- The code is statically analysed by the bytecode analysis engine before it can run on the proxy board, and a codebase gets `LoadClassPermission` only if it has the digest that SCAP processed, wherever it was downloaded from (RL-JGDMS-0048).

**STATED: the intent and the policy model.**

- "That's the intent of the proxy board, no dynamic code on the host."
- Policy is governed at three levels: SPIFFE policy, the djinn remote administration policy and dynamic policy.
- A grant requires the presence of user, process and code identity: the SPIFFE process principal, a JWT user principal and trusted code digests. `GrantPermission` checks that the caller has permission to grant the permissions the proxy requests.
- `LoadClassPermission` is granted dynamically to the jar digest and the SPIFFE process principal. `SecureClassLoader` stamps the digest into the `ProtectionDomain`.
- The digest grant is dynamic, following proxy authentication and SCAP bytecode verification.
- The identities are checked only when the grant is made. "Once the permissions have been granted, they cannot be undone without shutting down the proxy board."
- JGDMS also has ephemeral permissions. Some permission guards release the guarded object instead of deciding on every invocation, `SocketPermission` for example.

**STATED: the verdict.**

- The verdict registry holds the verdict, and `PreferredProxyCodebaseProvider` contacts the verdict registry. **FOUND** (from that class's declarations and comments, not a full trace): it verifies the registry's inline signature on each verdict against a pinned public key, so a verdict can be relayed without trusting the relay.
- Of three ways for a node to get a verdict, the operator chose the first: the host fetches the signed verdict and delivers it to the node. "The host node could monitor verdicts and shut down the proxy board when the verdict changes."
- A local cache at the host, so that a registry outage does not take every board down and bring them all back together: "multiple services going down at the same time and coming back up at the same time results in heavy network loads, this situation should be avoided".

**STATED and PROPOSED (F13): the two-step load.** The operator said "the two step exchange looks good" and that the JWT user principal arrives with the host's step 2 call.

1. The host hands the node the marshalled proxy. The node, running only its trusted image, fetches the code, hashes it and returns the digest of each jar, without defining any class.
2. The host obtains the verdict for each digest and, only if every verdict is safe, calls the node again with the signed verdicts and the JWT user principal. Code on the node holding `GrantPermission` then makes the grant, and the code loads.

Conditions proposed with it, not yet answered: the node defines classes from the same bytes it hashed, with no second fetch (this is P9's buffer rule); one jar without a safe verdict refuses the whole proxy; step 1 has a size and a time limit; the token is single-use, short-lived, names that node as its audience, and cannot be read by the proxy once loaded.

**What the analysis claims.**

- From `JGDMS-STD-002` v1.3: untrusted analysis engines with no outbound network and no path to the verdict registry, a verdict keyed by content hash that needs a quorum of engines to say safe and condemns on one negative, and an engine crash that counts as an automatic dangerous verdict.
- Its listed checks are `<clinit>` blocking, circular `<clinit>` dependency and `@AtomicSerial` compliance. The standard treats a blocking `<clinit>` as a denial of service and gives it a dangerous verdict, so the analysis does make a claim about that one form of denial of service. Whether it also scans for dangerous APIs was not established (P10).
- **STATED:** "Static analysis cannot detect timing attacks, it can only identify a limited range of attacks."
- **PROPOSED (F7):** the documents should say what the analysis and the digest grant establish, which is that the code that loads is the code that was analysed and stored, and what they do not.
- The check runs in the board's own JVM. It stops unapproved code loading and does not protect against a board already compromised, which is what the isolation and power-cycle below are for.

**PROPOSED:** the loader hashes the buffer it defines classes from, over the same normalised form SCAP uses, and the grant mapping reaches the board as a signed update from the host (P9). The grant ceiling, the lifetime of a grant, socket authority and the permissions a node's role should never cover are F14 to F17.

## 5. The node's JVM

The proxy runs on DirtyChai. What follows is recorded here because the design leans on it; the authority is DirtyChai's `SECURITY_MODEL.md` (version 2.5, sections 11.1, 11.7 and 13), which was updated the same day.

- **STATED:** DirtyChai adds guards that OpenJDK does not have, among them thread creation guards; the proxy board's JVM can have its timer resolution reduced; `doPrivileged` calls are limited and carry no ambient authority; DirtyChai starts its daemon threads early during JVM bootstrap, and JGDMS has not had the same treatment yet.
- **STATED:** tests do not use `-Djava.security.manager=allow`. A policy is established with `-Djava.security.manager=polpAudit`, audited, widened to make it generic, and untrusted code and principals are removed from it.
- **FOUND** (a test, run by the operator): `Thread.startVirtualThread` made no permission check, so code without `createVirtualThread` could start a second thread. The operator added the check. `VirtualThreadCreationPermissionTest` passed under `-Djava.security.manager=default` at 16:53.
- **FOUND** (a test, run by the operator): once trusted code had used the common pool, code without `createInnocuousThread` could run a task on one of its workers by seven routes. The operator added checks that are active when clock coarsening is set. `CommonPoolSubmissionPermissionTest` passed under `-Djava.security.manager=default` at 16:16; its last revision has not been run.
- **FOUND, open** (read by the reviser, not tested): the operator then limited all of the new checks to the common pool (the code as read at 17:54), so by reading they no longer sit on the path that schedules virtual threads. Virtual threads in a JVM with coarsening on have still not been run under a test. The virtual-thread carrier factory creates threads with no check, and the finalizer thread is removed only by a launcher flag the VM does not enforce.
- **PROPOSED (F18):** the node role's ceiling covers no thread creation of any kind, and the node's JVM runs with reduced timer resolution.

## 6. When a proxy is suspected of attacking

1. The suspected attacker's board is isolated from the public network, and its proxy is persisted and analysed using DER (RL-JGDMS-0044).
2. The power-down follows the isolation (RL-JGDMS-0049).
3. The attacker's service is removed from the lookup service (RL-JGDMS-0045).
4. The proxy's state at isolation can differ from its stored initial state, because a DER TLS connection to its service has been established (RL-JGDMS-0050). A difference is expected and is not by itself evidence of tampering (memo section 16.2 strikes the earlier remark that it was).
5. **Not ruled:** whether this response is automatic or confirmed by a person, and who is able to remove a lookup entry.
6. **STATED:**
   - "We would be looking to detect timing attacks, or DOS." Thresholds are open; the operator said that is not his speciality.
   - On a changed verdict the host shuts the board down, and "yes we could isolate first".
   - Remove an origin from an allow list instead of adding it to a deny list: "deny lists tend to be ever expanding, it's easier to manage an allow list".
7. **PROPOSED:** take the capture outside the node (egress switch, controller) before any power-off; revoke the origin's identity and remove its address from the switch allowlist; quarantine before permanent removal; a reinstatement path; a limit on how long an isolated node keeps running; evidence stored content-addressed and write-once, never executed on the store (P7, P8).
8. **PROPOSED (F2, F3, F4, F12):** what detects an attack; which steps are automatic; the allow list as an ordinary grant in the remote administration policy; and what happens when a verdict changes but inspection finds no attack. The review also arrived at three graded responses, each stronger and less dependent on the node's own software: let the leases lapse, cut the egress link, power down. Persisting the proxy means running the suspect's own code, so that output is the suspect's account and not evidence by itself.

Memo section 15 (agent reading) already names detection by something the board's software cannot forge, performance-counter signals read from a secure-world monitor, and records the detection signal and its latency as unspecified (O34).

## 7. What the host reads back from a node

**FOUND** (an agent's trace of the JGDMS source; the reviser checked three places in it):

- An Atomic DER stream carries no codebase location, classes are resolved with a null codebase, and a type the receiver does not hold fails with `ClassNotFoundException` or an `IOException` wrapping one. Classes are loaded without running static initialisers.
- One exception. `DerProxySerializer` is a `Resolve` class, and `ObjectCodec.admissibleConstructClass` admits any `Resolve` class into any slot. Its `readResolve()` builds a bootstrap proxy from the handler decoded from the stream and passes it to `ProxyCodebaseSpi`. `PreferredProxyCodebaseProvider.resolve` then calls `getClassAnnotation()` on that endpoint and can build a codebase loader from the reply.
- With `net.jini.loader.pref.smartProxyIsolation.enabled=true` the provider fails closed, but the check comes after the remote call.
- This was not shown to be exploitable. `DeSerializationPermission`, integrity and signer checks and the digest-gated `LoadClassPermission` stand in the way under a security manager, and the host reaches only private addresses.
- No test asserts that an unknown record class fails without a download, or that a proxy carrier from an untrusted stream triggers no remote call.
- Smaller points: the thread-context loader is a fallback in `PreferredClassProvider` although comments say it is never used; `ProxyWireSupport.resolveTolerant` drops interface names it cannot resolve and builds the proxy from the rest.

So "no dynamic code on the host" currently rests on how the host is configured, not on the decoder. **PROPOSED (F1):** the host refuses a proxy carrier where no proxy is expected and makes no call to an endpoint named in a node's reply. Three ways to enforce it were put to the operator and not chosen between: leave the codebase provider off the host, move the isolation check ahead of the remote call, or give the decoder a local-only mode.

## 8. The carrier board

**STATED:** the proxy boards "are intended to be isolated from each other and the host with no writable storage".

**Correction to the first version.** The first version listed read-only boot and no writable storage for the mailbox node only. By the agent's reading, the board document already requires, for every node, secure boot with fused keys (N4), a RAM root with no writable storage (N6), a re-image between principals (B2), rollback protection (B5) and an egress allowlist applied by the controller (S2). Its acceptance tests cover power, watchdog, switch and boot, and nothing about the return path, the lookup service or the digest grant.

The operator designs the carrier board, so some of the above can be enforced in hardware (advice only, for the operator to write into the board document; none of it is ruled, and it has not been reconciled item by item with what the board document already says):

- per-node power control with rail discharge; the controller kills either link of a node independently of the node;
- a mirror or tap port per node, so the evidence is taken outside the node;
- no path between nodes through shared buses or debug ports; read-only boot and no writable storage on the mailbox node;
- a hardware ACL on the mailbox node's single inbound port, moved with the role by the controller;
- per-node rate limits on the egress uplink, or a dedicated uplink per node;
- the controller and the switch chip are then part of the trusted base, with signed firmware and rollback protection.

**PROPOSED (F5, F11):** a node changes role only through a power cycle and re-image, which extends B2 from "between principals" to "between roles"; the mailbox node enters the board document as a node type; a node's host-side port reaches the host and nothing else, with the verdict registry added to the acceptance tests as a destination a node must not reach.

## 9. Proposals from the review of 2026-11-15

None of these is ruled. Where the operator's statement already settles the substance, the proposal is only suggested wording.

- **F1** No dynamic code on the host: the host decodes only into types it holds, an unknown type is a decode failure, no location named in the stream is acted on, and a result is the node's claim unless the origin signed it.
- **F2** Detection: denial of service from signals outside the node (switch rates and volumes, latency measured by the host, mailbox quotas, the watchdog); timing attacks by counter signals from a monitor the board cannot forge, started as advisory. Limits declared per service when the parties agree to connect, enforced at the switch and the mailbox, set with a margin above a measured baseline.
- **F3** Isolation is automatic; power-down follows automatically once the capture outside the node is complete; removal from a lookup service is confirmed by a person.
- **F4** The host loads a proxy only for an origin on its allow list, held as a grant in the remote administration policy; removal from a lookup service that other parties use is a separate step by its administrator.
- **F5** Every node, whatever its role, meets N4 and N6, and changes role only through a power cycle and re-image.
- **F6** The proxy process on a node presents its own SPIFFE identity; the host's keys are never on a node; the service authorises the role, so the identity names the host's trust domain and the proxy-node role; a refuse-renewal flag reaches every issuer, and an issuer that cannot confirm it refuses.
- **F7** State what the analysis and the digest grant establish and what they do not: no claim about timing, runtime resource exhaustion or hostile use of permitted APIs.
- **F8** Wording: the controller is independent of the nodes and commanded by the host; a node bridging its two links is an accepted risk limited by the switch.
- **F9** One negative test for each control, each failing when its control is removed.
- **F10** The host obtains, relays and monitors verdicts by polling; running nodes continue on cached verdicts during a registry outage, a digest with no cached verdict is not loaded, the outage raises an alarm, and re-checks are staggered when the registry returns.
- **F11** The verdict registry is on the private network, secured as though public: mutual TLS by SPIFFE identity, every verdict verified by signature, only the host's role may query it, and no node has a route to it.
- **F12** When a verdict changes and inspection finds no attack, the node is re-imaged and the digest stays refused, but the service keeps its lookup entry, its identity and its place on the allow list; a corrected jar has a new digest and loads in the ordinary way.
- **F13** The two-step load (section 4).
- **F14** On a node, the permissions granted to a proxy are limited to what the granting code's `GrantPermission` covers for that role; a proxy that requests more is not loaded, the refusal is logged, and no partial grant is made.
- **F15** A stable grant lasts for the life of the node's JVM and is withdrawn only by power-down and re-image. An ephemeral grant is used only where the guard decides on each invocation or the object is reached through a delegate that rechecks; authority already released as an object is withdrawn at the switch where it is a connection, and otherwise by power-down.
- **F16** A proxy's `SocketPermission` on a node is a stable grant limited to `connect` to its origin, never `accept` or `listen`; the connection is ended at the switch.
- **F17** The node role's ceiling does not cover defining classes at run time, native invocation, native memory access or Java serialization.
- **F18** The node role's ceiling covers no thread creation, and the node's JVM runs with reduced timer resolution.
- **F19** Each node has a hardware watchdog that resets the board when it is not kicked, so that stopping a proxy that exhausts the CPU or heap, or blocks, does not depend on the host noticing (the operator raised the hardware watchdog of the SPARCclassic as the precedent). The kick is made only on evidence of useful work, for example when the node controller answers the host's liveness call within its deadline, not by a timer thread that keeps running while the node is starved. The watchdog device is guarded so that no proxy can reach it, and the node role's ceiling does not cover it. A reset returns the node to its image with no grants (N6). The host counts resets against the digests that were loaded and removes a repeat offender from the allow list. This makes the watchdog named in F2 a control on the board itself and not only a signal to the host. Not checked: whether the candidate boards' watchdogs can be made impossible to disable once started.

## 10. What is still open

- Automatic or confirmed response, and who can remove a lookup entry, including whether the lookup service is shared between parties (section 6, item 5; F3, F4).
- Detection thresholds (F2).
- What hardware evidence the issuer accepts for a node's identity (memo O24, O25). **STATED:** identity is determined by SPIFFE and would need redundancy, and SPIFFE policy varies per role and can prevent a JVM running on the wrong hardware.
- Where the code bytes are stored (host or controller), and which fields an established connection changes (P8). The two-step load offers one answer, the node returning the bytes with the digests in step 1; the operator has not answered.
- How the host enforces "no dynamic code" given the proxy-carrier path (section 7).
- Whether a long verdict-registry outage should ever isolate nodes.
- Which permissions are to be ephemeral on a node.
- Virtual thread scheduling in a JVM with clock coarsening on, and the other unguarded thread sites (section 5).
- Which JGDMS threads are first started with proxy code on the stack, now that a privileged block yields only what policy grants its class.
- The memo's proposals P1 to P11 (section 16.1) and F1 to F19 above.
- The STATED items are now in the claim register (see the tag list at the top); the operator has not reviewed those entries.
- No end-to-end attack exercise has been written or run against the chain. The first step suggested was a set of negative tests, one per ruling, each failing when its control is removed. Two such tests now exist, in DirtyChai and for the node's JVM only (section 5); none exists yet for the rulings themselves.
