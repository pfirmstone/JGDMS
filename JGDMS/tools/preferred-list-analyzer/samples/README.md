# Generated reference artifacts

These are **reference outputs** of the analyzer run against the compiled
`jgdms-platform` (2026-06-18), not live module resources:

- `jgdms-platform.PREFERRED.LIST` — the `META-INF/PREFERRED.LIST` the tool would
  generate: global default `Preferred: false` (share by default) with explicit
  `Preferred: true` only for `net.jini.id.UuidFactory` and
  `net.jini.security.proxytrust.ProxyTrustExporter`.
- `jgdms-platform.analysis-report.txt` — the human-readable report (the PREFER
  evidence, the CONFLICT `DelegationAbsoluteTime`, and the 12 needs-review rows).

Regenerate with the CLI (or via `mvn -pl tools/preferred-list-analyzer` which runs
`check` in report-only mode during `verify`):

```
java -cp target/classes:<asm.jar> org.apache.river.tool.preferred.PreferredListTool \
     generate ../../jgdms-platform/target/classes \
     --out samples/jgdms-platform.PREFERRED.LIST \
     --report samples/jgdms-platform.analysis-report.txt
```

As of 2026-06-18 the live
`jgdms-platform/src/main/resources/META-INF/PREFERRED.LIST` **is** this generated
list (the share-by-default flip was applied), and the build enforces it with
`check --fail-on-drift`. These snapshots are kept as a point-in-time reference and
for the human-readable report, which is not otherwise checked in.
