/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.activation.arg;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UID;

/**
 * Activation makes use of special identifiers to denote remote objects that
 * can be activated over time. An activation identifier (an instance of the 
 * class ActivationID) contains several pieces of information needed for
 * activating an object:
 * <ul>
 *    <li>a {@link Remote} reference to the object's activator, and</li>
 *    <li>a unique identifier (a UID instance) for the object. </li>
 * </ul>
 * 
 * @since 4.0
 */
public interface ActivationID {

    /**
     * Activate the object for this id.
     * @param force if true, forces the activator to contact the group 
     * when activating the object (instead of returning a cached reference);
     * if false, returning a cached value is acceptable.
     * @return the reference to the active remote object
     * @throws java.rmi.RemoteException if remote call fails
     * @throws ActivationException if activation fails
     * @throws UnknownObjectException if the object is unknown
     */
    public Remote activate(boolean force) throws ActivationException, UnknownObjectException,
            RemoteException ;
    
    /**
     * Returns the unique identifier for the object.
     * @return the unique identifier for the object.
     */
    public UID getUID();

}
