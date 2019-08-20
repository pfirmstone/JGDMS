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
 * Revised on August 9, 2019, 11:53 AM AEST.
 */

package org.apache.river.test.impl.lookupdiscovery;

import org.apache.river.config.Config;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.Test;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.logging.Level;
import net.jini.config.Configuration;
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
    /* P. Firmstone - 19th Aug 2019
     *
     * Previously this test had a SkipConfigTestVerifier, for all configurations
     * except for jeri.  It is not known whether jeri was left out by accident,
     * as running with jeri has been a releatively recent occurrence 
     * for a long time tests have been run with none configuration, which 
     * would mean this test was skipped.  It seems this test was not completed.
     *
     * This test is meant to confirm the average delay time of the 
     * "initialMulticastRequestDelayRange" configuration option for LookupDiscovery
     * over a number of tests is within an expected range, from the middle of 
     * the delay duration, as the delay time is randomised between 0 and the
     * configured delay range.
     *
     * Originally there were 100 LookupDiscovery instances created, resulting
     * in significant creation time overhead, the last LookupDiscovery created
     * also incurred the creation time of 99 other LookupDiscovery objects,
     * which added significantly to the delay, resulting in test failures.
     * Further complicating the issue is previously started LookupDiscovery 
     * instances are also still sending Datagram packets, so we might be counting
     * datagram packets from the wrong LD.
    
     * A much better approach is to start each LookupDiscovery
     * individually, wait for the first repsonse, measure the delay, record it, 
     * stop the LookupDiscovery, then start another LookupDiscovery and so on,
     * and average the delays.   This way we are only incurring the start up
     * time of one LookupDiscovery in our delay time and we can be idempotent,
     * ignoring any Datagram packets that arrive before a LookupDiscovery starts.
     * The number of LookupDiscovery objects started has been reduced to ten,
     * this appears to be enough to confirm average delay time, 
     * if there are test failures, this number can be increased.
     *
     * Additionally the test was looking for the localhost IP address, 
     * rather than confirming that the network address originated from this
     * node, so it didn't recognise any received events.
     * 
     * After fixing this test, I found I needed to adjust LookupDiscovery
     * to reduce the configured delay by its creation time.  Unfortunately
     * in order to maintain the same average delay, we must also reduce
     * the upper bound, by a similar amount, resulting in a smaller standard 
     * deviation.
     * Following the range adjustment to allow for creation time of LD, with
     * 100 LD's the average is within 25ms of the required average delay.  With
     * 10 LD's the average is within 500ms of the required average delay, in any
     * case all test durations appear to be with the upper and lower bounds as
     * a result of the range adjustment in any case.  With 10 LD's the test
     * duration is 1 minute and 40 seconds, with 100, it's over 12 minutes.
     *
     * Obviously the longer the delay time configured, the
     * less impact LD's creation time will have on the standard deviation.
    */
    private static final int NUMLD = 10;
    private Configuration config;
    private Throwable failure = null;
    private boolean done = false;
    // Synchronize on acceptTime for startTime too.
    private final long acceptTime[] = new long[NUMLD];
    private final long startTime[] = new long[NUMLD];
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
		while (i < NUMLD) {
		    dgram.setLength(buf.length);
		    socket.receive(dgram);
                    long time = System.currentTimeMillis();
                    // We confirm that this is the local expected address used during testing, not some foreign.
                    NetworkInterface networkInterface = NetworkInterface.getByInetAddress(dgram.getAddress());
		    if (networkInterface != null) { 
                        synchronized (acceptTime){
                            if (startTime[i] == 0) continue;  // IGNORE: We haven't started this must be another from the last LD. Idempotent.
                            acceptTime[i] = time;
                        }
                        synchronized (this){
                            this.notify();
                        }
			logger.log(Level.FINEST, "Received request {0}", i);
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
    
    @Override
    public void run() throws Exception {
	long delay = Config.getLongEntry(config,
				    "net.jini.discovery.LookupDiscovery",
				    "initialMulticastRequestDelayRange",
				    10000, 0, Long.MAX_VALUE);
	long expectedDelay = delay / 2;
	logger.log(Level.FINE, "Expected average delay {0}", expectedDelay);
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
	
	for (int i = 0; i < NUMLD; i++) {
            LookupDiscovery ld = null;
            synchronized (acceptTime){
                startTime[i] = System.currentTimeMillis();
            }
            try {
                 ld = new LookupDiscovery(
                        new String[] {
                            InetAddress.getLocalHost().getHostName() + "_mrd_" +
                                    System.currentTimeMillis()
                        },
                        config);
                synchronized(t) {
                    t.wait(delay * 3 / 2);
                }
            } finally {
                if (ld != null) ld.terminate();
                Thread.sleep(2000); // Allow some time for remaining events to be ignored.
            }
	}
	
	if (failure != null) {
	    throw new RuntimeException("Test failed ", failure);
	}
	
	if (!done) {
	    throw new RuntimeException("All "
		    + NUMLD + " multicast requests not received");
	}

	float averageDelay = 0f;
        synchronized (acceptTime){
            for (int i = 0; i < NUMLD; i++) {
                logger.log(
                        Level.FINEST, "Start time: {0}, Finish time: {1}, Delay: {2}",
                        new String [] {
                            Long.toString(startTime[i]),
                            Long.toString(acceptTime[i]),
                            Long.toString(acceptTime[i] - startTime[i])
                        }
                );
                long timei = acceptTime[i] - startTime[i];
                averageDelay = averageDelay + (timei / NUMLD);
            }
        }
	logger.log(Level.FINE, "Average delay {0}", (long) averageDelay);
	if ((averageDelay < lBound) || (averageDelay > uBound)) {
	    throw new RuntimeException("Elapsed time out of expected range " +
					+ averageDelay +
					" lower bound " + lBound +
					" upper bound " + uBound);
	}
    }
    
    @Override
    public Test construct(QAConfig qaconfig) throws Exception {
	super.construct(qaconfig);
	config = qaconfig.getConfig().getConfiguration();
        return this;
    }
 
    @Override
    public void tearDown() {
	super.tearDown();
	for (int i = 0; i < NUMLD; i++) {
	    ldArray[i].terminate();
	}
    }
}
