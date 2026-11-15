import subprocess, sys
REG = r"E:\Claude\claim_register\claim_register.py"
BY = "Claude Sonnet 5.5, JGDMS path-rating session 757702ae"
LOC = ("session 757702ae transcript, operator reply of 2026-11-15 after RL-JGDMS-0037 to RL-JGDMS-0048 were recorded "
       r"(C:\Users\peter\.claude\projects\C--Users-peter-Documents-GitHub-DirtyChai\757702ae-f74f-45fd-95a6-a302242c73cd.jsonl)")
UN = "the operator says otherwise"
ids = {}


def new(key, statement, note):
    a = [sys.executable, "-I", REG, "new", "JGDMS", "IN-FORCE", "--kind", "ruling", "--statement", statement,
         "--locator", LOC, "--by", BY, "--note", note, "--unblock", UN]
    r = subprocess.run(a, capture_output=True, text=True, encoding="utf-8")
    out = (r.stdout + r.stderr).strip()
    print(key, "->", out.replace("\n", " | ")[-120:])
    got = [l.split()[1] for l in out.splitlines() if l.startswith("allocated ")]
    if r.returncode != 0 or not got:
        sys.exit("STOPPED at " + str(key))
    ids[key] = got[0]


new("A", "For a suspected attacker's proxy node the power-down follows the isolation: the board is isolated from the public network first, its proxy persisted and analysed, and the node powered down afterwards. This settles the point RL-JGDMS-0045 left open on whether the power-down follows or is replaced by the isolation.",
    'Operator verbatim: "Power down should follow isolation."')
new("B", "The proxy's state at isolation can differ from its stored pre-load serial form, because a DER TLS connection between the proxy and its service has been established. A difference between the persisted proxy and the stored initial form is therefore expected and is not by itself evidence of tampering.",
    'Operator verbatim: "A DER TLS connection between the proxy and it\'s service are established, so its state might differ from it\'s initial state." (the second sentence of the statement is the assistant\'s inference from this and was not stated by the operator; it withdraws the assistant\'s earlier remark that such a difference would itself be evidence.)')
print(ids)
