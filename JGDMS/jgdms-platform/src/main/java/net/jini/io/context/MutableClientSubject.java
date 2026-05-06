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
 * Extension of {@link ClientSubject} that formerly allowed the invocation
 * dispatcher to merge user principals into the server-side client Subject.
 *
 * <p><b>Deprecated approach:</b> the old design merged the client's
 * <em>user</em> principals (from {@code Subject.callAs} / wire protocol
 * version {@code 0x02}) into the <em>worker</em> principals (from TLS),
 * producing a single merged Subject.  This was wrong for virtual-thread
 * architectures: the merged Subject would replace the server-process ACC,
 * causing virtual threads to inherit the <em>client's</em> identity rather
 * than the server's worker identity.</p>
 *
 * <p><b>Current approach:</b> the dispatcher now keeps the two Subjects
 * completely separate:
 * <ul>
 *   <li>The TLS-authenticated <em>worker Subject</em> is placed in the
 *       {@code AccessControlContext} via {@code Subject.doAs} so that virtual
 *       threads inherit the server process identity.</li>
 *   <li>The user-forwarded <em>user Subject</em> is established only for the
 *       dispatch thread via {@code Subject.callAs} (ScopedValue) and is
 *       accessible through {@link ClientUserSubject} in the server context.</li>
 * </ul>
 * {@link #mergeUserPrincipals} is no longer called by the dispatcher.
 *
 * @see ClientSubject
 * @see ClientUserSubject
 * @see net.jini.export.ServerContext#getServerContextElement
 * @since 3.1
 */
public interface MutableClientSubject extends ClientSubject {

    /**
     * @deprecated The dispatcher no longer merges user principals into the
     *     worker Subject.  User principals are now assembled into a separate
     *     read-only Subject accessible via {@link ClientUserSubject} in the
     *     server context.  This method is retained for API backward
     *     compatibility but is no longer invoked by
     *     {@code BasicInvocationDispatcher}.
     *
     * @param userPrincipals the user principals transmitted in the request
     *        header; must not be {@code null}
     * @throws NullPointerException if {@code userPrincipals} is {@code null}
     */
    @Deprecated
    void mergeUserPrincipals(Set<? extends Principal> userPrincipals);
}
