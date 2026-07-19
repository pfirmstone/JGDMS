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
import java.net.URL;
import java.util.Collection;
import net.jini.export.CodebaseAccessor;
import net.jini.io.MarshalledInstance;

/**
 * Extension seam for the byte/framing-level {@code MarshalledInstance} handoff
 * over the Unix-Domain-Socket to an isolated subprocess &mdash; explicitly the
 * job of sibling task&nbsp;T4, not task&nbsp;T2.  T2 owns the pool, admin
 * surface, and lifecycle up to a spawned/reused {@link SubProcessHandle}; it
 * then calls this seam to perform the wire handoff and obtain the thin
 * client-side stub.  Mirrors the way task&nbsp;T1 left this task an
 * {@code UnsupportedOperationException} placeholder rather than pre-inventing
 * downstream work.
 *
 * @since 3.1.1
 */
public interface SubProcessWireHandoff {

    /**
     * Drives the codebase load + proxy unmarshal inside {@code handle}'s
     * subprocess and returns the thin client-side stub.  The subprocess is
     * responsible for running {@link HostedProxyGuard} on the resolved proxy
     * before export.
     *
     * @param handle         the (spawned or reused) subprocess for the peer
     * @param bootstrapProxy the codebase accessor for the remote service
     * @param serviceProxy   the marshalled service proxy to unmarshal remotely
     * @param codebase       the resolved codebase URLs
     * @param path           the codebase annotation string
     * @param parent         the parent (stream) class loader
     * @param verifier       the integrity verifier class loader
     * @param context        the unmarshalling stream context collection
     * @return the thin client-side stub to return from {@code resolve}
     * @throws IOException            on communication / handoff failure
     * @throws ClassNotFoundException if a required class cannot be resolved
     */
    Object handoff(SubProcessHandle handle,
                   CodebaseAccessor bootstrapProxy,
                   MarshalledInstance serviceProxy,
                   URL[] codebase,
                   String path,
                   ClassLoader parent,
                   ClassLoader verifier,
                   Collection context)
            throws IOException, ClassNotFoundException;

    /**
     * Default until task&nbsp;T4 lands: fails closed with
     * {@link UnsupportedOperationException}.
     */
    final class UnsupportedSubProcessWireHandoff implements SubProcessWireHandoff {
        @Override
        public Object handoff(SubProcessHandle handle,
                              CodebaseAccessor bootstrapProxy,
                              MarshalledInstance serviceProxy,
                              URL[] codebase, String path,
                              ClassLoader parent, ClassLoader verifier,
                              Collection context) {
            throw new UnsupportedOperationException(
                "Subprocess wire handoff is not yet operational: the"
                + " Unix-Domain-Socket MarshalledInstance handoff is task T4."
                + " Subprocess pooling key: "
                + (handle == null ? "<none>" : handle.key()));
        }
    }
}
