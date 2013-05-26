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
package com.sun.jini.test.share;

import java.rmi.RemoteException;

import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.UnknownEventException;
import net.jini.export.Exporter;
import net.jini.security.proxytrust.ServerProxyTrust;
import net.jini.security.TrustVerifier;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Remote listener impl to be sub-classed by a class which actually does work
 */
class RemoteListener implements RemoteEventListener, 
                                ServerProxyTrust, 
                                Serializable 
{

    protected static Logger logger = 
        Logger.getLogger("com.sun.jini.qa.harness");

    private Object proxy;
    private final Exporter exporter;
    private final AccessControlContext context;

    RemoteListener(Exporter exporter) throws RemoteException {
	this.exporter = exporter;
        this.context = AccessController.getContext(); // Save context for lazy export.
    }
    
    /**
     * Must synchronize on this when called.
     * @return proxy
     * @throws ExportException 
     */
    private Object getProxy() throws ExportException{
        if (proxy == null){
            try {
                proxy = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){

                    @Override
                    public Object run() throws ExportException {
                        return exporter.export(RemoteListener.this);
                    }

                }, context);
            } catch (PrivilegedActionException ex) {
                throw (ExportException) ex.getException();
            }
        }
        return proxy;
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
        try {
            return getProxy();
        } catch (ExportException ex) {
            throw new WriteAbortedException("Unable to export proxy", ex);
        }
    }

    public synchronized void notify(RemoteEvent theEvent)
	throws UnknownEventException, RemoteException
    {
    }

    public synchronized TrustVerifier getProxyVerifier() {
        try {
            return new BasicProxyTrustVerifier(getProxy());
        } catch (ExportException ex) {
            throw new IllegalStateException("Unable to export proxy", ex);
        }
    }
}
