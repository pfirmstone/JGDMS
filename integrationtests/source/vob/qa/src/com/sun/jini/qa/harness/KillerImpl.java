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
package com.sun.jini.qa.harness;

import com.sun.jini.admin.DestroyAdmin;

import java.io.IOException;
import java.rmi.activation.ActivationID;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jini.activation.ActivationExporter;
import net.jini.admin.Administrable;
import net.jini.export.Exporter;
import net.jini.export.ProxyAccessor;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * An implementation of the <code>VMKiller</code> interface. This
 * implementation is an activatable service hard-coded to export itself
 * using a Jeri exporter. A <code>Configuration</code> is not used
 * by this service.
 */
class KillerImpl 
    implements VMKiller, ProxyAccessor, Administrable, DestroyAdmin  
{

    /** Configure logger */
    private static final Logger logger =
        Logger.getLogger("com.sun.jini.qa.harness");

    /** The inner proxy of this server */
    private final Remote ourStub;

    /** The exporter */
    Exporter exporter;

    /**
     * Shared activation constructor required by the 
     * <code>ActivateWrapper</code> class. 
     *
     * @param activationID the activationID for this service instance
     * @param data the payload object for this service instance (unused)
     */
    public KillerImpl(ActivationID activationID, MarshalledObject data)
	throws IOException, ClassNotFoundException
    {
	logger.log(Level.INFO, "Starting VMKiller service");
	exporter = 
	    new ActivationExporter(
		   activationID, 
		   new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					 new BasicILFactory()));
	ourStub = exporter.export(this);
    }

    // inherit javadoc
    public void killVM() throws RemoteException {
	logger.log(Level.FINE, "Killing VM");
	System.exit(0);
    }

    public Object getAdmin() throws RemoteException {
	return ourStub;
    }

    public void destroy() throws RemoteException {
	logger.log(Level.INFO, "Destroying VMKiller service");
	new DestroyThread().start();
    }

    /**
     * Stolen from norm
     */
    boolean unexport(boolean force) throws NoSuchObjectException {
	return exporter.unexport(force);
    }

    /**
     * Stolen from norm
     */
    private class DestroyThread extends Thread {
        /** Maximum delay for unexport attempts */
        private static final long MAX_DELAY = 2 * 60 * 1000;

	/** Create a non-daemon thread */
	private DestroyThread() {
	    super("DestroyThread");
	    /* override inheritance from RMI daemon thread */
	    setDaemon(false);
	}
	
	public void run() {
	    logger.log(Level.FINEST, "DestroyThread running");
	    
	    /*
	     * Work for up to MAX_DELAY to try to nicely
	     * unexport our stub, but if that does not work just end
	     */
	    final long end_time = System.currentTimeMillis() + MAX_DELAY;
	    boolean unexported = false;

	    try {
		while ((!unexported) &&
		       (System.currentTimeMillis() < end_time))
                {
		    /* wait for any pending operations to complete */
		    logger.log(Level.FINEST,
			       "Calling unexport (force=false)...");

		    unexported = unexport(false);

		    logger.log(Level.FINEST, "...rslt = " + unexported);

		    if (!unexported) {
			Thread.yield();
		    }
		}
	    } catch (NoSuchObjectException e) {
		logger.log(Level.FINEST, "...rslt = NoSuchObjectException");

		unexported = true; // This works too
	    } catch (Throwable t) {
		logger.log(Level.FINEST, "...rslt = ", t);
	    }

	    if (!unexported) {
		/* Attempt to forcefully export the service */
		try {
		    logger.log(Level.FINEST, "Calling unexport (force=true)");

		    unexport(true);
		} catch (NoSuchObjectException e) {
		    // This works too
		}
	    }
	}
    }

    // inherit javadoc
    public Object getProxy() {
	return ourStub;
    }
}
