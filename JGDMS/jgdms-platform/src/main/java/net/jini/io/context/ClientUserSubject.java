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

import javax.security.auth.Subject;

/**
 * Server context element that carries the authenticated <em>user</em> Subject(s)
 * separately from the server-process <em>worker</em> Subject.
 *
 * <p>On the client side there are two distinct identities:
 * <ul>
 *   <li>The <em>worker Subject</em> — the client process identity, established
 *       via {@code Subject.doAsPrivileged} and used for TLS authentication.
 *       Its principals travel to the server through the TLS certificate chain
 *       and are available via
 *       {@link ClientSubject#getClientSubject() ClientSubject.getClientSubject()}.</li>
 *   <li>The <em>user Subject(s)</em> — the human user identit(ies), established
 *       on the client via {@code Subject.callAs} and transmitted in-band in the
 *       JERI request header (wire protocol version {@code 0x02}).  When multiple
 *       user Subjects are present on the client (e.g. a multi-party transaction
 *       context established via the DirtyChai {@code Subject.currentAll()} array),
 *       all of them are transmitted and available via {@link #getUserSubjects()}.
 *       The first (outermost) Subject is also accessible via
 *       {@link #getUserSubject()} for single-subject callers.</li>
 * </ul>
 *
 * <p>On the server side the dispatcher reconstructs all Subjects and runs the
 * invocation with each user Subject nested inside the previous via
 * {@code Subject.callAs}:
 * <pre>
 *   Subject.doAs(workerSubject, () -&gt; {          // ACC; virtual threads inherit
 *       Subject.callAs(userSubjects[0], () -&gt; {  // outermost user Subject
 *           Subject.callAs(userSubjects[1], () -&gt; { // next user Subject, if any
 *               ...
 *               invoke(...)
 *           });
 *       });
 *   });
 * </pre>
 *
 * <p>The user principals are <em>asserted</em> by the authenticated client
 * worker; they are not independently verified by TLS.  Callers should
 * therefore trust them only to the extent they trust the authenticated worker
 * identity.
 *
 * <p>An instance of this interface is placed in the server context by the
 * invocation dispatcher when user principals are present in the incoming
 * request.  Retrieve it with:
 * <pre>
 *   ClientUserSubject cus = (ClientUserSubject)
 *       ServerContext.getServerContextElement(ClientUserSubject.class);
 * </pre>
 *
 * @see ClientSubject
 * @see net.jini.export.ServerContext#getServerContextElement
 * @since 3.1
 */
public interface ClientUserSubject {

    /**
     * Returns all user Subjects assembled from the principals transmitted in
     * the JERI request header.  The array is ordered outermost-first, matching
     * the order in which the client established them via {@code Subject.callAs}.
     *
     * <p>Each Subject is read-only ({@code Subject.isReadOnly() == true}) and
     * contains no credentials.
     *
     * @return a non-null, non-empty array of user Subjects; never {@code null}
     *         and never empty when this context element is present
     */
    Subject[] getUserSubjects();

    /**
     * Returns the first (outermost) user Subject, or {@code null} if no user
     * principals were present in the request.
     *
     * <p>Convenience method for single-Subject callers; equivalent to
     * {@code getUserSubjects()[0]} when the array is non-empty.
     *
     * <p>The returned Subject is read-only ({@code Subject.isReadOnly() == true})
     * and contains no credentials.
     *
     * @return the first user Subject, or {@code null}
     */
    default Subject getUserSubject() {
        Subject[] subjects = getUserSubjects();
        return subjects.length > 0 ? subjects[0] : null;
    }
}
