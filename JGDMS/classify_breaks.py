#!/usr/bin/env python3
"""Classify a japicmp '-b' (binary-incompatible) text report against the JGDMS
accepted-breaks ledger. Exit non-zero only on UNCLASSIFIED (candidate accidental)
breaks. Also reports surface-hygiene widenings (newly public) separately."""
import sys, re

report = open(sys.argv[1]).read().splitlines()

# Split into per-class blocks: a class header + its indented member lines.
blocks, cur = [], None
HDR = re.compile(r'^(\*\*\*!?|---!?|\+\+\+!?)\s+(MODIFIED|REMOVED|NEW)\s+(CLASS|INTERFACE):\s*(.*)$')
for ln in report:
    m = HDR.match(ln)
    if m:
        cur = {'fqcn': m.group(4), 'lines': [ln]}
        blocks.append(cur)
    elif cur is not None and (ln.startswith('\t') or ln.startswith(' ')):
        cur['lines'].append(ln)

def fqcn_name(h):
    toks = h.replace('(', ' ').split()
    return next((t for t in toks if '.' in t and t[0].islower()), h)

def security_doas_only(lines):
    """True if every breaking (*** / --- / +++ with '!') member line of a
    net.jini.security.Security block is a REMOVED METHOD doAs/doAsPrivileged
    (the class-file-version line is ignored: it shows on every class)."""
    brk = [l for l in lines[1:] if re.match(r'^\s*(\*\*\*|---|\+\+\+)!', l)
           and 'CLASS FILE FORMAT VERSION' not in l]
    return bool(brk) and all(
        re.search(r'REMOVED METHOD:.*\b(doAs|doAsPrivileged)\(', l) for l in brk)

ACT = ('ActivatableInvocationHandler','ActivationExporter','ActivationGroup',
       'ActivationAdmin','MarshalledWrapper')
REMOVALS = ('IncomingUnicastResponse','OutgoingUnicastResponse','.Discovery ',
            'DiscoveryProtocolVersion','CombinerSecurityManager','DelegateSecurityManager',
            'RemotePolicy')
buckets = {'A serial':[], 'B jdk-activation':[], 'C marshalledobject':[],
           'D deliberate-removal':[], 'E pre-ai-human':[], 'G subject-domain-removal':[], 'benign':[], 'UNCLASSIFIED':[]}
widenings = []

for b in blocks:
    body = '\n'.join(b['lines']); fq = fqcn_name(b['fqcn'])
    if re.search(r'\(<- (PACKAGE_PROTECTED|PROTECTED)\)', body):
        widenings.append(fq)
    if any(a in fq for a in ACT) or 'java.rmi.activation' in body or 'activation.arg' in body:
        cat = 'B jdk-activation'
    elif any(r.strip() in (fq+' ') for r in REMOVALS):
        cat = 'D deliberate-removal'
    elif 'MarshalledObject' in body:
        cat = 'C marshalledobject'
    elif fq.endswith('LookupLocator') or 'WakeupManager' in fq:
        cat = 'E pre-ai-human'
    elif fq.endswith('DelegationAbsoluteTime'):
        cat = 'benign'
    elif fq.endswith('org.apache.river.api.security.SubjectDomain') or (
            fq.endswith('net.jini.security.Security') and security_doas_only(b['lines'])):
        # Category G (2026-10-23, maintainer's direction): the SubjectDomain
        # feature was removed -- the SubjectDomain marker interface and the
        # four Security.doAs/doAsPrivileged overloads. Matched narrowly: the
        # Security block qualifies only if EVERY breaking member line is a
        # removed doAs/doAsPrivileged method, so any other break in Security
        # is classified by the other branches (or stays UNCLASSIFIED). Placed
        # before category A, whose generic "(not serializable)" header match
        # would otherwise swallow these (japicmp tags every non-Serializable
        # class that way). See docs/JGDMS-API-Compatibility-Accepted-Breaks.md.
        cat = 'G subject-domain-removal'
    elif ('REMOVED INTERFACE: java.io.Serializable' in body or '(not serializable)' in body
          or '(Serializable removed)' in body
          or 'serialVersionUID' in body or '(field removed)' in body or '(type of field' in body):
        cat = 'A serial'
    elif fq.endswith('ArrayClassNotFoundException'):
        # Named ledger entry (not a generic pattern): the trunk-baseline diff
        # (T3) surfaced removal of the PutField-based putArgs(...) helper,
        # part of the same Serializable-removal program as the rest of
        # category A, just not matching its generic text patterns (no
        # "not serializable"/"field removed" wording for a plain method
        # removal). See docs/JGDMS-API-Compatibility-Accepted-Breaks.md.
        cat = 'A serial'
    else:
        cat = 'UNCLASSIFIED'
    buckets[cat].append(fq)

print("=== binary-incompat changes by category ===")
for k,v in buckets.items():
    print(f"  {k:22} {len(v)}")
    for fq in v: print(f"       - {fq}")
print(f"\n=== surface-hygiene: newly public (access widened) : {len(widenings)} ===")
for w in widenings: print(f"       ! {w}")
n = len(buckets['UNCLASSIFIED'])
print(f"\nGATE: {'FAIL' if n else 'PASS'} — {n} unclassified break(s)")
sys.exit(1 if n else 0)
