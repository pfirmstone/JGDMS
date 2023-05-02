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
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * AdminProxy subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ConstrainableAdminProxy
    extends AdminProxy implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;
    
    private static final String CONSTRAINTS = "constraints";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(CONSTRAINTS, MethodConstraints.class)
        };
    }
    
    public static void serialize(PutArg arg, ConstrainableAdminProxy cap) throws IOException{
        arg.put(CONSTRAINTS, cap.constraints);
        arg.writeArgs();
    }

    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    private static MethodConstraints checkConstraints(GetArg arg) 
	    throws IOException, ClassNotFoundException{
	MethodConstraints constraints = arg.get(CONSTRAINTS, null, MethodConstraints.class);
	AdminProxy sup = new AdminProxy(arg);
	MethodConstraints proxyCon = null;
	if (sup.server instanceof RemoteMethodControl && 
	    (proxyCon = ((RemoteMethodControl)sup.server).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return reverseTranslateConstraints(proxyCon);
	}
	verifyConsistentConstraints(constraints, sup.server);
	return constraints;
    }
    
    public ConstrainableAdminProxy(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg, checkConstraints(arg));
    }
    
    ConstrainableAdminProxy(GetArg arg, MethodConstraints constraints) 
	    throws IOException, ClassNotFoundException{
	super(arg);
	this.constraints = constraints;
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
