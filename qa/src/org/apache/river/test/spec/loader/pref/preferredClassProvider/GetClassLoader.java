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
package org.apache.river.test.spec.loader.pref.preferredClassProvider;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;

// org.apache.river.qa
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;

// java.io
import java.io.IOException;

// java.net
import java.net.URL;
import java.net.MalformedURLException;

// java.rmi.server
import java.rmi.server.RMIClassLoader;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;
import net.jini.loader.pref.PreferredClassProvider;

// instrumented preferred class loader
import org.apache.river.test.spec.loader.util.Item;
import org.apache.river.test.spec.loader.util.Util;
import org.apache.river.test.spec.loader.util.QATestPreferredClassProvider;

// test base class
import org.apache.river.test.spec.loader.pref.AbstractTestBase;
import net.jini.loader.ClassLoading;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>public ClassLoader getClassLoader(String codebase)
 *        throws MalformedURLException</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassProvider</code>
 * class:
 *
 * <br><blockquote>
 * Returns a class loader that loads classes from the given codebase URL path.
 * <br><br>
 * </blockquote>
 *  <ul><lh>Parameters:</lh>
 *    <li>codebase - the list of URLs (space-separated) from which the returned
 *                   class loader will load classes from, or
 *                   <code>null</code></li>
 *  </ul>
 *
 * <b>Test Description</b><br><br>
 *
 *  This test uses {@link QATestPreferredClassProvider} that is created
 *  passing various parameters to parent PreferredClassProvider constructor.
 *  <br><br>
 *
 *  {@link QATestPreferredClassProvider} should be configured as
 *  java.rmi.server.RMIClassLoaderSpi, so that ClassLoading.getClassLoader
 *  calls QATestPreferredClassProvider.getClassLoader method.
 *  <br><br>
 *
 *  This test should be run with appropriate parameters.
 *  <br>
 *  All parameters should be set via config (properties) file.
 *  <ul><lh>Possible parameters are:</lh>
 *  <li>boolean isHttp: if <code>true</code> then will download classes via
 *                      http, otherwise will download classes via file based
 *                      url</li>
 *  <li>int httpPort: port do download classes via http</li>
 *  <li>boolean requireDlPerm: <code>false</code>, <code>true</code></li>
 *  </ul>
 *
 *  If requireDlPerm is equal to true then this test should be run
 *  with policy.loader policy file, otherwise with policy.loaderNoDlPerm file.
 *  <br>
 *  policy.loader policy file grants needed download permissions.
 *  <br>
 *  policy.loaderNoDlPerm file does not grant needed download permissions.
 *  <br><br>
 *
 *  This test iterates over a set of preferred/non-preferred classes.
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
 *
 *  This test will try to get PreferredClassLoader invoking
 *  RMIClassLoader.getClassLoader method passing codebase to qa1-loader-pref.jar
 *  file, then each test will verify that returned PreferredClassLoader works
 *  as expected:
 *  <ol>
 *   <li>
 *    for each preferred/non-preferred class this test will try to execute
 *    Class.forName passing ClassLoader.getSystemClassLoader() (call it SCL)
 *    and will try to execute Class.forName passing returned
 *    PreferredClassLoader (call it PCL):
 *    <ul>
 *     <li>Class.forName(N, false, SCL)</li>
 *     <li>Class.forName(N, false, PCL)</li>
 *    </ul>
 *   </li>
 *   <li>
 *    then this test will verify class identity using "equals" method.
 *    Loaded classes should be equal for non-preferred classes and should be
 *    not equal for preferred classes.
 *   </li>
 *   <li>
 *    additionally, this test will try to call Class.forName(N, false, SCL)
 *    and Class.forName(N, false, PCL) for classes that can be found in the
 *    parent class loader (such as in the class path) but cannot be found in
 *    the preferred class loader.
 *   </li>
 *  </ol>
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ol><lh>This test requires the following infrastructure:</lh>
 *  <li> {@link QATestPreferredClassProvider} is an instrumented
 *       PreferredClassProvider using for davis.loader's and davis.loader.pref's
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
 *  <li>
 *   run test passing {@link QATestPreferredClassProvider} as
 *   java.rmi.server.RMIClassLoaderSpi
 *  </li>
 *  <li>
 *   call ClassLoading.getClassLoader method passing codebase to
 *   qa1-loader-pref.jar file. The test should get PreferredClassLoader.
 *  </li>
 *  <li>
 *   for each preferred/non-preferred class do the following:
 *   <ol>
 *    <li>invoke Class.forName method passing system class loader</li>
 *    <li>invoke Class.forName method passing returned preferred class
 *        loader</li>
 *    <li>verify that returned classes are equal for non-preferred classes
 *         and are not equal for preferred classes</li>
 *   </ol>
 *  </li>
 *  <li>
 *   for classes that can be found in the parent class loader (such as in
 *       the class path) but cannot be found in the preferred class loader
 *       do the following:
 *   <ol>
 *    <li>invoke Class.forName method passing system class loader</li>
 *    <li>invoke Class.forName method passing returned preferred class
 *        loader</li>
 *    <li>verify that returned classes are equal for all classes</li>
 *   </ol>
 *  </li>
 * <ol>
 *
 */
public class GetClassLoader extends AbstractTestBase {

    /** String to format message string */
    static final String str1 = "preferred class";

    /** String to format message string */
    static final String str2 = "non-preferred class";

    /**
     *
     * run test passing {@link QATestPreferredClassProvider} as
     *     java.rmi.server.RMIClassLoaderSpi
     */
    public void run() throws Exception {
        String message = "";
        ClassLoader parent = Util.systemClassLoader();
        String cb = null;
        ClassLoader classLoader = null;

        /*
         * Get codebase to qa1-loader-pref.jar.
         */
        cb = Util.getUrlAddr(isHttp, config, port) + Util.PREFERREDJarFile;

        /*
         * 2) call ClassLoading.getClassLoader method passing codebase to
         *    qa1-loader-pref.jar file.
         */
        try {
            classLoader = ClassLoading.getClassLoader(cb);
        } catch (MalformedURLException me) {
            // Do not expect MalformedURLException.
            // Tests case with expected MalformedURLException
            // is GetClassLoaderMalformedURLException
            message += "ClassLoading.getClassLoader("
                     + cb + ")\n"
                     + "  throws: " + me.toString() + "\n"
                     + "  expected: PreferredClassLoader";
            throw new TestException(message);
        } catch (SecurityException sex) {
            // Do not expect SecurityException.
            // Tests case with expected SecurityException
            // is GetClassLoaderSecurityException
            message += "ClassLoading.getClassLoader("
                     + cb + ")\n"
                     + "  throws: " + sex.toString() + "\n"
                     + "  expected: PreferredClassLoader";
            throw new TestException(message);
        }

        /*
         * 3) for each preferred/non-preferred class do the following:
         *  - invoke Class.forName method passing system class loader,
         *  - invoke Class.forName method passing returned preferred class
         *    loader
         *  - verify that returned classes are equal for non-preferred
         *    classes and are not equal for preferred classes.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            boolean pref = Util.listClasses[item].pref;
            Class classDefault = null;
            Class classPreferred = null;

            try {
                classDefault = Class.forName(name, false, parent);
                classPreferred = Class.forName(name, false, classLoader);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException.
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  throws: " + e.toString() + "\n"
                         + "  expected: " + (pref ? str1 : str2);
                throw new TestException(message);
            } catch (SecurityException se) {
                // Do not expect SecurityException.
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  throws: " + se.toString() + "\n"
                         + "  expected: " + (pref ? str1 : str2);
                throw new TestException(message);
            }
            boolean expected = !pref;
            boolean returned = classDefault.equals(classPreferred);

            if (expected != returned) {
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  returned: " + (expected ? str1 : str2) + "\n"
                         + "  expected: " + (expected ? str2 : str1);
                throw new TestException(message);
            } else {
                String msg = "Class.forName(" + name
                           + ", false, PreferredClassLoader)\n"
                           + "  returned " + (expected ? str2 : str1)
                           + "  as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         * 4) for classes that can be found in the parent class loader (such
         *    as in the class path) but cannot be found in the preferred
         *    class loader do the following:
         *  - invoke Class.forName method passing system class loader,
         *  - invoke Class.forName method passing returned preferred class
         *    loader
         *  - verify that returned classes are equal for all classes
         */
        for (int item = 0; item < Util.listLocalClasses.length; item++) {
            String name = Util.listLocalClasses[item].name;
            Class classDefault = null;
            Class classPreferred = null;

            try {
                classDefault = Class.forName(name, false, parent);
                classPreferred = Class.forName(name, false, classLoader);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException.
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  throws: " + e.toString() + "\n"
                         + "  expected: returned class";
                throw new TestException(message);
            } catch (SecurityException se) {
                // Do not expect SecurityException.
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  throws: " + se.toString() + "\n"
                         + "  expected: returned class";
                throw new TestException(message);
            }
            boolean returned = classDefault.equals(classPreferred);

            if (!returned) {
                message += "Class.forName("
                         + name + ", false, PreferredClassLoader)\n"
                         + "  returned:" + str1 + "\n"
                         + "  expected:" + str2;
                throw new TestException(message);
            } else {
                String msg = "Class.forName(" + name
                           + ", false, PreferredClassLoader)\n"
                           + "  returned " + str2 + " as expected";
                logger.log(Level.FINE, msg);
            }
        }

        if (message.length() > 0) {
            throw new TestException(message);
        }
    }
}
