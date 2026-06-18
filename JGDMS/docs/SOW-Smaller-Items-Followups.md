# Scope of Work — Smaller follow-up items (code/doc hygiene)

*Draft SOW — 2026-06-15. Residual items from `agent-authority-code-review-2026-06-14.md`,
bundled for a separate agent. Code-comment / minor-doc edits only; no behavioural change without a
separate decision. **Dispatch AFTER the documentation-status pass** to avoid overlapping edits.*

## Items

1. **Fix the `polpAudit` property-name doc/code drift** *(review §4.5, claim 8).*
   `SecurityPolicyWriter` Javadoc (`SecurityPolicyWriter.java:124-125`) names the legacy property
   `SecurityPolicyWriter.path.properties`, but the code reads `polpAudit.path.properties` (`:186`).
   A user following the Javadoc gets no substitutions. Align the Javadoc to the actual property name
   (or vice-versa per the intended canonical name). Low severity, easy fix.

2. **Document the `DigestGrant` reflection-coupling footgun** *(review §4.3).*
   `DigestGrant` resolves `DigestCodeSource` reflectively (`DigestGrant.java:61-75`); on a non-DirtyChai
   JVM the class is absent, so **every** `DigestGrant` returns false (fail-closed — correct, but
   silent). Add a clear startup warning/log (or a prominent doc note) so an operator does not misread
   "no digest-matched grants apply" as "policy broken" when they are simply on the wrong JVM.

3. **Backlog: make the class-load digest algorithm configurable** *(review §4.4).*
   `SecureClassLoader.getProtectionDomain()` hard-codes SHA-256 (`:368`, already flagged in-code as a
   planned follow-up). Capture as a tracked backlog item (behavioural change → its own decision/PR,
   not a fix-now).

4. **Correct any prose stating "polpAudit emits scoped *GrantPermission*"** *(review §5).*
   The accurate statement is "scoped least-privilege **grant blocks** (with `digest`/`principal`
   scoping)"; a `GrantPermission` line appears only if the audited code exercised a `DynamicPolicy.grant`.
   *(If the documentation-status pass already corrected this in the .md docs, mark done — this item is
   for any remaining occurrences, e.g. in code comments.)*

5. **Credit the existing peer-scoped network vocabulary where relevant** *(review §5 / Q-C).*
   `AuthenticationPermission`'s `peer` clause already provides peer-identity-scoped network on the
   authenticated SSL/JERI path (by X500Principal from the SVID; raw egress is still `SocketPermission`).
   Ensure any doc that frames peer-scoped egress as "open/desired" notes it already exists, with the
   X500-vs-SPIFFE and authenticated-path qualifications.

## Constraint
Javadoc / minor-doc edits only. Item 3 is a backlog entry, not an implementation. Do not touch the
SOW files, the review report, or the `.md` standards docs already handled by the documentation-status
pass (coordinate to avoid double-editing).
