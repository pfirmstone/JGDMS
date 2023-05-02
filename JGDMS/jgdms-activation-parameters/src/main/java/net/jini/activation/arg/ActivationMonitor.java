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

/**
 * An ActivationMonitor is specific to an ActivationGroup and is obtained
 * when a group is reported active via a call to ActivationSystem.activeGroup
 * (this is done internally). An activation group is responsible for informing
 * its ActivationMonitor when either: its objects become active or inactive,
 * or the group as a whole becomes inactive.
 * 
 * @since 4.0
 */
public interface ActivationMonitor extends Remote {
    /**
     * Informs that an object is now active. An ActivationGroup informs its
     * monitor if an object in its group becomes active by other means 
     * than being activated directly (i.e., the object is registered
     * and "activated" itself).
     * 
     * @param id the active object's id
     * @param reference the marshalled form of the object's remote reference.
     * @throws UnknownObjectException if object is unknown
     * @throws RemoteException if remote call fails
     */
    void activeObject(ActivationID id, MarshalledObject reference) throws UnknownObjectException,
            RemoteException;

    /**
     * An activation group calls its monitor's inactiveObject method when an
     * object in its group becomes inactive (deactivates). An activation group
     * discovers that an object (that it participated in activating) in its
     * VM is no longer active, via calls to the activation group's
     * inactiveObject method.
     * <p>
     * The inactiveObject call informs the ActivationMonitor that the remote
     * object reference it holds for the object with the activation identifier,
     * id, is no longer valid. The monitor considers the reference associated 
     * with id as a stale reference. Since the reference is considered stale, 
     * a subsequent activate call for the same activation identifier results 
     * in re-activating the remote object.
     * 
     * @param id the object's activation identifier
     * @throws UnknownObjectException if object is unknown
     * @throws RemoteException if remote call fails
     */
    void inactiveObject(ActivationID id) throws UnknownObjectException, RemoteException;

    /**
     * Informs that the group is now inactive. The group will be recreated
     * upon a subsequent request to activate an object within the group. 
     * A group becomes inactive when all objects in the group report that 
     * they are inactive.
     * 
     * @param groupId the group's id
     * @param incarnation the group's incarnation number
     * @throws UnknownGroupException if group is unknown
     * @throws RemoteException if remote call fails
     */
    void inactiveGroup(ActivationGroupID groupId, long incarnation)
            throws UnknownGroupException, RemoteException;
}
