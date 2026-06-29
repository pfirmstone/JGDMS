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

ACT = ('ActivatableInvocationHandler','ActivationExporter','ActivationGroup',
       'ActivationAdmin','MarshalledWrapper')
REMOVALS = ('IncomingUnicastResponse','OutgoingUnicastResponse','.Discovery ',
            'DiscoveryProtocolVersion','CombinerSecurityManager','DelegateSecurityManager',
            'RemotePolicy')
buckets = {'A serial':[], 'B jdk-activation':[], 'C marshalledobject':[],
           'D deliberate-removal':[], 'E pre-ai-human':[], 'benign':[], 'UNCLASSIFIED':[]}
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
    elif ('REMOVED INTERFACE: java.io.Serializable' in body or '(not serializable)' in body
          or 'serialVersionUID' in body or '(field removed)' in body or '(type of field' in body):
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
