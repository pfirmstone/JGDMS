#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Blocked monitor entries from a JFR recording: which monitor classes and call sites made threads wait, and for how long.
# Record with: -XX:StartFlightRecording=filename=x.jfr,dumponexit=true,jdk.JavaMonitorEnter#threshold=0ms
# Run:    python3 jfr-monitors.py x.jfr      (needs the JDK `jfr` tool on the PATH)
# Written with AI assistance (Claude); see the README.
f=sys.argv[1]
out=subprocess.run(['jfr','print','--events','jdk.JavaMonitorEnter','--stack-depth','8',f],capture_output=True,text=True).stdout
evs=out.split('jdk.JavaMonitorEnter {')[1:]
byclass=collections.Counter(); dur=collections.Counter(); site=collections.Counter(); sitedur=collections.Counter()
for e in evs:
    d=re.search(r'duration = ([\d.]+) (\w+)',e)
    if not d: continue
    v=float(d.group(1)); u=d.group(2); us=v*{'ns':1e-3,'us':1,'ms':1e3,'s':1e6}[u]
    m=re.search(r'monitorClass = (\S+)',e); c=m.group(1) if m else '?'
    byclass[c]+=1; dur[c]+=us
    fr=re.findall(r'^\s+(\S+)\(.*?\)\s+line:\s*(\d+)',e,re.M)
    s=' <- '.join('%s:%s'%(a.split('.')[-2]+'.'+a.split('.')[-1],b) for a,b in fr[:3])
    site[(c.split('.')[-1],s)]+=1; sitedur[(c.split('.')[-1],s)]+=us
print(f,'events',len(evs))
for c,n in byclass.most_common(6): print('  %-50s n=%6d total=%8.1f ms avg=%.1f us'%(c,n,dur[c]/1000,dur[c]/n))
for k,n in sitedur.most_common(6): print('   site %-12s %s  n=%d total=%.1f ms'%(k[0],k[1][:120],site[k],n/1000))
