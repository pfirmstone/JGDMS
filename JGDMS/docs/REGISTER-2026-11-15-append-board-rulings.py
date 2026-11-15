import subprocess, sys
REG = r"E:\Claude\claim_register\claim_register.py"
BY = "Claude Sonnet 5.5, JGDMS path-rating session 757702ae"
LOC = ("session 757702ae transcript, operator replies of 2026-11-15 on the proxy-board design "
       r"(C:\Users\peter\.claude\projects\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd.jsonl)")
UN = "the operator says otherwise"
ids = {}

def new(key, statement, note, unblock=UN):
    a = [sys.executable, "-I", REG, "new", "JGDMS", "IN-FORCE", "--kind", "ruling", "--statement", statement,
         "--locator", LOC, "--by", BY, "--note", note, "--unblock", unblock]
    r = subprocess.run(a, capture_output=True, text=True, encoding="utf-8")
    out = (r.stdout + r.stderr).strip()
    print(key, "->", out.replace("\n", " | ")[:300])
    got = [l.split()[1] for l in out.splitlines() if l.startswith("allocated ")]
    if r.returncode != 0 or not got:
        sys.exit("STOPPED at " + key)
    ids[key] = got[0]

new(1, "The trusted host is on a private network and its proxy board is connected to it directly, and to its service server over the public network. The board never calls the host: only the host invokes methods on the proxy. No call-back listeners are used.",
    'Operator verbatim: "We were going to have our trusted host on a private network, the proxy board would have a connection directly to the host on the private network and a connection to its service server via public network." and "The proxy board doesn\'t call the host, only the host invokes method calls on the proxy, provided no call-back listeners are used."')
new(2, "Services that need events or leases (for example Outrigger on the service server) are served by an event mailbox as the relay, on a board separate from every board running untrusted proxy code; the host pulls events by method call and exports no listener. Lease renewals go host to board to origin.",
    'Operator verbatim: "There is an event mailbox service, which could be used instead as a relay." and "Separate board" (the operator also said Outrigger runs on the service server across the public network).')
new(3, "The event mailbox node has its own unique SPIFFE role and policy and runs no untrusted or downloaded code.",
    'Operator verbatim: "Yes.  The "event mailbox node" can be assigned a unique Spiffe role and policy, and run no untrusted or downloaded code."')
new(4, "One event mailbox node serves a host and processes all remote events for it, possibly from several partially trusted third-party services.",
    'Operator verbatim: "The mailbox node might need to process events from a number of partially trusted third party services." and "As the mailbox node will process all remote events for the host" (the sentence was left unfinished by the operator).')
new(5, "The host can move the mail service to another proxy board, and must re-register the event listeners when it does.",
    'Operator verbatim: "The host can dynamically change the mail service to another proxy board." and "Yes it has to reregister the event listeners"')
new(6, "If all other proxy nodes are busy the host may power one down and swap it with the mailbox node, or it may power the mailbox node down and back on.",
    'Operator verbatim: "If all the other proxy nodes are busy, it can power one down and swap with the mailbox node" and "Or it could just power down the mailbox node and power it back on."')
new(7, "The host may use two event mailbox nodes, divide the services between them and, on an attack, record it, determine the attacker and move the non-attacking services to the node that is not under attack.",
    'Operator verbatim: "What it could do is establish two nodes, and split the services, between them, if it can record and attack, it may be able to determine the attacker, allowing the non attacking services to be relocated to the node that isn\'t under attack." (the assistant read "record and attack" as "record the attack"; the operator was asked and the ruling was confirmed with Yes.)')
new(9, "The suspected attacker's proxy board is isolated from the public network, and its proxy is persisted and analysed using DER.",
    'Operator verbatim: "Good point, the suspected proxy attacker\'s board should be isolated from the public network and it\'s proxy persisted and analysed using DER." This amends the power-down step of the ruling on the attacker (recorded next).')
new(8, "Once the attacker is identified its proxy's node is powered down and its service is removed from the lookup service. The power-down step is amended by " + ids[9] + ": the board is first isolated from the public network and its proxy persisted and analysed. Whether the power-down follows the isolation or is replaced by it, and whether the response is automatic or confirmed by a person, are not yet ruled.",
    'Operator verbatim: "Once the attacker is identified it\'s proxy\'s node is powered down, and it\'s service is removed from the lookup service."')
new(10, "Before a proxy is loaded, its digest, its code and its serial form are stored.",
    'Operator verbatim: "The proxy\'s digest would be stored before loading." and "In fact we can store both the code and the serial form prior to loading."')
new(11, "The host holds the marshalled proxy from the SafeServiceRegistrar.",
    'Operator verbatim: "The host has the marshalled proxy from the SafeServiceRegistrar."')
new(12, "Code is statically analysed by the bytecode analysis engine before it can run on the proxy board, and a codebase gets LoadClassPermission only if it has the digest that SCAP processed, wherever it was downloaded from.",
    'Operator verbatim: "The code will be statically analysed by the bytecode analysis engine before it can run on the proxy board." and "It doesn\'t matter where the jar is downloaded from, it must have the same digest SCAP processed, otherwise, it won\'t have LoadClassPermission." (the operator corrected the typo "haver" to "have".)')
print(ids)
