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

import java.io.IOException;
import java.nio.channels.ByteChannel;

/**
 * Seam that actually spawns an isolated OS subprocess and stands up its
 * {@link SubProcessAdministrable} surface (task&nbsp;T2, requirement&nbsp;#1 /
 * #3).  Kept behind an interface so the pool logic, admin-authority binding,
 * reject-on-load, and DGC lifecycle are unit-testable with an in-process fake,
 * and so the real OS-process + Unix-Domain-Socket bring-up (task&nbsp;T4) can
 * be dropped in without touching the pool.
 *
 * @since 3.1.1
 */
public interface SubProcessLauncher {

    /**
     * Spawns (or provisions) the isolated subprocess for {@code key} and
     * returns a handle to its management surface and shutdown hook.
     *
     * @param key the canonical pooling key naming the per-principal pool slot
     * @return the spawned subprocess resources
     * @throws IOException if the subprocess cannot be spawned
     */
    Spawned launch(IsolationPoolingKey key) throws IOException;

    /** Resources produced by a successful {@link #launch}. */
    interface Spawned {
        /**
         * @return the subprocess's authenticated administrative surface; its
         *         {@code getSubProcessPolicyAdmin()} is fail-closed
         */
        SubProcessAdministrable adminSurface();

        /**
         * Opens a fresh, dedicated business/wire-handoff channel to this
         * subprocess (task&nbsp;T4, {@link SubProcessWireHandoff}).  One
         * channel is opened <em>per hosted-object handoff</em> -- it carries
         * exactly one {@code REQUEST}/{@code REPLY} handoff exchange
         * (see {@code SubProcessReconstructionServer}'s wire-framing spec)
         * followed by that hosted object's business
         * {@code INVOKE_REQUEST}/{@code INVOKE_REPLY} traffic for the
         * lifetime of the resulting thin client-side stub.  Deliberately
         * <strong>separate</strong> from {@link #adminSurface()}'s channel
         * (S1 of the wiring SOW): a caller holding this channel proves
         * nothing about admin authority, and vice versa.
         *
         * <p>Default implementation fails closed with
         * {@link UnsupportedOperationException} until a real launcher
         * supplies a live channel to the spawned subprocess (mirrors
         * {@link UnsupportedSubProcessLauncher}); kept as a {@code default}
         * method so existing {@link Spawned} test fakes that pre-date task
         * T4 keep compiling unchanged.
         *
         * @return a freshly-opened, connected channel to the subprocess
         * @throws IOException if the channel cannot be opened
         */
        default ByteChannel openWireChannel() throws IOException {
            throw new UnsupportedOperationException(
                "Subprocess wire-handoff channel is not yet operational:"
                + " the real OS-process + Unix-Domain-Socket launcher is"
                + " a follow-up to task T4 (SubProcessWireHandoff).");
        }

        /** Terminates the subprocess and releases its resources. */
        void shutdown();
    }

    /**
     * Production default until task&nbsp;T4 supplies the real OS-process +
     * UDS launcher: every call fails closed with
     * {@link UnsupportedOperationException}.  With the isolation flag on and a
     * real smart proxy to isolate, this is the expected outcome while the
     * subprocess transport is not yet built &mdash; never a silent in-process
     * fallback.
     */
    final class UnsupportedSubProcessLauncher implements SubProcessLauncher {
        @Override
        public Spawned launch(IsolationPoolingKey key) {
            throw new UnsupportedOperationException(
                "Isolated subprocess spawning is not yet operational: the real"
                + " OS-process + Unix-Domain-Socket launcher is task T4."
                + " Pooling key: " + key);
        }
    }
}
