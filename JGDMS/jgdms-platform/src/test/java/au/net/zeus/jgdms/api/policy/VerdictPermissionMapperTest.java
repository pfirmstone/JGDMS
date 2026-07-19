/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.api.policy;

import au.net.zeus.jgdms.api.codebase.RegistryVerdict;
import au.net.zeus.jgdms.api.codebase.VerdictType;
import java.io.ByteArrayInputStream;
import java.io.FilePermission;
import java.nio.charset.StandardCharsets;
import java.security.AllPermission;
import java.security.Permission;
import java.security.UnresolvedPermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.PropertyPermission;
import java.util.Set;
import net.jini.security.GrantPermission;
import org.apache.river.api.net.Uri;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Adversarial tests for {@link VerdictPermissionMapper}, the verdict-to-ceiling
 * mapping that establishes the authority ceiling of every isolated subprocess.
 * The probes here are the ones the SOW-SubProcessDynamicPolicy T2 brief
 * mandates: a crafted / over-broad {@code PERMISSIONS.LIST} must not exceed the
 * caller's own {@code GrantPermission}; a boundary-case verdict must fail
 * closed; and the INCONCLUSIVE ceiling must be strictly narrower than SAFE for
 * identical declared needs.
 */
public class VerdictPermissionMapperTest {

    private static final String HASH =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    private static RegistryVerdict verdict(VerdictType t) {
        try {
            return new RegistryVerdict(
                    new Uri[]{ new Uri("http://example.com/foo-dl.jar") },
                    t, 1L, new byte[]{ 1 });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Set<Permission> set(Permission[] p) {
        return new HashSet<Permission>(Arrays.asList(p));
    }

    // ---------------------------------------------------------------- SAFE

    @Test
    public void safe_admitsDeclaredNeedWithinCallerGrant() {
        Permission need = new PropertyPermission("java.version", "read");
        GrantPermission caller = new GrantPermission(need);

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH,
                new Permission[]{ need }, caller, false);

        assertEquals(set(new Permission[]{ need }), set(ceiling));
    }

    @Test
    public void safe_admitsWhenCallerGrantIsBroaderWildcard() {
        Permission need = new PropertyPermission("java.version", "read");
        // Caller holds authority over ALL property reads; declared need is one.
        GrantPermission caller =
                new GrantPermission(new PropertyPermission("*", "read"));

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH,
                new Permission[]{ need }, caller, false);

        assertEquals(set(new Permission[]{ need }), set(ceiling));
    }

    /**
     * THE key adversarial probe: a maliciously over-broad PERMISSIONS.LIST must
     * NOT produce a ceiling broader than the caller's own GrantPermission.
     */
    @Test
    public void safe_craftedBroadListCannotExceedCallerGrant() {
        // Caller may only grant a single narrow property read.
        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));

        // Malicious declared needs: filesystem all-access, an unrelated broad
        // property write, and outright AllPermission.
        Permission[] declared = new Permission[]{
            new FilePermission("/", "read,write,execute,delete"),
            new PropertyPermission("*", "read,write"),
            new AllPermission(),
            new PropertyPermission("java.version", "read") // the one legit need
        };

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);

        // Only the one permission the caller could itself grant survives.
        assertEquals(
                set(new Permission[]{ new PropertyPermission("java.version", "read") }),
                set(ceiling));
        // Explicitly: nothing broad leaked through.
        for (Permission p : ceiling) {
            assertFalse("AllPermission must never appear", p instanceof AllPermission);
            assertFalse("FilePermission not grantable by caller",
                    p instanceof FilePermission);
        }
    }

    /**
     * Even when the caller holds AllPermission-level grant authority, a proxy's
     * declared grant-meta / all-authority permissions are excluded from the
     * ceiling (they are policy-grant capabilities, not declared needs).
     */
    @Test
    public void safe_metaPermissionsExcludedEvenUnderAllPermissionCaller() {
        GrantPermission caller = new GrantPermission(new AllPermission());
        Permission legit = new FilePermission("/tmp/work", "read");

        Permission[] declared = new Permission[]{
            new AllPermission(),
            new GrantPermission(new FilePermission("/", "read")),
            legit
        };

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);

        assertEquals(set(new Permission[]{ legit }), set(ceiling));
    }

    /**
     * Unresolved AllPermission/GrantPermission (by target type) must not slip
     * past the class-name meta check.
     */
    @Test
    public void safe_unresolvedMetaPermissionExcluded() {
        GrantPermission caller = new GrantPermission(new AllPermission());
        Permission unresolvedAll = new UnresolvedPermission(
                "java.security.AllPermission", null, null, null);
        Permission legit = new PropertyPermission("user.dir", "read");

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH,
                new Permission[]{ unresolvedAll, legit }, caller, false);

        for (Permission p : ceiling) {
            assertFalse(p instanceof UnresolvedPermission
                    && "java.security.AllPermission".equals(
                            ((UnresolvedPermission) p).getUnresolvedType()));
        }
        assertEquals(set(new Permission[]{ legit }), set(ceiling));
    }

    @Test
    public void safe_nullCallerGrantYieldsEmpty() {
        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH,
                new Permission[]{ new PropertyPermission("java.version", "read") },
                null, false);
        assertEquals(0, ceiling.length);
    }

    @Test
    public void safe_nullDeclaredNeedsYieldsEmpty() {
        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, null,
                new GrantPermission(new AllPermission()), false);
        assertEquals(0, ceiling.length);
    }

    // -------------------------------------------------------- INCONCLUSIVE

    /**
     * The narrowing probe: for identical declared needs and caller grant, the
     * INCONCLUSIVE ceiling (no tolerance) is strictly narrower than SAFE.
     */
    @Test
    public void inconclusive_strictlyNarrowerThanSafe_whenNoTolerance() {
        Permission need = new PropertyPermission("java.version", "read");
        GrantPermission caller = new GrantPermission(need);
        Permission[] declared = new Permission[]{ need };

        Permission[] safe = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);
        Permission[] inconclusive = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.INCONCLUSIVE), HASH, declared, caller,
                /* inconclusiveToleranceGranted */ false);

        assertTrue("SAFE must admit the declared need", safe.length > 0);
        assertEquals("INCONCLUSIVE without tolerance must be empty",
                0, inconclusive.length);
        // Strict subset: inconclusive ⊊ safe.
        assertTrue(set(safe).containsAll(set(inconclusive)));
        assertNotEquals(set(safe), set(inconclusive));
    }

    /**
     * When the per-hash tolerance IS held, INCONCLUSIVE matches the SAFE
     * caller-bounded computation (the tolerance gate is the narrowing).
     */
    @Test
    public void inconclusive_withTolerance_matchesSafeComputation() {
        Permission need = new PropertyPermission("java.version", "read");
        GrantPermission caller = new GrantPermission(need);
        Permission[] declared = new Permission[]{ need };

        Permission[] safe = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);
        Permission[] inconclusive = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.INCONCLUSIVE), HASH, declared, caller, true);

        assertEquals(set(safe), set(inconclusive));
    }

    /**
     * Tolerance does not lift the caller-grant bound: a crafted broad list is
     * still clamped to the caller's authority even for a tolerated INCONCLUSIVE.
     */
    @Test
    public void inconclusive_toleranceStillBoundedByCallerGrant() {
        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] declared = new Permission[]{
            new FilePermission("/", "read,write"),
            new AllPermission(),
            new PropertyPermission("java.version", "read")
        };

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.INCONCLUSIVE), HASH, declared, caller, true);

        assertEquals(
                set(new Permission[]{ new PropertyPermission("java.version", "read") }),
                set(ceiling));
    }

    // ------------------------------------------------- fail-closed / bounds

    @Test(expected = IllegalStateException.class)
    public void dangerous_failsFast() {
        VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.DANGEROUS), HASH,
                new Permission[]{ new PropertyPermission("java.version", "read") },
                new GrantPermission(new AllPermission()), true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullVerdict_rejected() {
        VerdictPermissionMapper.computeSubProcessCeiling(
                null, HASH, new Permission[0],
                new GrantPermission(new AllPermission()), false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyContentHash_rejected() {
        VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), "", new Permission[0],
                new GrantPermission(new AllPermission()), false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullContentHash_rejected() {
        VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), null, new Permission[0],
                new GrantPermission(new AllPermission()), false);
    }

    // ----------------------------------------------------- PERMISSIONS.LIST

    @Test
    public void parseDeclaredNeeds_readsListWithCommentsAndBlanks() throws Exception {
        String list =
                "# a comment\n"
                + "\n"
                + "// another comment\n"
                + "(java.util.PropertyPermission \"java.version\" \"read\")\n"
                + "(java.io.FilePermission \"/tmp/x\" \"read\")\n";
        Permission[] parsed = VerdictPermissionMapper.parseDeclaredNeeds(
                new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)),
                VerdictPermissionMapperTest.class.getClassLoader());

        Set<Permission> expected = set(new Permission[]{
            new PropertyPermission("java.version", "read"),
            new FilePermission("/tmp/x", "read")
        });
        assertEquals(expected, set(parsed));
    }

    /**
     * End-to-end: a crafted PERMISSIONS.LIST declaring AllPermission, parsed via
     * the real parser, still cannot exceed the caller's grant.
     */
    @Test
    public void parseThenCompute_craftedListCannotExceedCallerGrant() throws Exception {
        String malicious =
                "(java.security.AllPermission)\n"
                + "(java.io.FilePermission \"/\" \"read,write,execute,delete\")\n"
                + "(java.util.PropertyPermission \"java.version\" \"read\")\n";
        Permission[] declared = VerdictPermissionMapper.parseDeclaredNeeds(
                new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8)),
                VerdictPermissionMapperTest.class.getClassLoader());

        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));

        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);

        assertEquals(
                set(new Permission[]{ new PropertyPermission("java.version", "read") }),
                set(ceiling));
    }

    @Test
    public void parseDeclaredNeeds_unknownClassBecomesUnresolvedAndIsDropped()
            throws Exception {
        String list = "(com.example.NoSuchPermission \"x\" \"y\")\n";
        Permission[] declared = VerdictPermissionMapper.parseDeclaredNeeds(
                new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)),
                VerdictPermissionMapperTest.class.getClassLoader());
        assertEquals(1, declared.length);
        assertTrue(declared[0] instanceof UnresolvedPermission);

        // A caller that does not hold the (unresolvable) permission cannot grant
        // it: it is dropped from the ceiling.
        GrantPermission caller =
                new GrantPermission(new PropertyPermission("java.version", "read"));
        Permission[] ceiling = VerdictPermissionMapper.computeSubProcessCeiling(
                verdict(VerdictType.SAFE), HASH, declared, caller, false);
        assertEquals(0, ceiling.length);
    }
}
