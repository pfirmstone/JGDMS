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
 * The ActivationSystem provides a means for registering groups and 
 * "activatable" objects to be activated within those groups. 
 * The ActivationSystem works closely with the Activator, which 
 * activates objects registered via the ActivationSystem, 
 * and the ActivationMonitor, which obtains information about 
 * active and inactive objects, and inactive groups.
 * 
 * @since 4.0
 */
public interface ActivationSystem extends Remote {
    /**
     * The port to lookup the activation system.
     */
    final int SYSTEM_PORT = 1098;

    /**
     * Callback to inform activation system that group is now active. 
     * This call is made internally by the ActivationGroup.createGroup
     * method to inform the ActivationSystem that the group is now active.
     * 
     * @param gID the activation group's identifier
     * @param aInst the group's instantiator
     * @param incarnation the group's incarnation number
     * @return monitor for activation group
     * @throws UnknownGroupException if group is not registered
     * @throws ActivationException if a group for the specified id is already
     * active and that group is not equal to the specified group or that group
     * has a different incarnation than the specified group
     * @throws RemoteException if remote call fails
     */
    ActivationMonitor activeGroup(ActivationGroupID gID, ActivationInstantiator aInst,
            long incarnation) throws UnknownGroupException, ActivationException,
            RemoteException;

    /**
     * Set the activation group descriptor, desc for the object with the
     * activation group identifier, id. The change will take effect
     * upon subsequent activation of the group.
     * 
     * @param gID the activation group identifier for the activation group
     * @param gDesc the activation group descriptor for the activation group
     * @return the previous value of the activation group descriptor
     * @throws ActivationException for general failure (e.g., unable to update log)
     * @throws UnknownGroupException the group associated with id is not a registered group
     * @throws RemoteException if remote call fails
     */
    ActivationGroupDesc setActivationGroupDesc(ActivationGroupID gID, ActivationGroupDesc gDesc)
            throws ActivationException, UnknownGroupException, RemoteException;

    /**
     * Set the activation descriptor, desc for the object with the activation
     * identifier, id. The change will take effect upon subsequent 
     * activation of the object.
     * 
     * @param aID the activation identifier for the activatable object
     * @param aDesc the activation descriptor for the activatable object
     * @return the previous value of the activation descriptor
     * @throws ActivationException for general failure (e.g., unable to update log)
     * @throws UnknownObjectException the activation id is not registered
     * @throws UnknownGroupException the group associated with desc is not a registered group
     * @throws RemoteException if remote call fails
     */
    ActivationDesc setActivationDesc(ActivationID aID, ActivationDesc aDesc)
            throws ActivationException, UnknownObjectException, UnknownGroupException,
            RemoteException;

    /**
     * The registerObject method is used to register an activation descriptor,
     * desc, and obtain an activation identifier for a activatable remote
     * object. The ActivationSystem creates an ActivationID 
     * (a activation identifier) for the object specified by the
     * descriptor, desc, and records, in stable storage, the activation
     * descriptor and its associated identifier for later use. When the
     * Activator receives an activate request for a specific identifier, 
     * it looks up the activation descriptor (registered previously) for 
     * the specified identifier and uses that information to activate the
     * object.
     * 
     * @param aDesc the object's activation descriptor
     * @return the activation id that can be used to activate the object
     * @throws ActivationException if registration fails (e.g., database update failure, etc).
     * @throws UnknownGroupException if group referred to in desc is not registered with this system
     * @throws RemoteException if remote call fails
     */
    ActivationID registerObject(ActivationDesc aDesc) throws ActivationException,
            UnknownGroupException, RemoteException;

    /**
     * Register the activation group. An activation group must be 
     * registered with the ActivationSystem before objects can be
     * registered within that group.
     * 
     * @param gDesc the group's descriptor
     * @return an identifier for the group
     * @throws ActivationException if group registration fails
     * @throws RemoteException if remote call fails
     */
    ActivationGroupID registerGroup(ActivationGroupDesc gDesc) throws ActivationException,
            RemoteException;

    /**
     * Returns the activation group descriptor, for the group with the 
     * activation group identifier, id.
     * 
     * @param gID the activation group identifier for the group
     * @return the activation group descriptor
     * @throws ActivationException for general failure
     * @throws UnknownGroupException if id is not registered
     * @throws RemoteException if remote call fails
     */
    ActivationGroupDesc getActivationGroupDesc(ActivationGroupID gID)
            throws ActivationException, UnknownGroupException, RemoteException;

    /**
     * Returns the activation descriptor, for the object with the activation 
     * identifier, id.
     * 
     * @param aID the activation identifier for the activatable object
     * @return the activation descriptor
     * @throws ActivationException for general failure
     * @throws UnknownObjectException if id is not registered
     * @throws RemoteException if remote call fails
     */
    ActivationDesc getActivationDesc(ActivationID aID) throws ActivationException,
            UnknownObjectException, RemoteException;

    /**
     * Remove the activation id and associated descriptor previously registered
     * with the ActivationSystem; the object can no longer be activated
     * via the object's activation id.
     * 
     * @param aID the object's activation id (from previous registration)
     * @throws ActivationException if unregister fails (e.g., database update failure, etc).
     * @throws UnknownObjectException if object is unknown (not registered)
     * @throws RemoteException if remote call fails
     */
    void unregisterObject(ActivationID aID) throws ActivationException, UnknownObjectException,
            RemoteException;

    /**
     * Remove the activation group. An activation group makes this call back 
     * to inform the activator that the group should be removed (destroyed).
     * If this call completes successfully, objects can no longer be 
     * registered or activated within the group. All information of the
     * group and its associated objects is removed from the system.
     * 
     * @param gID the activation group's identifier
     * @throws ActivationException if unregister fails (e.g., database update failure, etc).
     * @throws UnknownGroupException if group is not registered
     * @throws RemoteException if remote call fails
     */
    void unregisterGroup(ActivationGroupID gID) throws ActivationException,
            UnknownGroupException, RemoteException;

    /**
     * Shutdown the activation system. Destroys all groups spawned by the 
     * activation daemon and exits the activation daemon.
     * 
     * @throws RemoteException if failed to contact/shutdown the activation daemon
     */
    void shutdown() throws RemoteException;
}
