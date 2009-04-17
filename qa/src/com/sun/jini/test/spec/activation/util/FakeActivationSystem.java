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
package com.sun.jini.test.spec.activation.util;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.rmi.activation.ActivationSystem;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.activation.ActivationMonitor;
import java.rmi.activation.ActivationException;
import java.rmi.activation.UnknownGroupException;
import java.rmi.activation.UnknownObjectException;
import java.rmi.RemoteException;


/**
 * A fake implementation of the <code>ActivationSystem</code>
 * abstract class for test purposes.
 */
public class FakeActivationSystem implements ActivationSystem {
    Logger logger;

    public FakeActivationSystem(Logger logger) {
        super();
        this.logger = logger;
        logger.log(Level.FINEST, "(" + logger + ")");
    }

    public ActivationID registerObject(ActivationDesc desc)
            throws ActivationException, UnknownGroupException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public void unregisterObject(ActivationID id)
            throws ActivationException, UnknownObjectException,
            RemoteException {
        logger.log(Level.FINEST, "");
    };

    public ActivationGroupID registerGroup(ActivationGroupDesc desc)
            throws ActivationException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public ActivationMonitor activeGroup(ActivationGroupID id,
            ActivationInstantiator group, long incarnation)
            throws UnknownGroupException, ActivationException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public void unregisterGroup(ActivationGroupID id)
            throws ActivationException, UnknownGroupException, RemoteException {
        logger.log(Level.FINEST, "");
        return;
    };

    public void shutdown() throws RemoteException { };

    public ActivationDesc setActivationDesc(ActivationID id,
            ActivationDesc desc)
            throws ActivationException, UnknownObjectException,
            UnknownGroupException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public ActivationGroupDesc setActivationGroupDesc(ActivationGroupID id,
            ActivationGroupDesc desc)
            throws ActivationException, UnknownGroupException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public ActivationDesc getActivationDesc(ActivationID id)
            throws ActivationException, UnknownObjectException,
            RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };

    public ActivationGroupDesc getActivationGroupDesc(ActivationGroupID id)
            throws ActivationException, UnknownGroupException, RemoteException {
        logger.log(Level.FINEST, "");
        return null;
    };
}
