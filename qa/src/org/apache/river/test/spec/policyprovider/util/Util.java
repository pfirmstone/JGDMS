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
package org.apache.river.test.spec.policyprovider.util;

import java.util.logging.Level;

// java.net
import java.net.URL;
import java.net.MalformedURLException;
import java.net.InetAddress;

// java.util
import java.util.Random;
import java.util.StringTokenizer;

// java.lang.reflect
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;


/**
 *  Helper class to define preferred classes.
 *  Static arrays of this class describe class names
 *  according with
 *  org/apache/river/test/spec/policyprovider/util/classes/META-INF/PREFERRED.LIST
 *  file.
 */
public class Util {

    /** jar file in the executing VM's classpath */
    public static final String QAJar = "qa1.jar";

    /** jar file with preferred list, classes and resources */
    public static final String POLICYJar = "qa1-policy-provider.jar";

    /** Static array of classes to be preferred or not preferred */
    public static final Item[] listClasses = {
            new Item("classes.Class01", true, true),
            new Item("classes.Class02", true, true),
    };

    /** Static array of classes extended java.security.Policy class */
    public static final Item[] listPolicy = {
            new Item("QABadPolicy", true, true),
    };

    /**
     * Helper to format test status string.
     *
     * @param msg head of status message.
     * @param ret message about returned value.
     * @param exp message about expected value.
     *
     * @return status string.
     */
    public static String fail(String msg, String ret, String exp) {
        StringBuffer buf = new StringBuffer("\n");
        buf.append(msg).append("\n");
        buf.append("  returned: ").append(ret).append("\n");
        buf.append("  expected: ").append(exp).append("\n");
        return buf.toString();
    }

    /**
     * Helper to format test status string.
     *
     * @param msg head of status message.
     * @param ret exception that was thrown.
     * @param exp message about expected value.
     *
     * @return status string.
     */
    public static String fail(String msg, Exception ret, String exp) {
        ret.printStackTrace(System.err);
        StringBuffer buf = new StringBuffer("\n");
        buf.append(msg).append("\n");
        buf.append("  throws:   ").append(ret.toString()).append("\n");
        buf.append("  expected: ").append(exp).append("\n");
        return buf.toString();
    }

    /**
     * Helper to format test status string.
     *
     * @param msg head of status message.
     * @param exp exception that was thrown as expected.
     *
     * @return status string.
     */
    public static String pass(String msg, Exception exp) {
        StringBuffer buf = new StringBuffer(msg);
        buf.append(" throws ").append(exp.toString()).append(" as expected");
        return buf.toString();
    }

    /**
     * Helper to format test status string.
     *
     * @param msg head of status message.
     * @param exp message about expected value.
     *
     * @return status string.
     */
    public static String pass(String msg, String exp) {
        StringBuffer buf = new StringBuffer(msg);
        buf.append(" ").append(exp).append(" as expected");
        return buf.toString();
    }
}
