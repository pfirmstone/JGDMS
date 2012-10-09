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
 * @summary Functional test to verify configurability of the preferred
 * classes provider
 *
 * 
 *
 * @library ../../../../../../testlibrary
 * @build ExerciseProviderConfig Foo Bar ConnectBack TestLoaderProvider
 * @build TestLibrary TestParams
 * @run main/othervm/policy=testprovider.policy
 *     -Djava.rmi.server.RMIClassLoaderSpi=TestLoaderProvider
 *     ExerciseProviderConfig
 */

import java.io.File;
import java.net.URL;
import java.rmi.server.RMIClassLoader;
import java.net.MalformedURLException;
import java.util.Arrays;

import net.jini.loader.pref.PreferredClassLoader;

/**
 * Test to verify that a sub-class of PreferredClassProvider can be
 * used to configure the preferred classes provider to:
 *
 * 1. Restrict class downloading by default
 *
 * 2. Not auto-grant connect back permission to downloaded code
 *
 * Note: The test also accomodates testing of the facilitator
 * providers in net.jini.loader.pref.  The test will
 * expect different behavior when different class loader providers are
 * installed.  Specfically the test will assert that:
 *
 *     - classes are or are not able to connect to the location from
 *       where they were loaded
 *
 *     - downloading from codebases not granted DownloadPermission is
 *       or is not restricted
 *
 *     All combinations of these two checks are verified
 * 
 * 3. Use a custom class annotation object to provide custom class
 *    annotations for classes loaded in the system class loader and
 *    above.
 *
 * The test also performs the following checks:
 *
 * - Construct a preferred class loader.  Get a custom annotation for
 *    a class loaded from that loader.
 *
 * - Create preferred class loaders using
 *   PreferredClassLoader.newInstance.  Ensure that permission to
 * access urls of the loader are checked in the newInstance method.
 */
public class ExerciseProviderConfig {
    private static boolean mustConnect;
    private static boolean mustDownload;
    private static boolean testProvider = false;

    // initalize pass requirements for class loading tests
    static {
	String loaderPref = "net.jini.loader.pref.";
	String spiProp =
	    System.getProperty("java.rmi.server.RMIClassLoaderSpi");
	System.err.println("Test configured with: " + spiProp);
	
	if ((spiProp == null) || spiProp.equals("")) {
	    mustConnect = true;
	    mustDownload = true;
	} else if (spiProp.equals("TestLoaderProvider")) {
	    testProvider = true;
	    mustConnect = true;
	    mustDownload = false;
	} else if (spiProp.equals(loaderPref + "RequireDlPermProvider")) {
	    mustConnect = true;
	    mustDownload = false;
	}
    }
    
    /*
     * checkDownload from a PreferredClassLoader created by the
     * PreferredClassLoader.newInstance()
     */
    private static void checkDownloadNewInstance(URL fooUrl)
	throws Exception
    {
	SecurityException sex = null;
	Class fooClass = null;
	try {
	    PreferredClassLoader loader =
		PreferredClassLoader.newInstance(new URL[] {fooUrl},
		    ClassLoader.getSystemClassLoader(),
		    "not used", true);

	    fooClass = loader.loadClass("Foo");

	    System.err.println("Foo class incorrectly loaded from: " +
			       fooClass.getProtectionDomain().
			       getCodeSource().getLocation());
	} catch (SecurityException e) {
	    sex = e;
	}
	if (sex == null) {
	    // sux for you
	    TestLibrary.bomb("test newInstance permitted to download, " +
			     "should be restricted");
	} else {
	    System.err.println("\nTest newInstance correctly unable to " +
			       "download from Foo url");
	}
    }

    /**
     * Try to download a class from a location that has not been
     * granted downloadPermission.
     */
    private static void checkDownload(URL fooUrl)
	throws Exception
    {
	SecurityException ex = null;
	Class fooClass = null;
	String not = "";	
	try {
	    fooClass =
		RMIClassLoader.loadClass(fooUrl.toString(), "Foo");
	    System.err.println("Foo class loaded from: " +
			       fooClass.getProtectionDomain().
			       getCodeSource().getLocation());
	} catch (SecurityException e) {
	    ex = e;
	    not = "not ";
	}
	
	if (((mustDownload) && (ex != null)) ||
	    ((!mustDownload) && (ex == null)))
	{
	    TestLibrary.bomb("Incorrectly " + not +
			     "able to download class from: " + fooUrl);
	} else {
	    System.err.println("\nCorrectly " + not +
			       "able to download class from: " + fooUrl);
	}
    }

    /**
     * Check that a class loaded through the provider is able to connect
     * back to its codebase, or not, as appropriate.  The "not" case applied
     * when the preferred classes loaders and providers supported a
     * "no automatic permissions" mode, but it is no longer relevant, but
     * this part of the test was retained to make sure that the automatic
     * permissions are correctly granted.
     */
    private static void checkAutoPermissions(URL barUrl)
	throws Exception
    {
	Class barClass =
	    RMIClassLoader.loadClass(barUrl.toString(), "Bar");
	barUrl =
	    barClass.getProtectionDomain().
	    getCodeSource().getLocation();
	System.err.println("\nBar class loaded from: " +
			   barUrl);
	String not = "";
	
	ConnectBack back =
	    (ConnectBack) barClass.newInstance();

	SecurityException ex = null;
	try {
	    System.err.println("barUrl: " + barUrl);
	    back.connect(new URL(barUrl, "Bar.class"));
	} catch (SecurityException e) {
	    not = "not ";
	    ex = e;
	}
	if (((mustConnect) && (ex != null)) ||
	    ((!mustConnect) && (ex == null)))
	{
	    TestLibrary.bomb("Class incorrectly " + not +
			     "able to connect back");
	} else {
	    System.err.println("\nBar correctly " + not +
			       "able to connect back to its codesource");
	}
    }

    private static void cantAccessAllUrls(URL fooUrl) {
	/* new instance tests */
	URL[] urls = null;
	SecurityException secEx = null;
	try {
	    urls = new URL[] {fooUrl, new URL("file:///not-allowed-to-access/")};
	} catch (MalformedURLException e) {
	}
	try {
	    PreferredClassLoader.newInstance(urls,
		ClassLoader.getSystemClassLoader(),
		(String) null, false);
	} catch (SecurityException e) {
	    System.err.println("\nCorrectly received SecurityException when " +
			       "trying to access: " + Arrays.asList(urls));
	    secEx = e;
	}
	if (secEx == null) {
	    TestLibrary.bomb("no security exception thrown" +
			     "when trying to access forbidden url");
	}
    }

    private static void checkNewInstanceAnnotations(URL fooUrl)
	throws ClassNotFoundException
    {
	PreferredClassLoader loader =
	    PreferredClassLoader.newInstance(new URL[] {fooUrl},
		ClassLoader.getSystemClassLoader(),
		"test annotation", false);

	Class c = loader.loadClass("Foo");
	if (!RMIClassLoader.getClassAnnotation(c).
	    equals("test annotation"))
	{
	    TestLibrary.bomb("class Foo had incorrect annotation in " +
			     "loader: test annotation");
	} else {
	    System.err.println("\nclass Foo had correct annotation in loader\n");
	}
    }

    private static void checkLocalClassAnnotation() throws Exception {
	/* custom annotation test */
	String bogus =
	    RMIClassLoader.getClassAnnotation(ConnectBack.class);
	    
	if (!bogus.equals("file:/bogus/annotation/")) {
	    TestLibrary.bomb("unexpected annotation for " +
			     "class Connectback: " + bogus);
	} else {
	    System.err.println("Connectback had expected annotation: " +
			       bogus);
	}
    }

    public static void main(String[] args) {
	try {
	    System.err.println("\nFunctional test to verify " +
			       "configurability of the " +
			       "preferred classes provider configurability");
	    
	    TestLibrary.suggestSecurityManager(null);

	    // doPivileged, javatest.jar will fail access check otherwise
	    java.security.AccessController.doPrivileged(
		new java.security.PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			URL fooUrl =
			    TestLibrary.installClassInCodebase("Foo", "foocb");
			URL barUrl =
			    TestLibrary.installClassInCodebase("Bar", "barcb");
			TestLibrary.installClassInCodebase("Bar$Action",
							   "barcb");

			checkDownloadNewInstance(fooUrl);

			checkDownload(fooUrl);

			checkAutoPermissions(barUrl);

			cantAccessAllUrls(fooUrl);

			checkNewInstanceAnnotations(fooUrl);
			
			return null;
		    }
		}
	    );

	    if (testProvider) {
		checkLocalClassAnnotation();
	    }

	    System.err.println("\nTEST PASSED");

	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    }
	    TestLibrary.bomb("unexpected exception", e);
	}
    }
}

