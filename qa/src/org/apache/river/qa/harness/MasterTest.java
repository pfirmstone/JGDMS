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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.Subject;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.security.policy.DynamicPolicy;
import net.jini.security.policy.DynamicPolicyProvider;
import org.apache.river.api.security.CombinerSecurityManager;
//import org.apache.river.tool.SecurityPolicyWriter;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;
//import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

/**
 * A wrapper which drives the execution of a test on the master host.
 */
class MasterTest {
    
    /**
     * Note the selection of cipher suite provider must be determined at the
     * server end, so the client can use a compatible provider.
     */
   static {
//	java.security.Security.addProvider(new BouncyCastleProvider());
//	java.security.Security.addProvider(new BouncyCastleJsseProvider());
//	java.security.Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
   }

    private final static int EXTERNAL_REQUEST_PORT=10005;

    /** The logger */
    private final static Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness");

    /** The System.err stream created when the process was exec'd */
    private volatile static PrintStream origErr;

    /** The QAConfig instance, read from System.in */
    private volatile static QAConfig config;

    /** obtained from QATestEnvironment after construct returns */
    private volatile static AdminManager manager;

    private volatile static SlaveTest slaveTest;

    private volatile static String callAutot;

    /**
     * The main method invoked in the test VM. The first argument is assumed to
     * be the name of the name of the test to execute. The config is obtained
     * from the System.in stream as a serialized object, which automatically
     * builds the associated <code>ConfigurationFile</code> object.  If the
     * <code>ConfigurationFile</code> contains a login context, the test is
     * executed in that context. The context is assumed to be named
     * <code>org.apache.river.qa.harness.test.login</code>. Any failure analyzers
     * identified by the test property
     * <code>org.apache.river.qa.harness.analyzers</code> are registered with the
     * config object. This VMs System.err stream is redirected to it's
     * System.out stream. On completion of the test, a status text message is
     * passed back to the <code>MasterHarness</code> VM through this VM's
     * original System.err stream.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
	origErr = System.err;
	System.setErr(System.out);
	logger.log(Level.FINE, "Starting MasterTest");
        if (System.getSecurityManager() == null) {
//	    System.setSecurityManager(new java.rmi.RMISecurityManager());
//            System.setSecurityManager(new SecurityPolicyWriter()); // Seems to be ok here
            System.setSecurityManager(new CombinerSecurityManager());
	}
	Policy policy = Policy.getPolicy();
	if (!(policy instanceof DynamicPolicy)){
	    Policy.setPolicy(new DynamicPolicyProvider(policy));
	}
	if (args.length < 1) {
	    exit(false, Test.ENV, "Arguments missing");
	}
  	String testName = args[0];
	try {
	    ObjectInputStream ois = new ObjectInputStream(System.in);
	    config = (QAConfig) ois.readObject();
	} catch (Exception e) {
	    e.printStackTrace();
	    exit(false, Test.ENV, "Could not read config from System.in");
	}

        HeartOfTheMachine.start();

	// used to be handled by config.readObject, but this broke SlaveHarness
	try {
	    config.loadTestConfiguration();
	} catch (TestException e) {
	    e.printStackTrace();
	}
	TestDescription td = config.getTestDescription();
	try {
	    String analyzers = 
		config.getStringConfigVal("testFailureAnalyzers", null);
	    String[] analyzerArray = config.parseString(analyzers, ", \t");
	    if (analyzerArray != null) {
		for (int i = 0; i < analyzerArray.length; i++) {
		    Class aClass = Class.forName(analyzerArray[i]);
		    FailureAnalyzer f = (FailureAnalyzer) aClass.getDeclaredConstructor().newInstance();
		    config.addFailureAnalyzer(f);
		}
	    }
	} catch (Throwable e) { // should never happen
            e.printStackTrace();
	    exit(false, Test.ENV, "Problem during test initialization: " + e);
	}
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
	    e.printStackTrace();
	    exit(false, Test.ENV, "Problem getting login context: " + e);
	}	
	if (context != null) {
	    doTestWithLogin(context, td); //must call exit
	} else {
	    doTest(td); // must call exit
	}
    }

    /**
     * Run the test in the context of the given <code>LoginContext</code>.
     *
     * @param context the <code>LoginContext</code> to use
     * @param td the test description for the test
     */
    private static void doTestWithLogin(LoginContext context, 
					final TestDescription td) {
	try {
	    context.login();
	} catch (Throwable e) {
	    e.printStackTrace();
	    exit(false, Test.ENV, "login failed: " + e);
	}
	// doTest should always call exit, so this call never returns
	Subject.doAsPrivileged(context.getSubject(),
			       new PrivilegedAction() {
				       public Object run() {
					   doTest(td);
					   return null;
				       }
				   },
			       null);
	exit(false, Test.ENV, "doTest returned unexpectedly");
    }

    /**
     * Run the test represented by the given <code>TestDescription</code>.
     * If the test's setup method throws an exception, the run method is
     * not called. If <code>setup</code> or <code>run</code> throw an exception,
     * the exception is pass to all registered failure analyzers. If no 
     * exception is thrown, <code>null</code> is passed to all failure analyzers.
     * If analysis determines that the test has failed,
     * a <code>Test.RERUN</code> status is returned to the harness.
     * Otherwise, the status returned by the analyzers is returned to the 
     * harness. If no analyzers are registered, an exception is presumed to 
     * be a test failure. The teardown method is called in all cases.
     *
     * @param td the test description for the test
     */
    private static void doTest(final TestDescription td) {
	TestEnvironment testEnv = td.getTest();
        Test test = null;
	String shortName = "<popup>" + td.getShortName() + "</popup>";
	String progress = " " + config.getTestIndex() + " of " 
	                   + config.getTestTotal();
	if (testEnv == null) {
	    exit(false, 
		 Test.ENV,
		 "Error constructing test from test description");
	}
	String msg = "OK";
	boolean result = true ; // assumed passed
        int type = Test.TEST; // ignored for passing tests
	boolean setupOK = true;
	Thread autotRequestThread =
	    new Thread(new AutotRequestHandler());
	callAutot = 
	    config.getStringConfigVal("org.apache.river.qa.harness.callAutoT", 
				      null);
	if ((testEnv instanceof QATestEnvironment) && callAutot != null) {
	    autotRequestThread.setDaemon(true);
	    autotRequestThread.start();
	    config.enableTestHostCalls(true);
	}
	config.callTestHost(new InboundCallsEnabledRequest(true));
	config.callTestHost(new TestStatusRequest("setting up test " 
						  + shortName + progress));
	try {
	    logger.log(Level.INFO, 
		"\n============================== CALLING CONSTRUCT() " +
		"==============================\n");
	    test = testEnv.construct(config);
            if (test == null) throw new TestException("construct returned null Test");
	    if (testEnv instanceof QATestEnvironment) {
		manager = ((QATestEnvironment) testEnv).getManager();
	    }
	} catch (Throwable e) {
	    setupOK = false;
	    
	    type = config.analyzeFailure(e, Test.ENV);
	    if (type != Test.PASSED) {
		e.printStackTrace();
		msg = (e instanceof TestException) ? "Construct Failed"
		                                   : "Unexpected Exception in construct";
		msg += ": " + e;
		result = false;
	    }
	} 
	if (setupOK) {
	    config.callTestHost(new TestStatusRequest("running test "
						      + shortName + progress));
	    try {
	        logger.log(Level.INFO, 
		    "\n=============================== CALLING RUN() " +
		    "===============================\n");
		String osName = System.getProperty("os.name");
		if (osName == null) {
		    throw new TestException("No OS name found");
		}
		// don't start this thread on windows to avoid hangs
		// occuring on win2k when InetAddress.getByName was being called
		if (! (osName.startsWith("Win"))) {
		    Thread requestThread = new Thread(new RequestHandler(testEnv), 
						      "RequestHandler");
		    requestThread.setDaemon(true);
		    requestThread.start();
		}
		test.run();
		type = config.analyzeFailure(null, Test.PASSED);
		if (type != Test.PASSED) {
		    msg = "Test failed to throw an expected exception";
		    result = false;
		}
	    } catch (Throwable e) {
		type = config.analyzeFailure(e, Test.TEST);
		if (type != Test.PASSED) {
		    e.printStackTrace();
		    msg = (e instanceof TestException) ? "Test Failed"
			: "Test Failed with an Unexpected Exception";
		    msg += ": " + e;
		    result = false;
		}
	    }
	} 
	if (!result) {
	    type = Test.RERUN;
	}
	try {
	    config.callTestHost(new TestStatusRequest("tearing down test "
						      + shortName + progress));
	    logger.log(Level.INFO, 
		"\n============================ CALLING TEARDOWN() " +
		"=============================\n");
	    testEnv.tearDown();
	} catch (Exception e) { //ignore failures, no logical recovery
	    logger.log(Level.INFO, "Exception occurred during teardown", e);
	}
	if (config.getTestIndex() < config.getTestTotal()) {
	    config.callTestHost(new TestStatusRequest("advancing to test " + (config.getTestIndex() + 1) + " of " + config.getTestTotal()));
	} else {
	    config.callTestHost(new TestStatusRequest("test run ending"));
	}
	config.callTestHost(new InboundCallsEnabledRequest(false));
	exit(result, type, msg);
    }

    /**
     * Generate a status message, write the message string as the last
     * line to the original System.err stream, and exit. The format
     * of the string is specified in the <code>MasterHarness</code> class.
     *
     * @param state the success status of the test
     * @param type the failure type, if the test failed
     * @param message an informative message describing the failure
     */
    private static void exit(boolean state, int type, String message) {
	origErr.println(MasterHarness.genMessage(state, type, message));
	System.out.flush();
	System.out.close();
	origErr.flush();
	origErr.close();
	System.exit((state ? 0 : 1));
    }

    static interface MasterTestRequest extends Serializable{

	public void doRequest(TestEnvironment test) throws Exception;
    }

    private static class RequestHandler implements Runnable {

	final TestEnvironment testExEnv;

	RequestHandler(TestEnvironment test) {
	    this.testExEnv =test;
	}

	public void run() {
	    try {
		ObjectInputStream ois = new ObjectInputStream(System.in);
		while (true) {
		    try {
			MasterTestRequest request = 
			    (MasterTestRequest) ois.readObject();
			request.doRequest(testExEnv);
		    } catch (Exception e) {
			logger.log(Level.SEVERE, 
				   "Exception processing request",
				   e);
		    }
		}
	    } catch (IOException e) {
		logger.log(Level.SEVERE, 
			   "Could not create object stream from System.in",
			   e);
	    }
	}
    }

    private static class AutotRequestHandler implements Runnable {

	public void run() {
            ServerSocket socket = null;
	    try {
		socket = new ServerSocket(InboundAutotRequest.PORT);
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
			logger.log(Level.SEVERE, "Unexpected Exception", e);
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
		logger.log(Level.SEVERE, "Unexpected exception", e);
	    } finally {
                try {
                    socket.close();
                } catch (IOException ex) {/*Ignore*/}
            }
	}
    }
}
