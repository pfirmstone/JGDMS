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
package com.sun.jini.outrigger;

import java.io.IOException;
import java.rmi.RemoteException;
import net.jini.core.event.UnknownEventException;
import net.jini.security.ProxyPreparer;
import net.jini.space.JavaSpace;

/**
 * <code>EventSender</code>s encapsulates a remote event listener, a
 * handback, an event sequence number, and an event type (an event ID
 * and class of <code>RemoteEvent</code>). <code>EventSender</code>s
 * provide a method that attempts to deliver an event of the
 * encapsulated type with the encapsulated handback and sequence number 
 * to the encapsulated listener.
 */
interface EventSender {
    /**
     * Send a remote event to the encapsulated listener of the encapsulated
     * type, with the encapsulated handback, sequence number.  No locks
     * should be held while calling the listener. This method may be called
     * more than once if all previous tries failed. This call may do
     * nothing and return normally if it is determined that delivering the
     * event is no longer useful. It is assumed that once this
     * method returns normally it will not be called again.
     *
     * @param source the source the event object
     *        sent to the lister should have.
     * @param now The current time.
     * @param preparer to apply to the listener if it has
     *        been recovered from a store and not yet re-prepared
     *        in this VM.
     * @throws IOException if the listener can not
     *         be unmarshalled. May throw {@link RemoteException}
     *         if the call to the listener or preparer does
     * @throws ClassNotFoundException if the listener
     *         needs to be unmarshalled and a necessary
     *         class can not be found.
     * @throws UnknownEventException if the
     *         call to the listener does. Note, this
     *         will not cause the watcher to remove itself.
     * @throws SecurityException if the call to the listener does
     *         or if the listener needs to be prepared and 
     *         the <code>prepareProxy</code> call does.
     * @throws RuntimeException if the call to the listener does.
     */
    public void sendEvent(JavaSpace source, long now, ProxyPreparer preparer)
	throws UnknownEventException, ClassNotFoundException, IOException;

    /**
     * Called when the event sending infrastructure decides
     * to give up on the event registration associated with
     * this sender.
     */
    public void cancelRegistration();

    /**
     * Return <code>true</code> if the passed <code>EventSender</code>
     * should run before this one, otherwise return <code>false</code>.
     * @param other the sender this object should compare itself too.
     * @return <code>true</code> if this object should run after
     * <code>other</code>.
     */
    public boolean runAfter(EventSender other);
}
