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
package org.apache.river.test.impl.reliability;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.apache.river.qa.harness.QAConfig;

/**
 * The ApplicationServer class provides the other server side of the "juicer"
 * stress test of RMI.
 */
public class ApplicationServer implements Runnable {
    
    /** number of remote Apple objects to export */
    private static final Logger logger = Logger.getLogger("org.apache.river.qa.harness");
    private static final int LOOKUP_ATTEMPTS = 10;
    private static final int DEFAULT_NUMAPPLES = 10;
    private static final String DEFAULT_REGISTRYHOST = "localhost";
    private final int numApples;
    private final String registryHost;
    private final Apple[] appleProxies;
    private AppleUser user;

    public ApplicationServer() {
        this(DEFAULT_REGISTRYHOST);
    }

    public ApplicationServer(String registryHost) {
	numApples = QAConfig.getConfig().getIntConfigVal(
            "org.apache.river.test.impl.reliability.maxThreads",DEFAULT_NUMAPPLES);
        this.registryHost = registryHost;
        appleProxies = new Apple[numApples];
    }

    /*
     * On initialization, export remote objects and register
     * them with server.
     */
    public void run() {
	try {
	    int i = 0;

	    /*
	     * Locate apple user object in registry.  The lookup will
	     * occur until it is successful or fails LOOKUP_ATTEMPTS times.
	     * These repeated attempts allow the ApplicationServer
	     * to be started before the AppleUserImpl.
	     */
	    Exception exc = null;
	    for (i = 0; i < LOOKUP_ATTEMPTS; i++) {
	        try {
		    Registry registry = LocateRegistry.getRegistry(
			registryHost, 2006);
		    user = (AppleUser) registry.lookup("AppleUser");
		    user.startTest();
		    break; //successfully obtained AppleUser
	        } catch (Exception e) {
		    exc = e;
		    Thread.sleep(30000); //sleep 30 seconds and try again
		}
	    }
	    if (user == null) {
	        logger.log(Level.SEVERE, "Failed to lookup AppleUser:", exc);
		return;
	    }

	    /*
	     * Create and export apple implementations.
	     */
	    try {
		for (i = 0; i < numApples; i++) {
		    appleProxies[i] = (
			    new AppleImpl("AppleImpl #" + (i + 1))
			).export();
		}
	    } catch (RemoteException e) {
	        logger.log(Level.SEVERE, 
		    "Failed to create AppleImpl #" + (i + 1) + ":", e);
		user.reportException(e);
		return;
	    }

	    /*
	     * Hand apple objects to apple user.
	     */
	    try {
		for (i = 0; i < numApples; i++) {
		    user.useApple(appleProxies[i]);
                }
	    } catch (RemoteException e) {
	        logger.log(Level.SEVERE, 
		    "Failed to register callbacks for "+appleProxies[i]+":",e);
		user.reportException(e);
		return;
	    }
	} catch (Exception e) {
	    logger.log(Level.SEVERE, "Unexpected exception:", e);
	}
    }

}
