/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.loader.isolation;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import au.net.zeus.jgdms.api.policy.VerdictPermissionMapper;
import java.io.FilePermission;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.security.AllPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import java.security.Principal;
import javax.security.auth.Subject;
import net.jini.security.GrantPermission;
import org.apache.river.api.net.Uri;
import org.apache.river.api.security.PermissionGrant;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link SubProcessGrantOrchestrator}: the caller-side wiring from
 * a known verdict to {@link VerdictPermissionMapper#computeSubProcessCeiling}
 * to {@link SubProcessAdminRegistry} to {@link PolicyAdmin#grant}.
 *
 * <p>Built against stubs written for this task (a recording {@link PolicyAdmin}
 * and a fixed {@link SubProcessAdministrable}), per
 * {@code SOW-Smart-Proxy-Isolation-Remaining-Work.md}'s T3 scope: prove the
 * wiring itself is correct, independent of the real {@code PolicyAdmin}
 * grant-application backend (T1) and the real wire-handoff "verdict becomes
 * known" trigger (T2), both still in progress elsewhere. One test
 * ({@link #realAdminGate_nonAdminCallerCannotBypass_viaOrchestrator()}) does
 * compose with the already-landed real {@link SubProcessPolicyAdmin}
 * authentication gate, to prove this class adds no bypass of it.
 */
public class SubProcessGrantOrchestratorTest {

    private static final String HASH =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String OTHER_HASH =
            "1122334455667788990011223344556677889900112233445566778899aabb";

    private static RegistryVerdict verdict(VerdictType t) {
        return verdictAt(t, 1L);
    }

    private static RegistryVerdict verdictAt(VerdictType t, long timestamp) {
        try {
            return new RegistryVerdict(
                    new Uri[]{ new Uri("http://example.com/foo-dl.jar") },
                    t, timestamp, new byte[]{ 1 });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Set<Permission> set(Permission[] p) {
        return new HashSet<Permission>(Arrays.asList(p));
    }

    private static IsolationPoolingKey key(String spiffeId) {
        try {
            return IsolationPoolingKey.derive(new Principal[]{ principal(spiffeId) });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Principal principal(final String n) {
        return new Principal() {
            public String getName() { return n; }
            public boolean equals(Object o) {
                return o instanceof Principal && n.equals(((Principal) o).getName());
            }
            public int hashCode() { return n.hashCode(); }
            public String toString() { return n; }
        };
    }

    /** Stub {@link PolicyAdmin} written for this task: records every grant. */
    static final class RecordingPolicyAdmin implements PolicyAdmin {
        final List<PermissionGrant> grants = new ArrayList<PermissionGrant>();
        public void grant(PermissionGrant g) { grants.add(g); }
        public void refresh() { }
        public PermissionGrant[] getGrants() {
            return grants.toArray(new PermissionGrant[grants.size()]);
        }
    }

    /**
     * Stub {@link SubProcessAdministrable} written for this task: hands back a
     * fixed {@link PolicyAdmin} unconditionally (no authentication gate of its
     * own -- deliberately, so these tests isolate the orchestrator's own
     * wiring from T2's real authentication mechanism, which is exercised
     * separately in {@link #realAdminGate_nonAdminCallerCannotBypass_viaOrchestrator()}).
     */
    static final class FixedSubProcessAdministrable implements SubProcessAdministrable {
        private final PolicyAdmin policyAdmin;
        FixedSubProcessAdministrable(PolicyAdmin policyAdmin) {
            this.policyAdmin = policyAdmin;
        }
        public PolicyAdmin getSubProcessPolicyAdmin() throws RemoteException {
            return policyAdmin;
        }
    }

    // ------------------------------------------------------ construction

    @Test(expected = NullPointerException.class)
    public void constructor_nullRegistry_rejected() {
        new SubProcessGrantOrchestrator(null);
    }

    @Test(expected = NullPointerException.class)
    public void applyVerdictCeiling_nullTargetKey_rejected() throws Exception {
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(new SubProcessAdminRegistry());
        orchestrator.applyVerdictCeiling(null, verdict(VerdictType.SAFE), HASH,
                new Permission[0], new GrantPermission(new AllPermission()), false);
    }

    // ------------------------------------------------ correct end-to-end

    @Test
    public void safeVerdict_pushesExactMapperCeiling_toRegisteredTarget()
            throws Exception {
        Permission need = new PropertyPermission("java.version", "read");
        GrantPermission caller = new GrantPermission(need);
        Permission[] declaredNeeds = new Permission[]{ need };

        // Independently-derived oracle: what the (already-tested) mapper
        // itself would compute, so this test does not just re-implement the
        // mapper's own expectations.
        Permission[] expected = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declaredNeeds, caller, false);
        assertTrue("test sanity: expect a non-empty ceiling", expected.length > 0);

        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        Permission[] returned = orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.SAFE), HASH, declaredNeeds, caller, false);

        assertEquals("returned ceiling must be exactly the mapper's own output",
                set(expected), set(returned));

        assertEquals("exactly one grant must have been pushed",
                1, recording.grants.size());
        PermissionGrant pushed = recording.grants.get(0);
        assertEquals("the pushed grant's permissions must be exactly the"
                + " mapper's ceiling -- no additional judgment at this layer",
                set(expected), new HashSet<Permission>(pushed.getPermissions()));
    }

    @Test
    public void craftedBroadDeclaredNeeds_stillClampedToCallerGrant_endToEnd()
            throws Exception {
        // Mirrors VerdictPermissionMapperTest's key adversarial probe, but
        // exercised through the full orchestrator wiring: nothing added
        // between the mapper's output and what actually gets pushed.
        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new FilePermission("/", "read,write,execute,delete"),
            new AllPermission(),
            new PropertyPermission("java.version", "read")
        };

        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        Permission[] returned = orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.SAFE), HASH, declared, caller, false);

        Set<Permission> expected = set(new Permission[]{
            new PropertyPermission("java.version", "read") });
        assertEquals(expected, set(returned));
        assertEquals(expected,
                new HashSet<Permission>(recording.grants.get(0).getPermissions()));
        for (Permission p : returned) {
            assertFalse(p instanceof AllPermission);
            assertFalse(p instanceof FilePermission);
        }
    }

    @Test
    public void inconclusiveNoTolerance_pushesEmptyCeilingVerbatim()
            throws Exception {
        // Even a zero-permission ceiling is pushed as-is (never silently
        // "upgraded" or skipped in a way that would hide what the mapper
        // actually decided).
        GrantPermission caller = new GrantPermission(new AllPermission());
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };

        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        Permission[] returned = orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.INCONCLUSIVE), HASH, declared, caller,
                /* inconclusiveToleranceGranted */ false);

        assertEquals(0, returned.length);
        assertEquals(1, recording.grants.size());
        assertEquals(0, recording.grants.get(0).getPermissions().size());
    }

    @Test(expected = IllegalStateException.class)
    public void dangerousVerdict_failsFast_neverReachesGrant() throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        try {
            orchestrator.applyVerdictCeiling(
                    target, verdict(VerdictType.DANGEROUS), HASH,
                    new Permission[]{ new PropertyPermission("java.version", "read") },
                    new GrantPermission(new AllPermission()), true);
        } finally {
            assertEquals("a DANGEROUS verdict must never reach PolicyAdmin.grant",
                    0, recording.grants.size());
        }
    }

    // ------------------------------------------------- canonical-key-only

    @Test(expected = IllegalStateException.class)
    public void unregisteredKey_failsClosed_neverGuessesAnotherTarget()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        // Register a DIFFERENT key only.
        registry.register(key("spiffe://example/workload/other"),
                new FixedSubProcessAdministrable(new RecordingPolicyAdmin()));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        orchestrator.applyVerdictCeiling(
                key("spiffe://example/workload/unregistered"),
                verdict(VerdictType.SAFE), HASH,
                new Permission[]{ new PropertyPermission("java.version", "read") },
                new GrantPermission(new AllPermission()), false);
    }

    @Test
    public void wrongKey_neverReachesTheOtherSubprocessesGrant() throws Exception {
        // Two distinct subprocesses registered; targeting one must never
        // touch the other's PolicyAdmin -- the canonical key is the only
        // routing signal.
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey keyA = key("spiffe://example/workload/a");
        IsolationPoolingKey keyB = key("spiffe://example/workload/b");
        RecordingPolicyAdmin recordingA = new RecordingPolicyAdmin();
        RecordingPolicyAdmin recordingB = new RecordingPolicyAdmin();
        registry.register(keyA, new FixedSubProcessAdministrable(recordingA));
        registry.register(keyB, new FixedSubProcessAdministrable(recordingB));

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);
        orchestrator.applyVerdictCeiling(
                keyA, verdict(VerdictType.SAFE), HASH,
                new Permission[]{ new PropertyPermission("java.version", "read") },
                new GrantPermission(new PropertyPermission("java.version", "read")),
                false);

        assertEquals("the targeted subprocess must receive the grant",
                1, recordingA.grants.size());
        assertEquals("a non-targeted subprocess must receive nothing",
                0, recordingB.grants.size());
    }

    /**
     * Self-probe (mirrors the task's own "self-test before you call it done"
     * requirement): the only public entry point capable of reaching a
     * {@link PolicyAdmin#grant} is {@code applyVerdictCeiling(IsolationPoolingKey,
     * ...)}. There must be no sibling public method accepting a raw
     * {@code String}/{@code Object} id, a {@link PolicyAdmin}, or a
     * {@link SubProcessAdministrable} directly -- any such overload would be
     * exactly the spoofable target-id shortcut
     * {@code SOW-Smart-Proxy-Isolation-Remaining-Work.md} &sect;2 rules out.
     * A future edit that adds such an overload fails this test.
     */
    @Test
    public void selfProbe_noAlternatePublicRouteToGrant_bypassingTheCanonicalKey() {
        Method[] methods = SubProcessGrantOrchestrator.class.getDeclaredMethods();
        List<Method> publicNonStatic = new ArrayList<Method>();
        for (Method m : methods) {
            if (Modifier.isPublic(m.getModifiers())) {
                publicNonStatic.add(m);
            }
        }
        assertEquals("exactly one public method is expected on this class"
                + " (the canonical-key-only entry point); a second public"
                + " method is a candidate spoofable-target-id bypass and must"
                + " be justified explicitly, not added incidentally: "
                + publicNonStatic,
                1, publicNonStatic.size());
        Method only = publicNonStatic.get(0);
        assertEquals("applyVerdictCeiling", only.getName());
        Class<?>[] params = only.getParameterTypes();
        assertEquals("routing must be through the canonical pooling key,"
                + " the first parameter", IsolationPoolingKey.class, params[0]);
        // The remaining parameters (verdict, contentHash, declaredNeeds,
        // callerGrantCeiling, inconclusiveToleranceGranted) feed the mapper's
        // OWN documented inputs -- contentHash is a String, but it identifies
        // the JAR the ceiling is being computed/scoped for, never a
        // subprocess/routing target, so a String parameter here is not itself
        // a violation. What must never appear is a parameter that could
        // substitute for -- or shortcut around -- the canonical-key lookup.
        for (int i = 1; i < params.length; i++) {
            assertNotEquals("no parameter after the canonical key may itself"
                    + " be a target-identifying handle",
                    PolicyAdmin.class, params[i]);
            assertNotEquals(SubProcessAdministrable.class, params[i]);
            assertNotEquals(SubProcessHandle.class, params[i]);
        }
    }

    // --------------------------------------- composes with the real T2 gate

    /** Injectable caller identity, mirroring {@code IsolationSecurityCriticalTest}. */
    static final class Caller implements AdminPrincipalAuthenticator.CallerIdentity {
        volatile Subject subject;
        public Subject current() { return subject; }
    }

    private static Subject subjectWith(Principal... ps) {
        Set<Principal> set = new LinkedHashSet<Principal>(Arrays.asList(ps));
        return new Subject(true, set, Collections.<Object>emptySet(),
                Collections.<Object>emptySet());
    }

    /**
     * Composes {@link SubProcessGrantOrchestrator} with the real, already-
     * landed {@link SubProcessPolicyAdmin} authentication gate (not a stub) to
     * prove the orchestrator adds no bypass of it: an unauthenticated caller
     * must be refused by the real gate exactly as it would be if invoked
     * directly, and the underlying backing must see zero grant calls.
     */
    @Test
    public void realAdminGate_nonAdminCallerCannotBypass_viaOrchestrator()
            throws Exception {
        Principal admin = principal("spiffe://ctrl/admin");
        RecordingPolicyAdmin backing = new RecordingPolicyAdmin();
        Caller caller = new Caller(); // unauthenticated: subject == null

        SubProcessPolicyAdmin realAdminSurface =
                new SubProcessPolicyAdmin(admin, backing, caller);

        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        registry.register(target, realAdminSurface);

        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        try {
            orchestrator.applyVerdictCeiling(
                    target, verdict(VerdictType.SAFE), HASH,
                    new Permission[]{ new PropertyPermission("java.version", "read") },
                    new GrantPermission(new PropertyPermission("java.version", "read")),
                    false);
            fail("an unauthenticated caller must be refused by the real T2"
                    + " admin gate; the orchestrator must not bypass it");
        } catch (SecurityException expected) {
            // good: the real gate's own fail-closed check fired, unchanged.
        }
        assertEquals("no grant may reach the backing without authenticating"
                + " as the admin principal",
                0, backing.grants.size());

        // Now authenticate as the real admin principal and confirm the same
        // orchestrator call succeeds end-to-end through the real gate.
        caller.subject = subjectWith(admin);
        Permission[] returned = orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.SAFE), HASH,
                new Permission[]{ new PropertyPermission("java.version", "read") },
                new GrantPermission(new PropertyPermission("java.version", "read")),
                false);
        assertEquals(1, returned.length);
        assertEquals(1, backing.grants.size());
    }

    // ---------------------------------------------------- digest scoping

    @Test
    public void differentContentHash_producesIndependentlyScopedGrants()
            throws Exception {
        // Two verdicts for two different JARs pushed to the same subprocess
        // must not be conflated into one shared-scope grant; each push is
        // scoped to its own contentHash.
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };

        orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.SAFE), HASH, declared, caller, false);
        orchestrator.applyVerdictCeiling(
                target, verdict(VerdictType.SAFE), OTHER_HASH, declared, caller, false);

        assertEquals(2, recording.grants.size());
        // Distinct digest-scoped grants are not equal to one another even
        // though their permission sets are identical.
        assertNotEquals("grants scoped to different content hashes must not"
                + " be treated as the same grant",
                recording.grants.get(0), recording.grants.get(1));
    }

    // ------------------------------------- anti-replay / freshness gate
    // (2026-07-20 T4 adversarial-pass Finding 2)

    /**
     * <strong>Finding 2 reproduction / closure.</strong> A board reviewer
     * proved that resubmitting a byte-for-byte identical {@code
     * applyVerdictCeiling} call -- same target, same verdict, same
     * contentHash -- silently reinstated a ceiling after it had already,
     * correctly, expired. This test doesn't need a real lease/TTL to prove
     * the point: it proves the narrower, sufficient claim that the SAME
     * verdict object (hence the same signed timestamp) applied twice to the
     * SAME (targetKey, contentHash) pair is refused the second time --
     * exactly what stops a resend of old, already-processed verdict bytes
     * from ever reaching {@link PolicyAdmin#grant} again.
     */
    @Test
    public void replayedIdenticalVerdict_sameTargetAndHash_refusedSecondTime()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };
        RegistryVerdict v = verdictAt(VerdictType.SAFE, 1_000L);

        // First application: genuinely new (no prior generation recorded for
        // this target+hash) -- must succeed.
        orchestrator.applyVerdictCeiling(target, v, HASH, declared, caller, false);
        assertEquals(1, recording.grants.size());

        // Byte-for-byte identical resubmission (same verdict object, same
        // timestamp, same target, same contentHash): must be refused, fail
        // closed, and must never reach PolicyAdmin.grant a second time.
        try {
            orchestrator.applyVerdictCeiling(target, v, HASH, declared, caller, false);
            fail("a replayed/identical verdict re-application must be refused");
        } catch (SecurityException expected) {
            // correct: fail-closed anti-replay guard.
        }
        assertEquals("the replayed call must never have reached PolicyAdmin.grant",
                1, recording.grants.size());
    }

    /**
     * A genuinely fresher re-verdict (a strictly later signed timestamp) for
     * the exact same target+contentHash pair must be accepted, not confused
     * with a replay -- this is the "re-verdict" case Finding 1's supersession
     * fix (at T1) depends on actually being able to reach {@code
     * PolicyAdmin.grant} in the first place.
     */
    @Test
    public void freshRVerdict_laterTimestamp_sameTargetAndHash_isAccepted()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };

        orchestrator.applyVerdictCeiling(
                target, verdictAt(VerdictType.SAFE, 1_000L), HASH, declared, caller, false);
        // Strictly later timestamp: a genuine re-verdict, not a replay.
        orchestrator.applyVerdictCeiling(
                target, verdictAt(VerdictType.SAFE, 2_000L), HASH, declared, caller, false);

        assertEquals("a strictly-fresher re-verdict for the same target+hash"
                + " must be accepted, not treated as a replay",
                2, recording.grants.size());
    }

    /**
     * An older (or equal) timestamp for the same target+hash must be refused
     * even when it did NOT arrive from a literal byte-for-byte resend of a
     * previous call's arguments -- staleness, not merely object identity, is
     * the guard.
     */
    @Test(expected = SecurityException.class)
    public void staleVerdict_earlierTimestamp_sameTargetAndHash_isRefused()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };

        orchestrator.applyVerdictCeiling(
                target, verdictAt(VerdictType.SAFE, 5_000L), HASH, declared, caller, false);
        // An OLDER timestamp than what's already been applied: refused.
        orchestrator.applyVerdictCeiling(
                target, verdictAt(VerdictType.SAFE, 4_000L), HASH, declared, caller, false);
    }

    /**
     * A first-time application for a given target+contentHash pair must
     * never be rejected merely because SOME other (target, contentHash) pair
     * already has a recorded generation -- the freshness gate is scoped
     * exactly to the pair, not global. Also confirms two DIFFERENT targets
     * legitimately applying the identical verdict (identical timestamp) to
     * two different subprocesses -- a plausible "same JAR pooled twice" case
     * -- never falsely contend with one another.
     */
    @Test
    public void firstTimeGrant_neverRejected_evenAfterUnrelatedPairRecorded()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey targetA = key("spiffe://example/workload/a");
        IsolationPoolingKey targetB = key("spiffe://example/workload/b");
        RecordingPolicyAdmin recordingA = new RecordingPolicyAdmin();
        RecordingPolicyAdmin recordingB = new RecordingPolicyAdmin();
        registry.register(targetA, new FixedSubProcessAdministrable(recordingA));
        registry.register(targetB, new FixedSubProcessAdministrable(recordingB));
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };
        RegistryVerdict sameVerdict = verdictAt(VerdictType.SAFE, 42L);

        // Record a generation for (targetA, HASH).
        orchestrator.applyVerdictCeiling(targetA, sameVerdict, HASH, declared, caller, false);
        assertEquals(1, recordingA.grants.size());

        // A DIFFERENT target, same verdict/timestamp, same contentHash: this
        // is target B's OWN first-time application, not a replay against B.
        orchestrator.applyVerdictCeiling(targetB, sameVerdict, HASH, declared, caller, false);
        assertEquals("a different target's first-time application must never"
                + " be rejected because of an unrelated target's recorded"
                + " generation", 1, recordingB.grants.size());

        // Likewise, the SAME target but a DIFFERENT contentHash: target A's
        // own first-time application for that other JAR.
        orchestrator.applyVerdictCeiling(targetA, sameVerdict, OTHER_HASH, declared, caller, false);
        assertEquals("a different contentHash on the SAME target must never"
                + " be rejected because of an unrelated contentHash's"
                + " recorded generation", 2, recordingA.grants.size());
    }

    /**
     * A call refused further downstream (here: an unregistered target) must
     * NOT consume the freshness slot -- a legitimate retry of the identical
     * verdict, once the precondition is fixed, must still succeed.
     */
    @Test
    public void refusedAttempt_neverConsumesFreshnessSlot_legitimateRetrySucceeds()
            throws Exception {
        SubProcessAdminRegistry registry = new SubProcessAdminRegistry();
        IsolationPoolingKey target = key("spiffe://example/workload/target");
        SubProcessGrantOrchestrator orchestrator =
                new SubProcessGrantOrchestrator(registry);

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new PropertyPermission("java.version", "read") };
        RegistryVerdict v = verdictAt(VerdictType.SAFE, 7L);

        // First attempt: target not registered yet -- fails closed, upstream
        // of the freshness gate's own downstream effects.
        try {
            orchestrator.applyVerdictCeiling(target, v, HASH, declared, caller, false);
            fail("expected IllegalStateException: target not yet registered");
        } catch (IllegalStateException expected) {
            // correct
        }

        // Now register the target and retry with the SAME verdict object --
        // must succeed: the earlier failed attempt must not have consumed
        // the freshness slot for (target, HASH).
        RecordingPolicyAdmin recording = new RecordingPolicyAdmin();
        registry.register(target, new FixedSubProcessAdministrable(recording));
        orchestrator.applyVerdictCeiling(target, v, HASH, declared, caller, false);
        assertEquals("a legitimate retry of the same verdict after an upstream"
                + " failure must not be mistaken for a replay",
                1, recording.grants.size());
    }
}
