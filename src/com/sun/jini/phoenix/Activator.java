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

import com.sun.jini.proxy.MarshalledWrapper;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationID;
import java.rmi.activation.UnknownObjectException;
import net.jini.io.MarshalledInstance;

/**
 * The activator used by {@link AID}.  The {@link #activate activate}
 * method returns the activated object's proxy as a {@link
 * MarshalledInstance} wrapped in a {@link MarshalledWrapper} so that
 * integrity can be verified on the enclosed proxy.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
interface Activator extends Remote {

    /**
     * Returns a marshalled proxy wrapped in a
     * <code>MarshalledWrapper</code> for the activated object
     * corresponding to the specified activation identifier.
     *
     * @param id the activation identifier for the remote object
     * @param force the value to pass to the <code>activate</code> method
     * of the activation identifier
     * @return the remote object's proxy as a <code>MarshalledInstance</code>
     * wrapped in a <code>MarshalledWrapper</code> 
     * @throws ActivationException if object activation fails
     * @throws UnknownObjectException if object is unknown (not registered)
     * @throws RemoteException if remote call fails
     **/
    MarshalledWrapper activate(ActivationID id, boolean force)
	throws ActivationException, RemoteException; 
    

}
