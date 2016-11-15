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
package net.jini.lease;

/**
 * Subinterface of <code>LeaseListener</code> that clients must
 * implement if they want to receive desired expiration reached events
 * in addition to renewal failure events.
 *
 * @author Sun Microsystems, Inc.
 * @see LeaseRenewalManager
 */
public interface DesiredExpirationListener extends LeaseListener {
    /**
     * Method used to delivered desired expiration reached events. The
     * <code>getException</code> method of the passed event will always
     * return <code>null</code>.
     * <p>
     * Note that, prior to invoking this method, the
     * <code>LeaseRenewalManager</code> removes the affected lease from
     * the managed set of leases. Note also that, because of the
     * reentrancy guarantee made by the
     * <code>LeaseRenewalManager</code>, it is safe to call back into
     * the renewal manager from this method.
     *
     * @param e instance of <code>LeaseRenewalEvent</code> containing
     *		information about the lease that was removed from the
     *		<code>LeaseRenewalManager</code> because its desired
     *		expiration was reached
     */
    public void expirationReached(LeaseRenewalEvent e);
}
