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

package com.sun.jini.test.impl.locatordiscovery;

import com.sun.jini.config.Config;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.Constants;
import net.jini.discovery.LookupDiscovery;
import net.jini.discovery.LookupLocatorDiscovery;

/**
 * Tests the <code>initialUnicastDelayRange</code> config entry for
 * <code>LookupLocatorDiscovery</code>. The test starts up a number of
 * <code>LookupLocatorDiscovery</code> instances and checks if the average
 * delay in initiating unicast discovery requests is in the range
 * <code>0.25 * initialUnicastDelayRange < averageDelay <
 * .75 * initialUnicastDelayRange</code>.
 *
 */
public class UnicastDelay extends QATestEnvironment implements Test {
    
    private static final int DISCOVERYPORT = Constants.discoveryPort;
    private Configuration config;
    private static final int NUMLD = 100;
    private Throwable failure = null;
    private boolean done = false;
    private long acceptTime[] = new long[NUMLD];
    LookupLocatorDiscovery[] ldArray = new LookupLocatorDiscovery[NUMLD];
    
    private class AcceptThread extends Thread {
	
	public AcceptThread() {
	    super("unicast request");
	    setDaemon(true);
	}
	
	public void run() {
	    try {
		ServerSocket s = new ServerSocket(DISCOVERYPORT);
		logger.log(Level.FINE, "going to accept");
		for (int i = 0; i < NUMLD; i++) {
		    s.accept();
		    acceptTime[i] = System.currentTimeMillis();
		    logger.log(Level.FINEST, "Accepted unicast request " + i);
		}
	    } catch (Throwable t) {
		failure = t;
	    } finally {
		synchronized (this) {
		    done = true;
		    // Wakeup main thread - we're done
		    this.notify();
		}
	    }
	}
    }
    
    public Test construct(QAConfig qaConfig) throws Exception {
	super.construct(qaConfig);
	config = qaConfig.getConfig().getConfiguration();
        return this;
    }
    
    public void run() throws Exception {

	long delay = Config.getLongEntry(config,
				    "net.jini.discovery.LookupLocatorDiscovery",
				    "initialUnicastDelayRange",
				    10000, 0, Long.MAX_VALUE);
	long expectedDelay = delay / 2;
	logger.log(Level.FINE, "Expected average delay " + expectedDelay);
	long spread = expectedDelay / 2;

	long lBound = expectedDelay - spread;
	long uBound = expectedDelay + spread;
	if ((lBound < 0) || (uBound < 0)) {
	    throw new IllegalArgumentException("Invalid delay " + delay);
	}
	
	if (NUMLD < 2) {
	    throw new IllegalArgumentException("Invalid number of LDs " + NUMLD);
	}

	Thread t = new AcceptThread();
	t.start();
	// Wait for AcceptThread to set up its socket.
	Thread.sleep(1000);
	long startTime = System.currentTimeMillis();
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i] = new LookupLocatorDiscovery(
		    new LookupLocator[] {
			new LookupLocator(
				InetAddress.getLocalHost().getHostName(),
				DISCOVERYPORT)
		    }, config);
	}

	synchronized(t) {
	    t.wait(delay * 3 / 2);
	}
	
	if (failure != null) {
	    throw new RuntimeException("Test failed ", failure);
	}

	if (!done) {
	    throw new RuntimeException("All "
		    + NUMLD + " unicast requests not received");
	}

	float averageDelay = 0f;
	for (int i = 0; i < NUMLD; i++) {
	    long timei = acceptTime[i] - startTime;
	    averageDelay = averageDelay  + (timei / NUMLD);
	}
	logger.log(Level.FINE, "Average delay " + (long) averageDelay);
	if ((averageDelay < lBound) || (averageDelay > uBound)) {
	    throw new RuntimeException("Elapsed time out of expected range " +
					averageDelay +
					" lower bound " + lBound +
					" upper bound " + uBound);
	}
    }
    
    public void tearDown() {
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i].terminate();
	}
    }
}
