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
/*
 * MulticastRequestDelay.java
 *
 * Created on March 4, 2005, 11:14 AM
 */

package com.sun.jini.test.impl.lookupdiscovery;

import com.sun.jini.config.Config;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.Test;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.discovery.Constants;
import net.jini.discovery.LookupDiscovery;

/**
 * Tests the <code>initialMulticastRequestDelayRange</code> configuration entry
 * for <code>LookupDiscovery</code>. It starts a number of
 * <code>LookupDisocvery</code> instances and waits for multicast requests
 * from them. The average delay of multicast discovery requests that are
 * initiated is measured and must fall within the range
 * <code>0.25 * initialMulticastRequestDelayRange < averageDelay <
 * 0.75 * initialMulticastRequestDelayRange</code>.
 *
 * 
 */
public class MulticastRequestDelay extends QATestEnvironment implements Test {
    private static final int DISCOVERYPORT = Constants.discoveryPort;
    private static final int NUMLD = 100;
    private static final InetAddress localHost;
    private Configuration config;
    static {
	try {
	    localHost = InetAddress.getLocalHost();
	} catch (UnknownHostException ue) {
	    throw new RuntimeException(ue);
	}
    }
    private Throwable failure = null;
    private boolean done = false;
    private long acceptTime[] = new long[NUMLD];
    LookupDiscovery[] ldArray = new LookupDiscovery[NUMLD];
    
    private class AcceptThread extends Thread {
	private MulticastSocket socket;
	public AcceptThread() throws Exception {
	    super("multicast request");
	    setDaemon(true);
	    socket = new MulticastSocket(Constants.discoveryPort);
	    socket.joinGroup(Constants.getRequestAddress());
	}
	
	public void run() {
	    byte[] buf = new byte[576];
	    DatagramPacket dgram = new DatagramPacket(buf, buf.length);
	    int i = 0;
	    try {
		while (i < 100) {
		    dgram.setLength(buf.length);
		    socket.receive(dgram);
		    if (dgram.getAddress().equals(localHost)) {
			acceptTime[i] = System.currentTimeMillis();
			logger.log(Level.FINEST, "Received request " + i);
			i++;
		    }
		}
	    } catch (Throwable t) {
		failure = t;
	    } finally {
		synchronized (this) {
		    done = true;
		    this.notify();
		    socket.close();
		}
	    }
	}
    }
    
    public void run() throws Exception {
	long delay = Config.getLongEntry(config,
				    "net.jini.discovery.LookupDiscovery",
				    "initialMulticastRequestDelayRange",
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
	    ldArray[i] = new LookupDiscovery(
		    new String[] {
			InetAddress.getLocalHost().getHostName() + "_mrd_" +
				System.currentTimeMillis()
		    },
		    config);
	}
	synchronized(t) {
	    t.wait(delay * 3 / 2);
	}
	
	if (failure != null) {
	    throw new RuntimeException("Test failed ", failure);
	}
	
	if (!done) {
	    throw new RuntimeException("All "
		    + NUMLD + " multicast requests not received");
	}

	float averageDelay = 0f;
	for (int i = 0; i < NUMLD; i++) {
	    long timei = acceptTime[i] - startTime;
	    averageDelay = averageDelay + (timei / NUMLD);
	}
	logger.log(Level.FINE, "Average delay " + (long) averageDelay);
	if ((averageDelay < lBound) || (averageDelay > uBound)) {
	    throw new RuntimeException("Elapsed time out of expected range " +
					+ averageDelay +
					" lower bound " + lBound +
					" upper bound " + uBound);
	}
    }
    
    public Test construct(QAConfig qaconfig) throws Exception {
	super.construct(qaconfig);
	config = qaconfig.getConfig().getConfiguration();
        return this;
    }
 
    public void tearDown() {
	super.tearDown();
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i].terminate();
	}
    }
}
