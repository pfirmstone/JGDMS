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

import net.jini.core.event.RemoteEvent;

/**
 * <code>RemoteEventIterator</code> defines the interface through which
 * a client can synchronously retrieve events associated with a given
 * registration.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.1
 */
public interface RemoteEventIterator {

    /**
     * Retrieves stored event notifications, if any. 
     *
     * @param timeout the maximum time, in milliseconds, the event mailbox 
     * service should wait for the receipt of an event notification   
     * associated with this iterator's registration.
     *
     * @return The <code>RemoteEvent</code> 
     *
     * @throws IllegalArgumentException if the supplied <code>timeout</code> 
     *         parameter is less than 0.
     *
     * @throws InvalidIteratorException if called on an invalidated iterator.
     * @throws java.rmi.RemoteException if there is
     *  a communication failure between the client and the service.
     */
    public RemoteEvent next(long timeout) 
        throws RemoteException, InvalidIteratorException; 
    
    /**
     * Ends all event processing being performed by this iterator 
     * and invalidates the iterator.
     *
     * @throws InvalidIteratorException if called on an invalidated iterator.
     *
     */
    public void  close() 
        throws InvalidIteratorException; 

}
