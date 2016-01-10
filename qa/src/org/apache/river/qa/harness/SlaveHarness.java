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

package org.apache.river.qa.harness;

import java.io.File;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.JarFile;

/**
 * A 'harness' which accepts work requests from a master over
 * a socket. 
 * <p>
 * Generation of the MasterTest and SlaveTest command lines:
 * <p>
 * There are 4 VMs which participate in the distributed test  process:
 * <ul>
 *   <li>the master harness
 *   <li>the slave harness
 *   <li>the master test
 *   <li>the slave test request handler
 * <ul>
 * All 4 of these VMs maintain an instance of QAConfig. Because the
 * VMs are running on different systems, potentially with different
 * versions of the jdk/jsk kits, the values which define installation 
 * directories for the master/slave tests are defined as system properties
 * on the test VMs invoked by the master/slave harnesses.
 * <p>
 * When the MasterHarness and SlaveHarness classes are instantiated,
 * they construct an instance of QAConfig. The constructor of QAConfig
 * verifies the existance of the following system properties:
 * <ul>
 *    <li>org.apache.river.jsk.home
 *    <li>org.apache.river.qa.home
 * </ul>
 * which are typically provided by the user config file. These values serve as
 * the default values to apply to the master/slave test VMs.  When the
 * <code>MasterHarness</code> creates the master test VM, it obtains the command
 * line from the <code>TestDescription</code>.  When generating the command
 * line, the TD temporarily sets override properties generated from the QAConfig
 * getSystemProps("master") method.  Any master overrides supplied (indirectly)
 * through the environment file (provided by the -env command line option) will
 * be used to build the command line. Typically, one or more of the installation
 * system properties listed above will be replaced if the test is to use a
 * non-default kit. The master harness always provides property definitions for
 * all of these properties so that policy files and ConfigurationFile entries
 * have access to these definitions. The MasterHarness passes a serialized copy
 * of its QAConfig to the master test over its System.in stream; it is not
 * necessary for the master test to construct a QAConfig from scratch. Because
 * system property definitions override the user configuration file values when
 * QAConfig searches for a property, the values set on the command line
 * by the harness will take precedence.
 * <p>
 * When the <code>MasterHarness</code> submits a request to the slave
 * harness to create a <code>SlaveTest</code>, it also passes
 * a serialized copy of its QAConfig. The <code>SlaveHarness</code>
 * generates a command line, this time using overrides resulting from
 * calling the QAConfig getSystemProps("slave") method. The information
 * required to generate the slave overrides are carried in the serialized
 * QAConfig object, so the <code>MasterHarness</code> controls the
 * environment in which the <code>SlaveTest</code> runs.
 */
class SlaveHarness {

    /** The port used for piping System.out/System.err to the master */
    final static int LOG_PORT=10001;

    /** The port used to accept request messages */
    final static int REQUEST_PORT=10002;

    /** the logger */
    private static Logger logger =
	Logger.getLogger("org.apache.river.qa.harness");

    /** 
     * The config object used directly by this class. It's primary
     * purpose is to establish default values for installation
     * properties such as org.apache.river.jsk.home. The config used
     * by the request handler vm is provided by the master harness
     * and may provide overrides for the installation properties.
     */
    QAConfig config;

    /** The list of slaves participating in this test run */
    private static ArrayList slaveList = new ArrayList();

    /** Data structure holding slave info */
    private static class SlaveData {
	String name;
	InetAddress addr;
	Socket logSocket;
	Pipe pipe;
    };
    
    /**
     * Called by the master harness to connect to all slave harnesses
     * to be used by the test. If the test property
     * org.apache.river.qa.harness.testhosts specifies any slave harnesses,
     * create the pipes to capture any output from the slave harnesses
     * into the test log. Failure to connect to any slave within 5
     * minutes (XXX parameterize) results in a TestException being
     * thrown, aborting the test run.
     * 
     * @throws TestException on connection timeout
     */
    static void connect() throws TestException {
	ArrayList hostList = QAConfig.getConfig().getHostList();
	if (hostList.size() < 2) {
	    return;
	}
	// connect to all slaves and build slaveList
	for (int i = 1; i < hostList.size(); i++) {
	    String host = (String) hostList.get(i);
	    SlaveData slave = new SlaveData();
	    slave.name = host;
	    connectToSlave(slave); // throws exception on timeout
	    slaveList.add(slave);
	}
    }

    /**
     * Called by the master harness to set the log stream for
     * all slaves. Used to allow log output to be discarded
     * for passing tests.
     */
    static void setLogStreams(PrintStream stream) {
	for (int i = 0; i < slaveList.size(); i++) {
	    SlaveData slave = (SlaveData) slaveList.get(i);
	    slave.pipe.setStream(stream);
	}
    }

    /**
     * Connect to the given slave harness. Repeatedly attempt to open a socket
     * to the slave log port. When successful, examine the value of the
     * test property org.apache.river.qa.harness.slavepipe. If defined and false,
     * write a 0 to the socket. Otherwise, write a 1 to the socket. The slave
     * reads this value to determine whether to pipe output through this socket.
     * In either case, establish a pipe to pass input from the socket to the 
     * logger. 
     *
     * @param slave the <code>SlaveData</code> corresponding to the slave
     * @throws TestException if slave host is unknown or on timeout
     */
    private static void connectToSlave(SlaveData slave) throws TestException {
	try {
	    slave.addr = InetAddress.getByName(slave.name);
	} catch (UnknownHostException e) {
	    throw new TestException("Unexpected exception", e);
	}
	for (int i = 0; i < 120; i++) { // try for 20 min at 10 sec intervals
	    try {
		slave.logSocket = new Socket(slave.addr, LOG_PORT);
	        OutputStream os = slave.logSocket.getOutputStream();
	        boolean doPipe = 
		    QAConfig.getConfig().getBooleanConfigVal(
					  "org.apache.river.qa.harness.slavepipe", 
					  true);
		os.write((doPipe? 1 : 0));
	 	os.flush();
		slave.pipe = new Pipe("slave-" + slave.name,
				      slave.logSocket.getInputStream(),
				      System.out,
				      null,
				      null);
                slave.pipe.start();
		return;
	    } catch (ConnectException ignore) {
	    } catch (IOException e) {
		throw new TestException("IOException connecting to " 
					+ slave.name,
					e);
	    }
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
	    }
	}
	throw new TestException("Timeout connecting to slave " + slave.name);
    }

    /**
     * Sends a <code>HarnessRequest</code> object to a slave harness.
     * A socket to the given slave's request port is opened and
     * the request object is written to it. Then a read is hung
     * on the socket. The object read from the socket is returned
     * by this method; null is returned if the read throws an
     * EOFException. In either case the socket is closed.
     *
     * @param slave the <code>SlaveData</code> describing the slave
     * @param request the request to send
     * @returns the reply object send by the slave, or null if the
     *          slave sent no reply
     * @throws TestException on a connection or communication failure
     */
    private static Object sendHarnessRequest(SlaveData slave, 
					     HarnessRequest request)
	throws TestException
    {
	logger.log(Level.FINER,
		   "Sending request to " + slave.name + ": " + request);
	Socket s = null;
	try {
	    s = new Socket(slave.addr, REQUEST_PORT);
	    ObjectOutputStream oos =
		new ObjectOutputStream(s.getOutputStream());
	    oos.writeObject(request);
	    oos.flush();
	} catch (Exception e) {
	    // fatal, so don't worry about closing sockets/streams
	    throw new TestException("Unexpected exception sending " 
				    + "request to slave " 
				    + slave.name,
				    e);
	}
	ObjectInputStream ois = null;
	try {
	    ois = new ObjectInputStream(s.getInputStream());
	    Object o = ois.readObject();
	    return o;
	} catch (EOFException e) {
	    return null;
	} catch (Exception e) {
	    throw new TestException("Unexpected exception receiving " 
				    + "response from slave "
				    + slave.name, e);
	} finally {
	    try {
		ois.close();
		s.close(); // redundant, I think
	    } catch (Exception ignore) {
	    }
	}
    }

    /**
     * Broadcast the given request to all participating slaves.
     *
     * @param request the request to broadcast
     * @throws TestException on a connection or communication failure
     */
    static void broadcastRequest(HarnessRequest request) 
	throws TestException 
    {
	for (int i = 0; i < slaveList.size(); i++) {
	    SlaveData slave = (SlaveData) slaveList.get(i);
	    sendHarnessRequest(slave, request);
	}
    }

    /**
     * Construct the slave harness and its associated default config object.
     * Exits the vm if the arg list is empty or an exception occurs while
     * constructing the config object.
     *
     * @param args the command line args
     */
    SlaveHarness(String[] args) {
	if (args.length < 1) {
	    logger.log(Level.SEVERE, "Missing arguments");
	    System.exit(1);
	}
	try {
	    config = new QAConfig(args);
	} catch (Exception e) {
	    e.printStackTrace();
	    logger.log(Level.SEVERE, 
		       "Unexpected exception constructing config", 
		       e);
	    System.exit(1);
	}
	// set these system properties so they will override any install
	// properties included in a config sent by the master
	System.setProperty("org.apache.river.qa.home", config.getKitHomeDir());
	System.setProperty("org.apache.river.jsk.home", config.getJSKHomeDir());
	System.setProperty("org.apache.river.jsk.port", 
			   config.getStringConfigVal("org.apache.river.jsk.port", 
						     null));
	System.setProperty("org.apache.river.qa.port", 
			   config.getStringConfigVal("org.apache.river.qa.port",
						     null));
	System.setProperty(
		"org.apache.river.qa.harness.runjiniserver", 
		config.getStringConfigVal("org.apache.river.qa.harness.runjiniserver",
					  null));
	System.setProperty(
		"org.apache.river.qa.harness.runkitserver", 
		config.getStringConfigVal("org.apache.river.qa.harness.runkitserver", 
					  null));
	boolean genHtml = config.getBooleanConfigVal(
	    "org.apache.river.qa.harness.generateHtml", false);
	if (genHtml) {
	    try {
		HtmlReport htmlReport = new HtmlReport(config, null);
		htmlReport.generate();
	    } catch (Exception e) {
		logger.log(Level.SEVERE, "Exception trying to generate index.html", e);
	    }
	}
    }

    /**
     * Start the request handler thread and wait for a connection to the
     * logging port. When the log connection is made, read a byte from the
     * socket. If the value of the byte is non-zero, redirect System.out
     * and System.err and logger output to the sockets output stream. 
     * Start the keep-alive thread which will detect the death of the master.
     * A System.exit is done if any exceptions occur.
     */
    void handleRequests() {
	new Thread(new RequestHandler(), "RequestThread").start();
	try {
	    ServerSocket socket = new ServerSocket(LOG_PORT);
	    Timeout.TimeoutHandler handler = 
		new Timeout.ServerSocketTimeoutHandler(socket);
	    Timeout timeout = new Timeout(handler, 20 * 60 * 1000); // 20 min
	    timeout.start();
	    Socket logSocket = socket.accept();
	    timeout.cancel();
	    InputStream is = logSocket.getInputStream();
	    int pipeFlag = is.read();
	    PrintStream ps = new PrintStream(logSocket.getOutputStream());
	    if (pipeFlag !=0) {
		System.setOut(ps);
		System.setErr(ps);
		reconfigureLogger();
	    }
	    InetAddress masterAddress = logSocket.getInetAddress();
	    new Thread(new Keepalive(masterAddress), "KeepAlive").start();
	} catch (Exception e) {
	    logger.log(Level.SEVERE, "Unexpected exception", e);
	    System.exit(1);
	}
    }
    
    /**
     * Replace the <code>ReportHander</code> bound to the logger with a new
     * instance of <code>ReportHandler</code>. This is necessary when output
     * is redirected because the handler caches the value of System.err/out.
     */
    private void reconfigureLogger() {
	Handler[] handlers = logger.getHandlers();
	for (int i = 0; i < handlers.length; i++) {
	    if (handlers[i] instanceof ReportHandler) {
		logger.removeHandler(handlers[i]);
	    }
	}
	logger.addHandler(new ReportHandler());
    }

    /**
     * A thread which opens a socket to the master harness keep-alive
     * port and hangs a read on the socket. If an EOF occurs, or any
     * IOException is thrown, a System.exit is done.
     */
    private class Keepalive implements Runnable {

	InetAddress addr;

	public Keepalive(InetAddress addr) {
	    this.addr = addr;
	}

	public void run() {
	    try {
		Socket s = new Socket(addr, MasterHarness.KEEPALIVE_PORT);
		InputStream is = s.getInputStream();
		while (true) {
		    if (is.read() == -1) {
			//best effort teardown in case master died. Do this
			//because the System.exit doesn't seem to cleanup
			try {
			    callLocalSlaveHandler(new TeardownRequest());
			} catch (Exception ignore) {
			}
			System.exit(1);
		    }
		}
	    } catch (IOException e) {
		e.printStackTrace();
		System.exit(1);
	    }
	}
    }

    /**
     * A thread which processes slave <code>HarnessRequest</code> objects.
     * A socket is accepted, the request object read from the socket
     * input stream, and the requests <code>doHarnessRequest</code> method
     * is called. On return, the request socket is closed; no return object
     * is written to the socket. Any exception thrown will result in a
     * System.exit.
     */
    private class RequestHandler implements Runnable {

	ServerSocket socket;
	Socket requestSocket;
	Class policyClass = null;

	public void run() {
	    if (policyClass == null) {
		try {
		    policyClass = 
			Class.forName("org.apache.river.qa.harness.MergedPolicyProvider");
		    if (policyClass.getClassLoader().getParent() != null) {
			logger.log(Level.SEVERE, 
				   "MergedPolicyProvider must be "
				 + "installed in an extensions directory");
//			System.exit(1);
		    }
		} catch (Exception e) {
		    logger.log(Level.SEVERE, 
			       "failed to find MergedPolicyProvider");
		    System.exit(1);
		}
	    }
	    try {
		socket = new ServerSocket(REQUEST_PORT);
		while (true) {
		    requestSocket = socket.accept();
		    ObjectInputStream ois = 
			new ObjectInputStream(requestSocket.getInputStream());
		    HarnessRequest request = (HarnessRequest) ois.readObject();
		    logger.log(Level.FINER, 
			       "Received harness request: " + request);
		    request.doHarnessRequest(SlaveHarness.this);
		    ois.close();
		    requestSocket.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
		System.exit(1);
	    }
	}
    }

    /**
     * Start the slave request handler VM. This is a callback to be used
     * by a request handler. The given config object is used to construct
     * the command line and is passed to the handler vm as a serialized
     * object written to the processes System.in stream. Note that the
     * given config is NOT the config associated with the slave harness,
     * but is the serialized copy sent by the master harness in the
     * request message. This method does not return until the request
     * handlers request port is ready to accept requests.
     *
     * @param config the config object associated with the test
     */
    void startSlaveTest(QAConfig masterConfig) {
	if (getHarnessJar() != null) {
	    masterConfig.setDynamicParameter("harnessJar", getHarnessJar());
	} 
	if (config.getStringConfigVal("testJar", null) != null) {
	    masterConfig.setDynamicParameter("testJar", config.getStringConfigVal("testJar", null));
	} 
	masterConfig.buildSearchList(config.getStringConfigVal("searchPath", ""));
	// configuration used to be reread by QAConfig.readObject, but jar references
	// need to be fixed up before loading the configuration, and the local
	// testJar param is not available to readObject
	try {
	    masterConfig.loadTestConfiguration();
	} catch (TestException e) {
	    e.printStackTrace();
	}
        try {
	    /*
	     * the following should theoretically be done with overrides set,
	     * since the qa kit may not be default and the TD for the test
	     * could be different. Ignoring this for now, due to an expectation
	     * that ultimately all qa kits must be the same on participants
	     * to avoid QAConfig versioning problems
	    */
	    TestDescription td = masterConfig.getTestDescription();
	    /* 
	     * The getSystemProps method returns a collection of properties
	     * which override the default values. These properties will
	     * typically be org.apache.river.jsk.home, etc. In many cases
	     * this collection will be empty.
	     */
	    Properties overrideProperties = new Properties();
	    String overrideProp = 
		config.getStringConfigVal("org.apache.river.qa.harness.slaveOverrides",
					  null);
	    if (overrideProp != null) {
		String[] overrides = config.parseString(overrideProp, ", "); // null OK
		for (int i = 0; i < overrides.length; i++) {
		    String key = overrides[i];
		    String value = config.getStringConfigVal(key, null);
		    if (value != null) {
			masterConfig.setDynamicParameter(key, value);
			overrideProperties.setProperty(key, value);
		    }
		}
	    }
	    masterConfig.setHostNameToken();
//XXXXXXX NEED TO SET NEW VALUES FOR testJar AND harnessJar !!!!!!
	    String[] cmdArray = 
//  		td.getCommandLine(masterConfig.getSystemProps("slave"));
		td.getCommandLine(overrideProperties);
	    if (logger.isLoggable(Level.FINE)) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < cmdArray.length; i++) {
		    if (cmdArray[i].indexOf(' ') >= 0) {
			sb.append("'").append(cmdArray[i]).append("' ");
		    } else {
			sb.append(cmdArray[i]).append(" ");
		    }
		}
		logger.log(Level.FINE, 
			   "Starting slave request handler "
			 + "in separate process with command:");
		logger.log(Level.FINE, sb.toString());
	    }
	    File workingDir = td.getWorkingDir();
            Process proc = 
		Runtime.getRuntime().exec(cmdArray, null, workingDir);
	    ObjectOutputStream os = 
		new ObjectOutputStream(proc.getOutputStream());
	    os.writeObject(masterConfig);
	    os.flush();
            bindOutput(proc);
	    if (! waitForSlaveTest()) {
		//XXX should I throw an exception here?
		logger.log(Level.SEVERE, 
			   "No response from slave request handler");
	    }
	    // an exception here seems pretty fatal, so log it and exit
	    // Delay to allow output to drain???
        } catch (Throwable e) {
	    logger.log(Level.INFO, "Unexpected exception", e);
	    System.exit(1);
	} 
    }

    // return null if not found - support running from classes directory
    private String getHarnessJar() {
	String classpath = System.getProperty("java.class.path");
	StringTokenizer tok = 
	    new StringTokenizer(classpath, File.pathSeparator);
	while (tok.hasMoreTokens()) {
	    String path = tok.nextToken();
	    JarFile jarFile;
	    try {
		jarFile = new JarFile(path);
	    } catch (IOException e) {
		logger.log(Level.FINEST, "failed to open jar file " + path, e);
		continue;
	    }
	    if (jarFile.getEntry("org/apache/river/qa/harness/QARunner.class") 
		                  != null) 
	    {
		return path;
	    }
	}
	return null;
    }

    /**
     * Repeatedly attempt to send ping requests to the test request handler
     * running on this host. This method returns when a ping is sent
     * successfully, or after a ten-second timeout expires.
     * 
     * @return true if a ping was went successfully, false if all pings failed
     */
    private boolean waitForSlaveTest() {
	for (int i = 0; i < 20; i++) {
	    try {
		callLocalSlaveHandler(new PingRequest());
		return true;
	    } catch (Exception ignore) {
	    }
	    try {
		Thread.sleep(500);
	    } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
	    }
	}
	return false;
    }

    /**
     * Call the test request handler on the local system. Any exception
     * thrown is propogated to the caller
     *
     * @param request the message to send
     * @throws Exception if the local host name cannot be resolved, or if
     *         the call to the request handler throws an exception.
     */
    private void callLocalSlaveHandler(SlaveRequest request) throws Exception {
	String name = InetAddress.getLocalHost().getHostName();
	SlaveTest.call(name, request);
    }

    /**
     * Bind the given process System.out/System.err streams to this 
     * objects logger.
     *
     * @param proc the process to bind
     * @throws IOException if there is a problem getting the I/O streams
     */
    private void bindOutput(Process proc) throws IOException {
        new Pipe("slaveharness-out", 
		 proc.getInputStream(),
		 System.out, 
		 null,
		 null).start();
	new Pipe("slaveharness-err", 
		 proc.getErrorStream(),
		 System.out,
		 null,
		 null).start();
    }
}
