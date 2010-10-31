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
package com.sun.jini.landlord;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.ObjectInputStream;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.id.Uuid;
import net.jini.id.ReferentUuid;
import com.sun.jini.proxy.ConstrainableProxyUtil;

/**
 * Constrainable sub-class of <code>LandlordLease</code>.
 * Instances of this class can be verified using the
 * <code>LandlordProxyVerifier</code> class.
 * @see LandlordProxyVerifier
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final public class ConstrainableLandlordLease extends LandlordLease 
    implements RemoteMethodControl 
{
    static final long serialVersionUID = 1L;

    /**
     * Returns the public method for the specified <code>Class</code> type,
     * method name, and array of parameter types.
     * <p>
     * This method is typically used in place of {@link Class#getMethod
     * Class.getMethod} to get a method that should definitely be defined;
     * thus, this method throws an error instead of an exception if the
     * given method is missing.
     * <p>
     * This method is convenient for the initialization of a static
     * variable for use as the <code>mappings</code> argument to 
     * {@link com.sun.jini.proxy.ConstrainableProxyUtil#translateConstraints 
     * ConstrainableProxyUtil.translateConstraints}.
     *
     * @param type           the <code>Class</code> type that defines the
     *                       method of interest
     * @param name           <code>String</code> containing the name of the
     *                       method of interest
     * @param parameterTypes the <code>Class</code> types of the parameters
     *                       to the method of interest
     *
     * @return a <code>Method</code> object that provides information about,
     *         and access to, the method of interest
     *
     * @throws <code>NoSuchMethodError</code> if the method of interest 
     *         cannot be found
     * @throws <code>NullPointerException</code> if <code>type</code> or
     *         <code>name</code> is <code>null</code> 
     */
    private static Method getMethod(Class type, String name, 
				    Class[] parameterTypes)
    {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw (Error)
		(new NoSuchMethodError(e.getMessage()).initCause(e));
        }
    }

    /**
     * Convenience field containing the <code>renew</code> method
     * defined in the <code>Lease</code> interface. This field is used in the
     * method mapping arrays and when retrieving method constraints
     * for comparison in <code>canBatch</code>.
     */
    private static final Method renewMethod =
	getMethod(Lease.class, "renew", new Class[] {long.class});

    /**
     * Convenience field containing the <code>cancel</code> method
     * defined in the <code>Lease</code> interface. This field is used in the
     * method mapping arrays and when retrieving method constraints
     * for comparison in <code>canBatch</code>.
     */
    private static final Method cancelMethod =
	getMethod(Lease.class, "cancel", new Class[] {});

    /**
     * Convenience field containing the <code>renewAll</code> method
     * defined in the <code>Landlord</code> interface. This field is
     * used in the method mapping arrays.
     */
    private static final Method renewAllMethod =
	getMethod(Landlord.class, "renewAll",
		  new Class[] {Uuid[].class, long[].class});

    /**
     * Convenience field containing the <code>cancelAll</code> method
     * defined in the <code>Landlord</code> interface. This field is
     * used in the method mapping arrays.
     */
    private static final Method cancelAllMethod =
	getMethod(Landlord.class, "cancelAll", new Class[] {Uuid[].class});

    /**
     * Array containing element pairs in which each pair of elements
     * represents a mapping between two methods having the following
     * characteristics:
     * <ul>
     * <li> the first element in the pair is one of the public, remote
     *      methods that may be invoked by the client through 
     *      <code>Lease</code>.
     * <li> the second element in the pair is the method, implemented
     *      in the backend server class, that is ultimately executed in
     *      the server's backend when the client invokes the corresponding
     *      method in this proxy.
     * </ul>
     */
    private static final Method[] methodMapArray =  {
	renewMethod,
	getMethod(Landlord.class, "renew", 
		  new Class[] {Uuid.class, long.class}),

	cancelMethod,
	getMethod(Landlord.class, "cancel", new Class[] {Uuid.class})
    };

    /**
     * When creating a <code>LeaseMap</code> we generate an implicit
     * set of constraints based on the constraints found on the
     * the initial lease, where <code>Lease.renew</code> maps to
     * <code>Landlord.renewAll</code> and <code>Lease.cancel</code> maps to 
     * <code>Landlord.cancelAll</code>. This array holds this mapping.
     * Mapping also used by <code>ConstrainableLandlordLeaseMap</code> in
     * <code>canContainKey</code>.
     */
    static final Method[] leaseMapMethodMapArray = {
	renewMethod, renewAllMethod,
	cancelMethod, cancelAllMethod
    };

    /**
     * <code>canBatch</code> needs to check if this lease and
     * the passed in lease have compatible constraints, this
     * is the set of methods to compare. Structured so we can
     * use {@link ConstrainableProxyUtil#equivalentConstraints}.
     */
    private static final Method[] comparableMethodsMapArray =  {
	renewMethod, renewMethod,
	cancelMethod, cancelMethod
    };

    /** 
     * Client constraints placed on this proxy (may be <code>null</code>)
     * @serial
     */
    private final MethodConstraints methodConstraints;
    
    /** 
     * Create a new <code>ConstrainableLandlordLease</code>.
     * @param cookie a <code>Uuid</code> that universally and uniquely
     *                 identifies the lease this object is to be a proxy for
     * @param landlord <code>Landlord</code> object that will be used to
     *                 communicate renew and cancel requests to the granter
     *                 of the lease.
     * @param landlordUuid a universally unique id that has been
     *                 assigned to the server granting of the lease.
     *                 Ideally the <code>Uuid</code> {@link
     *                 ReferentUuid#getReferentUuid landlord.getUuid} would
     *                 return if <code>landlord</code> implemented
     *                 {@link ReferentUuid}. Used to determine when
     *                 two leases can be batched together.  
     * @param expiration The initial expiration time of the lease in
     *                 milliseconds since the beginning of the epoch.
     * @param methodConstraints the client method constraints to place on
     *                 this proxy (may be <code>null</code>).
     * @throws NullPointerException if <code>landlord</code>, 
     *                 <code>landlordUuid</code>, or <code>cookie</code>
     *                 is <code>null</code>.
     * @throws ClassCastException if <code>landlord</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    public ConstrainableLandlordLease(Uuid cookie, Landlord landlord, 
	Uuid landlordUuid, long expiration, 
	MethodConstraints methodConstraints)
    {
	super(cookie, constrainServer(landlord, methodConstraints, 
				      methodMapArray), 
	      landlordUuid, expiration);
	this.methodConstraints = methodConstraints;
    }

    /**
     * Returns a copy of the given {@link Landlord} proxy having the
     * client method constraints that result after a specified mapping
     * is applied to the given method constraints. For details on the
     * mapping see {@link ConstrainableProxyUtil#translateConstraints 
     * ConstrainableProxyUtil.translateConstraints}.
     *
     * @param server the proxy to attach constraints to
     * @param constraints the source method constraints
     * @param mapping mapping of methods to methods
     * @throws NullPointerException if <code>server</code> is 
     *         <code>null</code>.
     * @throws ClassCastException if <code>server</code>
     *         does not implement <code>RemoteMethodControl</code>.
     */
    private static Landlord constrainServer(Landlord server,
	MethodConstraints constraints, Method[] mapping)
    {
	final MethodConstraints serverRefConstraints 
	    = ConstrainableProxyUtil.translateConstraints(constraints,
							  mapping);
	final RemoteMethodControl constrainedServer = 
	    ((RemoteMethodControl)server).
	    setConstraints(serverRefConstraints);

	return (Landlord)constrainedServer;
    }

    // doc inherited from super
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
	return new ConstrainableLandlordLease(cookie(), landlord(), 
	    landlordUuid(), expiration, constraints);
    }

    // doc inherited from super
    public MethodConstraints getConstraints() {
	return methodConstraints;
    }

    /** 
     * Returns a proxy trust iterator that is used in 
     * <code>ProxyTrustVerifier</code> to retrieve this object's
     * trust verifier.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
	return new SingletonProxyTrustIterator(landlord());
    }

    private void readObject(ObjectInputStream s)  
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();

	/* basic validation of landlord and cookie were performed by
	 * LandlordLease.readObject(), we just need to verify than
	 * landlord implements RemoteMethodControl and that it has
	 * appropriate constraints.
	 */
	ConstrainableProxyUtil.verifyConsistentConstraints(
            methodConstraints, landlord(), methodMapArray);
    }

    // doc inherited from super
    public boolean canBatch(Lease lease) {
	if (!super.canBatch(lease))
	    return false;	

	// Same landlord, check to see if we have comparable constraints.
	if (!(lease instanceof ConstrainableLandlordLease))
	    return false;

	final MethodConstraints lmc = 
	    ((ConstrainableLandlordLease)lease).methodConstraints;
	
	return 
	    ConstrainableProxyUtil.equivalentConstraints(
		methodConstraints, lmc, comparableMethodsMapArray);
    }

    // doc inherited from super
    public LeaseMap createLeaseMap(long duration) {
	return new ConstrainableLandlordLeaseMap(
	    constrainServer(landlord(), methodConstraints, 
			    leaseMapMethodMapArray),
	    landlordUuid(), this, duration);
    }
}

