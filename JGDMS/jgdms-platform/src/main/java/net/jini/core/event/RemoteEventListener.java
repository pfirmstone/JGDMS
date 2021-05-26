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

package net.jini.core.event;

/**
 * The RemoteEventListener interface needs to be implemented by any object
 * that wants to receive a notification of a remote event from some other
 * object. 
 * <p>
 * The object implementing this interface does not need to be the object 
 * that originally registered interest in the occurrence of an event. To 
 * allow the notification of an event's occurrence to be sent to an entity
 * other than the one that made the interest registration, the registration
 * call needs to accept a destination parameter, which indicates the object
 * to which the notification should be sent. This parameter must be an
 * object which supports the RemoteEventListener interface.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
public interface RemoteEventListener
			extends java.rmi.Remote, java.util.EventListener
{

    /**
     * Notify the listener about an event.
     * <p>
     * The call to notify is synchronous to allow the party making the call
     * to know if the call succeeded.  However, it is not part of the
     * semantics of the call that the notification return can be delayed
     * while the recipient of the call reacts to the occurrence of the event.
     * Simply put, the best strategy on the part of the recipient is to note
     * the occurrence in some way and then return from the notify method as
     * quickly as possible.
     * <p>
     * UnknownEventException is thrown when the recipient does not recognize
     * the combination of the event identifier and the event source as
     * something in which it is interested.  Throwing this exception has the
     * effect of asking the sender to not send further notifications of
     * this kind of event from this source in the future.
     *
     * @param theEvent the remote event that occurred
     *
     * @throws UnknownEventException the recipient does not recognize the
     *         combination of event identifier and event source
     * @throws java.rmi.RemoteException if a connection problem occurs.
     */
    void notify(RemoteEvent theEvent)
	throws UnknownEventException, java.rmi.RemoteException;
}
