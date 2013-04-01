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
/* @test 
 *  
 * @summary Ensures that a registry with a stub class in CLASSPATH
 * preserves the codebase annotation of that stub class (using
 * preferred classes) when a marshalled instance of that class is
 * passed through the rmiregistry in a series of remote method
 * invocations.  The stub class is preferred.
 * 
 * Comment Monday, April 1st, 2013:  This test fails and has done
 * for some time, I suspect this occurs because the stub is no longer required, 
 * reflective proxy's are used instead, so we get file: instead of the 
 * http: codebase annotation.
 *
 * @author Laird Dornin
 *
 * @library ../../../../../../testlibrary
 * @build RegistryRetainCodebase BasicRemote Client RegistryRetainCodebase_Stub
 * @build TestLibrary TestParams JavaVM HTTPD TestFailedException
 * @run main/othervm -Djava.rmi.server.logCalls=true -Djava.rmi.server.hostname=localhost RegistryRetainCodebase
 */

import java.io.File;
import java.io.IOException;        
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.server.UnicastRemoteObject;

import java.rmi.server.RMIClassLoader;

/**
 * Test uses the following processes to carry out communication
 * between the rmiregistry, the test, and a client:
 *
 * 1. Main test process.  Has stub classes in CLASSPATH, annotates
 * stub classes with http server location.
 *
 * 2. rmiregistry has stub classes in CLASSPATH. Should load stub
 * classes from http server anyway if the stub classes are preferred.
 *
 * 3. Http server which serves the preferred classes
 *
 * 4. Client to receive the preferred classes.
 *
 * Test verifies that classes which pass through the rmiregistry dont
 * loose their codebase annotation when preferred classes are in use.
 */
public class RegistryRetainCodebase extends UnicastRemoteObject
    implements BasicRemote
{
    static {
	// set the logging configuration file
	System.setProperty("java.util.logging.config.file",
			   TestParams.testSrc + "/../../logging.properties");
    }

    private static String OPTIONS =
	"-Djava.rmi.server.logCalls=true";
    
    private static String SRC_BASE = "." + File.separator;
    static {
	try {
	    if (!TestParams.testSrc.startsWith(".")) {
		SRC_BASE = (new URL("file", "",
		    (TestParams.testSrc + File.separator).
		    replace(File.separatorChar, '/')).toString());
	    }
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}
    }

    private static HTTPD codeServer = null;
    private static JavaVM registry = null;
    private static JavaVM client = null;
    private static boolean passed = false;
    
    private static Object lock = new Object();
    private static RegistryRetainCodebase impl = null;

    final static int codePort = 9867;

    RegistryRetainCodebase() throws RemoteException {}
    
    public void simpleMethod() throws RemoteException {
	synchronized (lock) {
	    lock.notifyAll();
	    passed = true;
	}

	System.err.println("received a method invocation");
    }

    private static void forceLoadJar()
	throws NoSuchMethodException, MalformedURLException,
	       IllegalAccessException, InvocationTargetException
    {
	Method addURLMethod =
	    URLClassLoader.class.getDeclaredMethod("addURL",
					   new Class[] {URL.class});

	String retainJarLocation
	    = ((SRC_BASE.startsWith(".")) ?
	       "file:retain.jar" : SRC_BASE + "retain.jar");
	
	addURLMethod.setAccessible(true);
	addURLMethod.invoke(ClassLoader.getSystemClassLoader(),
			    new Object[] {new URL(retainJarLocation)});
    }
    
    public static void main(String[] args) {
	try {
	    // set the codebase property before it is sampled
	    System.setProperty("java.rmi.server.codebase",
			       "http://localhost:" +
			       codePort + "/retain.jar");

	    forceLoadJar();
	    
	    impl = new RegistryRetainCodebase();
		
	    // run a codeserver to act as the server's codebase
  	    codeServer = new HTTPD(codePort, TestParams.testSrc);
  	    Thread.sleep(8 * 1000);
	    
            // create a 3rd party (the rmiregistry) that has no
            // codebase annotation
  	    registry = new JavaVM("sun.rmi.registry.RegistryImpl",
		" -Djava.util.logging.config.file=" +
		    TestParams.testSrc + "/../../logging.properties" +
		" -Djava.security.policy=./client.policy " +
  		OPTIONS + " -classpath " +
		TestParams.testSrc +
		File.separator + "retain.jar",
  		TestLibrary.REGISTRY_PORT + "", "registry");

  	    registry.start();
  	    Thread.sleep(4 * 1000);

	    // rebind this object
	    Naming.rebind("rmi://localhost:" +
			  TestLibrary.REGISTRY_PORT +
			  "/registryRetainCodebase", impl);
	    
	    // run the client
	    client = new JavaVM("Client", OPTIONS +
		" -Djava.util.logging.config.file=" +
		    TestParams.testSrc + "/../../logging.properties" +
		" -Djava.class.path=" + TestParams.testSrc +
		    File.separator + "retain.jar" + File.pathSeparator +
		TestParams.testClasses + File.separator +
		" -Djava.security.policy=" + TestParams.testSrc +
		    File.separator + "client.policy", "", "client");

	    client.start();

	    synchronized (lock) {
		lock.wait(60 * 1000);
		    
		if (passed) {
		    System.err.println("test passed");
		} else {
		    TestLibrary.bomb("client failed to call back in time");
		}
	    }
	    
	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    }
	    TestLibrary.bomb("unexpected exception", e);
	} finally {
	    try {
		UnicastRemoteObject.unexportObject(impl, true);
	    } catch (NoSuchObjectException e) {
	    }
	    impl = null;
	    if (codeServer != null) {
		try {
		    codeServer.stop();
		} catch (IOException e) {
		}
	    }
	    if (registry != null) {
		registry.destroy();
	    }
	    if (client != null) {	    
		client.destroy();
	    }
	    codeServer = null;
	    registry = null;
	    client = null;
	}
    }
}
