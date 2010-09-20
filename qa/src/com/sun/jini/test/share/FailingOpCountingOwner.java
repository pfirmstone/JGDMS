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

// net.jini
import net.jini.core.lease.LeaseDeniedException;
import net.jini.core.lease.UnknownLeaseException;

/**
 * A lease owner whose behavior is to throw expecptions and keep a
 * count of the operations performed on its TestLease.
 *
 * @author Steven Harris - SMI Software Development */
public class FailingOpCountingOwner extends FailingOwner {
    
    /**
     * the counters for keeping track of renew and cancel calls 
     */
    private long renewCalls = 0;
    private long cancelCalls = 0;
    private long batchRenewCalls = 0;
    private long batchCancelCalls = 0;


    // purposefully inherit javadoc from parent class
    public FailingOpCountingOwner(Throwable toThrow, int renewsUntilError, 
				  long maxExtension) {
	super(toThrow, renewsUntilError, maxExtension);
    }

    // purposefully inherit javadoc from parent class
    public FailingOpCountingOwner(Throwable[] toThrow, int renewsUntilError, 
				  long maxExtension) {
	super(toThrow, renewsUntilError, maxExtension);
    }

    /**
     * The renewal service is renewing the given lease. 
     * <p>
     * @param extension The requested extension for the lease.
     * @return Ether a <code>Long</code> representing the new duration
     * for the lease it can simulate a  <code>RemoteException</code>,
     * <code>Error</code>, or <code>RuntimeException</code> by
     * returning an object of one of these types
     * @throws LeaseDeniedException if it wants to
     * @throws UnknownLeaseException if it wants to
     */
    public Object renew(long extension)
        throws LeaseDeniedException, UnknownLeaseException {
	
	++renewCalls;
	return super.renew(extension);
    }

    /**
     * The renewal service is canceling the given lease 
     * @return If the method want to simulate a
     * <code>RemoteException</code>, <code>Error</code>, or
     * <code>RuntimeException</code> it can do by returning an object
     * of one of these types.
     * @throws UnknownLeaseException if it wants to     
     */
    public Throwable cancel() {

	++cancelCalls;
	return super.cancel();
    }

    /**
     * The renewal service is renewing the lease through a lease map.
     * @param extension The requested extension for the lease.
     * @return The new duration for the lease.  Note owner can not
     * directly simulate a runtime/remote exception and/or error using
     * this method.
     */
    public long batchRenew(long extension) throws UnknownLeaseException,
                                                  LeaseDeniedException {

	++batchRenewCalls;
	return super.batchRenew(extension);
    }

    /**
     * The renewal service is canceling the lease through a lease map.
     * Note owner can not directly simulate a runtime/remote exception
     * and/or error using this method.
     */
    public void batchCancel()  {
	++batchCancelCalls;
	super.batchCancel();
    }

    /**
     * Return the number of calls made to the renew method.
     * 
     * @return the number of times the renew method has been called.
     * 
     */
    public long getRenewCalls() { 
	return renewCalls;
    }

    /**
     * Return the number of calls made to the cancel method.
     * 
     * @return the number of times the cancel method has been called.
     * 
     */
    public long getCancelCalls() { 
	return cancelCalls;
    }

    /**
     * Return the number of calls made to the batchRenew method.
     * 
     * @return the number of times the batchRenew method has been called.
     * 
     */
    public long getBatchRenewCalls() { 
	return batchRenewCalls;
    }

    /**
     * Return the number of calls made to the batchCancel method.
     * 
     * @return the number of times the batchCancel method has been called.
     * 
     */
    public long getBatchCancelCalls() { 
	return batchCancelCalls;
    }


    /**
     * Reset all the call counts.
     */
    public synchronized void resetCallCounts() {

	// all counts go to zero
	renewCalls = cancelCalls = batchRenewCalls = batchCancelCalls = 0;
    }

} // FailingOpCountingOwner
