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
package com.sun.jini.mercury;

import com.sun.jini.landlord.Landlord;
import com.sun.jini.proxy.ThrowThis;
import com.sun.jini.start.ServiceProxyAccessor;


import java.rmi.RemoteException;
import java.util.Collection;

import net.jini.admin.Administrable;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.UnknownEventException;
import net.jini.event.InvalidIteratorException;
import net.jini.event.MailboxRegistration;
import net.jini.event.PullEventMailbox;
import net.jini.id.Uuid;

/**
 * MailboxBackEnd defines the private protocol between the various client-side
 * proxies and the event mailbox server.
 * <p>
 * The declared methods are pretty straightforward mappings of the
 * <tt>PullEventMailbox</tt> and <tt>MailboxPullRegistration</tt> interfaces.
 * <p>
 * Note: The <tt>Landlord</tt> interface extends <tt>Remote</tt>, 
 * which implicitly makes this interface Remote as well. 
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.1
 */
interface MailboxBackEnd extends Landlord, Administrable, MailboxAdmin,
    PullEventMailbox, ServiceProxyAccessor
{

    /**
     * Enable delivery of events for the given registration
     * to the specified target
     *
     * @param registrationID The unique registration identifier
     *
     * @param target The designated delivery target for event notifications
     *
     * @see net.jini.event.MailboxRegistration#enableDelivery
     */
    public void enableDelivery(Uuid registrationID, RemoteEventListener target) 
	throws RemoteException, ThrowThis;
	
    /**
     * Disable delivery of events for the given registration
     *
     * @param registrationID The unique registration identifier
     *
     * @see net.jini.event.MailboxRegistration#disableDelivery
     */
     
    public void disableDelivery(Uuid registrationID) 
	throws RemoteException, ThrowThis;
	
    /**
     * Get events for the given registration via the returned iterator.
     *
     * @param uuid The unique registration identifier
     *
     * @see net.jini.event.MailboxPullRegistration#getRemoteEvents
     */
    public RemoteEventIteratorData getRemoteEvents(Uuid uuid) 
	throws RemoteException, ThrowThis;
	
    /**
     * Get next batch of events for the given registration.
     *
     * @param regId The unique registration identifier
     *
     */
    public Collection getNextBatch(Uuid regId, Uuid iterId, 
        long timeout, Object lastEventCookie) 
	throws RemoteException, InvalidIteratorException, ThrowThis;
    
    /**
     * Get events for the given registration
     *
     * @param uuid The unique registration identifier
     *
     * @param unknownEvents collection of unknown events to be added to
     * the associated registration's unknown event list.
     *
     * @see net.jini.event.MailboxPullRegistration#getRemoteEvents
     */
    public void addUnknownEvents(
	Uuid uuid, Collection unknownEvents) 
	throws RemoteException, ThrowThis;
    
    /**
     * Collect remote events for the associated registration.
     *
     * @param registrationID The unique registration identifier
     *
     * @param theEvent The event to store and/or forward
     *
     * @see net.jini.core.event.RemoteEventListener#notify
     */
    public void notify(Uuid registrationID, RemoteEvent theEvent) 
	throws UnknownEventException, RemoteException, ThrowThis;
}
