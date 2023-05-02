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
package org.apache.river.test.impl.start;

import java.io.*;
import java.rmi.*;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

import org.apache.river.start.*;
import org.apache.river.api.util.Startable;


public class ProbeImpl implements Probe, Startable, ProxyAccessor {

    // TBD - use Debug class
    private final PrintWriter dbgInit = new PrintWriter(System.out, true);

    // Reference to server stub
    private Remote ourStub;

    // Reference to our activation id
    private final ActivationID activationID;
    private final ActivationExporter exporter;

    public void ping () {
	System.out.println("ProbeImpl::ping()");
    }

    public static Object activate(ActivationID activationID, 
	net.jini.activation.arg.MarshalledObject data) throws Exception 
    {
	ProbeImpl p = new ProbeImpl(activationID, null);
        p.start();
	return p.ourStub;
	
    }

    // Activation constructor
    public ProbeImpl(ActivationID activationID, String[] data) 
	throws IOException, ClassNotFoundException
    {
	this.activationID = activationID;
	if (dbgInit != null) {
	    dbgInit.println("ProbeImpl is being activated.");
	}
        this.exporter = new ActivationExporter(activationID,
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new BasicILFactory(),
            false, true));
	
    }
    
    public void start() throws Exception {
        try {
	    try {
	        if (dbgInit != null) {
	            dbgInit.println("ProbeImpl is being exported");
	        }
		ourStub = exporter.export(this);
	    } catch (RemoteException e) {
		 alertAndUnregister("Failure exporting object", e);
		 throw e;
	    }
	} catch (RuntimeException e) {
		alertAndUnregister("Unexpected exception during activation",
				   e);
	    throw e;
	} catch (Error e) {
	    alertAndUnregister("Unexpected exception during activation", e);
	    throw e;
	}
        throw new IOException("Not implemented yet.");
    }

    /**
     * If for some reason we can't activate, we dump a message to the log 
     * and unregister with activation.
     * @param m message to dump to <code>System.err</code>
     * @param e If non-<code>null</code> this exception is dumped to
     * <code>System.err</code> as well 
     */
    private void alertAndUnregister(String m, Throwable e) {
	System.err.println("Fatal error during activation:" + m);
	if (e != null)
	    e.printStackTrace();
	
	Exception failed = null;

	try {
	    System.err.println(
		"Attempting to unregister with activation system");
	    ActivationSystem sys = net.jini.activation.ActivationGroup.getSystem();
            System.err.println("Contacted activation system");
	    sys.unregisterObject(activationID);
	    System.err.println(
		"Unregistered object with activation system");
	} catch (Exception ee) {
	    failed = ee;
	} 
	
	if (failed != null) {
	    System.err.println("Failed to unregister with activation system");
	    failed.printStackTrace();
	} else {
	    System.err.println("Succeeded in unregistering");
	}
    }

    @Override
    public Object getProxy() {
        return ourStub;
    }
}
