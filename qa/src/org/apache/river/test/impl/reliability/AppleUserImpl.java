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
/* @test 1.6 03/10/28
 *
 * @summary The juicer is the classic RMI stress test.  The juicer makes
 * a large number of concurrent, long running, remote method invocations
 * between many threads which have exported remote objects.  These
 * threads use remote objects that carry on deep "two party"
 * recursion.  The juicer relies on Distributed Garbage Collection to 
 * unexport these remote objects when no more references are held to them
 * (thus in JERI, dgc must be enabled for this test to function).
 * The two parties in the recursion are OrangeImpl and
 * OrangeEchoImpl.  OrangeImpl checks the base case of the recursion
 * so that the program will exit.
 *
 * When the run() method is invoked, the class binds an
 * instance of itself in a registry.  A second server process,
 * an ApplicationServer, is started which looks up the recently 
 * bound AppleUser object.  The default is to start the server in
 * the same VM.  If the test is being run in distributed mode, then
 * the server will be started in a separate VM on a different host.
 *
 * The second server process instructs the AppleUserImpl to "use" some apples.
 * AppleUserImpl creates a new thread for each apple.  These threads
 * initiate the two party recursion.
 * 
 * Each recursive call nests to a depth determined by this
 * expression: (2 + Math.abs(random.nextInt() % (maxLevel + 1)), 
 * where maxLevel is defined by the property 
 * org.apache.river.test.impl.reliabililty.maxLevel.  Thus each recursive
 * call nests a random number of levels between 2 and maxLevel.
 * 
 * The test ends when an exception is encountered or the stop time
 * has been reached.
 *
 * Test properties are:
 *
 *     org.apache.river.test.impl.reliabililty.minutes  
 *           The number of minutes to run the juicer.
 *           The default is 1 minute.
 *     org.apache.river.test.impl.reliabililty.maxLevel 
 *           The maximum number of levels to
 *           recurse on each call.
 *           The default is 7 levels.
 *
 * @author Peter Jones, Nigel Daley
 */
package org.apache.river.test.impl.reliability;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.util.Random;
import java.util.logging.Level;
import java.net.InetAddress;

import net.jini.config.Configuration;
import net.jini.export.Exporter;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.SlaveRequest;
import org.apache.river.qa.harness.SlaveTest;
import org.apache.river.qa.harness.Test;

/**
 * The AppleUserImpl class implements the behavior of the remote
 * "apple user" objects exported by the server.  The application server
 * passes each of its remote "apple" objects to an apple user, and an
 * AppleUserThread is created for each apple.
 */
public class AppleUserImpl extends QATestEnvironment implements AppleUser, Test {

    private int threadNum = 0;
    private long testDuration;
    private int maxLevel;
    private Exception status = null;
    private boolean finished = false;
    private boolean startTestNotified = false;
    private final Random random = new Random();
    private final Object lock = new Object();
    private long startTime = 0;
    private Exporter exporter;
    
    /**
     * Allows the other server process to indicate that it is ready
     * to start "juicing".
     */
    public synchronized void startTest() throws RemoteException {
	startTestNotified = true;
        this.notifyAll();
    }

    /**
     * Allows the other server process to report an exception to this
     * process and thereby terminate the test.
     */
    public void reportException(Exception status) throws RemoteException {
	synchronized (lock) {
	    this.status = status;
	    lock.notifyAll();
        }
    }

    /**
     * "Use" supplied apple object.  Create an AppleUserThread to
     * stress it out.
     */
    public synchronized void useApple(Apple apple) throws RemoteException {
	String threadName = Thread.currentThread().getName();
	logger.log(Level.FINEST, 
	    threadName + ": AppleUserImpl.useApple(): BEGIN");

	AppleUserThread t =
	    new AppleUserThread("AppleUserThread-" + (++threadNum), apple);
	t.start();

	logger.log(Level.FINEST, 
	    threadName + ": AppleUserImpl.useApple(): END");
    }
    
    /**
     * The AppleUserThread class repeatedly invokes calls on its associated
     * Apple object to stress the RMI system.
     */
    class AppleUserThread extends Thread {

	final Apple apple;

	public AppleUserThread(String name, Apple apple) {
	    super(name);
	    this.apple = apple;
	}

	public void run() {
	    int orangeNum = 0;
            long stopTime = System.currentTimeMillis() + testDuration;
	    
	    try {
	        do { // loop until stopTime is reached

		    /*
		     * Notify apple with some apple events.  This tests
                     * serialization of arrays.
		     */
                    int numEvents = Math.abs(random.nextInt() % 5); 
		    AppleEvent[] events = new AppleEvent[numEvents];
		    for (int i = 0; i < events.length; i++) {
			events[i] = new AppleEvent(orangeNum % 3);
		    }
		    apple.notify(events);

		    /*
		     * Request a new orange object be created in 
                     * the application server.
		     */
		    Orange orange = apple.newOrange(
		        "Orange(" + getName() + ")-" + (++orangeNum));

		    /*
		     * Create a large message of random ints to pass to orange.
		     */
                    int msgLength = 1000 + Math.abs(random.nextInt() % 3000);
		    int[] message = new int[msgLength];
		    for (int i = 0; i < message.length; i++) {
			message[i] = random.nextInt();
		    }

		    /*
		     * Invoke recursive call on the orange.  Base case
                     * of recursion inverts messgage.
		     */
		    OrangeEcho orangeEchoProxy = (
			new OrangeEchoImpl(
				"OrangeEcho(" + getName() + ")-" + orangeNum)
			).export();
		    int[] response = orange.recurse(orangeEchoProxy, message,
			2 + Math.abs(random.nextInt() % (maxLevel + 1)));

		    /*
		     * Verify message was properly inverted and not corrupted
		     * through all the recursive method invocations.
		     */
		    if (response.length != message.length) {
			throw new RuntimeException(
			    "ERROR: CORRUPTED RESPONSE: " +
			    "wrong length of returned array " + "(should be " +
			    message.length + ", is " + response.length + ")");
		    }
		    for (int i = 0; i < message.length; i++) {
			if (~message[i] != response[i]) {
			    throw new RuntimeException(
			        "ERROR: CORRUPTED RESPONSE: " +
			        "at element " + i + "/" + message.length +
				" of returned array (should be " +
				Integer.toHexString(~message[i]) + ", is " +
				Integer.toHexString(response[i]) + ")");
			}
		    }

	            try {
	                Thread.sleep(Math.abs(random.nextInt() % 10) * 1000);
	            } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
	            }

	        } while (System.currentTimeMillis() < stopTime);

	    } catch (Exception e) {
	        status = e;
	    }
	    finished = true;
	    synchronized (lock) {
	        lock.notifyAll();
	    }
	}
    }

    // inherit javadoc
    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
        return this;
    }

    /**
     * Entry point for the "juicer" server process.  Create and export
     * an apple user implementation in an rmiregistry running on localhost.
     */
    public void run() throws Exception {
	// run ApplicationServer on a separate host if running distributed
        boolean othervm = (QAConfig.getConfig().getHostList().size() > 1);
	maxLevel = getConfig().getIntConfigVal(
            "org.apache.river.test.impl.reliability.maxLevel",7);
	testDuration = getConfig().getIntConfigVal(
            "org.apache.river.test.impl.reliability.minutes",1) * 60 * 1000;

        Configuration c = QAConfig.getConfig().getConfiguration();
        exporter = (Exporter) c.getEntry("test",
                                         "reliabilityExporter",
                                         Exporter.class);
	AppleUser appleUserProxy = (AppleUser) exporter.export(this);

        Thread server = null;
	synchronized (this) {
	    // create new registry and bind new AppleUserImpl in registry
            LocateRegistry.createRegistry(2006);
            LocateRegistry.getRegistry(2006).rebind(
		"AppleUser",appleUserProxy);
    
	    // start the other server if applicable
	    if (othervm) {
	        // the other server must be running in a separate process
	        logger.log(Level.INFO, "Application server will be " +
		    "started on a separate host");
		SlaveRequest sr = new StartApplicationServerRequest(
		    InetAddress.getLocalHost().getHostName());
		SlaveTest.broadcast(sr);
	    } else {
	        Class app = Class.forName(
		    "org.apache.river.test.impl.reliability.ApplicationServer");
	        server = new Thread((Runnable) app.newInstance());
	        logger.log(Level.INFO, "Starting application server " +
                    "in same process");
	        server.start();
	    }

	    // wait for other server to call startTest method
	    logger.log(Level.INFO, "Waiting for application server " +
                "process to start");
	    while (!startTestNotified) {
	       this.wait();
	    }
	}

	startTime = System.currentTimeMillis();
	logger.log(Level.INFO, "Test starting");

	// wait for exception to be reported or first thread to complete
	logger.log(Level.INFO, "Waiting " + (testDuration/60000) + 
	    " minutes for test to complete or exception to be thrown");

	synchronized (lock) {
	    while (status == null && !finished) {
		lock.wait();
	    }
	}

	if (status != null) {
	    throw new RuntimeException("TEST FAILED: "
		+ "juicer server reported an exception", status);
	} else {
	    logger.log(Level.INFO, "TEST PASSED");
        }
    }

    // inherit javadoc
    public void tearDown() {
	long actualDuration = System.currentTimeMillis() - startTime;
	logger.log(Level.INFO, "Test finished");
	exporter.unexport(true);
	logger.log(Level.INFO, "Test duration was " +
	    (actualDuration/1000) + " seconds " +
	    "(" + (actualDuration/60000) + " minutes or " +
	    (actualDuration/3600000) + " hours)");
	super.tearDown();
    }

}
