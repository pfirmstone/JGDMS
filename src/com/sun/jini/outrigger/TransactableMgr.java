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

import net.jini.core.transaction.server.ServerTransaction;
import net.jini.security.ProxyPreparer;

/**
 * This interface is implemented by entities in the system that manage
 * a <code>OutriggerServerImpl</code> object's transaction state for a
 * particular transaction.  A <code>TransactableMgr</code> object has
 * a list of <code>Transactable</code> objects which represent
 * operations performed on the space under this transaction.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutriggerServerImpl
 * @see Transactable 
 */
interface TransactableMgr extends TransactableConstants {
    /**
     * Return the <code>ServerTransaction</code> which this manager
     * handles. If necessary deserialize the manager and optionally
     * prepare it. Will only deserialize the manager if it has not
     * already been deserialized. Will only prepare the manager if
     * <code>preparer</code> is non-null and no previous call to
     * <code>getTransaction</code> has succeeded. If this method
     * throws an exception, preparation has not succeeded. If a
     * previous call to this method has succeed, all future calls will
     * succeed and return the same object as the first successful
     * call.
     *
     * @param preparer the <code>ProxyPreparer</code> to
     *                 be used to prepare the reference. May
     *                 be <code>null</code>.
     * @return the <code>ServerTransaction</code> which this manager
     *         handles.
     * @throws IOException if the unmarshalling fails. Will
     *                 also throw {@link RemoteException}
     *                 if <code>preparer.prepareProxy</code>
     *                 does.
     * @throws ClassNotFoundException if unmarshalling fails
     *                 with one.
     * @throws SecurityException if <code>preparer</code> does.  
     */
    ServerTransaction getTransaction(ProxyPreparer preparer) 
	throws IOException, ClassNotFoundException;

    /**
     * Add a new <code>Transactable</code> object to the list of transactable
     * operations managed by this object.
     */
    Transactable add(Transactable t);
}
