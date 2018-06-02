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

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import net.jini.config.Configuration;
import org.apache.river.api.security.CombinerSecurityManager;
import org.apache.river.tool.SecurityPolicyWriter;

/**
 * The slave side of a distributed test. This class provides the main
 * method for in its own VM, provides static utility method for
 * sending messages to a <code>SlaveTest</code> instance, and implement
 * a message handler to receive and execute <code>SlaveRequest</code>
 * messages.
 */
public class SlaveTest {

    /** the port for receiving <code>SlaveRequest</code> messages */
    private final static int REQUEST_PORT=10003;

    /** the logger */
    private static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** the original <code>System.err</code> stream for this VM */
    private static PrintStream origErr;

    /** the test properties, unmarshalled from <code>System.in</code> */
    static QAConfig config; // MasterTest access hack

    /** a flag indicating that the VM should <code>System.exit</code> */
    private static boolean doExit = false;

    /** the admin manager */
    static AdminManager manager; // MasterTest access hack

    /** persistent storage across calls */
    private static HashMap storageMap = new HashMap();

    /**
     * Utility method to send a <code>SlaveRequest</code> message
     * to a SlaveTest instance.
     *
     * @param name the name of the slave host
     * @param request the request to send
     * @return the object by the <code>SlaveRequest.doSlaveRequest</code> method
     * @throws TestException if the call fails
     */
    public static Object call(String name, SlaveRequest request) 
	throws TestException 
    {
	Socket s = null;
	logger.log(Level.FINE, 
		   "Sending request to slave test on " + name + ": " + request);
	try {
	    s = new Socket(name, REQUEST_PORT);
	    ObjectOutputStream oos =
		new ObjectOutputStream(s.getOutputStream());
	    oos.writeObject(request);
	    oos.flush();
	    //	    oos.close();
	} catch (Exception e) {
	    // fatal, so don't worry about closing sockets/streams
	    throw new TestException("Unexpected exception sending " 
				    + "request to slave " 
				    + name, e);
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
				    + name);
	} finally {
	    try {
		ois.close();
		s.close(); // redundant, I think
	    } catch (Exception ignore) {
	    }
	}
    }

    /**
     * Utility method to 'broadcast' a <code>SlaveRequest</code> message
     * to all participating <code>SlaveTest</code>s. The request
     * is sent to each participant in sequence. Failures are logged,
     * but otherwise ignored.
     *
     * @param request the <code>SlaveRequest</code> to send
     */
    public static void broadcast(SlaveRequest request) {
	List hostList = QAConfig.getConfig().getHostList();
	if (hostList.size() < 2) {
	    return;
	}
	for (int i = 1; i < hostList.size(); i++) {
	    String name = (String) hostList.get(i);
	    try {
		call(name, request);
	    } catch (Exception e) {
		logger.log(Level.INFO, "Call to slave threw exception", e);
	    }
	}
    }

    /**
     * Wait for all slaves to die. All participating slave are
     * pinged once per second until <code>timeout</code> seconds
     * elapse. When all pings throw <code>Exception</code>,
     * the slaves are assumed to be dead and this method returns.
     *
     * @param timeout maximum time in seconds to way for slave death. If
     *        zero or negative, this method returns immediately
     */ 
    public static void waitForSlaveDeath(int timeout) {
	List hostList = QAConfig.getConfig().getHostList();
	if (hostList.size() < 2) {
	    return;
	}
	List slaveList = new ArrayList();
	for (int i = 1; i < hostList.size(); i++) {
	    slaveList.add(hostList.get(i));
	}
	PingRequest ping = new PingRequest();
	for (int i = 0; i < timeout; i++) {
	    for (int j = slaveList.size() - 1; j >= 0; j--) {
		String slaveName = (String) slaveList.get(j);
		try {
		    call(slaveName, ping);
		} catch (Exception e) {
		    slaveList.remove(j);
		}
	    }
	    if (slaveList.size() == 0) {
		return;
	    }
	    try {
		Thread.sleep(1000);
	    } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore
	    }
	}
    }
	

    /**
     * The main method for the slave test VM. The <code>QAConfig</code>
     * object is read from <code>System.in</code>, and <code>AdminManager</code>
     * is instantiated, class servers are started, and the request
     * handling loop is entered (optionally in the context of a user login).
     * The <code>args</code> passed to the main method are not used; the
     * args supplied to the master test are available in <code>QAConfig</code>.
     *
     * @param args the command line arguments (unused)
     */
    public static void main(String[] args) {
	origErr = System.err;
	System.setErr(System.out);
	if (System.getSecurityManager() == null) {
//	    System.setSecurityManager(new SecurityPolicyWriter());// Seems to be ok here with jsse
	    System.setSecurityManager(new CombinerSecurityManager());
	}
	try {
	    ObjectInputStream ois = new ObjectInputStream(System.in);
	    config = (QAConfig) ois.readObject();
	} catch (Exception e) {
            e.fillInStackTrace();
	    logger.log(Level.SEVERE, "Unexpected exception ", e);
	    System.exit(1);
	}
	// used to be handled by config.readObject, but this broke SlaveHarness
	try {
	    config.loadTestConfiguration();
	} catch (TestException e) {
            e.fillInStackTrace();
	    logger.log(Level.SEVERE, "Unexpected exception ", e);
	}
	manager = new AdminManager(config);
	Configuration c = config.getConfiguration(); // the davis config
	LoginContext context = null;
	try {
	    context = (LoginContext) c.getEntry("test", 
						"loginContext",
						LoginContext.class, 
						null);
	    if (context != null) {
		logger.log(Level.FINEST, "got a login context");
	    }
	} catch (Throwable e) {
            e.fillInStackTrace();
	    logger.log(Level.SEVERE, "Unexpected exception ", e);
	    System.exit(1);
	}	
	Thread autotRequestThread =
	    new Thread(new AutotRequestHandler());
	// this property is supplied by the generator
	String callAutot = 
	    config.getStringConfigVal("org.apache.river.qa.harness.callAutoT", 
				      null);
	if (callAutot != null) {
	    autotRequestThread.setDaemon(true);
	    autotRequestThread.start();
	    config.enableTestHostCalls(true);
	}
	config.callTestHost(new InboundCallsEnabledRequest(true));
	config.callTestHost(new TestStatusRequest("Handling slave requests"));
	if (context != null) {
	    handleRequestsWithLogin(context); //must call exit
	} else {
	    handleRequests(); // must call exit
	}
    }

    /**
     * Run the request loop in the context of the given 
     * <code>LoginContext</code>.
     *
     * @param context the <code>LoginContext</code> to use
     */
    private static void handleRequestsWithLogin(LoginContext context) {
	try {
	    context.login();
	} catch (Throwable e) {
            e.fillInStackTrace();
	    logger.log(Level.SEVERE, "Unexpected exception ", e);
	    System.exit(1);
	}
	// doTest should always call exit, so this call never returns
	Subject.doAsPrivileged(context.getSubject(),
			       new PrivilegedAction() {
				       public Object run() {
					   handleRequests();
					   return null;
				       }
				   },
			       null);
    }

    /**
     * The loop for accepting and processing <code>SlaveRequest</code>
     * messages. Each message is sent over a separate socket connection
     * in serialized form. After unmarshalling the request, the
     * <code>doSlaveRequeset</code> method is called passing an instance
     * of this class; the object returned by the method is written
     * back to the caller and the connection closed. If the method
     * throws an exception, the exception is written back instead. 
     * The loop (and VM) exits when a <code>SlaveRequest</code> is
     * received which call <code>SlaveTest.exit()</code>.
     */
    private static void handleRequests() {
	try {
	    ServerSocket socket = new ServerSocket(REQUEST_PORT);
	    while (!doExit) {
		Socket requestSocket = socket.accept();
	 	logger.log(Level.FINER, "Got a test slave request");
		ObjectInputStream ois = 
		    new ObjectInputStream(requestSocket.getInputStream());
		SlaveRequest request = (SlaveRequest) ois.readObject();
	        logger.log(Level.FINER, "Request is: " + request);
		Object o = null;
		try {
		    o = request.doSlaveRequest(new SlaveTest());
		} catch (Throwable e) {
                    e.fillInStackTrace();
		    logger.log(Level.SEVERE, "Unexpected Exception ", e);
		    o = e;
		}
		ObjectOutputStream oos = 
		    new ObjectOutputStream(requestSocket.getOutputStream());
		oos.writeObject(o);
		oos.flush();
		oos.close();
		requestSocket.close();//redundant??
	    }
	    socket.close();
	    config.callTestHost(new TestStatusRequest("Advancing to next test"));
	    config.callTestHost(new InboundCallsEnabledRequest(false));
	    System.exit(0);
	} catch (Throwable e) {
            e.fillInStackTrace();
	    logger.log(Level.SEVERE, "Unexpected exception", e);
	    System.exit(1); //???
	}
    }

    /**
     * Called by a <code>SlaveRequest</code> to cause the <code>SlaveTest</code>
     * to exit.
     */
    void exit() {
	doExit = true;
    }

    /**
     * Accessor for <code>SlaveRequest</code> to obtain the config object.
     *
     * @return the <code>QAConfig</code> object
     */
    public QAConfig getConfig() {
	return config;
    }

    /**
     * Accessor for <code>SlaveRequest</code> to obtain the admin manager.
     *
     * @return the <code>AdminManager</code>
     */
    public AdminManager getAdminManager() {
	return manager;
    }

    /**
     * Accessor for the storage map which allow the test to
     * maintain state across slave calls.
     * 
     * @return the storage map
     */
    public HashMap getStorageMap() {
	return storageMap;
    }

    private static class AutotRequestHandler implements Runnable {

	public void run() {
	    try {
		ServerSocket socket = 
		    new ServerSocket(InboundAutotRequest.PORT);
		while (true) {
		    Socket requestSocket = socket.accept();
		    logger.log(Level.FINER, "Got an external request");
		    ObjectInputStream ois = 
			new ObjectInputStream(requestSocket.getInputStream());
		    InboundAutotRequest request = 
			(InboundAutotRequest) ois.readObject();
		    logger.log(Level.FINER, "Request is: " + request);
		    Object o = null;
		    try {
			o = request.doRequest(config, manager);
		    } catch (Throwable e) {
                        e.fillInStackTrace();
			logger.log(Level.SEVERE, "Unexpected Exception ", e);
			o = e;
		    }
		    ObjectOutputStream oos = 
			new ObjectOutputStream(requestSocket.getOutputStream());
		    oos.writeObject(o);
		    oos.flush();
		    oos.close();
		    requestSocket.close();//redundant??
		}
	    } catch (Throwable e) {
                e.fillInStackTrace();
		logger.log(Level.SEVERE, "Unexpected exception ", e);
	    }
	}
    }
}
