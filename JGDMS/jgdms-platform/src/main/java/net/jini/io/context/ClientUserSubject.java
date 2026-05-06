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
 * Server context element that carries the authenticated <em>user</em> Subject
 * separately from the server-process <em>worker</em> Subject.
 *
 * <p>On the client side there are two distinct identities:
 * <ul>
 *   <li>The <em>worker Subject</em> — the client process identity, established
 *       via {@code Subject.doAsPrivileged} and used for TLS authentication.
 *       Its principals travel to the server through the TLS certificate chain
 *       and are available via
 *       {@link ClientSubject#getClientSubject() ClientSubject.getClientSubject()}.</li>
 *   <li>The <em>user Subject</em> — the human user identity, established on
 *       the client via {@code Subject.callAs} and transmitted in-band in the
 *       JERI request header (wire protocol version {@code 0x02}).  Its
 *       principals are available via this interface.</li>
 * </ul>
 *
 * <p>On the server side the dispatcher reconstructs both Subjects and runs the
 * invocation as:
 * <pre>
 *   Subject.doAs(workerSubject, () -&gt; {        // ACC; virtual threads inherit
 *       Subject.callAs(userSubject, () -&gt; {    // ScopedValue; dispatch thread only
 *           invoke(...)
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
     * Returns the user Subject assembled from the principals transmitted in
     * the JERI request header, or {@code null} if no user principals were
     * present in the request.
     *
     * <p>The returned Subject is read-only (i.e., {@code Subject.isReadOnly()
     * == true}) and contains no credentials.
     *
     * @return the user Subject, or {@code null}
     */
    Subject getUserSubject();
}
