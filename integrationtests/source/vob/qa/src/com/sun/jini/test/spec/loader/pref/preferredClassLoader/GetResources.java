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
import java.net.MalformedURLException;

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
 * <code>public java.net.URL getResource(String name)</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassLoader</code> class:
 *
 * <br><blockquote>
 *  Override getResource() to implement support for preferred classes in RMI.
 *  If the name parameter is not preferred, the search is the same as with
 *  ClassLoader.getResource(). If name is preferred, then this method will call
 *  findResource() and will not delegate to the parent class loader to find the
 *  resource.
 * </blockquote>
 *  <ul><lh>Parameters:</lh>
 *    <li>name - the name of resource</li>
 *  </ul>
 *
 * <b>Test Description</b><br><br>
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
 *  classes.
 *  There are two sets of classes with the same names there. The first set of
 *  classes can be found in the executing VM's classpath. The second set of
 *  classes are placed in the qa1-loader-pref.jar file and can be downloaded
 *  using http or file based url.
 *  <br><br>
 *  Class {@link Util} has a statically defined lists of all resources
 *  placed in the qa1-loader-pref.jar file. {@link Util#listClasses},
 *  {@link Util#listResources}, {@link Util#listLocalClasses},
 *  {@link Util#listLocalResources} define names and preferred status of
 *  these resources.
 *  <br><br>
 *  For each preferred/non-preferred resource this test will try to invoke
 *  QATestPreferredClassLoader.getResource() method and compare returned url
 *  with appropriate url.
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
 *    <li> Get urls for qa1-loader-pref.jar file and qa1.jar file.
 *         qa1-loader-pref.jar file is file to download preferred resources.
 *         qa1.jar file is file in the executing VM's classpath.
 *    </li>
 *    <li> construct a {@link QATestPreferredClassLoader} with a single URL to
 *         the qa1-loader-pref.jar file and appropriate parameters.
 *    </li>
 *    <li> for each preferred/non-preferred resource invoke
 *         QATestPreferredClassLoader.getResource(name) and
 *         compare returned url with appropriate url to qa1.jar file for
 *         preffered resources or qa1-loader-pref.jar file for non-preffered
 *         resources.
 *    </li>
 *    <li> for each preferred/non-preferred class invoke
 *         QATestPreferredClassLoader.getResource(name) and
 *         verify that we get <code>null</code> as expected result.
 *    </li>
 *    <li> Ten times invoke {@link Util#getRandomName} and
 *         invoke loader.getResource(name)
 *         passinng this name and assert that we get <code>null</code> as
 *         expected result.
 *    </li>
 *    <li> invoke loader.getResource(name)
 *         for recources that can be found in the parent class loader
 *         (such as in the class path) but cannot be found in the
 *         preferred class loader and assert that we get url to
 *         qa1.jar file.
 *    </li>
 * </ol>
 *
 */
public class GetResources extends AbstractTestBase {

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
         * 1) Get urls for qa1-loader-pref.jar file and qa1.jar file.
         *    qa1-loader-pref.jar file is file to download preferred resources.
         *    qa1.jar file is file in the executing VM's classpath.
         */
        String jarfile = Util.PREFERREDJarFile;
        URL[] pUrls = null; // urls for qa1-loader-pref.jar
        URL[] qUrls = null; // urls for qa1.jar
        pUrls = Util.getUrls(isHttp, jarfile, null, config, port);
        qUrls = Util.getUrls(false, Util.QAJarFile, null, config, -1);
        URL pUrl = pUrls[0]; // url for qa1-loader-pref.jar
        URL qUrl = qUrls[0]; // url for qa1.jar

        /*
         * 2) construct a QATestPreferredClassLoader with a single URL
         *    to the "qa1-loader-pref.jar file.
         */
        createLoader(jarfile);

        /*
         * 3) for each preferred/non-preferred resource invoke
         *    QATestPreferredClassLoader.getResource(name) and
         *    compare returned url with appropriate url to qa1.jar file for
         *    preffered resources or qa1-loader-pref.jar file for non-preffered
         *    resources.
         */
        for (int item = 0; item < Util.listResources.length; item++) {
            String name = Util.listResources[item].name;
            URL expectedURL = Util.listResources[item].pref ? pUrl : qUrl;
            URL returnedURL = loader.getResource(name);
            String expected = expectedURL.toExternalForm();
            String returned = returnedURL.toExternalForm();
            expected = "jar:" + expected + "!/" + name;

            if (!expected.equals(returned)) {
                message += "\ngetResource("
                         + name + ")\n"
                         + "   returned:" + returned + "\n"
                         + "   expected:" + expected;

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "getResource(" + name + ")" + " returned "
                           + returned + " as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 4) for each preferred/non-preferred class invoke
         *    QATestPreferredClassLoader.getResource(name) and
         *    assert that we get null as expected result.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            URL returnedURL = loader.getResource(name);

            if (returnedURL != null) {
                message += "\ngetResource("
                         + name + ")\n"
                         + "  returned: " + returnedURL.toExternalForm() + "\n"
                         + "  expected: " + "null";

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "getResource(" + name + ")" + " returned null"
                           + "  as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 5) Ten times invoke loader.getRandomName(false) and
         *    invoke loader.getResource(name)
         *    passinng this name and assert that we get null as
         *    expected result.
         */
        for (int item = 0; item < 10; item++) {
            String name = Util.getRandomName(false);
            URL returnedURL = loader.getResource(name);

            if (returnedURL != null) {
                message += "\ngetResource("
                         + name + ")\n"
                         + "  returned: " + returnedURL.toExternalForm() + "\n"
                         + "  expected: " + "null";

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "getResource(" + name + ")" + " returned null"
                           + " as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 6) invoke loader.getResource(name)
         *    for recources that can be found in the parent class loader
         *    (such as in the class path) but cannot be found in the
         *    preferred class loader and assert that we get url to
         *    qa1.jar file.
         */
        for (int item = 0; item < Util.listLocalResources.length; item++) {
            String name = Util.listLocalResources[item].name;
            URL expectedURL = qUrl;
            URL returnedURL = loader.getResource(name);
            String expected = expectedURL.toExternalForm();
            String returned = returnedURL.toExternalForm();
            expected = "jar:" + expected + "!/" + name;

            if (!expected.equals(returned)) {
                message += "\ngetResource("
                         + name + ")\n"
                         + "  returned: " + returned + "\n"
                         + "  expected: " + expected;

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "getResource(" + name + ")" + " returned "
                           + returned + " as expected";
                logger.log(Level.FINE, msg);
            }
        }
    }
}
