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
package com.sun.jini.phoenix;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.UnknownGroupException;
import java.util.Map;

/**
 * An administrative interface for the activation system daemon. This
 * interface is implemented directly by the same proxy that implements
 * {@link java.rmi.activation.ActivationSystem}.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public interface ActivationAdmin extends Remote {
    /**
     * Returns a map from {@link ActivationGroupID} to
     * {@link java.rmi.activation.ActivationGroupDesc} for all registered
     * activation groups. The map contains a snapshot of the state at
     * the time of the call; subsequent state changes are not reflected
     * in the map, nor do changes in the map cause changes in the state
     * of the activation system daemon.
     *
     * @return a map from <code>ActivationGroupID</code> to
     * <code>ActivationGroupDesc</code> for all registered activation groups
     * @throws RemoteException if a communication-related exception occurs
     */
    Map getActivationGroups() throws RemoteException;

    /**
     * Returns a map from {@link java.rmi.activation.ActivationID} to
     * {@link java.rmi.activation.ActivationDesc} for all activatable objects
     * registered in the group registered under the specified activation
     * group identifier. The map contains a snapshot of the state at
     * the time of the call; subsequent state changes are not reflected
     * in the map, nor do changes in the map cause changes in the state
     * of the activation system daemon.
     *
     * @param id activation group identifier
     * @return a map from <code>ActivationID</code> to
     * <code>ActivationDesc</code> for all activatable objects registered in
     * the group registered under the specified activation group identifier
     * @throws UnknownGroupException if no group is registered under the
     * specified activation group identifier
     * @throws RemoteException if a communication-related exception occurs
     */
    Map getActivatableObjects(ActivationGroupID id)
	throws UnknownGroupException, RemoteException;
}
