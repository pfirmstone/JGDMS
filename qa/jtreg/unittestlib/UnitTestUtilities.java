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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.*;
import java.security.AccessControlContext;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.UnresolvedPermission;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;

/** Provides common utilities for running tests that implement Test. */
public class UnitTestUtilities {

    /* -- Fields -- */

    /**
     * Controls the amount of information printed.
     *  0 -- Just print final results and failures.
     *  5 -- Include stack trace for unexpected exceptions (default).
     * 10 -- Print test number and class for each new top level test class
     * 15 -- Print test number and class for every test.
     * 20 -- Print full test entry and pass/fail for every test.
     * 25 -- Include passing results.
     * 30 -- Include additional test debugging output, including time.
     */
    public static final int testLevel =
	Integer.getInteger("testLevel", 5).intValue();

    /** The index of the first test to run */
    public static final int firstTest =
	Integer.getInteger("firstTest", 1).intValue();

    /** The index of the last test to run */
    public static final int lastTest =
	Integer.getInteger("lastTest", Integer.MAX_VALUE).intValue();

    /** The number of the current test */
    public static int testNumber = 0;

    /** If true, stop after first failure. */
    public static final boolean stopOnFail = Boolean.getBoolean("stopOnFail");

    /** The name of the last top-level test class */
    private static String lastTopLevelTestClass;

    /* -- Classes -- */

    /** Holds test results */
    private static class TestResults {
	int pass;
	int fail;
    }

    /** Used to signal that lastTest has been done. */
    private static class DoneException extends RuntimeException { }

    /* -- Methods -- */

    /**
     * Performs a series of tests and throws an exception if any of them fail.
     *
     * @param test the object to test.  If test implements Test, then run that
     * test; if test is an Object[], then recurse on the elements, if test is a
     * Collection, then recurse on the elements, if test is null, then ignore
     * it, otherwise throw a RuntimeException.
     */
    public static void test(Object test) {
	TestResults results = new TestResults();
	long start = System.currentTimeMillis();
	System.out.println("\n*** Start test: " + new Date(start));
	try {
	    test(test, results);
	} catch (DoneException e) {
	}
	long stop = System.currentTimeMillis();
	System.out.println("\n*** Test results:");
	if (firstTest != 1) {
	    System.out.println("***   First test: " + firstTest);
	}
	if (lastTest != Integer.MAX_VALUE) {
	    System.out.println("***   Last test: " + lastTest);
	}
	System.out.println(
	    "***   PASS: " + results.pass + "\n" +
	    "***   FAIL: " + results.fail +
	    ((stopOnFail && results.fail != 0)
	     ? " (stopped on failure)\n" : "\n") +
	    "***   Time: " + (stop - start) + " ms\n");
	if (results.fail != 0) {
	    throw new Test.FailedException(
		results.fail + " test failure" +
		(results.fail == 1 ? "" : "s"));
	}
    }

    /**
     * Runs the tests for the specified test object and updates results.
     */
    private static void test(Object test, TestResults results) {
	if (stopOnFail && results.fail != 0) {
	    return;
	} else if (test instanceof Test) {
	    test((Test) test, results);
	} else if (test instanceof Object[]) {
	    Object[] array = (Object[]) test;
	    for (int i = 0; i < array.length; i++) {
		test(array[i], results);
		if (stopOnFail && results.fail != 0) {
		    break;
		}
	    }
	} else if (test instanceof Collection) {
	    for (Iterator iter = ((Collection) test).iterator();
		 iter.hasNext(); )
	    {
		test(iter.next(), results);
		if (stopOnFail && results.fail != 0) {
		    break;
		}
	    }
	} else if (test != null) {
	    throw new RuntimeException("Unrecognized test: " + test);
	}
    }

    /** Runs an individual test and updates results. */
    private static void test(Test test, TestResults results) {
	if (++testNumber < firstTest) {
	    return;
	}
	if (testNumber > lastTest) {
	    throw new DoneException();
	}
	if (testLevel >= 20) {
	    /* Print test number and entry */
	    System.out.println("Test " + testNumber + ": " +
			       testName(test, test.name()));
	} else if (testLevel >= 15) {
	    /* Print test number and class */
	    System.out.println("Test " + testNumber + ": " +
			       test.getClass().getName());
	} else if (testLevel >= 10) {
	    /* Print test number and class for new top level test class */
	    String testClass = test.getClass().getName();
	    int idx = testClass.indexOf("$");
	    String topLevelTestClass =
		(idx != -1) ? testClass.substring(0, idx) : testClass;
	    if (!topLevelTestClass.equals(lastTopLevelTestClass)) {
		System.out.println("Test " + testNumber + ": " +
				   topLevelTestClass);
		lastTopLevelTestClass = topLevelTestClass;
	    }
	}

	long start = (testLevel >= 30) ? System.currentTimeMillis() : 0;
	String errorMessage = null;
	try {
	    Object result = test.run();
	    try {
		test.check(result);
		if (testLevel >= 20) {
		    /* Print results */
		    System.out.print("PASS: ");
		    if (testLevel >= 25) {
			if (testLevel >= 30) {
			    System.out.print(
				(System.currentTimeMillis() - start) +
				" ms: ");
			}
			System.out.print(
			    "Result: " + 
			    (result instanceof Throwable
			     ? toString((Throwable) result)
			     : String.valueOf(result)));
		    }
		    System.out.println();
		}
		results.pass++;
	    } catch (Test.FailedException e) {
		errorMessage = e.getMessage() + "\n      Result: " +
		    (result instanceof Throwable
		     ? toString((Throwable) result) : String.valueOf(result));
	    }
	} catch (Test.FailedException e) {
	    errorMessage = e.getMessage();
	} catch (Exception e) {
	    errorMessage = "Unexpected exception: " + toString(e);
	}
	if (errorMessage != null) {
	    if (testLevel < 20) {
		System.out.println("Test " + testNumber + ": " +
				   testName(test, test.name()));
	    }
	    System.out.print("FAIL: ");
	    if (testLevel >= 30) {
		System.out.print(
		    (System.currentTimeMillis() - start) + " ms: ");
	    }
	    System.out.println(errorMessage);
	    results.fail++;
	}
    }

    /**
     * Returns the appropriate String for describing a Throwable at the current
     * test level.
     */
    public static String toString(Throwable t) {
	return (testLevel < 5) ? String.valueOf(t) : getStackTrace(t);
    }

    /** Returns a String containing the stack trace for a throwable. */
    public static String getStackTrace(Throwable t) {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	PrintStream ps = new PrintStream(baos);
	t.printStackTrace(ps);
	ps.flush();
	String result = baos.toString();
	ps.close();
	return result;
    }

    /**
     * Returns the test name to use for the specified test object and
     * test-specific name.
     */
    private static String testName(Test test, String name) {
	Class clss = test.getClass();
	String className = getClassName(clss);
	if (isClassNameAnonymous(className)) {
	    /*
	     * This is an anonymous class.  Include the name of the superclass
	     * or interface.
	     */
	    className += " (" + getAnonymousSuperName(clss) + ")";
	}
	return className + ": " + name;
    }

    /** Returns the class name, without the package */
    private static String getClassName(Class clss) {
	String className = clss.getName();
	return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Returns the name of the superclass or interface for an anonymous class.
     */
    private static String getAnonymousSuperName(Class clss) {
	Class superclass = clss.getSuperclass();
	if (superclass == Object.class) {
	    Class[] interfaces = clss.getInterfaces();
	    if (interfaces.length > 0) {
		superclass = interfaces[0];
	    }
	}
	return getClassName(superclass);
    }

    /** Returns true if the argument is the name of an anonymous class */
    private static boolean isClassNameAnonymous(String className) {
	int dollarPos = className.indexOf('$');
	if (dollarPos < 0) {
	    return false;
	}
	char firstChar = className.charAt(dollarPos + 1);
	return (firstChar >= '0') && (firstChar <= '9');
    }

    /**
     * Prints the specified debugging message if the test level is at least
     * the value specified.
     */
    public static void debugPrint(int forLevel, String message) {
	if (testLevel >= forLevel) {
	    System.out.println(message);
	}
    }

    /** Same as equals, but handles null objects */
    public static boolean safeEquals(Object x, Object y) {
	if (x == null) {
	    return y == null;
	} else {
	    return x.equals(y);
	}
    }

    /** Returns a RuntimeException to throw for an unexpected exception */
    public static RuntimeException unexpectedException(Throwable t) {
	return (t instanceof RuntimeException)
	    ? (RuntimeException) t
	    : new RuntimeException("Unexpected exception: " + t);
    }

    /** Converts the contents of an Object array to a String. */
    public static String toString(Object[] array) {
	if (array == null) {
	    return "null";
	}
	StringBuffer buf = new StringBuffer("[");
	for (int i = 0; i < array.length; i++) {
	    if (i != 0) {
		buf.append(", ");
	    }
	    buf.append(array[i]);
	}
	buf.append("]");
	return buf.toString();
    }

    /** Returns the result of serializing and deserializing the argument. */
    public static Object serialized(Object o) throws IOException {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ObjectOutputStream out = new ObjectOutputStream(baos);
	out.writeObject(o);
	out.flush();
	try {
	    return new ObjectInputStream(
		new ByteArrayInputStream(baos.toByteArray())).readObject();
	} catch (ClassNotFoundException e) {
	    throw unexpectedException(e);
	}
    }

    /**
     * Returns an AccessControlContext similar to the current one, but only the
     * specified permissions of the specified type.
     */
    public static AccessControlContext withPermissions(Class permissionClass,
						       Permission[] permissions)
    {
	ProtectionDomain domain = UnitTestUtilities.class.getProtectionDomain();
	PermissionCollection origPerms =
	    Policy.getPolicy().getPermissions(domain);
	PermissionCollection perms = new Permissions();
	for (Enumeration en = origPerms.elements();
	     en.hasMoreElements(); )
	{
	    Permission perm = (Permission) en.nextElement();
	    if (!(permissionClass.isInstance(perm)
		  || isUnresolvedInstanceOf(perm, permissionClass)))
	    {
		perms.add(perm);
	    }
	}
	if (permissions != null) {
	    for (int i = 0; i < permissions.length; i++) {
		perms.add(permissions[i]);
	    }
	}
	return new AccessControlContext(
	    new ProtectionDomain[] {
		new ProtectionDomain(null, perms)
	    });
    }

    private static Field unresolvedPermissionType;

    static boolean isUnresolvedInstanceOf(Permission perm, Class permClass) {
	if (!(perm instanceof UnresolvedPermission)) {
	    return false;
	}
	synchronized (UnitTestUtilities.class) {
	    if (unresolvedPermissionType == null) {
		try {
		    unresolvedPermissionType =
			UnresolvedPermission.class.getDeclaredField("type");
		    unresolvedPermissionType.setAccessible(true);
		} catch (NoSuchFieldException e) {
		    throw unexpectedException(e);
		}	    
	    }
	}
	try {
	    String type = (String) unresolvedPermissionType.get(perm);
	    return type.equals(permClass.getName());
	} catch (IllegalAccessException e) {
	    throw unexpectedException(e);
	}
    }
}
