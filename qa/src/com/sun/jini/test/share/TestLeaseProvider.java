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


package com.sun.jini.test.share;

// java.rmi
import java.rmi.RemoteException;

// com.sun.jini
import com.sun.jini.test.share.LeaseOwner;
import com.sun.jini.test.share.LeaseBackEndImpl;
import com.sun.jini.test.share.TestLease;


/**
 * This class provides leases for testing purposes.  Although this
 * class will provide normal Lease objects its primary purpose is to
 * provide Lease Objects which will behave badly when their methods
 * are invoked.
 *
 * This class uses the Facade Pattern to unify and simplify the set of
 * "test" lease classes written by John McClain. These classes can be
 * found in the com.sun.jini.test.share package.
 * 
 *
 * 
 */
public class TestLeaseProvider {

    /**
     * The grantor of the lease. 
     */
    private LeaseBackEndImpl leaseBackEnd = null;

    /**
     * Constructor taking a single argument.
     * 
     * @param leaseCount  the maximum number of leases to grant.
     * 
     * @exception RemoteException
     *          when construction of the Landlord fails. 
     * 
     */
    public TestLeaseProvider(int leaseCount) throws RemoteException {
	leaseBackEnd = new LeaseBackEndImpl(leaseCount);
    }

    /**
     * Constructor allowing the use of TestLeaseFactory subclass.
     * 
     * @param leaseCount  the maximum number of leases to grant.
     * @param factoryClass  the class of the object that creates TestLeases
     * 
     * @exception RemoteException
     *          when construction of the Landlord fails. 
     */
    public TestLeaseProvider(int leaseCount, Class factoryClass) 
	       throws RemoteException {
	leaseBackEnd = new LeaseBackEndImpl(leaseCount, factoryClass);
    }

    /**
     * Return a TestLease.
     * 
     * <P>Notes:</P><BR> The constructor for LeaseBackEndImpl (the grantor
     * of TestLeases) takes an int which is the max number of leases it
     * will create. Be aware of this when writing tests.
     * 
     * @param owner  the LeaseOwner that implements special lease behaviors
     * @param expiration the time at which this lease will expire
     * 
     * @return a new instance of a TestLease.
     * 
     */
    public TestLease createNewLease(LeaseOwner owner, long expiration) {
	return leaseBackEnd.newLease(owner, expiration);
    }

    /**
     * Convenience method used when the lease owner is not important
     * to the functioning of the test.
     * 
     * @param expiration the time at which this lease will expire
     * 
     * @return a new instance of a TestLease.
     * 
     */
    public TestLease createNewLease(long expiration) {
	LeaseOwner owner = new BasicLeaseOwner(expiration);
	return createNewLease(owner, expiration);
    }

} // TestLeaseProvider



