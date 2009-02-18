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
/**
 * 
 */

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.activation.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import net.jini.url.httpmd.HttpmdUtil;

/**
 * Utility class that creates an instance of rmid with a policy
 * file of name <code>TestParams.defaultPolicy</code>.
 *
 * Activation groups should run with the same security manager as the
 * test.
 */
public class RMID extends JavaVM {

    public static String POLICY_OPTION="-Djava.security.policy=";
    public static String MANAGER_OPTION="-Djava.security.manager=";

    /** Test port for rmid */
    protected static int port = ActivationSystem.SYSTEM_PORT;

    protected static HTTPD httpd;

    static void mesg(Object mesg) {
	System.err.println("RMID: " +
			   (mesg != null ? mesg.toString(): "null"));
    }

    /** make test options and arguments */
    protected static String makeOptions(boolean useHttpmd) {
	String options = (" -Dtest.src=" + TestParams.testSrc +
			  " -Dtest.classes=" + TestParams.testClasses +
			  " -Djsk.home=" + TestLibrary.getExtraProperty(
					     "jsk.home", TestLibrary.jskHome));
	String host = (String) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    try {
			return InetAddress.getLocalHost().getHostAddress();
		    } catch (UnknownHostException e) {
			e.printStackTrace();
			return "127.0.0.1";
		    }
		}
	});
	String url;
	if (useHttpmd) {
	    options +=
		" -Djava.protocol.handler.pkgs=net.jini.url";
	    final String mdurl =
		("httpmd://" + host + ":" +
		 HTTPD.getDefaultPort() + "/phoenix-dl.jar;md5=0" +
		 " httpmd://" + host + ":" +
		 HTTPD.getDefaultPort() + "/jsk-dl.jar;md5=0");
	    url =
		(String) AccessController.doPrivileged(new PrivilegedAction() {
		    public Object run() {
			try {
			    return HttpmdUtil.computeDigestCodebase(
						HTTPD.getDefaultDir(), mdurl);
			} catch (IOException e) {
			    throw new RuntimeException("digest failure", e);
			}
		    }
		});
	} else {
	    url = ("http://" + host + ":" +
		   HTTPD.getDefaultPort() + "/phoenix-dl.jar" +
		   " http://" + host + ":" +
		   HTTPD.getDefaultPort() + "/jsk-dl.jar");
	}
	return " -Djava.rmi.server.codebase=\"" + url + "\"" + options;
    }

    protected static String makeArgs() {
	return (TestParams.testSrc + File.separator + "rmid.config");
    }

    public static RMID createRMID() {
	return new RMID(false);
    }

    public static RMID createRMID(boolean useHttpmd) {
	return new RMID(useHttpmd);
    }

    public static RMID createRMID(String classpath) {
	return new RMID(classpath, false);
    }

    public static RMID createRMID(String classpath, boolean useHttpmd) {
	return new RMID(classpath, useHttpmd);
    }

    protected RMID(boolean useHttpmd) {
	super(getDefaultLocation(),
	      makeOptions(useHttpmd) + " -jar", makeArgs(),
	      System.out, System.err);
	setPolicyFile(TestParams.defaultRmidPolicy);
    }

    protected RMID(String classpath, boolean useHttpmd) {
	super("com.sun.jini.phoenix.Activation",
	      makeOptions(useHttpmd) + " -cp " + getDefaultLocation() +
	      File.pathSeparator + classpath + " -Dphoenix.location=" +
	      getDefaultLocation(),
	      makeArgs(), System.out, System.err);
	setPolicyFile(TestParams.defaultRmidPolicy);
    }
    
    public static String getDefaultLocation() {
	return (TestLibrary.getExtraProperty("jsk.home", TestLibrary.jskHome) +
		File.separator + "lib" + File.separator + "phoenix.jar");
    }

    public static String getDefaultGroupLocation() {
	return (TestLibrary.getExtraProperty("jsk.home", TestLibrary.jskHome) +
		File.separator + "lib" + File.separator +
		"phoenix-group.jar");
    }

    public static void removeLog() {
	/*
	 * Remove previous log file directory before
	 * starting up rmid.
	 */
	File f = new File("log");
	
	if (f.exists()) {
	    mesg("removing rmid's old log file...");
	    String[] files = f.list();
	    
	    if (files != null) {
		for (int i=0; i<files.length; i++) {
		    (new File(f, files[i])).delete();
		}
	    }
	    
	    if (f.delete() != true) {
		mesg("\t" + " unable to delete old log file.");
	    }
	}
    }
    
    public void start() throws IOException {
	start(10000);
    }

    public void slowStart() throws IOException {
	start(60000);
    }

    public void start(long waitTime) throws IOException {

	if (getVM() != null) return;

	synchronized (RMID.class) {
	    if (httpd == null) {
		httpd = new HTTPD();
	    }
	}

	// if rmid is already running, then the test will fail with
	// a well recognized exception (port already in use...).

	mesg("starting rmid...");
	super.start();
	
	// give rmid time to come up
	do {
	    try {
		Thread.sleep(Math.min(waitTime, 10000));
	    } catch (InterruptedException ie) {
		Thread.currentThread().interrupt();
	    }
	    waitTime -= 10000;

	    // is rmid present?
	    if (ActivationLibrary.rmidRunning(port)) {
		mesg("finished starting rmid.");
		return;
	    }
	} while (waitTime > 0);
	TestLibrary.bomb("start rmid failed... giving up", null);
    }

    public void restart() throws IOException {
	destroy();
	start();
    }
    
    /** 
     * Ask rmid to shutdown gracefully using a remote method call.
     * catch any errors that might occur from rmid not being present
     * at time of shutdown invocation.
     *
     * Shutdown does not nullify possible references to the rmid 
     * process object (destroy does though).
     */
    public static void shutdown() {

	try {
	    ActivationSystem system = null;

	    try {
		mesg("getting a reference to the activation system");
		system = (ActivationSystem) Naming.lookup("rmi://localhost:" + 
		    TestLibrary.RMID_PORT + 
		    "/java.rmi.activation.ActivationSystem");
		mesg("obtained a reference to the activation system");
	    } catch (java.net.MalformedURLException mue) {
	    }

	    if (system == null) {
		TestLibrary.bomb("reference to the activation system was null");
	    }
	    system.shutdown();
	    
	} catch (Exception e) {
	    mesg("caught exception trying to shutdown rmid");
	    mesg(e.getMessage());
	    e.printStackTrace();
	}
	
	mesg("testlibrary finished shutting down rmid");
    }

    /** 
     * Ask rmid to shutdown gracefully but then destroy the rmid
     * process if it does not exit by itself.  This method only works
     * if rmid is a child process of the current VM.  
     */
    public void destroy() {

	// attempt graceful shutdown of the activation system on
	// TestLibrary.RMID_PORT
	shutdown();

	if (vm != null) {
	    try {
		for (int i = 30; --i >= 0; ) {
		    try {
			vm.exitValue();
			vm = null;
			mesg("rmid exited on shutdown request");
			return;
		    } catch (IllegalThreadStateException illegal) {
			Thread.sleep(1000);
		    }
		}
	    } catch (InterruptedException e) {
	    }
	    mesg("Had to destroy RMID's process " + 
		 "using Process.destroy()");
	    // destroy rmid if it is still running...
	    super.destroy();
	}
    }
}
