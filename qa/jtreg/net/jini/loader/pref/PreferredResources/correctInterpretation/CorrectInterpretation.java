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
 * @summary functional test to verify that preferred resource provider
 * correctly implements preferred classes and resource functionality
 * @author Laird Dornin
 *
 * @library ../../../../../../testlibrary
 * @build TestLibrary
 * @build CorrectInterpretation NonpreferredInterface
 * @build One Two Three Four HasInner Test
 * @run main/othervm/policy=security.policy CorrectInterpretation
 */

/*
 * WARNING: when running the test manually, make sure to put the class
 * "NonpreferredInterface.class" in its package directory, "annoying".
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.rmi.server.RMIClassLoader;
import java.rmi.MarshalledObject;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The following checks are carried out by the test:
 *
 * 1. Check preferred names: ensures that a set of names has correct
 * preference values in a PreferredResources object created with the
 * preferred list, TestParams.testSrc + /META-INF/PREFERRED.LIST.  The
 * test fails if the names do not have correct preference values.
 *
 * 2. Check that prefs are rewritten correctly: the test reads the
 * above preferred list file from child.jar and creates a
 * PreferredResources object from the file.  The test then causes the
 * contents of the PreferredResources object to be written into a byte
 * array.  The test then directly reads the contents of the above
 * preferred resources file into a new byte array.  The test fails if
 * the contents of the two byte arrays are not the same.
 *
 * 3. Load preferred classes and check proper assignability: the test
 * loads a series of classes into a PreferredClassLoader whose names
 * are described in the preferred list of that class loader.  The test
 * checks that the classes are only assignable to local classes when
 * those classes are preferred and extend local interfaces or the
 * classes are not preferred.  If one of the newly loaded classes is
 * not assignable to a local class, the test uses this characteristic
 * as evidence that the class has been preferred.  The test fails if
 * the loaded classes do not have expected assignability
 * characteristics.
 *
 * 4. Check the syntax of a score of dummy preferred lists contained
 * in the directory, preflists. The test creates a PreferredResources
 * object for every list in this directory.  Construction of this
 * object for some of the lists will cause a sytnax exception to be
 * thrown.  If the name of the lists starts with "true" and
 * construction of the list does not produce an exception the test
 * fails.  If the name of the list starts with "false", the test fails
 * if the list does produce a syntax exception.
 *
 * 5. Check non-class resources: The test loads a series of non-class
 * resources which are available in the system class loader and
 * ensures that all preferred resources are loaded from a child class
 * loader (will be a PreferredClassLoader) and the non-preferred
 * resources are loaded from the system loader.
 */
public class CorrectInterpretation {
    
    // PreferredResources was moved from public namespace to package private,
    // Reflection is used to enable access for testing.
    static Class prefRes = null;
    static Constructor prefResCons = null;
    static Method write = null;
    static Method getNameState = null;
    static Method getDefaultPreference = null;
    static Method getWildcardPreference = null;
    public static final int NAME_NO_PREFERENCE = 0;
    public static final int NAME_NOT_PREFERRED = 1;
    public static final int NAME_PREFERRED = 2;
    public static final int NAME_PREFERRED_RESOURCE_EXISTS = 3;
    static {
	// set the logging configuration file
	System.setProperty("java.util.logging.config.file",
			   TestParams.testSrc + "/../../logging.properties");
        
        try {
            prefRes = Class.forName("net.jini.loader.pref.PreferredResources");
            prefResCons = prefRes.getDeclaredConstructor(InputStream.class);
            prefResCons.setAccessible(true);
            write = prefRes.getDeclaredMethod("write", OutputStream.class);
            write.setAccessible(true);
            Class [] params = { String.class, Boolean.TYPE};
            getNameState = prefRes.getDeclaredMethod("getNameState", params);
            getNameState.setAccessible(true);
            getDefaultPreference = prefRes.getDeclaredMethod("getDefaultPreference", new Class[0]);
            getDefaultPreference.setAccessible(true);
            getWildcardPreference = prefRes.getDeclaredMethod("getWildcardPreference", String.class);
            getWildcardPreference.setAccessible(true);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace(System.err);
        } catch (NoSuchMethodException ex) {
            ex.printStackTrace(System.err);
        } catch (SecurityException ex) {
            ex.printStackTrace(System.err);
        }
        
    }

    final static String PREF_LIST = "META-INF/PREFERRED.LIST";

    private static String RESOURCE_URL = "file:";
    static {
	try {
	    if (!TestParams.testSrc.startsWith(".")) {
		RESOURCE_URL = (new URL("file", "",
		    (TestParams.testSrc + File.separator).
		    replace(File.separatorChar, '/')).toString());
	    }
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}
    }

    private static void checkListSyntax(String absName,
					boolean failureExpected)
	throws IOException
    {
	try {
	    System.err.println("");
	    Object pr = null;
                pr = prefResCons.newInstance(new FileInputStream(absName));
	    if (failureExpected) {
                if (pr != null) write.invoke(pr, System.err);
		TestLibrary.bomb("preferences list syntax error not detected");
	    } else {
		System.err.println(absName + ": Syntax ok");
	    }
	} catch (Exception e) {
	    if (!failureExpected) {
		throw new IOException("Test Failed",e);
	    } else {
		System.err.println(absName + ": received expected failure: " +
				   e.getMessage());
	    }
	}
    }

    /**
     * Create a preferred resources object for a series of preferred
     * lists which are contained in a test sub-directory
     */
    private static void checkSyntax() throws IOException {
	File dir = new File(TestParams.testSrc + File.separator + "preflists");
	if (!dir.isDirectory()) {
	    throw new RuntimeException("could not find pref lists directory");
	}
	File[] files = dir.listFiles();
	for (int i = 0 ; i < files.length ; i++) {
	    File absFile = files[i];
	    String absList = absFile.toString();
	    String listName = absList.substring(absList.lastIndexOf(File.separator) + 1,
						absList.length());
	    if (!absFile.isDirectory()) {
		boolean failureExpected = listName.startsWith("true");
		checkListSyntax(absList, failureExpected);
	    }
	}
    }

    /**
     * Check that the preference value for the given name matches the
     * expected parameter.
     */
    private static void checkPreferred(Object prefs,
				       String name, boolean expected)
	throws IOException
    {
	boolean is = isPreferred(prefs, name, true);
	if (is != expected) {
	    TestLibrary.bomb("unexpected prefs, " + is +
			     ", for name: " + name);
	} else {
	    System.err.println("name: " + name +
			       " had correct preference value: " +
			       expected);
	}
    }
    
    /**
     * Check that a series of names have expected preferences values.
     */
    private static void checkPreferredNames(Object pr)
	throws IOException
    {
	checkPreferred(pr, "One.class", true);
	checkPreferred(pr, "Two.class", false);
	checkPreferred(pr, "p/p2/Three.class", true);
	checkPreferred(pr, "p1/Four.class", true);
	checkPreferred(pr, "HasInner.class", true);
	checkPreferred(pr, "HasInner$Inner1.class", false);
	checkPreferred(pr, "HasInner$Inner1$Inner2.class", false);
	checkPreferred(pr, "HasInner$Inner1$Inner2$Inner3.class", true);
    }

    /**
     * Read preferred list from a file, interpret them and write them
     * to a byte array.  Read a preferred list file (dont interpret)
     * and compare the interpreted list to the non-interpreted list.
     * The bytes of the two lists must be the same.
     */
    private static void checkPrefRewrite(Object pr)
	throws IOException
    {
	FileInputStream fin = new FileInputStream(TestParams.testSrc +
						  File.separator + PREF_LIST);
	byte[] fileBytes = new byte[400];
	int nRead = fin.read(fileBytes);
	fileBytes[nRead] = 0;
	String readPrefs = new String(fileBytes, 0, nRead);
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            write.invoke(pr, bout);
            write.invoke(pr, System.out);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        }
        
	String rewrittenPrefs = bout.toString();
	    
	System.err.println("READ PREFS-----");
	System.err.print(readPrefs);
	System.err.println("REWRITTEN PREFS-----");	    
	System.err.print(rewrittenPrefs);
	System.err.println("-----");
	    
	if (!readPrefs.equals(rewrittenPrefs)) {
	    TestLibrary.bomb("read prefs not the same as rewritten prefs");
	}
    }

    /**
     * Check if a class with name, name is assignable to a local
     * version of the class.  The named class must implement the
     * non-preferred interface class with name, npInterface.
     */
    private static boolean parentAssignableFromChild(String name,
						     String npInterface)
	throws ClassNotFoundException, MalformedURLException
    {
	// parent (i.e. local) class
	Class pc = Class.forName(name);
	// child class
	Class cc = RMIClassLoader.loadClass(RESOURCE_URL +
					    "child.jar", name);
	Class np = null;
	
	if (npInterface != null) {
	    np = Class.forName(npInterface);
	    if (!np.isAssignableFrom(cc)) {
		TestLibrary.bomb("preferred class does not implement " +
				 "non-preferred interface");
	    } else {
		System.err.println("preferred child class correctly " +
				   "implements non-preferred interface");
	    }
	}

    	System.err.println("    parent class: " + pc.getName() + " source: " +
	    pc.getProtectionDomain().getCodeSource().getLocation());
	System.err.println("    child class:  " + cc.getName() + " source: " +
	    cc.getProtectionDomain().getCodeSource().getLocation());

	if (pc.isAssignableFrom(cc)) {
	    return true;
	}
	return false;
    }

    /**
     * Check that preferred classes are not assignable to local
     * classes unless those preferred classes extend a local
     * interface.
     */
    private static void checkAssignability() throws
        ClassNotFoundException, MalformedURLException
    {
	if (parentAssignableFromChild("One", null)) {
	    TestLibrary.bomb("One: parent and child classes of the same type");
	} else {
	    System.err.println("One: child class correctly preferred\n");
	}
	    
	if (!parentAssignableFromChild("Two", null)) {
	    TestLibrary.bomb("Two: parent and child classes not of the " +
			     "same type");
	} else {
	    System.err.println("Two: correctly not preferred\n");
	}
	if (parentAssignableFromChild("p.p2.Three", null)) {
	    TestLibrary.bomb("p.p2.Three: parent and child classes of " +
			     "the same type");
	} else {
	    System.err.println("p.p2.Three: child class " +
			       "correctly preferred\n");
	}
	// check that a preferred implementation class can have
	// the same type as a parent interface.
	if (parentAssignableFromChild("p1.Four",
				      "annoying.NonpreferredInterface"))
	{
	    TestLibrary.bomb("p1.Four: child class incorrectly not preferred");
	} else {
	    System.err.println("p1.Four: child class correctly " +
			       "preferred\n");
	}
    }
    
    /**
     * Signal if the current preference settings declare the given
     * name to be preferred.  Since the preferred resources object no
     * longer has a simple isPreferred method, the test needs its own
     * method to perform the same task which is implemented in
     * net.jini.loader.pref.PreferredClassLoader.
     *
     * net.jini.loader.pref.PreferredClassLoader.isPreferredResource()
     * method will be exercised by the assignability checks earlier in
     * this test.
     */
    private static boolean isPreferred(Object prefs,
				       String name,
				       boolean isClass)
	throws IOException
    {
        Object[] params = {name, isClass};
	int state = -1;
	boolean preferred = false;
        try {
            state = ((Integer) getNameState.invoke(prefs, params)).intValue();
            preferred = ((Boolean) getDefaultPreference.invoke(prefs, (Object[]) null)).booleanValue();
        } catch (IllegalAccessException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        }

	switch (state) {
	case NAME_NO_PREFERENCE:
	    Boolean wildcardPref = Boolean.FALSE;
        try {
            wildcardPref = (Boolean) getWildcardPreference.invoke(prefs, name);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        }
	    if (wildcardPref != null) {
		preferred = wildcardPref.booleanValue();
	    }
	    break;
	case NAME_NOT_PREFERRED:
	    preferred = false;
	    break;
	case NAME_PREFERRED_RESOURCE_EXISTS:
	case NAME_PREFERRED:
	    preferred = true;
	    break;
	default:
	    TestLibrary.bomb("unknown preference value: " + state);
   	}

	return preferred;
    }

    private static void checkNonClassResource(String name,
					      boolean preferred,
					      ClassLoader loader)
	throws IOException
    {
	URL url = loader.getResource(name);
	if (url == null) {
	    TestLibrary.bomb("could not find non-class resource, " + name);
	}
	String location = url.toString();
	System.err.println("Location for name: " + name + " : " + location);
	
	if (location.startsWith("jar:")) {
	    if (preferred) {
		System.err.println("Name correctly preferred: " + name + "\n");
	    } else {
		TestLibrary.bomb("Name should not have been preferred: " +
				 name);
	    }
	} else {
	    if (!preferred) {
		System.err.println("Name correctly not preferred\n");
	    } else {
		TestLibrary.bomb("Name should have been preferred: " + name);
	    }
	}
    }
    
    /**
     * Checks that preferred non-class resources will be correctly
     * loaded from a child class loader when appropriate.
     */
    private static void checkNonClassResources() throws IOException {
	Thread.currentThread().setContextClassLoader(
            new URLClassLoader(
	        new URL[] {new URL(RESOURCE_URL +
				   "non-class-resources/")}));
	ClassLoader loader =
	    RMIClassLoader.getClassLoader(RESOURCE_URL +
					  "non-class-resources.jar");
		
	String loaderName = null;
	if ((loader == null) ||
	    !(loaderName = loader.getClass().getName()).
	    startsWith("net.jini.loader.pref.PreferredClass"))
	{
	    TestLibrary.bomb("RMIClassLoader.getClassLoader() did not " +
			     "return correct class loader: \n" + loaderName);
	}

	// check the that the default preference is used
	checkNonClassResource("afile", true, loader);
	checkNonClassResource("images/foo.jpg", false, loader);
	checkNonClassResource("images/fakeimage.jpg", true, loader);
	checkNonClassResource("looks.like.a.class", true, loader);		
	checkNonClassResource("images/people/smiley.jpg", false, loader);
    }

    /**
     * Loads the preferred list from child.jar and creates a
     * PreferredResources object from that list.
     */
    private static Object getPreferredResources()
	throws IOException
    {
        Object prefs = null;
	JarFile jar = new JarFile(TestParams.testSrc +
				  File.separator + "child.jar");
        JarEntry e = jar.getJarEntry(PREF_LIST);
        try {
            // if found, then load the index
            prefs = prefResCons.newInstance(jar.getInputStream(e));
        } catch (InstantiationException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(CorrectInterpretation.class.getName()).log(Level.SEVERE, null, ex);
        }
        return prefs;
    }

    /**
     * Install test classes in appropriate directories
     */
    private static void installClasses() throws MalformedURLException {
	// installClassInCodebase cant handle nested dirs, so help it out
	String dir = TestLibrary.getProperty("user.dir", ".") +
	    File.separator + "p";
	(new File(dir)).mkdir();
	dir += File.separator + "p2";
	(new File(dir)).mkdir();

	try {
	    TestLibrary.installClassInCodebase("Three", "p/p2");
	    TestLibrary.installClassInCodebase("Four", "p1");
	} catch (RuntimeException e) {
	    if (e.getMessage().indexOf("try rebuilding") < 0) {
		throw e;
	    }
	}
    }
    
    public static void main(String[] args) {
	try {
	    System.err.println("\nRegression test to check that preferred " +
			       "classes implementation has correct behavior\n");
	    
	    Object pr = getPreferredResources();

	    installClasses();

	    // check that a series of names have expected preference values.
	    System.err.println("\n----- Checking name prefs ---------------\n");
	    checkPreferredNames(pr);
		
	    // check that prefs are rewritten correctly.
	    System.err.println("\n----- Checking pref rewrite -------------\n");
	    checkPrefRewrite(pr);
	    
	    TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

	    // check that a series of classes are only assignable to
	    // local classes when those classes are preferred and
	    // extend local interfaces or the classes are not
	    // preferred
	    System.err.println("\n----- Checking assignability ------------\n");
	    checkAssignability();
	    
	    // check for syntax errors in test PREFERRED.LISTs
	    System.err.println("\n----- Checking syntax errors ------------\n");
	    checkSyntax();

	    System.err.println("\n----- Checking non-class resources ------\n");
	    checkNonClassResources();
	    
	    System.err.println("");
	    System.err.println("TEST PASSED");
	    
	} catch (Exception e) {
	    if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    }
	    TestLibrary.bomb("test received unexpected exception", e);
	}
    }
}
