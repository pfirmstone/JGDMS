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
package com.sun.jini.test.spec.loader.pref.preferredClassLoader;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;

// java.io
import java.io.IOException;

// java.net
import java.net.URL;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;

// instrumented preferred class loader
import com.sun.jini.test.spec.loader.util.Item;
import com.sun.jini.test.spec.loader.util.Util;
import com.sun.jini.test.spec.loader.util.QATestPreferredClassLoader;

// test base class
import com.sun.jini.test.spec.loader.pref.AbstractTestBase;


/**
 *
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>public String getClassAnnotation()</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassLoader</code> class:
 *
 * <br><blockquote>
 * Return the string to be annotated with all classes loaded from
 * this class loader.
 * </blockquote>
 *
 * <b>Test Description</b><br><br>
 *
 *  This test iterates over a set of various parameters passing to
 *  {@link QATestPreferredClassLoader} constructors.
 *  All parameters are passing to the {@link #testCase} method.
 *  <ul><lh>Possible parameters are:</lh>
 *  <li>URL[] urls: http or file based url to qa1-loader-pref.jar file and
 *                  qa1-loader-pref-NO_PREFERRED_LIST.jar</li>
 *  <li>ClassLoader parent: ClassLoader.getSystemClassLoader()</li>
 *  <li>String exportAnnotation: <code>null</code>,
 *                               "Any export annotation string"</li>
 *  <li>boolean requireDlPerm: <code>true</code>, <code>false</code></li>
 *  </ul>
 *
 *  <br><br>
 *  This test verifies returned string annotation for <code>null</code> and
 *  non-<code>null</code>exportAnnotation passing to
 *  {@link QATestPreferredClassLoader} constructors.
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ol><lh>This test requires the following infrastructure:</lh>
 *  <li> {@link QATestPreferredClassLoader} is an instrumented
 *       PreferredClassLoader using for davis.loader's and davis.loader.pref's
 *       testing.</li>
 * </ol>
 *
 * <br>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> construct a {@link QATestPreferredClassLoader} with urls to
 *         the qa1-loader-pref.jar file and appropriate parameters.
 *    </li>
 *    <li> invoke loader.getClassAnnotation()
 *         and verify that we get expected result
 *    </li>
 * </ol>
 *
 */
public class GetClassAnnotation extends AbstractTestBase {

    /** String that indicates fail status */
    String message = "";

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        String annotation = super.annotation;
        testCase(true, null);
        testCase(true, annotation);
        testCase(false, null);
        testCase(false, annotation);

        if (message.length() > 0) {
            throw new TestException(message);
        }
    }

    /**
     * Reset construct parameters by passing parameters and create
     * {@link QATestPreferredClassLoader}.
     * <br><br>
     * Then run the test case according <b>Test Description</b>
     *
     * @param isHttp flag to define whether http or file url will be used
     *        for download preferred classes and resources
     * @param annotation the exportAnnotation string
     *
     * @throws TestException if could not create instrumented preferred class
     *         loader
     */
    public void testCase(boolean isHttp, String annotation)
            throws TestException {

        /*
         * Reset construct parameters by passing parameters.
         */
        super.isHttp = isHttp;
        super.annotation = annotation;

        /*
         * 1) construct a QATestPreferredClassLoader with urls
         *    to "qa1-loader-pref.jar file and
         *    qa1-loader-pref-NO_PREFERRED_LIST.jar files.
         */
        createLoader(Util.PREFERREDJarFile, Util.NOPREFERREDListJarFile);
        String expected = expectedAnnotationString();
        String returned = loader.getClassAnnotation();

        if (!expected.equals(returned)) {
            message += "\ngetClassAnnotation()\n"
                     + "   returned:" + returned + "\n"
                     + "   expected:" + expected;

            // Fast fail approach
            throw new TestException(message);
        } else {
            String msg = "getClassAnnotation()"
                       + "  returned " + returned + "  as expected";
            logger.log(Level.FINEST, msg);
        }
    }
}
