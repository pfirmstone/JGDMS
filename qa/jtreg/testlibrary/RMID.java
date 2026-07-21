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
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;
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
	// MANAGER_OPTION was declared but never actually used anywhere in
	// this file -- rmid was launched with no SecurityManager installed
	// at all. Without one, BasicInvocationDispatcher.checkClientPermission
	// (and DefaultGroupPolicy.checkGroup, which calls it) short-circuits
	// with "if (sm == null) return;", making every SystemPermission/
	// GroupPolicy check a silent no-op regardless of policy/permClass
	// wiring in rmid.config. Activation.java's own main() documents that
	// "the SecurityManager is selected at JVM launch via
	// -Djava.security.manager (forwarded to spawned activation groups by
	// buildGroupProcess)" -- confirming this is how it's meant to be
	// installed, not programmatically.
	String options = (" " + MANAGER_OPTION + "default" +
			  " -Dtest.src=" + TestParams.testSrc +
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
	// WI-5: the download codebase jars are named per the dist's
	// versioned lib-dl/ layout, not the legacy unversioned
	// "phoenix-dl.jar"/"jsk-dl.jar" names. "jsk-dl.jar" (logical
	// name) maps to the real jar jgdms-lib-dl-<version>.jar, per
	// qa/harness/policy/securitypolicywriterpath.properties and
	// qa/src/.../qaDefaults.properties (both agree; see SOW section 5).
	final String phoenixDlJar = findVersionedJar(HTTPD.getDefaultDir(), "phoenix-dl");
	final String jskDlJar = findVersionedJar(HTTPD.getDefaultDir(), "jgdms-lib-dl");
	String url;
	if (useHttpmd) {
	    options +=
		" -Djava.protocol.handler.pkgs=net.jini.url";
	    final String mdurl =
		("httpmd://" + host + ":" +
		 HTTPD.getDefaultPort() + "/" + phoenixDlJar + ";md5=0" +
		 " httpmd://" + host + ":" +
		 HTTPD.getDefaultPort() + "/" + jskDlJar + ";md5=0");
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
		   HTTPD.getDefaultPort() + "/" + phoenixDlJar +
		   " http://" + host + ":" +
		   HTTPD.getDefaultPort() + "/" + jskDlJar);
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

    /*
     * WI-3: the dist phoenix-<version>.jar has a bare manifest (no
     * Main-Class: it is a plain library jar, not an executable jar),
     * so "-jar phoenix.jar" cannot work. Launch the real main class
     * org.apache.river.phoenix.Activation (confirmed present, see
     * Activation.java:2315) via "-cp <dist lib/ classpath>" instead,
     * mirroring the RMID(String, boolean) constructor below. Do not
     * revert to "-jar": that would require making the shipped phoenix
     * jar executable, a build change out of this SOW's scope (see
     * SOW-Activation-Jtreg-Harness-Repair.md risk R2).
     */
    protected RMID(boolean useHttpmd) {
	super("org.apache.river.phoenix.Activation",
	      makeOptions(useHttpmd) + " -cp " + buildLibClasspath() +
	      " -Dphoenix.location=" + getDefaultLocation(),
	      makeArgs(), System.out, System.err);
	setPolicyFile(TestParams.defaultRmidPolicy);
    }

    protected RMID(String classpath, boolean useHttpmd) {
	super("org.apache.river.phoenix.Activation",
	      makeOptions(useHttpmd) + " -cp " + getDefaultLocation() +
	      File.pathSeparator + classpath + " -Dphoenix.location=" +
	      getDefaultLocation(),
	      makeArgs(), System.out, System.err);
	setPolicyFile(TestParams.defaultRmidPolicy);
    }

    private static String getLibDir() {
	return TestLibrary.getExtraProperty("jsk.home", TestLibrary.jskHome) +
	    File.separator + "lib";
    }

    /**
     * Finds the single jar in {@code dir} whose name matches
     * {@code <prefix>-<version>.jar}, where {@code <version>} begins
     * with a digit. This lets callers pin a logical jar name (e.g.
     * "phoenix") to whatever versioned name the dist actually produced
     * (e.g. "phoenix-4.0.0-SNAPSHOT.jar") without hardcoding the
     * project version in this test-infra fixture, and without the
     * "phoenix" prefix incorrectly matching sibling jars such as
     * "phoenix-group-...jar" or "phoenix-common-...jar" (those don't
     * have a digit immediately after "phoenix-").
     */
    private static String findVersionedJar(String dir, final String prefix) {
	File d = new File(dir);
	final Pattern p = Pattern.compile(
	    "^" + Pattern.quote(prefix) + "-[0-9].*\\.jar$");
	File[] matches = d.listFiles(new FilenameFilter() {
	    public boolean accept(File dir, String name) {
		return p.matcher(name).matches();
	    }
	});
	if (matches == null || matches.length == 0) {
	    throw new RuntimeException("RMID: no jar matching " + prefix +
					"-<version>.jar found in " + dir);
	}
	if (matches.length > 1) {
	    throw new RuntimeException("RMID: multiple jars matching " +
		prefix + "-<version>.jar found in " + dir + ": " +
		Arrays.toString(matches));
	}
	return matches[0].getName();
    }

    /**
     * Builds the "-cp" value used to launch phoenix
     * (org.apache.river.phoenix.Activation) directly, since the dist
     * phoenix jar is not executable (see the RMID(boolean) constructor
     * comment). Enumerates every jar in the dist's lib/ directory
     * (jsk.home/lib) -- phoenix, phoenix-common, phoenix-group,
     * phoenix-init, jgdms-jeri, jgdms-platform, jgdms-collections,
     * jgdms-lib, jgdms-activation(-parameters), jgdms-der,
     * jgdms-pref-class-loader, jgdms-url-integrity, high-scale-lib,
     * asm*, etc -- rather than hardcoding the list, since Runtime.exec
     * does not glob.
     *
     * Also includes jsk.home/lib-dl: Activation's ActivatorImpl
     * implements the org.apache.river.phoenix.dl.Activator remote
     * interface (phoenix-dl module), so the server process needs that
     * interface class (and its dependencies, e.g. the jgdms-lib-dl
     * proxy trust/constraint support classes) available locally too --
     * the standard RMI/Jini requirement that a server implementing a
     * remote interface needs the interface class on its own classpath,
     * not just fetchable via codebase. Empirically discovered when
     * "-cp <lib/ only>" produced
     * "NoClassDefFoundError: org/apache/river/phoenix/dl/Activator"
     * (SOW risk R3: further rot only visible once phoenix actually
     * launches).
     */
    private static String buildLibClasspath() {
	StringBuilder cp = new StringBuilder();
	String[] dirs = { getLibDir(), HTTPD.getDefaultDir() };
	boolean first = true;
	for (int d = 0; d < dirs.length; d++) {
	    File dir = new File(dirs[d]);
	    File[] jars = dir.listFiles(new FilenameFilter() {
		public boolean accept(File dir, String name) {
		    return name.endsWith(".jar");
		}
	    });
	    if (jars == null || jars.length == 0) {
		throw new RuntimeException("RMID: no jars found in " + dirs[d]);
	    }
	    Arrays.sort(jars);
	    for (int i = 0; i < jars.length; i++) {
		if (!first) cp.append(File.pathSeparator);
		first = false;
		cp.append(jars[i].getAbsolutePath());
	    }
	}
	return cp.toString();
    }

    public static String getDefaultLocation() {
	String libDir = getLibDir();
	return libDir + File.separator + findVersionedJar(libDir, "phoenix");
    }

    public static String getDefaultGroupLocation() {
	String libDir = getLibDir();
	return libDir + File.separator +
	    findVersionedJar(libDir, "phoenix-group");
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
		    "/net.jini.activation.arg.ActivationSystem");
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
