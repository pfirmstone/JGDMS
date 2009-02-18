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
package com.sun.jini.qa.harness;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;

/**
 * A class which drives the execution of a test on a VM other than the one in
 * which the test harness is running. It is assumed that the name of the test
 * class is provided as an argument, and that the test class in turn has a
 * <code>main</code> method which is to be invoked by this class.  Success is
 * indicated if the invoked <code>main</code> method returns normally. Failure
 * is indicated if an exception is thrown. The test status is sent back to the
 * harness encoded in a character stream over <code>System.err</code>.
 * <p>
 * It is assumed that all of the tests dependant classes are
 * loadable via the system class loader. Also, it is assumed
 * that the test environment is completely defined by the harness which
 * invokes this VM. No properties or other environmental object (such
 * as security managers) are set by this class.
 */
public class MainWrapper {

    /** The initial <code>System.err</code>, kept for private use */
    private static PrintStream origErr;

    /** The logger for this class */
    private static Logger logger =
	Logger.getLogger("com.sun.jini.qa.harness");

    /**
     * The main method invoked in the test VM. The first argument is
     * assumed to be the name of the test, which is only used for 
     * annotating messages. The second argument is assumed to be
     * the name of the test implementation class, which must provide a
     * main method. The remaining arguments are assumed to be
     * arguments which are to be provided to the test.
     * <p>
     * This method loads the test class named in the second argument
     * and invokes the main method of that class, passing an argument
     * list derived from the remaining arguments passed to this
     * method.  A normal return results in a successful status return
     * to the harness. An exception return results in a failure status
     * return to the harness. The status string is written as the final
     * output to <code>System.err.</code> Exit status of the VM is 0 if the
     * test passes and 1 for any type of failure.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
	//XXXXX SUPPORT NEW VM INVOCATION MECHANISM
	origErr = System.err;
	System.setErr(System.out);
	if (args.length < 2) {
	    exit(false, Test.ENV, "MainWrapper arguments missing");
	}
	String testName = args[0];
	String testClassName = args[1];
	String[] testArgs = new String[args.length - 2];
	for (int i=0; i < args.length - 2; i++) {
	    testArgs[i] = args[i + 2];
	}
	Method mainMethod = null;
	try {
	    logger.log(Level.FINEST, 
		       "MainWrapper classpath = '" 
		       + System.getProperty("java.class.path") 
		       + "'");
	    Class testClass = Class.forName(testClassName);
            mainMethod = testClass.getMethod("main",
					     new Class[]{testArgs.getClass()});
        } catch (Exception e) {
            e.printStackTrace();
	    exit(false,
		 Test.ENV,
		 "Failed to generate/instantiate test class:" + e);
	}
	try {
            mainMethod.invoke(null, new Object[]{testArgs});
	    exit(true, Test.TEST, "OK");
	} catch (InvocationTargetException e) {
	    e.getCause().printStackTrace();
            exit(false,
		 Test.TEST,
		 "Test " + testName + " failed: " + e.getCause());
	} catch (Throwable e) {
	    e.printStackTrace();
	    exit(false,
		 Test.TEST, 
		 "Test " + testName + " failed: " + e);
	}
	//XXX cleanup temp directory??? Perhaps support an optional parameter?
    }

    /**
     * Encode the given <code>Status</code> into a one-line string.  Write that
     * string as the last line to System.err, and exit.  
     * <p>
     * When a test is run in
     * another VM, it must pass back the status information to the harness. To
     * do this, the wrapper converts the <code>Status</code> object returned
     * by the <code>run</code> method to a specially formatted string which is
     * written as the last output to <code>System.err</code>. 
     * <code>AbstractRunner</code> tracks the data written to this stream,
     * searching for the string <code>Status.STATUS_TOKEN</code>
     * and converts the specially formatted info following this string
     * back to a <code>Status</code> object when the test VM
     * exits.
     *
     * @param s the status to encode, must never be <code>null</code>
     */
    private static void exit(boolean state, int type, String message) {
	origErr.println(MasterHarness.genMessage(state, type, message));
	System.out.flush();
	System.out.close();
  	origErr.flush();
  	origErr.close();
	System.exit((state ? 0 : 1));
    }
}
