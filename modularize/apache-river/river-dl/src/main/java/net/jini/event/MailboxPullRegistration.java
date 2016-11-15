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
import java.util.Collection;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lease.Lease;


/**
 * The <code>MailboxPullRegistration</code> defines the interface through which
 * a client manages its registration and its notification processing. 
 * Event mailbox clients use this interface to:
 * <UL>
 * <LI>Manage the lease for this registration.
 * <LI>Obtain a <code>RemoteEventListener</code> reference that
 *     can be registered with event generators. This listener will
 *     then store any received events for this registration.
 * <LI>Synchronously or asynchronously collect any event notifications 
 *     stored by this particular registration.
 * </UL>
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.1
 */
public interface MailboxPullRegistration extends MailboxRegistration {

    /**
     * Retrieves stored notifications, if any. 
     *
     * @return A <code>RemoteEventIterator</code> that can be used to retrieve
     * event notifications from the mailbox service.
     *
     * @throws java.rmi.RemoteException if there is
     *  a communication failure between the client and the service.
     */
    public RemoteEventIterator getRemoteEvents()
		    throws RemoteException;
    /**
     * Adds the provided collection of unknown events to this registration.
     * The mailbox will then propagate an 
     * {@link net.jini.core.event.UnknownEventException}
     * back to any event generator that attempts to deliver an event with an 
     * identifier-source combination held in a registration's unknown 
     * exception list. 
     *
     * @param unknownEvents  A <code>Collection</code> of unknown events 
     *         to be associated with this registration.
     *
     * @throws java.rmi.RemoteException if there is
     *  a communication failure between the client and the service.
     */
    public void addUnknownEvents(Collection unknownEvents)
		    throws RemoteException;
}
