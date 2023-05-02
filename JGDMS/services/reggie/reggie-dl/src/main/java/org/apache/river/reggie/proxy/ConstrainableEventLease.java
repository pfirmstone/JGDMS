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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * EventLease subclass that supports constraints.
 *
 * @author Sun Microsystems, Inc.
 *
 */
@AtomicSerial
public final class ConstrainableEventLease
    extends EventLease implements RemoteMethodControl
{
    private static final long serialVersionUID = 2L;

    /** Mappings between Lease and Registrar methods */
    private static final Method[] methodMappings = {
	Util.getMethod(Lease.class, "cancel", new Class[0]),
	Util.getMethod(Registrar.class, "cancelEventLease",
		       new Class[]{ long.class, Uuid.class }),

	Util.getMethod(Lease.class, "renew", new Class[]{ long.class }),
	Util.getMethod(Registrar.class, "renewEventLease",
		       new Class[]{ long.class, Uuid.class, long.class })
    };
    
    private static final String CONSTRAINTS = "constraints";
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm(CONSTRAINTS, MethodConstraints.class)
        };
    }
    
    public static void serialize(PutArg arg, ConstrainableEventLease cel) throws IOException{
        arg.put(CONSTRAINTS, cel.constraints);
        arg.writeArgs();
    }

    /**
     * Verifies that the client constraints for this proxy are consistent with
     * those set on the underlying server ref.
     */
    public static void verifyConsistentConstraints(
	MethodConstraints constraints, Object proxy) throws InvalidObjectException {
	ConstrainableProxyUtil.verifyConsistentConstraints(
	    constraints, proxy, methodMappings);
    }
    
    static MethodConstraints reverseTranslateConstraints(MethodConstraints constraints) {
	return ConstrainableProxyUtil.reverseTranslateConstraints(
		constraints, methodMappings);
    }



    /** Client constraints for this proxy, or null */
    private final MethodConstraints constraints;

    /**
     * The server proxy will have been downloaded first and already have 
     * constraints applied by AtomicMarshalInputStream.
     * @param arg
     * @return
     * @throws IOException 
     */
    private static MethodConstraints check(GetArg arg) throws IOException{
	MethodConstraints constraints = (MethodConstraints) arg.get(CONSTRAINTS, null);
	EventLease el = new EventLease(arg);
	MethodConstraints proxyCon = null;
	if (el.server instanceof RemoteMethodControl && 
	    (proxyCon = ((RemoteMethodControl)el.server).getConstraints()) != null) {
	    // Constraints set during proxy deserialization.
	    return reverseTranslateConstraints(proxyCon);
	}
	verifyConsistentConstraints(constraints, el.server);
	return constraints;
    }
    
    ConstrainableEventLease(GetArg arg) throws IOException{
	this(arg, check(arg));
    }
    
    ConstrainableEventLease(GetArg arg, MethodConstraints constraints) throws IOException{
	super(arg);
	this.constraints = constraints;
    }

    /**
     * Creates new ConstrainableEventLease with given server reference, event
     * and lease IDs, expiration time and client constraints.
     */
    ConstrainableEventLease(Registrar server,
			    ServiceID registrarID,
			    long eventID,
			    Uuid leaseID,
			    long expiration,
			    MethodConstraints constraints,
			    boolean setConstraints)
    {
	super( setConstraints ? (Registrar) ((RemoteMethodControl) server).setConstraints(
		  ConstrainableProxyUtil.translateConstraints(
		      constraints, methodMappings)) : server,
	      registrarID,
	      eventID,
	      leaseID,
	      expiration);
	this.constraints = constraints;
    }

    /**
     * Creates a constraint-aware lease map.
     */
    public LeaseMap<? extends Lease,Long> createLeaseMap(long duration) {
	return new ConstrainableRegistrarLeaseMap(this, duration);
    }

    /**
     * Two leases can be batched if they are both RegistrarLeases, share the
     * same server, and have compatible constraints.
     */
    public boolean canBatch(Lease lease) {
	if (!(super.canBatch(lease) && lease instanceof RemoteMethodControl)) {
	    return false;
	}
	return ConstrainableProxyUtil.equivalentConstraints(
	    ((RemoteMethodControl) lease).getConstraints(),
	    ConstrainableProxyUtil.translateConstraints(
		constraints, ConstrainableRegistrarLeaseMap.methodMappings),
	    ConstrainableRegistrarLeaseMap.methodMappings);
    }

    // javadoc inherited from RemoteMethodControl.setConstraints
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableEventLease(
	    server, registrarID, eventID, leaseID, getExpiration(), constraints, true);
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
