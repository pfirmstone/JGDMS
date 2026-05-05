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

    /** Not instantiable. */
    private SpiffeSubjectHolder() { }

    /**
     * Registers {@code subject} as the process-wide SPIFFE Subject.
     * Pass {@code null} to clear the registration.
     *
     * @param subject the Subject to register, or {@code null} to deregister
     */
    static void set(Subject subject) {
        PROCESS_SUBJECT.set(subject);
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
