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
 * Represents a constraint on delegation from the client to the server.
 * If the client delegates to the server, the server receives credentials
 * which allow the server to authenticate using the client's identity in
 * subsequent remote calls that the server makes or receives. (The credentials
 * should at least be sufficient for the authentication mechanism used
 * between the original client and server; they might not be sufficient if a
 * different mechanism is used when exercising the delegation.)
 * <p>
 * The use of an instance of this constraint does not directly imply a
 * {@link ClientAuthentication#YES} constraint; that must be specified
 * separately to ensure that the client actually authenticates itself.
 * <p>
 * Serialization for this class is guaranteed to produce instances that are
 * comparable with <code>==</code>.
 *
 * @author Sun Microsystems, Inc.
 * @see ClientAuthentication
 * @see DelegationAbsoluteTime
 * @see DelegationRelativeTime
 * @see net.jini.io.context.ClientSubject
 * @see net.jini.export.ServerContext
 * @since 2.0
 */
@AtomicSerial
public final class Delegation implements InvocationConstraint, Serializable {
    private static final long serialVersionUID = -8636854709107393245L;

    /**
     * If the client authenticates to the server, then delegate from the
     * client to the server, such that the server receives credentials which
     * allow it to authenticate using the
     * client's identity in subsequent remote calls that the server makes
     * or receives. The mechanisms and credentials used to support delegation
     * are not specified by this constraint. Note that, because this
     * constraint is conditional on client authentication, it does not
     * conflict with {@link ClientAuthentication#NO}.
     */
    public static final Delegation YES = new Delegation(true);
    /**
     * Do not delegate from the client to the server, such that the server
     * does not receive credentials which would allow it to authenticate using
     * the client's identity.
     */
    public static final Delegation NO = new Delegation(false);

    /**
     * <code>true</code> for <code>YES</code>, <code>false</code> for
     * <code>NO</code>
     *
     * @serial
     */
    private final boolean val;

    /**
     * Simple constructor.
     *
     * @param val <code>true</code> for <code>YES</code>, <code>false</code>
     * for <code>NO</code>
     */
    private Delegation(boolean val) {
	this.val = val;
    }
    
    public Delegation(GetArg arg) throws IOException{
	this(arg.get("val", false));
    }

    /**
     * Returns a string representation of this object.
     */
    public String toString() {
	return val ? "Delegation.YES" : "Delegation.NO";
    }

    /**
     * Canonicalize so that <code>==</code> can be used.
     * @return true for YES, false for NO.
     */
    public Object readResolve() {
	return val ? YES : NO;
    }
}
