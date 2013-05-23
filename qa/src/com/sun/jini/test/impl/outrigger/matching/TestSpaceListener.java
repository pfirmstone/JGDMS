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
package com.sun.jini.test.impl.outrigger.matching;

// java.rmi
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

// net.jini
import net.jini.core.event.RemoteEvent;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.UnknownEventException;
import net.jini.core.entry.Entry;

import java.io.Serializable;
import java.io.ObjectStreamException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.export.Exporter;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;

import com.sun.jini.proxy.BasicProxyTrustVerifier;

import com.sun.jini.qa.harness.QAConfig;
import java.rmi.server.ExportException;

/**
 * Simple listener that prints message to log when an event is received
 */
class TestSpaceListener
    implements RemoteEventListener, ServerProxyTrust, Serializable
{

    private static Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    final private Entry tmpl;
    private Object proxy;
    private final Exporter exporter;

    /**
     * Create a new TestSpaceListener that dumps to the pasted stream
     */
    TestSpaceListener(Configuration c, Entry tmpl) throws RemoteException {
	try {
	    Exporter exporter = QAConfig.getDefaultExporter();
	    if (c instanceof com.sun.jini.qa.harness.QAConfiguration) {
		exporter =
		(Exporter) c.getEntry("test", "outriggerListenerExporter", Exporter.class);
	    }
	    this.exporter = exporter;
	} catch (ConfigurationException e) {
	    throw new IllegalArgumentException("Bad configuration" + e);
	}
        this.tmpl = tmpl;
    }
    
    public synchronized void export() throws ExportException {
        proxy = exporter.export(this);
    }

    public synchronized Object writeReplace() throws ObjectStreamException {
        return proxy;
    }

    public synchronized TrustVerifier getProxyVerifier() {
	return new BasicProxyTrustVerifier(proxy);
    }

    public void notify(RemoteEvent theEvent)
            throws UnknownEventException, RemoteException {
        logger.log(Level.INFO, "Recived event that should match " + tmpl);
    }
}
