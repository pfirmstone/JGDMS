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
package com.sun.jini.norm.event;

import net.jini.core.event.RemoteEvent;

/**
 * Object associated with an <code>EventType</code> so it can ensure
 * that the lease on an event notification is still valid and notify
 * the client when a exception occurs in the course of attempting to
 * send an event.
 *
 * @author Sun Microsystems, Inc.
 * @see EventType
 * @see EventTypeGenerator
 */
public interface SendMonitor {
    /**
     * Method called when an attempt to send the event associated with
     * this object results in a definite exception
     * (e.g. <code>java.rmi.NoSuchObjectException</code> or any other
     * <code>Throwable</code> that is not a subclass of
     * <code>java.rmi.RemoteException</code>.
     * <p>
     * The caller will own no locks when calling this method.
     * @param type the object that generated the event
     * @param ev   the remote event that could not be sent
     * @param registrationNumber of the event registration that
     *             generated the exception.  This can be used
     *             to call <code>EventType.clearListenerIfSequenceMatch</code>
     *             ensure only the registration that cause the problem 
     *             gets cleared.
     * @param t    the definite exception that caused us to give up
     *             sending the event
     */
    public void definiteException(EventType           type,
                                  RemoteEvent         ev,
				  long                registrationNumber,
                                  Throwable           t);
    /**
     * Should return <code>true</code> if the lease associated with this
     * event is still valid and <code>false</code> otherwise.
     * <p>
     * The caller will own no locks when calling this method.
     */
    public boolean isCurrent();
}
