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
 *
 * @author Adrian Colley
 * @author Laird Dornin
 * @author Peter Jones
 * @author Ann Wollrath
 *
 * The rmi library directory contains a set of simple utiltity classes
 * for use in rmi regression tests.
 *
 * NOTE: The JavaTest group has recommended that regression tests do
 * not make use of packages.  
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import net.jini.export.Exporter;

/**
 * Class of utility/library methods (i.e. procedures) that assist with
 * the writing and maintainance of rmi regression tests.  
 */
public class TestLibrary {

    /** standard test port number for registry */
    public final static int REGISTRY_PORT = 2006;
    /** port for rmid necessary: not used to actually start rmid */
    public final static int RMID_PORT = 1098;
    /** in-view starter kit home directory */
    public final static String jskHome = (File.separator + "vob" +
					  File.separator + "jive");

    static void mesg(Object mesg) {
	System.err.println("TEST_LIBRARY: " + mesg.toString());
    }

    /**
     * Routines that enable rmi tests to fail in a uniformly
     * informative fashion.
     */
    public static void bomb(String message, Exception e) {
	String testFailed = "TEST FAILED: ";

	if ((message == null) && (e == null)) {
	    testFailed += " No relevant information";
	} else if (e == null) {
	    testFailed += message;
	}

	System.err.println(testFailed);
	if (e != null) {
	    System.err.println("Test failed with: " + 
			       e.getMessage());
	    e.printStackTrace(System.err);
	}
	throw new TestFailedException(testFailed, e);
    }
    public static void bomb(String message) {
	bomb(message, null);
    }
    public static void bomb(Exception e) {
	bomb(null, e);
    }

    /** 
     * Property accessors
     */
    private static boolean getBoolean(String name) {
	return (new Boolean(getProperty(name, "false")).booleanValue());
    }
    private static Integer getInteger(String name) {
	int val = 0;
	Integer value = null;
	
	String propVal = getProperty(name, null);
	if (propVal == null) {
	    return null;
	}
	
	try {
	    value = new Integer(Integer.parseInt(propVal));
	} catch (NumberFormatException nfe) {
	}
	return value;
    }
    public static String getProperty(String property, String defaultVal) {
	final String prop = property;
	final String def = defaultVal;
	return ((String) AccessController.doPrivileged
	    (new PrivilegedAction() {
		public Object run() {
		    return System.getProperty(prop, def);
		}
	    }));
    }

    /** 
     * Property mutators
     */
    public static void setBoolean(String property, boolean value) {
	setProperty(property, (new Boolean(value)).toString());
    }
    public static void setInteger(String property, int value) {
	setProperty(property, Integer.toString(value));
    }
    public static void setProperty(String property, String value) {
	final String prop = property;
	final String val = value;
	AccessController.doPrivileged(new PrivilegedAction() {
	    public Object run() {
		System.setProperty(prop, val);
		return null;
	    }
	});
    }

    /**
     * Routines to print out a test's properties environment.
     */
    public static void printEnvironment() {
	printEnvironment(System.err);
    }
    public static void printEnvironment(PrintStream out) {
	out.println("-------------------Test environment----------" + 
		    "---------");

	for(Enumeration keys = System.getProperties().keys(); 
	    keys.hasMoreElements();) {
	    
	    String property = (String) keys.nextElement();
	    out.println(property + " = " + getProperty(property, null));
	}
	out.println("---------------------------------------------" + 
		    "---------");
    }

    /**
     * Routine that "works-around" a limitation in jtreg.
     * Currently it is not possible for a test to specify that the
     * test harness should build a given source file and install the
     * resulting class in a location that is not accessible from the
     * test's classpath.  This method enables a test to move a
     * compiled test class file from the test's class directory into a
     * given "codebase" directory.  As a result the test can only
     * access the class file for <code>className</code>if the test loads
     * it from a classloader (e.g. RMIClassLoader).
     *
     * Tests that use this routine must have the following permissions
     * granted to them:
     *
     *   getProperty user.dir
     *   getProperty etc.
     */
    public static URL installClassInCodebase(String className,
					     String codebase)
	throws MalformedURLException
    {
	return installClassInCodebase(className, codebase, true);
    }

    public static URL installClassInCodebase(String className,
					     String codebase,
					     boolean delete)
	throws MalformedURLException
    {
	/*
	 * NOTES/LIMITATIONS: The class must not be in a named package,
	 * and the codebase must be a relative path (it's created relative
	 * to the working directory).
	 */
	String classFileName = className + ".class";

	/*
	 * Specify the file to contain the class definition.  Make sure
	 * that the codebase directory exists (underneath the working
	 * directory).
	 */
	File dstDir = (new File(getProperty("user.dir", "."), codebase));

	if (!dstDir.exists()) {
	    if (!dstDir.mkdir()) {
		throw new RuntimeException(
		    "could not create codebase directory");
	    }
	}
	File dstFile = new File(dstDir, classFileName);

	/*
	 * Obtain the URL for the codebase.
	 */
	URL codebaseURL = new URL("file", "",
	    dstDir.getAbsolutePath().replace(File.separatorChar, '/') + "/");

	/*
	 * Specify where we will copy the class definition from, if
	 * necessary.  After the test is built, the class file can be
	 * found in the "test.classes" directory.
	 */
	File srcDir = new File(getProperty("test.classes", "."));
        if (!srcDir.exists()) {
            mesg("Directory, doesn't existing creating: " + srcDir.toString());
            if (srcDir.mkdir()){
                mesg("Directory successfully created");
            } else {
                mesg("Directory couldn't be created");
            }
        }
	File srcFile = new File(srcDir, classFileName);

mesg(srcFile);
mesg(dstFile);

	/*
	 * If the class definition is not already located at the codebase,
	 * copy it there from the test build area.
	 */
	if (!dstFile.exists()) {
	    if (!srcFile.exists()) {
		throw new RuntimeException(
		    "could not find class file to install in codebase " +
		    "(try rebuilding the test): " + srcFile);
	    }

	    try {
		copyFile(srcFile, dstFile);
	    } catch (IOException e) {
		throw new RuntimeException(
		    "could not install class file in codebase");
	    }

	    mesg("Installed class \"" + className +
		"\" in codebase " + codebaseURL);
	}

	/*
	 * After the class definition is successfully installed at the
	 * codebase, delete it from the test's CLASSPATH, so that it will
	 * not be found there first before the codebase is searched.
	 */
	if (srcFile.exists()) {
	    if (delete && !srcFile.delete()) {
		throw new RuntimeException(
		    "could not delete duplicate class file in CLASSPATH");
	    }
	}

	return codebaseURL;
    }

    public static void copyFile(File srcFile, File dstFile)
	throws IOException
    {
	FileInputStream src = new FileInputStream(srcFile);
	FileOutputStream dst = new FileOutputStream(dstFile);

	byte[] buf = new byte[32768];
	while (true) {
	    int count = src.read(buf);
	    if (count < 0) {
		break;
	    }
	    dst.write(buf, 0, count);
	}

	dst.close();
	src.close();
    }

    /** routine to unexport an object */
    public static void unexport(Remote obj) {
	unexport(obj, null);
    }

    /**
     * Forcibly unexports the object.  If the exporter is null, then
     * it unexports the object using UncastRemoteObject's unexportObject
     * method, otherwise it uses the exporter to unexport the object.
     */
    public static void unexport(Remote obj, Exporter exporter) {
	if (obj != null) {
	    try {
		mesg("unexporting object...");
		if (exporter == null) {
		    UnicastRemoteObject.unexportObject(obj, true);
		} else {
		    exporter.unexport(true);
		}
	    } catch (NoSuchObjectException munch) {
	    } catch (Exception e) {
		System.err.println("exception occurred unexporting object");
		e.getMessage();
		e.printStackTrace();
	    }
	}
    }

    /** 
     * Allow test framework to control the security manager set in
     * each test.  
     *
     * @param managerClassName The class name of the security manager
     *                         to be instantiated and set if no security
     *                         manager has already been set.
     */
    public static void suggestSecurityManager(String managerClassName) {
	SecurityManager manager = null;

	if (System.getSecurityManager() == null) {
	    try {
		if (managerClassName == null) {
		    managerClassName = TestParams.defaultSecurityManager;
		}
		manager = ((SecurityManager) Class.
			   forName(managerClassName).newInstance());
	    } catch (ClassNotFoundException cnfe) {
		bomb("Security manager could not be found: " +
		     managerClassName, cnfe);
	    } catch (Exception e) {
		bomb("Error creating security manager. ", e);
	    }
	    
	    System.setSecurityManager(manager);
	}
    }

    /**
     * Method to capture the stack trace of an exception and return it
     * as a string.
     */
    public String stackTraceToString(Exception e) {
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	PrintStream ps = new PrintStream(bos);
	
	e.printStackTrace(ps);
	return bos.toString();
    }

    /** extra properties */
    private static Properties props;

    /**
     * Returns extra test properties. Looks for the file "../../test.props"
     * and reads it in as a Properties file. Assuming the working directory
     * is "<path>/JTwork/scratch", this will find "<path>/test.props".
     */
    private static synchronized Properties getExtraProperties() {
	if (props != null) {
	    return props;
	}
	props = new Properties();
	File f = new File(".." + File.separator + ".." + File.separator +
			  "test.props");
	if (!f.exists()) {
	    return props;
	}
	try {
	    FileInputStream in = new FileInputStream(f);
	    try {
		props.load(in);
	    } finally {
		in.close();
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	    throw new RuntimeException("extra property setup failed", e);
	}
	return props;
    }
    
    public static String getExtraProperty(String property, String defaultVal) { 
	Properties p = (Properties) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    return getExtraProperties();
		}
	    });
	return p.getProperty(property, defaultVal);
    }
}
