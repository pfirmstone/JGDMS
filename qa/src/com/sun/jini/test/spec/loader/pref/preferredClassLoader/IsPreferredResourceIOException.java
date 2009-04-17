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
import com.sun.jini.qa.harness.QATest;
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
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>protected boolean isPreferredResource(String name,
 *                                             boolean isClass)</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassLoader</code> class:
 *
 * <br><blockquote>
 *  Determine if any resources for the name parameter
 *  should be preferred by this loader.  Only returns
 *  true if a resource for name exists in
 *  the resources of this class loader and if the name
 *  parameter is marked as preferred in the preferred list for this
 *  loader.
 * </blockquote>
 *  <ul><lh>Parameters:</lh>
 *    <li>name - the name of resource for which a preferred value should be
 *               obtained</li>
 *    <li>isClass - true if the named parameter refers to a class resource</li>
 *  </ul>
 *
 * <b>Test Description</b><br><br>
 *
 *
 *  This test iterates over a set of various parameters passing to
 *  {@link QATestPreferredClassLoader} constructors.
 *  All parameters are passing to the {@link #testCase} method.
 *  <ul><lh>Possible parameters are:</lh>
 *  <li>URL[] urls: http or file based url to qa1-loader-pref.jar file</li>
 *  <li>ClassLoader parent: ClassLoader.getSystemClassLoader()</li>
 *  <li>String exportAnnotation: <code>null</code>,
 *                               "Any export annotation string"</li>
 *  <li>boolean requireDlPerm: <code>true</code>, <code>false</code></li>
 *  </ul>
 *
 *  Each {@link #testCase} iterates over a set of preferred/non-preferred
 *  resources.
 *  There are two sets of resources with the same names there. The first set of
 *  resources can be found in the executing VM's classpath. The second set of
 *  resources are placed in the qa1-loader-pref.jar file and can be downloaded
 *  using http or file based url.
 *  <br><br>
 *  Class {@link Util} has a statically defined lists of all resources
 *  placed in the qa1-loader-pref.jar file. {@link Util#listClasses},
 *  {@link Util#listResources}, {@link Util#listLocalClasses},
 *  {@link Util#listLocalResources} define names and preferred status of
 *  these resources.
 *  <br><br>
 *  This test does not start class server to download resources via http, so
 *  IOException is expected for preferred resources.
 *  For each preferred/non-preferred resource this test will try to invoke
 *  {@link QATestPreferredClassLoader#isPreferredResourceEx}
 *  and verify that IOException is thrown for preferred resources.
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ol><lh>This test requires the following infrastructure:</lh>
 *  <li> {@link QATestPreferredClassLoader} is an instrumented
 *       PreferredClassLoader using for davis.loader's and davis.loader.pref's
 *       testing.</li>
 *  <li> <code>META-INF/PREFERRED.LIST</code> with a set of
 *       preferred/non-preferred resources. <code>META-INF/PREFERRED.LIST</code>
 *       should be placed in the qa1-loader-pref.jar file.</li>
 *  <li> A first set of resources should be placed in the qa1.jar file, so these
 *       resource can be found in the executing VM's classpath.</li>
 *  <li> A second set of resources should be placed in the qa1-loader-pref.jar,
 *       so these resources can be downloaded.</li>
 * </ol>
 *
 * <br>
 *
 * <b>Actions</b><br><br>
 *
 * <ol>
 *    <li> construct a {@link QATestPreferredClassLoader} with a single URL to
 *         the qa1-loader-pref.jar file and appropriate parameters.
 *    </li>
 *    <li> for each preferred/non-preferred resource invoke
 *         QATestPreferredClassLoader.isPreferredResourceEx(name, false) and
 *         verify that IOException is thrown for preferred resources.
 *    </li>
 * </ol>
 *
 */
public class IsPreferredResourceIOException extends AbstractTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        // Try to emulate IOException, so that do not start classerver
        String annotation = super.annotation;
        testCase(true, null);
        testCase(true, annotation);
    }

    /**
     * Reset setup parameters by passing parameters and create
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
         * Reset setup parameters by passing parameters.
         */
        super.isHttp = isHttp;
        super.annotation = annotation;

        /*
         * 1) construct a QATestPreferredClassLoader with a single URL
         *    to the "qa1-loader-pref.jar file.
         */
        createLoader(Util.PREFERREDJarFile);

        /*
         * 2) for each preferred/non-preferred resource invoke
         *    loader.isPreferredResourceEx(name, false)
         *    and assert that IOException is thrown for preferred resources.
         */
        for (int item = 0; item < Util.listResources.length; item++) {
            String name = Util.listResources[item].name;
            Exception returned = loader.isPreferredResourceEx(name, false);

            if (returned != null && (returned instanceof IOException)) {
                String message = "isPreferredResource(" + name + ", false)"
                        + "  throws  IOException as expected";
                logger.log(Level.FINE, message);
            } else {
                String message = "isPreferredResource(" + name + ", false)\n";

                if (returned == null) {
                    message += "   does not throw IOException\n";
                } else {
                    message += "   returned:" + returned.toString() + "\n";
                    message += "   expected: instance of IOException";
                }

                // Fast fail approach
                throw new TestException(message);
            }
        }
    }
}
