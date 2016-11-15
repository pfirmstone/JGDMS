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

import java.util.EventListener;
import net.jini.core.lease.Lease;

/** 
 * The interface that receivers of <code>LeaseRenewalEvent</code>
 * instances must implement.
 * <p>
 * With respect to an entity that uses the
 * <code>LeaseRenewalManager</code> to manage leases granted to the
 * entity, this interface defines the mechanism through which an entity
 * receives notification that the <code>LeaseRenewalManager</code> has
 * failed to renew one of the leases the
 * <code>LeaseRenewalManager</code> is managing for the entity. Such
 * renewal failures typically occur because of one of the following
 * conditions is met:
 * <ul>
 *   <li> After successfully renewing a lease any number of times and
 *        experiencing no failures, the <code>LeaseRenewalManager</code>
 *        determines, prior to the next renewal attempt, that the actual
 *        expiration time of the lease has passed, implying that any
 *        further attempt to renew the lease would be fruitless.
 *   <li> An indefinite exception occurs during each attempt to renew a
 *        lease, from the point that the first such exception occurs
 *        until the point when the <code>LeaseRenewalManager</code>
 *        determines that lease's actual expiration time has passed.
 *   <li> A bad object, bad invocation, or <code>LeaseException</code>
 *        occurs during a lease renewal attempt (collectively referred
 *        to as definite exceptions).
 * </ul>
 * It is the responsibility of the entity to register with the
 * <code>LeaseRenewalManager</code>. The object that implements this
 * interface should define the actions to take upon receipt of such
 * notifications. Then, when one of the above conditions occurs, the
 * <code>LeaseRenewalManager</code> will send an instance of the
 * <code>LeaseRenewalEvent</code> class to that listener object. Note
 * that, prior to sending the event, the
 * <code>LeaseRenewalManager</code> will remove the affected lease from
 * its managed set of leases.
 *
 * @author Sun Microsystems, Inc.
 * @see Lease
 * @see LeaseRenewalManager
 * @see LeaseRenewalEvent
 */
public interface LeaseListener extends EventListener {
    /**
     * Called by the <code>LeaseRenewalManager</code> when it cannot
     * renew a lease that it is managing, and the lease's desired
     * expiration time has not yet been reached.
     * <p>
     * Note that, prior to invoking this method, the
     * <code>LeaseRenewalManager</code> removes the affected lease from
     * the managed set of leases. Note also that, because of the
     * reentrancy guarantee made by the
     * <code>LeaseRenewalManager</code>, new leases can be safely added
     * by this method.
     *
     * @param e instance of <code>LeaseRenewalEvent</code> containing
     *		information about the lease that the
     *		<code>LeaseRenewalManager</code> was unable to renew, as
     *		well as information about the condition that made the
     *		<code>LeaseRenewalManager</code> fail to renew the lease
     */
    void notify(LeaseRenewalEvent e);
}
