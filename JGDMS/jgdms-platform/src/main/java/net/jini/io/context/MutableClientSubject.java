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

package net.jini.io.context;

import java.security.Principal;
import java.util.Set;

/**
 * Extension of {@link ClientSubject} that allows the invocation dispatcher to
 * merge user principals (transmitted in-band in the JERI request body) into the
 * server-side client Subject.
 *
 * <p>When a client runs with both a <em>worker</em> Subject (established via
 * {@code Subject.doAsPrivileged} and used for TLS) and a <em>user</em> Subject
 * (established via {@code Subject.callAs} and carried in the request header),
 * the dispatcher calls {@link #mergeUserPrincipals} to produce a merged
 * read-only Subject that {@link #getClientSubject} subsequently returns.
 *
 * <p>The principals supplied to {@code mergeUserPrincipals} are
 * <em>asserted</em> by the authenticated client; they are not independently
 * verified by TLS.  Callers should therefore trust them only to the extent
 * they trust the authenticated worker identity.
 *
 * @see ClientSubject
 * @see net.jini.export.ServerContext#getServerContextElement
 * @since 3.1
 */
public interface MutableClientSubject extends ClientSubject {

    /**
     * Merges the given user principals with the existing TLS-verified worker
     * principals and replaces the Subject returned by
     * {@link #getClientSubject} with a new, read-only merged Subject.
     *
     * <p>The merged Subject contains all principals from the original worker
     * Subject together with every principal in {@code userPrincipals}.  Its
     * credential sets are the union of both source Subjects' credentials.
     * The result is read-only ({@code Subject.isReadOnly() == true}).
     *
     * <p>This method is called at most once per dispatched request, before
     * any service code is invoked, so implementations are not required to be
     * thread-safe with respect to concurrent updates.
     *
     * @param userPrincipals the user principals transmitted in the request
     *        header; must not be {@code null}
     * @throws NullPointerException if {@code userPrincipals} is {@code null}
     */
    void mergeUserPrincipals(Set<? extends Principal> userPrincipals);
}
