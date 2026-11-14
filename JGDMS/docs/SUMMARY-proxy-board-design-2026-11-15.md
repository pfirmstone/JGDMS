# Summary: the proxy-board design (reading aid)

**AS-OF 2026-11-15.** Written by Claude Sonnet 5.5 (session 757702ae) at the operator's request. **A reading aid, not an authority.** The authority for every ruled statement is the claim register (`E:\Claude\claim_register\rulings\JGDMS\INDEX.md`, entries RL-JGDMS-0037 to RL-JGDMS-0050); this file cites IDs and does not restate their status, so read `show <ID>` for the operator's own words and any later change. Anything marked **PROPOSED** is the assistant's suggestion, not a ruling (they are P1 to P11 in memo section 16.1).

**Purpose of the design** (operator, earlier in this project): it lets parties, such as companies that know each other, connect with limited trust.

**Not read when this was written:** the bytecode analysis engine's code, `DIGEST_GRANT_PLAN.md` in DirtyChai, and `SOW-BAE-Timing-Sidechannel-Denial.md`. What is said about them below comes from `JGDMS-STD-002` v1.3 and `PROXY_HOST_BOARD_REQUIREMENTS.md` only.

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
 | (independent of host  |  links,      | no downloaded code |
 |  and of nodes)        |  images      +--------------------+
 +-----------------------+
```

The board has two links, one on each network, and must not forward between them; the host treats every node as hostile (`PROXY_HOST_BOARD_REQUIREMENTS.md` H1, H2, lines 360 to 368). The host contacts only the lookup service, never the service's server (same document, H3, lines 369 to 373). The controller is independent of the host and of every node, and can power all nodes off if the host's heartbeat is lost (H4, lines 380 to 382).

## 2. Who starts each connection

| From | To | Why | Source |
|---|---|---|---|
| Host | Proxy board | The host invokes methods on the proxy. The board never calls the host. No call-back listeners. | RL-JGDMS-0037 |
| Proxy board | Service server | Outbound over the public network; the board's proxy code talks to its origin. | RL-JGDMS-0037; board document H3 |
| Host | Event mailbox node | The host pulls events by method call. The host exports no listener. | RL-JGDMS-0038 |
| Service server | Event mailbox node | Outrigger delivers events to the mailbox. This is the only inbound path to a node. | RL-JGDMS-0038, RL-JGDMS-0039. The board document has no inbound case yet (P5). |
| Host | Controller | The host commands the controller over a dedicated authenticated link. | board document H4 |

## 3. Events and leases

- Leases are renewed by the host through the board to the service server (RL-JGDMS-0038).
- Events cannot use a listener the host exports, because that would reverse the call direction (RL-JGDMS-0037). An event mailbox relays them, on a board separate from every board running untrusted proxy code (RL-JGDMS-0038). The host pulls them.
- The event mailbox node has its own unique SPIFFE role and policy and runs no untrusted or downloaded code (RL-JGDMS-0039). One such node serves a host and may process events from several partially trusted services (RL-JGDMS-0040).
- The host can move the mail service to another proxy board and must re-register the event listeners when it does (RL-JGDMS-0041). It can also power the node down and back on, or swap it with a busy node (RL-JGDMS-0042).
- The host may use two mailbox nodes, split the services between them, and on an attack move the non-attacking services to the node not under attack (RL-JGDMS-0043).
- **PROPOSED:** events arrive through an untrusted relay, so they are unverified claims unless the origin signs them (P1); the host detects sequence gaps after a move and re-reads the space (P2); the mailbox node is stateless and boots from a signed image (P3); per-origin identity, opaque payloads and quotas on a shared node (P4).

## 4. Loading a proxy

- The host holds the marshalled proxy from the `SafeServiceRegistrar` (RL-JGDMS-0047).
- Before a proxy is loaded, its digest, its code and its serial form are stored (RL-JGDMS-0046).
- The code is statically analysed by the bytecode analysis engine before it can run on the proxy board, and a codebase gets `LoadClassPermission` only if it has the digest that SCAP processed, wherever it was downloaded from (RL-JGDMS-0048).
- What SCAP does, from `JGDMS-STD-002` v1.3: untrusted analysis engines with no outbound network and no path to the verdict registry (lines 214 to 250 and 410 to 418), a verdict keyed by content hash that needs a quorum of engines to say safe and condemns on one negative (lines 488 to 519), and an engine crash that counts as an automatic dangerous verdict (lines 591 to 602). Its listed checks are `<clinit>` blocking, circular `<clinit>` dependency and `@AtomicSerial` compliance; whether it also scans for dangerous APIs was not established (P10).
- The check runs in the board's own JVM. It stops unapproved code loading and does not protect against a board already compromised, which is what the isolation and power-cycle below are for.
- **PROPOSED:** the loader hashes the buffer it defines classes from, over the same normalised form SCAP uses, and the grant mapping reaches the board as a signed update from the host (P9).

## 5. When a proxy is suspected of attacking

1. The suspected attacker's board is isolated from the public network, and its proxy is persisted and analysed using DER (RL-JGDMS-0044).
2. The power-down follows the isolation (RL-JGDMS-0049).
3. The attacker's service is removed from the lookup service (RL-JGDMS-0045).
4. The proxy's state at isolation can differ from its stored initial state, because a DER TLS connection to its service has been established (RL-JGDMS-0050). A difference is expected and is not by itself evidence of tampering (memo section 16.2 strikes the earlier remark that it was).
5. **Not ruled:** whether this response is automatic or confirmed by a person, and who is able to remove a lookup entry.
6. **PROPOSED:** take the capture outside the node (egress switch, controller) before any power-off; revoke the origin's identity and remove its address from the switch allowlist; quarantine before permanent removal; a reinstatement path; a limit on how long an isolated node keeps running; evidence stored content-addressed and write-once, never executed on the store (P7, P8).

## 6. The carrier board

The operator designs the carrier board, so some of the above can be enforced in hardware (advice only, for the operator to write into the board document; none of it is ruled):

- per-node power control with rail discharge; the controller kills either link of a node independently of the node;
- a mirror or tap port per node, so the evidence is taken outside the node;
- no path between nodes through shared buses or debug ports; read-only boot and no writable storage on the mailbox node;
- a hardware ACL on the mailbox node's single inbound port, moved with the role by the controller;
- per-node rate limits on the egress uplink, or a dedicated uplink per node;
- the controller and the switch chip are then part of the trusted base, with signed firmware and rollback protection.

## 7. What is still open

- Automatic or confirmed response, and who can remove a lookup entry (section 5, item 5).
- Where the code bytes are stored (host or controller), and which fields an established connection changes (P8).
- The memo's proposals P1 to P11 (section 16.1).
- No end-to-end attack exercise has been written or run against the chain. The assistant's suggested first step is a set of negative tests, one per ruling, each failing when its control is removed.
