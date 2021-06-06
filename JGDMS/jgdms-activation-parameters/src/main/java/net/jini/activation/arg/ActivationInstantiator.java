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
 * An ActivationInstantiator is responsible for creating instances of 
 * "activatable" objects. A concrete subclass of ActivationGroup 
 * implements the newInstance method to handle creating objects within the group.
 * 
 * @since 4.0
 */
public interface ActivationInstantiator extends Remote {
    /**
     * The activator calls an instantiator's newInstance method in order to 
     * recreate in that group an object with the activation identifier, 
     * id, and descriptor, desc. The instantiator is responsible for:
     * <ul>
     *    <li>determining the class for the object using the descriptor's 
     * getClassName method,</li>
     *    <li>loading the class from the code location obtained from the 
     * descriptor (using the getLocation method),</li>
     *    <li>creating an instance of the class by invoking the special 
     * "activation" constructor of the object's class that takes two 
     * arguments: 
     * <ol>
     * <li>the object's {@link ActivationID}, and</li>
     * <li>the {@link MarshalledObject} containing object specific initialization data in
     * the form of a String[] array.
     * </ol>
     * </li>
     *    <li>returning a MarshalledObject containing the stub for the 
     * remote object it created.</li>
     * </ul>
     * <p>
     * In order for activation to be successful, one of the following 
     * requirements must be met, otherwise ActivationException is thrown:
     * <ul>
     *    <li>The class to be activated and the special activation constructor are both public, or</li>
     *    <li>The class to be activated resides in a package that is open to at least the java.rmi module. </li>
     * </ul>
     * @param id the object's activation identifier
     * @param desc the object's descriptor
     * @return a marshalled object containing the serialized representation of the remote object
     * @throws ActivationException if object activation fails
     * @throws RemoteException if remote call fails
     */
    MarshalledObject newInstance(ActivationID id, ActivationDesc desc)
            throws ActivationException, RemoteException;
}
