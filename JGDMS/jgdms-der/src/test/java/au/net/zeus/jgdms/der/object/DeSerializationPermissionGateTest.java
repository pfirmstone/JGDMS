/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.object.fixtures.NestedValue;
import au.net.zeus.jgdms.der.schema.AtomicSerialSchemaRecord;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.apache.river.api.io.DeSerializationPermission;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.security.Permission;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@code DeSerializationPermission("ATOMIC")} gate added to
 * {@link ObjectCodec} (STD-008): before an {@code @AtomicSerial (GetArg)}
 * constructor runs, the classes being decoded must hold the permission.
 *
 * <p>Java&nbsp;21 forbids installing a {@link SecurityManager} at runtime unless
 * the JVM was started with {@code -Djava.security.manager=allow}, so the bulk of
 * the coverage drives the package-private
 * {@link ObjectCodec#checkAtomicDeSerializationPermitted(java.util.Collection, SecurityManager)}
 * seam with a manager passed in directly (no process-wide install). A separate
 * end-to-end test installs a real manager but self-skips when the flag is absent.
 */
@SuppressWarnings("removal") // SecurityManager is deprecated for removal but is the gate's mechanism
class DeSerializationPermissionGateTest {

    /** A SecurityManager that denies only DeSerializationPermission("ATOMIC"). */
    private static final class DenyAtomicSM extends SecurityManager {
        volatile boolean sawAtomicCheck = false;

        private boolean isAtomic(Permission p) {
            return p instanceof DeSerializationPermission && "ATOMIC".equals(p.getName());
        }

        @Override
        public void checkPermission(Permission perm) {
            if (isAtomic(perm)) {
                throw new SecurityException("test: ATOMIC de-serialization denied");
            }
            // everything else is allowed
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            if (isAtomic(perm)) {
                sawAtomicCheck = true;
                throw new SecurityException("test: ATOMIC de-serialization denied");
            }
        }
    }

    /** A SecurityManager that permits everything but records the ATOMIC check. */
    private static final class PermitAllSM extends SecurityManager {
        volatile boolean sawAtomicCheck = false;

        @Override
        public void checkPermission(Permission perm) {
            // allow all
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            if (perm instanceof DeSerializationPermission && "ATOMIC".equals(perm.getName())) {
                sawAtomicCheck = true;
            }
            // allow all
        }
    }

    @Test
    void nullSecurityManager_isNoOp() {
        assertDoesNotThrow(() ->
                ObjectCodec.checkAtomicDeSerializationPermitted(
                        List.of(NestedValue.class), null));
    }

    @Test
    void denyingManager_throwsSecurityException() {
        DenyAtomicSM sm = new DenyAtomicSM();
        assertThrows(SecurityException.class, () ->
                ObjectCodec.checkAtomicDeSerializationPermitted(
                        List.of(NestedValue.class), sm));
        assertTrue(sm.sawAtomicCheck, "the gate must check ATOMIC against the class domains");
    }

    @Test
    void permittingManager_passesAndChecksAtomic() {
        PermitAllSM sm = new PermitAllSM();
        assertDoesNotThrow(() ->
                ObjectCodec.checkAtomicDeSerializationPermitted(
                        List.of(NestedValue.class), sm));
        assertTrue(sm.sawAtomicCheck,
                "even when permitted, the gate must actually perform the ATOMIC check");
    }

    /**
     * End-to-end: a real installed SecurityManager that denies ATOMIC must make
     * {@link ObjectCodec#decode} fail before construction. Self-skips on a JVM not
     * started with {@code -Djava.security.manager=allow}.
     */
    @Test
    void endToEnd_realManagerDeniesDecode() throws Exception {
        NestedValue v = new NestedValue(1, "gate");
        AtomicSerialSchemaRecord schema = SchemaGenerator.generate(NestedValue.class);
        byte[] payload = ObjectCodec.encode(v, NestedValue.class, schema); // encode with no SM

        SecurityManager previous = System.getSecurityManager();
        DenyAtomicSM sm = new DenyAtomicSM();
        boolean installed = false;
        try {
            try {
                System.setSecurityManager(sm);
                installed = true;
            } catch (UnsupportedOperationException e) {
                Assumptions.assumeTrue(false,
                        "skipped: JVM not started with -Djava.security.manager=allow");
            }
            assertThrows(SecurityException.class,
                    () -> ObjectCodec.decode(NestedValue.class, schema, payload),
                    "decode must be denied when the class lacks DeSerializationPermission(ATOMIC)");
        } finally {
            if (installed) {
                System.setSecurityManager(previous);
            }
        }
    }
}
