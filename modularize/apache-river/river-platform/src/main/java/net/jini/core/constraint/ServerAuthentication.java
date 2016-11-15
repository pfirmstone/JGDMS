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

package net.jini.core.constraint;

import java.io.IOException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Represents a constraint on authentication of the server to the client.
 * <p>
 * Network authentication by a server (to a client) is implementation-specific,
 * but typically is also scoped and controlled by a <code>Subject</code>. The
 * server subject normally is contained in the
 * {@link net.jini.export.Exporter} used to export that remote
 * object and is taken from the current thread when the exporter is
 * constructed. However, a server might use one subject to control its local
 * execution and a different subject to control its network authentication.
 * A server generally must have permission (such as
 * {@link net.jini.security.AuthenticationPermission}) to authenticate itself
 * to clients.
 * <p>
 * It is important to understand that specifying
 * <code>ServerAuthentication.YES</code> as a requirement <i>does not</i>
 * ensure that a server is to be trusted; it <i>does</i> ensure that the
 * server authenticates itself as someone, but it does not ensure that the
 * server authenticates itself as anyone in particular. Without knowing who
 * the server authenticated itself as, there is no basis for actually
 * trusting the server. The client generally needs to specify a
 * {@link ServerMinPrincipal} requirement in addition, or else verify
 * that the server has specified a satisfactory
 * <code>ServerMinPrincipal</code> requirement for each of the methods that
 * the client cares about.
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see ServerMinPrincipal
 * @see net.jini.security.AuthenticationPermission
 * @since 2.0
 */
@AtomicSerial
public final class ServerAuthentication
				implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 2837982503744131014L;

    /**
     * Authenticate the server to the client. The mechanisms and credentials
     * used to authenticate the server are not specified by this constraint.
     */
    public static final ServerAuthentication YES =
					new ServerAuthentication(true);
    /**
     * Do not authenticate the server to the client, so that the server
     * remains anonymous.
     */
    public static final ServerAuthentication NO =
					new ServerAuthentication(false);

    /**
     * <code>true</code> for <code>YES</code>, <code>false</code> for
     * <code>NO</code>
     *
     * @serial
     */
    private final boolean val;
    
    public ServerAuthentication(GetArg arg) throws IOException{
	this(arg.get("val", true));
    }

    /**
     * Simple constructor.
     *
     * @param val <code>true</code> for <code>YES</code>, <code>false</code>
     * for <code>NO</code>
     */
    private ServerAuthentication(boolean val) {
	this.val = val;
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return val ? "ServerAuthentication.YES" : "ServerAuthentication.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     * @return true for YES, false for NO.
     */
    private Object readResolve() {
	return val ? YES : NO;
    }
}
