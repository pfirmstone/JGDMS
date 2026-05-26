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

import net.jini.io.context.ContextPermission;

/**
 * A client context element that supplies the authenticated server Subject
 * from the underlying transport layer (e.g. TLS).
 *
 * <p>This interface is added to the {@link java.util.Collection} context
 * passed to {@link net.jini.loader.pref.PreferredProxyCodebaseProvider#resolve
 * resolve} so that the codebase provider can scope permission grants to the
 * actual server identity that was verified during the TLS handshake, rather
 * than relying on client-declared {@code ServerMinPrincipal} constraints.
 *
 * <p>The returned Subject is read-only and contains only the principals
 * extracted from the server's TLS certificate: an {@code X500Principal} and,
 * when the certificate carries a SPIFFE URI Subject Alternative Name, a
 * corresponding {@code SpiffePrincipal}.  It carries no credentials.
 *
 * @see net.jini.io.context.ClientSubject
 * @since 3.1
 */
public interface ServerSubject {

    /**
     * Returns the authenticated identity of the remote server as a read-only
     * {@link Subject}, or {@code null} if the server was not authenticated
     * (e.g., the transport does not support server authentication).
     *
     * @return a read-only Subject containing the server's TLS-authenticated
     *         principals, or {@code null} if the server is anonymous
     *
     * @throws SecurityException if a security manager exists and its
     * {@code checkPermission} method invoked with the permission
     * {@link ContextPermission}{@code ("net.jini.io.context.ServerSubject.getServerSubject")}
     * throws a {@code SecurityException}
     */
    Subject getServerSubject();
}
