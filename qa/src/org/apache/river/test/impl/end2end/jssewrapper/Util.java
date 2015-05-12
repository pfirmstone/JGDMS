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

package org.apache.river.test.impl.end2end.jssewrapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * A utility class, primarily supporting logging. A system property
 * name jssewrapper.logArgs can be set to a string which controls logging.
 * The string contains comma separated tokens which specify what type
 * of information to print. The object which holds the flags table is
 * construct once during class initialization and never modified, therefore
 * no synchronization is required.
 */
public class Util {

    /** always log the message */
    static final String ALWAYS = "always";

    /** log method entry/exit */
    static final String CALLS  = "calls";

    /** log method call parameters */
    static final String PARAMS = "params";

    /** log method return values */
    static final String RETURN = "return";

    /** log miscellaneous info */
    static final String INFO   = "info";

    /** log a stack trace on some calls */
    static final String STACK  = "stack";

    /** log everything */
    static final String ALL    = "all";

    /** the set of flags parsed from the jssewrapper.logArgs property */
    private static HashSet logFlagsSet;

    static {
	logFlagsSet = new HashSet();
	String props = System.getProperty("jsseWrapper.logArgs");
        if (props == null) {
            props = ALWAYS;
        }
        else {
            props += "," + ALWAYS;
        }
	StringTokenizer st = new StringTokenizer(props, ",");
	while (st.hasMoreTokens()) {
	    logFlagsSet.add(st.nextToken());
	}
    }

    /**
     * log the given message if the given logFlag exists in the flags table
     *
     * @param logFlag the flag to test for
     * @param s the message to log
     */
    public static void log(String logFlag, String s) {
        if (logFlagsSet.contains(logFlag)
         || logFlagsSet.contains(ALL)) {
            System.out.println(s);
        }
    }

    /**
     * log the given exception if the given logFlag exists in the flags table.
     * In this case, a stack trace is generated
     *
     * @param logFlag the flag to test for
     * @param t the exception to log
     */
    public static void log(String logFlag, Throwable t) {
        if (logFlagsSet.contains(logFlag)
         || logFlagsSet.contains(ALL)) {
	    t.printStackTrace();
        }
    }

    /**
     * log a stack trace of the current thread if the given logFlag exists
     * in the flags table.
     *
     * @param logFlag the flag to test for
     */
    public static void log(String logFlag) {
        if (logFlagsSet.contains(logFlag)
         || logFlagsSet.contains(ALL)) {
            if (logFlag.equals(STACK))Thread.dumpStack();
        }
    }

    /**
     * Get the unqualified class name of the given object.
     *
     * @param the object who's name is desired
     * @return the unqualified class name of the object
     */
    static String getClassName(Object obj) {

	// this method fails if a class name ends with a '.', which
	// presumably never happens
	String name = obj.getClass().getName();
	try {
	    if (name.lastIndexOf('.') >= 0) {
		name = name.substring(name.lastIndexOf('.') + 1);
            }
	} catch (IndexOutOfBoundsException e) {}
	return name;
    }
}
