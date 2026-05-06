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
package net.jini.jeri.ssl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.security.auth.Subject;

/**
 * Process-wide fallback registry for a SPIFFE-managed {@link Subject}.
 *
 * <p>When a JVM uses {@link SpiffeCredentialManager} without wrapping every
 * outbound/inbound JERI call in {@code Subject.doAs()}, the SSL endpoint
 * implementations cannot locate the active Subject through the normal
 * {@code Subject.getSubject(AccessControlContext)} mechanism.  This class
 * provides a last-resort fallback: {@link SpiffeCredentialManager#start()}
 * registers the managed Subject here, and the SSL endpoint implementations
 * consult it when the ACC-based lookup returns {@code null}.
 *
 * <p>Only one Subject may be registered at a time (JGDMS's 1-SVID-per-host
 * model).  The reference is cleared by {@link SpiffeCredentialManager#close()}.
 *
 * <p>This class is package-private; it is an implementation detail of
 * {@code net.jini.jeri.ssl} and carries no public API surface.
 */
final class SpiffeSubjectHolder {

    private static final AtomicReference<Subject> PROCESS_SUBJECT =
            new AtomicReference<>();

    private static final Logger logger =
            Logger.getLogger(SpiffeSubjectHolder.class.getName());

    /** Not instantiable. */
    private SpiffeSubjectHolder() { }

    /**
     * Registers {@code subject} as the process-wide SPIFFE Subject.
     *
     * <p>SPIFFE maps one SVID to one process; only one
     * {@link SpiffeCredentialManager} may be active per JVM.  If a
     * non-null Subject is already registered by a different manager this
     * method logs a WARNING and throws {@link IllegalStateException}.
     *
     * @param subject the Subject to register; must not be {@code null}
     * @throws IllegalStateException if a different Subject is already registered
     */
    static void set(Subject subject) {
        // Atomic check-and-set: fail if a *different* non-null Subject is
        // already registered.  compareAndSet succeeds only when the current
        // value is null; if it fails we re-read to check whether the current
        // holder is the same instance (idempotent re-registration is benign).
        if (!PROCESS_SUBJECT.compareAndSet(null, subject)) {
            Subject current = PROCESS_SUBJECT.get();
            if (current != subject) {
                String msg = "SpiffeSubjectHolder: a SPIFFE Subject is already "
                        + "registered for this JVM.  Only one SpiffeCredentialManager "
                        + "may be active per JVM (one SPIFFE workload identity per "
                        + "process).  Close the existing manager before starting a "
                        + "new one.";
                logger.warning(msg);
                throw new IllegalStateException(msg);
            }
            // current == subject: same instance, nothing to do.
        }
    }

    /**
     * Clears the registered Subject if it is the same instance as
     * {@code owner}.  This is called by {@link SpiffeCredentialManager#close()}
     * to release the process-wide registration.  If another manager has
     * somehow replaced the holder in the meantime the clear is silently
     * skipped.
     *
     * @param owner the Subject to deregister; the holder is only cleared if
     *              it currently holds this exact instance
     */
    static void clear(Subject owner) {
        PROCESS_SUBJECT.compareAndSet(owner, null);
    }

    /**
     * Returns the currently registered SPIFFE Subject, or {@code null} if
     * none has been registered (or it has been cleared).
     *
     * @return the registered Subject, or {@code null}
     */
    static Subject get() {
        return PROCESS_SUBJECT.get();
    }
}
