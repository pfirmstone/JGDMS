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
package net.jini.event;

import java.rmi.RemoteException;
import net.jini.core.lease.LeaseDeniedException;

/**
 * The <code>EventMailbox</code> interface allows clients
 * to specify and use a third party for the purpose of
 * storing and retrieving events.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see MailboxRegistration
 *
 * @since 1.1
 */

public interface EventMailbox {
    /**
     * Defines the interface to the event mailbox service.
     * Event mailbox clients utilize this service by invoking
     * the <code>register</code> method to register themselves with
     * the service.
     *
     * @param leaseDuration the requested lease duration in milliseconds
     * @return A new <code>MailboxRegistration</code> 
     *
     * @throws IllegalArgumentException if 
     * <code>leaseDuration</code> is not positive or <code>Lease.ANY</code>.
     *
     * @throws java.rmi.RemoteException if there is
     *  a communication failure between the client and the service.
     *
     * @throws net.jini.core.lease.LeaseDeniedException 
     * if the mailbox service is unable or unwilling to grant this
     * registration request.
     */
    MailboxRegistration register(long leaseDuration) 
	throws RemoteException, LeaseDeniedException;
}
