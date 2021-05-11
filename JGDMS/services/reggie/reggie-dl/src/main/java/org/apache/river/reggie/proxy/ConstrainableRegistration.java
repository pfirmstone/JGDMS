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
package org.apache.river.reggie.proxy;

import org.apache.river.proxy.ConstrainableProxyUtil;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Registration subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ConstrainableRegistration
    extends Registration implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;
    
    private static final String CONSTRAINTS = "constriants";
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm(CONSTRAINTS, MethodConstraints.class)
        };
    }
    
    public static void serialize(PutArg arg, ConstrainableRegistration cr) throws IOException{
        arg.put(CONSTRAINTS, cr.constraints);
        arg.writeArgs();
    }

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    private static MethodConstraints check(GetArg arg) throws IOException {
	MethodConstraints constraints = (MethodConstraints) arg.get("constraints", null);
	Registration reg = new Registration(arg);
	MethodConstraints proxyCon = null;
	if (reg.server instanceof RemoteMethodControl && 
	    (proxyCon = ((RemoteMethodControl)reg.server).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return ConstrainableProxyUtil.reverseTranslateConstraints(
		    proxyCon, methodMappings);
	}
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, reg.server, methodMappings);
	return constraints;
    }
   
    ConstrainableRegistration(GetArg arg) throws IOException {
	this(arg, check(arg));
    }
    
    ConstrainableRegistration(GetArg arg, MethodConstraints constraints) throws IOException{
	super(arg);
	this.constraints = constraints;
    }

    /**
     * Creates new ConstrainableRegistration with given server reference,
     * service lease and client constraints.
     */
    ConstrainableRegistration(  Registrar server,
			      ServiceLease lease,
				MethodConstraints constraints,
				boolean setConstraints)
    {
	super( setConstraints ? (Registrar) ((RemoteMethodControl) server).setConstraints(
		  translateConstraints(constraints)): server,lease);
	this.constraints = constraints;
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    @Override
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableRegistration(server, lease, constraints, true);
    }

    // javadoc inherited from RemoteMethodControl.getConstraints
    @Override
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
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, server, methodMappings);
    }
}
