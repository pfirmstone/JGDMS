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
package org.apache.river.reggie;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lookup.ServiceID;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * AdminProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
final class ConstrainableAdminProxy
    extends AdminProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    private static GetArg check(GetArg arg) throws IOException{
	MethodConstraints constraints = (MethodConstraints) arg.get("constraints", null);
	AdminProxy sup = new AdminProxy(arg);
	verifyConsistentConstraints(constraints, sup.server);
	return arg;
    }
    
    ConstrainableAdminProxy(GetArg arg) throws IOException{
	super(check(arg));
	constraints = (MethodConstraints) arg.get("constraints", null);
    }

    /**
     * Creates new ConstrainableAdminProxy with given server reference, service
     * ID and client constraints.
     */
    ConstrainableAdminProxy(Registrar server,
			    ServiceID registrarID,
			    MethodConstraints constraints)
    {
	super((Registrar) ((RemoteMethodControl) server).setConstraints(
		  translateConstraints(constraints)),
	      registrarID);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableAdminProxy(server, registrarID, constraints);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    public MethodConstraints getConstraints() {
	return constraints;
    }

    /**
     * Returns iterator used by ProxyTrustVerifier to retrieve a trust verifier
     * for this object.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(server);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }

    /**
     * Verifies that the client constraints for this proxy are consistent with
     * those set on the underlying server ref.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	verifyConsistentConstraints(constraints, server);
    }
}
