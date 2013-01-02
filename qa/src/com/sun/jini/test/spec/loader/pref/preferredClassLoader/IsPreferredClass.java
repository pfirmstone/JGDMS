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
 *  For each preferred/non-preferred class this test will try to invoke
 *  {@link QATestPreferredClassLoader#isPreferredResourceTest} passing
 *  the name of class and verify expected result.
 *  <br><br>
 *  Also this test will try to
 *  invoke {@link QATestPreferredClassLoader#isPreferredResourceTest}
 *  for class names which do not exist and verify that returned result is equal
 *  to false.
 *  <br><br>
 *  Also this test will try to invoke
 *  {@link QATestPreferredClassLoader#isPreferredResourceTest}
 *  for classes that can be found in the parent class loader (such as in the
 *  class path) but cannot be found in the preferred class loader and verify
 *  that returned result is equal to false.
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
 *    <li> for each preferred/non-preferred class invoke
 *         QATestPreferredClassLoader.isPreferredResourceTest(name, true) and
 *         verify that we get expected result.
 *    </li>
 *    <li> Ten times invoke {@link Util#getRandomName} and
 *         invoke QATestPreferredClassLoader.isPreferredResourceTest(name, true)
 *         passinng this name and verify that we get false as expected result.
 *    </li>
 *    <li> invoke QATestPreferredClassLoader.isPreferredResourceTest(name, true)
 *         for classes that can be found in the parent class loader (such as in
 *         the class path) but cannot be found in the preferred class loader and
 *         verify that we get <code>false</code> as expected result.
 *    </li>
 * </ol>
 *
 */
public class IsPreferredClass extends AbstractTestBase {

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
         * 1) construct a QATestPreferredClassLoader with a single URL
         *    to the "qa1-loader-pref.jar file.
         */
        createLoader(Util.PREFERREDJarFile);

        /*
         * 2) for each preferred/non-preferred class invoke
         *    loader.isPreferredResourceTest(name, true)
         *    and assert that we get expected result.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            boolean expected = Util.listClasses[item].pref;
            boolean returned = loader.isPreferredResourceTest(name, true);

            if (expected != returned) {
                message += "\nisPreferredResource("
                         + name + ", true)\n"
                         + "  returned:" + returned + "\n"
                         + "  expected:" + expected;

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "isPreferredResource(" + name + ", true)"
                           + "  returned " + returned + "  as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 3) Ten times invoke loader.getRandomName(true) and
         *    invoke loader.isPreferredResourceTest(name, true)
         *    passinng this name and assert that we get false as
         *    expected result.
         */
        for (int item = 0; item < 10; item++) {
            String name = Util.getRandomName(true);
            boolean expected = false;
            boolean returned = loader.isPreferredResourceTest(name, true);

            if (expected != returned) {
                message += "\nisPreferredResource("
                         + name + ", true)\n"
                         + "  returned:" + returned + "\n"
                         + "  expected:" + expected;

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "isPreferredResource(" + name + ", true)"
                           + " returned " + returned + " as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 4) invoke loader.isPreferredResourceTest(name, true)
         *    for classes that can be found in the parent class loader
         *    (such as in the class path) but cannot be found in the
         *    preferred class loader and assert that we get false as
         *    expected result.
         */
        for (int item = 0; item < Util.listLocalClasses.length; item++) {
            String name = Util.listLocalClasses[item].name;
            boolean expected = false;
            boolean returned = loader.isPreferredResourceTest(name, true);

            if (expected != returned) {
                message += "\nisPreferredResource("
                         + name + ", true)\n"
                         + "  returned:" + returned + "\n"
                         + "  expected:" + expected;

                // Fast fail approach
                throw new TestException(message);
            } else {
                String msg = "isPreferredResource(" + name + ", true)"
                           + " returned false as expected";
                logger.log(Level.FINE, msg);
            }
        }
    }
}
