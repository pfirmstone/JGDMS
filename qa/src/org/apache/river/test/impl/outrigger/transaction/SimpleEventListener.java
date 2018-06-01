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
package org.apache.river.test.impl.outrigger.transaction;

// All other imports
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import java.rmi.RemoteException;

import java.io.Serializable;
import java.io.ObjectStreamException;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import org.apache.river.proxy.BasicProxyTrustVerifier;

import java.rmi.server.ExportException;
import net.jini.jeri.AtomicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * Simple event listener class.
 * This class will be used to test <code>notify</code> facilities.
 *
 * @author H. Fukuda
 */
public class SimpleEventListener
    implements RemoteEventListener, ServerProxyTrust, Serializable
{
    private long maxSeqNum = 0;
    private Object proxy;
    private final Exporter exporter;

    public SimpleEventListener(Configuration c) throws RemoteException {
	this(getExporter(c));
        // 
        export();
    }
    
    private SimpleEventListener(Exporter exporter){
        this.exporter = exporter;
    }
    
    private static Exporter getExporter(Configuration c) throws RemoteException {
        try {
	    Exporter exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				     new AtomicILFactory(null, null, SimpleEventListener.class ));
	    if (c instanceof org.apache.river.qa.harness.QAConfiguration) {
		exporter =
		(Exporter) c.getEntry("test", "outriggerListenerExporter", Exporter.class);
	    }
            return exporter;
	} catch (ConfigurationException e) {
	    throw new IllegalArgumentException("Bad configuration" + e);
	}
    }
    
    private synchronized void export() throws ExportException{
        proxy = exporter.export(this);
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
	return proxy;
    }

    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }

    /**
     * Event handler.
     * In this method, just a counter of events is incremented.
     *
     * @param ev Remote event object.
     */
    public synchronized void notify(RemoteEvent ev) throws RemoteException {
        maxSeqNum = Math.max(ev.getSequenceNumber(), maxSeqNum);
    }

    /**
     * Get how many times this event listener receives <code>notify</code>
     *
     * @return number of <code>notify</code> called.
     */
    public synchronized long getNotifyCount(EventRegistration reg) {
        return (maxSeqNum - reg.getSequenceNumber());
    }
}
