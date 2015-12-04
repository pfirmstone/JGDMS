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
package org.apache.river.test.impl.outrigger.leasing;

// java classes
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;
import java.io.Serializable;
import java.io.ObjectStreamException;

// jini classes
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.UnknownEventException;
import net.jini.core.entry.Entry;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import org.apache.river.proxy.BasicProxyTrustVerifier;

import org.apache.river.qa.harness.QAConfig;
import java.io.WriteAbortedException;
import java.rmi.server.ExportException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener that calls notifyAll on itself
 */
public class LeasedSpaceListener
    implements RemoteEventListener, ServerProxyTrust, Serializable
{
    private static Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private boolean received = false;
    private Object proxy;
    private final Exporter exporter;
    private final AccessControlContext context;

    public LeasedSpaceListener(Configuration c) throws RemoteException {
	try {
	    Exporter exporter = QAConfig.getDefaultExporter();
	    if (c instanceof org.apache.river.qa.harness.QAConfiguration) {
		exporter =
		(Exporter) c.getEntry("test", 
				      "outriggerListenerExporter",
				      Exporter.class);
	    }
            this.exporter = exporter;
            context = AccessController.getContext();
	    // Proxy was originally exported here, allowing "this" to escape.
	} catch (ConfigurationException e) {
	    throw new RemoteException("Bad configuration", e);
	}
    }
    
    private synchronized Object getProxy(){
        if (proxy == null) { 
            proxy = AccessController.doPrivileged(new PrivilegedAction<Object>(){

                @Override
                public Object run() {
                    try {
                        return exporter.export(LeasedSpaceListener.this);
                    } catch (ExportException ex) {
                        String message = "Proxy export failed for LeaseListener";
                        logger.log(Level.WARNING, message , ex);
                        return null;
                    }
                }
                
            }, context);
        }
        return proxy;
    }

    public Object writeReplace() throws ObjectStreamException {
        return getProxy();
    }

    public TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(getProxy());
    }

    public void notify(RemoteEvent theEvent)
            throws UnknownEventException, RemoteException {
        // Perform logging outside the synchronized block so we don't affect
        // timing.
        java.util.Date date = new java.util.Date();
        synchronized (this){
            received = true;
            this.notifyAll();
        }
        logger.log(Level.FINER, "notify called at {0}", date.getTime());
    }

    /**
     * @return the received
     */
    public synchronized boolean isReceived() {
        return received;
    }

    /**
     * @param received the received to set
     */
    public synchronized void setReceived(boolean received) {
        this.received = received;
    }
}
