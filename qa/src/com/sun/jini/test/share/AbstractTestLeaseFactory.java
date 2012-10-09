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

/**
 * AbstractTestLeaseFactory declares a single method which the LeaseBackEndImpl
 * can use to create any type of lease.
 *
 * 
 */
abstract public class AbstractTestLeaseFactory extends Object {
    
    /**
     * counter used to generate lease IDs 
     */
    private int leaseID = 0;

    /**
     * lock used to ensure atomic leaseID update
     */
    private Object idLock = new Object();

    /**
     * used to communicate to the backend
     */
    protected final LeaseBackEnd stub;

    /**
     * Constructor requiring the stub for communicating with the 
     *             LeaseBackEndImpl.
     * 
     * @param lbeStub the stub class used to communicate with the LeaseBackEnd
     *              implementation.
     * 
     */
    public AbstractTestLeaseFactory(LeaseBackEnd lbeStub) {
	stub = lbeStub;
    }

    /**
     * Returns a new instance of a Lease object.
     * 
     * <P>Notes:<BR>The derived class of Lease is determined by the
     * concrete factory implementation.</P>
     * 
     * @param expiration the expiration time for the lease being created
     * 
     * @return a new instance of a Lease whose type is determined by the 
     *         factory's implementation.
     * 
     */
    abstract public TestLease getNewLeaseInstance(long expiration);

    /**
     * Returns the next available lease id and advances the counter
     * 
     * @return an int that is the next available lease id number
     * 
     */
    protected int nextLeaseID() {
	synchronized (idLock) {
	    return leaseID++;
	}
    }

} // AbstractTestLeaseFactory


