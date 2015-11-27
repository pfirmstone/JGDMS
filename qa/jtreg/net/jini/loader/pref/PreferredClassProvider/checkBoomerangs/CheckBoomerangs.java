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
 * @summary Test ensures that no class boomerangs arise from the use
 * of preferred classes whose annotation matches that of a contextual
 * class loader.  A contextual class loader is one of the following
 * class loaders: the first non-null class loader on the execution
 * stack or the context class loader or one of its ancestors.  Note: a
 * class boomerang occurs when a class which is marked preferred is
 * accessible from the codebase of a VM and is loaded by that VM.
 * Since the VM is loading the class from its own resources, the class
 * should not be preferred.  If the class is preferred, its type will
 * not be compatible with local types defined from the same class file
 * in the relevant VM.
 *
 * @author Laird Dornin
 *
 * @library ../../../../../../testlibrary
 * @build Boomerang CheckBoomerangs CheckBoomerangs_Stub
 * @build Parameter Parameter2 Situation4Impl Situation4Impl_Stub
 * @build TestLibrary TestParams JavaVM StreamPipe
 * @run main/othervm/policy=security.policy -Djava.rmi.server.logCalls=true CheckBoomerangs
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClassLoader;
import java.lang.reflect.Constructor;

/**
 * The test ensures that classes loaded in the following situations
 * should be shared with local types (not preferred) even though those
 * classes are marked preferred:
 *
 * 1. VM passes object with preferred class to self in a remote call.
 *    The preferred class annotation is the same as the
 *    java.rmi.server.codebase property.  The class should not be
 *    preferred.
 * 
 * 2. Invoke a remote method whose return value class is preferred in
 *    its annotation.  The annotation is the same as the codebase
 *    property.  The return class is marked preferred but when loaded
 *    by the test VM, it should not be preferred.
 *
 * 3. Pass object with a null codebase annotation. The object's class
 *    exists in the urls of the local codebase annotation and is
 *    marked preferred.  This is a "trivial boomerang," the class
 *    should not be preferred.
 *
 * 4. Test invokes a remote method.  The method returns an object
 *    whose class is preferred in its annotation.  The annotation of
 *    this class is the same as the annotation of the first non-null
 *    class loader on the execution stack when the method return value
 *    is unmarshalled.  To avoid a boomerang, the return value class
 *    should be loaded from the first non-null loader instead of
 *    preferred in a new child RMI class loader.  The test checks to
 *    make sure that the class loader of the invoked stub (for the
 *    remote method call) is the same class loader as the one used to
 *    load the method return value class.
 */
public class CheckBoomerangs extends UnicastRemoteObject
    implements Boomerang
{
    static {
	// set the logging configuration file
	System.setProperty("java.util.logging.config.file",
			   TestParams.testSrc + "/../../logging.properties");
    }

    /*
     * rmiregistry name to pass remote reference between parent and
     * child vms
     */
    private final static String exchangeName =
	"rmi://localhost:" + TestLibrary.REGISTRY_PORT +
	"/exchangeClientStatus";
    
    private static String PARAMETER_URL = "file:parameter.jar";
    static {
	System.err.println("static initializer");

	try {
	    if (!TestParams.testSrc.startsWith(".")) {
		PARAMETER_URL = (new URL("file", "",
		    (TestParams.testSrc + File.separator +
		     "parameter.jar").replace(File.separatorChar, '/')).
		    toString());
	    }
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}
    }

    private static Registry registry = null;

    CheckBoomerangs() throws RemoteException {}

    public Object getObject() throws RemoteException {
	return new Parameter();
    }

    public void setObject(Object parameter) throws RemoteException {
	if (!Parameter.class.isAssignableFrom(parameter.getClass())) {
	    TestLibrary.bomb("parameter type not compatible with local " +
			     "class in setObject server method");
	}
    }

    private boolean passed = false;
    private boolean invoked = false;
    
    public synchronized void setChildPassed(boolean passed)
	throws RemoteException
    {
	this.passed = passed;
	invoked = true;
	System.err.println("Situation3: child returned status");
	this.notifyAll();
    }

    public synchronized boolean childPassed() {
	try {
	    if (!invoked) {
		this.wait(60 * 1000);
	    }
	    // if still not invoked, there is something wrong.
	    if (!invoked) {
		TestLibrary.bomb("child did not invoke method");
	    }
	    
	    return passed;
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	}
	return passed;
    }
    
    public static void main(String[] args) {
	System.err.println("The test is starting");

	CheckBoomerangs check = null;
	Object situation4Impl = null;

	try {

	    /*
	     * if the test does not have parameters, it will run the 4
	     * main boomerang situations.  if the test does have
	     * parameters, then it will check situation 3.
	     *
	     * Situation 3 must be run in a different VM than the main
	     * test VM because, that situation requires that its VM
	     * have no codebase annotation.  Situations 1 and 2
	     * require that the codebase annotation be set.
	     */
	    if (args.length == 0) {
	    
		System.setProperty("java.rmi.server.codebase",
				   PARAMETER_URL + " " +
				   "file:nonexistentcb");
	
		check = new CheckBoomerangs();
		Boomerang boom = (Boomerang)
		    UnicastRemoteObject.toStub(check);
	    
		URL situation4Cb =
		    TestLibrary.installClassInCodebase("Situation4Impl",
						       "situation4cb");
		TestLibrary.installClassInCodebase("Situation4Impl_Stub",
						   "situation4cb");
		TestLibrary.installClassInCodebase("Parameter2",
						   "situation4cb");
		/*
		 * Situation1: make sure object parameter is not
		 * preferred even though it is marked preferred in the
		 * local annotation.
		 */
    		boom.setObject(new Parameter());
    		System.err.println("Situation1: able to set parameter " +
    				   "in remote object");
		
		TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

		/*
  		 * Situation2: make sure return value is not preferred
  		 * even though it is marked preferred in the local
  		 * annotation.
		 */
    		Object parameter = (Object) boom.getObject();
    		if (!Parameter.class.
    		    isAssignableFrom(parameter.getClass()))
    		{
   		    TestLibrary.bomb("Parameter class not " +
    				     "assignable from return value class");
    		} else {
    		    System.err.println("Situation2: return value " +
    				       "equivalent with local type");
    		}

  		/*
  		 * Situation3: pass object with null annotation that
  		 * exists in codebase urls
		 */
  		registry =
		    LocateRegistry.createRegistry(TestLibrary.REGISTRY_PORT);
  		Naming.rebind(exchangeName, boom);

  		System.err.println("Situation3:");
//		String d = ".." + File.separator;
//		String d3 = d + d + d;
                String classpath = System.getProperty("test.class.path");
                System.err.println("ParentJVM Classpath: " + classpath);
  		JavaVM jvm = new JavaVM("CheckBoomerangs",
		    " -Djava.class.path=" + classpath +
//                            + TestParams.testClasses +
//		    File.pathSeparator + TestParams.testClasses +
//		    File.separator + d3 + d3 + d3 + "testlibrary" +
  		    " -Djava.security.policy" +
		    System.getProperty("java.security.policy"),
				       " situation3");
  		jvm.start();

		System.err.println("Situation3: checking child passed");
		if (!check.childPassed()) {
		    TestLibrary.bomb("child hit with trivial boomerang");
		}
		
  		System.err.println("");
  		System.err.println("");	    

		/*
		 * As a sanity check, make sure that a class can be
		 * preferred
		 */
		Class preferred = RMIClassLoader.loadClass(PARAMETER_URL,
							   "Parameter");
		if (preferred.isAssignableFrom(Parameter.class)) {
		    TestLibrary.bomb("was not able to prefer Parameter class");
		}
		
		/*
		 * Situation4: share types with fnnLoader
		 */
		URLClassLoader situation4Loader =
		    new URLClassLoader(new URL[] {new URL(PARAMETER_URL),
						  situation4Cb,
						  new URL("file:nonexistent")});
		
		Class situation4Class = situation4Loader.loadClass("Parameter2");
		Object situation4Parameter = situation4Class.newInstance();

		Class situation4ImplClass =
		    situation4Loader.loadClass("Situation4Impl");
		Constructor constructor = situation4ImplClass.getConstructor(
		    new Class[] {Object.class});
		situation4Impl = constructor.newInstance(
		    new Object[] {situation4Parameter});
		
		Boomerang situation4Boom =
		    (Boomerang) UnicastRemoteObject.toStub((Remote)
							   situation4Impl);
		Object obj = situation4Boom.getObject();

		if (obj.getClass().getClassLoader() !=
		    situation4Boom.getClass().getClassLoader())
		{
		    TestLibrary.bomb("situation4Loader not used in place of " +
				     "parameter loader");
		} else {
		    	System.err.println("Situation4: parameter object" +
					   " and stub loaded from the same " +
					   "class loader, passed");
		}
		
		System.err.println("TEST PASSED");

	    } else {
		check = new CheckBoomerangs();
		Boomerang boom = (Boomerang)
		    UnicastRemoteObject.toStub(check);

		/*
		 * Situation3: trivial boomerang.
		 */
		Object parameter = (Object) boom.getObject();

		System.err.println("looking up the boomerang");
		
		Boomerang exchange = null;
		try {
		    exchange = (Boomerang) Naming.lookup(exchangeName);
		} catch (Exception e) {
		    e.printStackTrace();
		}

		System.err.println("got the boomerang");
		
		if (!Parameter.class.
		    isAssignableFrom(parameter.getClass()))
		{
		    System.err.println("Situation3: Parameter class not " +
				       "assignable from return value class");
		    exchange.setChildPassed(false);
		} else {
		    System.err.println("Situation3: Class with no codebase " +
				       "annotation correctly loaded from " +
				       "CLASSPATH");
		    exchange.setChildPassed(true);		    
		}
	    }

	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    }
	    TestLibrary.bomb("unexpected exception", e);
	} finally {
	    TestLibrary.unexport(check);
	    TestLibrary.unexport((Remote) situation4Impl);
	}
    }
}
