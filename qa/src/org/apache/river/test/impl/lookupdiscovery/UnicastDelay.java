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
 * TestUnicastDelay.java
 *
 * Created on March 2, 2005, 3:18 PM
 */

package org.apache.river.test.impl.lookupdiscovery;

import org.apache.river.config.Config;
import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.EncodeIterator;
import org.apache.river.discovery.MulticastAnnouncement;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationProvider;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.Constants;
import net.jini.discovery.LookupDiscovery;

/**
 * Tests the <code>unicastDelayRange</code> configuration entry for
 * <code>LookupDiscovery</code>. It starts a number of
 * <code>LookupDisocvery</code> instances and then generates a multicast
 * announcement. The average delay of unicast discovery requests that are
 * initiated is measured and must fall within the range
 * <code>0.25 * unicastDelayRange < averageDelay <
 * 0.75 * unicastDelayRange</code>.
 *
 * 
 */
public class UnicastDelay extends QATestEnvironment implements Test {
    private static final int DISCOVERYPORT = Constants.discoveryPort;
    private static final int NUMLD = 100;
    private Throwable failure = null;
    private boolean done = false;
    private long acceptTime[] = new long[NUMLD];
    private Configuration config;
    LookupDiscovery[] ldArray = new LookupDiscovery[NUMLD];
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
		    logger.log(Level.FINEST, "accepted request " + i);
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
    
    public Test construct(QAConfig qaconfig) throws Exception {
	super.construct(qaconfig);
	config = qaconfig.getConfig().getConfiguration();
        return this;
    }
    
    public void run() throws Exception {

	long delay = Config.getLongEntry(config,
				    "net.jini.discovery.LookupDiscovery",
				    "unicastDelayRange",
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
	
	String[] groups = {InetAddress.getLocalHost().getHostName() + "_ud_" +
			   System.currentTimeMillis()};
	
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i] = new LookupDiscovery(groups, config);
	}
	Thread t = new AcceptThread();
	t.start();
	// Wait for AcceptThread to set up its socket.
	Thread.sleep(1000);
	
	MulticastSocket mcSocket = new MulticastSocket(DISCOVERYPORT);
	mcSocket.setTimeToLive(1);
	Discovery d = Discovery.getProtocol1();
	ServiceID sid = new ServiceID(5555, 4444);
	MulticastAnnouncement ma = new MulticastAnnouncement(
				    2222,
				    InetAddress.getLocalHost().getHostName(),
				    DISCOVERYPORT, 
				    groups,
				    sid);
							     
	final List packets = new ArrayList();
	EncodeIterator ei = d.encodeMulticastAnnouncement(ma, 1024, null);
	while (ei.hasNext()) {
	    packets.addAll(Arrays.asList(ei.next()));
	}
	DatagramPacket[] pArray = (DatagramPacket[]) packets.toArray(
				    new DatagramPacket[packets.size()]);
	for(int i=0; i<pArray.length; i++) {
	    mcSocket.send(pArray[i]);
	}//end loop
	
	long startTime = System.currentTimeMillis();
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
	    throw new RuntimeException("Elapsed time out of expected range "
					+ averageDelay +
					" lower bound " + lBound +
					" upper bound " + uBound);
	}
    }
    
    public void teardown() {
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i].terminate();
	}
    }
}
