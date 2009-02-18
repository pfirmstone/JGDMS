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
package com.sun.jini.test.spec.loader.pref.preferredClassProvider;

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

// java.rmi.server
import java.rmi.server.RMIClassLoader;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;
import net.jini.loader.pref.PreferredClassProvider;

// instrumented preferred class loader
import com.sun.jini.test.spec.loader.util.Item;
import com.sun.jini.test.spec.loader.util.Util;
import com.sun.jini.test.spec.loader.util.QATestPreferredClassProvider;

// test base class
import com.sun.jini.test.spec.loader.pref.AbstractTestBase;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>public Class loadClass(String codebase, String name,
 *                           ClassLoader defaultLoader)
 *    throws MalformedURLException, ClassNotFoundException</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassProvider</code>
 * class:
 *
 * <br><blockquote>
 * Loads a class from a codebase URL path, only using the supplied
 * loader if the given name is not preferred by the class loader
 * for the codebase path.
 * <br><br>
 * If the name parameter is marked as preferred in the preferred
 * class loader which is requested to load the named class, that
 * class loader will not delegate to its parent to load the
 * requested class.  Instead the class loader will only search its
 * own resources to find the class.
 * <br><br>
 * </blockquote>
 *  <ul><lh>Parameters:</lh>
 *    <li>codebase - the list of URLs (separated by spaces) from
 *                   which the class should be loaded,
 *                   or <code>null</code></li>
 *    <li>name - the name of the class to load</li>
 *    <li>defaultLoader - additional contextual class loader to use,
 *                         or <code>null</code></li>
 *  </ul>
 *
 * <b>Test Description</b><br><br>
 *
 *  This test uses {@link QATestPreferredClassProvider} that is created
 *  passing various parameters to parent PreferredClassProvider constructor.
 *  <br><br>
 *
 *  {@link QATestPreferredClassProvider} should be configured as
 *  java.rmi.server.RMIClassLoaderSpi, so that RMIClassLoader.loadClass
 *  calls QATestPreferredClassProvider.loadClass method.
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
 *  For each preferred/non-preferred class this test will try to execute
 *  Class.forName passing ClassLoader.getSystemClassLoader() and will try to
 *  execute RMIClassLoader.loadClass(codebase, name, parent) passing:
 *  <ul>
 *   <li>codebase - string representation of url to qa1-loader-pref.jar
 *                  file</li>
 *   <li>name - name of preferred/non-preferred class</li>
 *   <li>parent - ClassLoader.getSystemClassLoader()</li>
 *  </ul>
 *
 *  Then this test will verify class identity using "equals" method.
 *  Loaded classes should be equal for non-preferred classes and should be
 *  not equal for preferred classes.
 *  <br><br>
 *
 *  Additionally, this test will try to call Class.forName and
 *  RMIClassLoader.loadClass for classes that can be found in the parent
 *  class loader (such as in the class path) but cannot be found in the
 *  preferred class loader.
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
 *   for each preferred/non-preferred class do the following:
 *   <ol>
 *    <li>invoke Class.forName method passing system class loader</li>
 *    <li>invoke RMIClassLoader.loadClass method passing:</li>
 *     <ul>
 *      <li>codebase - string representation of url to qa1-loader-pref.jar</li>
 *      <li>name - name of preferred/non-preferred class</li>
 *      <li>parent - ClassLoader.getSystemClassLoader()</li>
 *     </ul>
 *    <li>verify that returned classes are equal for non-preferred classes
 *        and are not equal for preferred classes</li>
 *   </ol>
 *  </li>
 *  <li>
 *   for classes that can be found in the parent class loader (such as in
 *       the class path) but cannot be found in the preferred class loader
 *       do the following:
 *   <ol>
 *    <li>invoke Class.forName method passing system class loader</li>
 *    <li>invoke RMIClassLoader.loadClass method passing:</li>
 *     <ul>
 *      <li>codebase - string representation of url to qa1-loader-pref.jar</li>
 *      <li>name - name of class</li>
 *      <li>parent - ClassLoader.getSystemClassLoader()</li>
 *     </ul>
 *    <li>verify that returned classes are equal for all classes</li>
 *   </ol>
 *  </li>
 * <ol>
 *
 */
public class LoadClasses extends AbstractTestBase {

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

        /*
         * Get codebase to qa1-loader-pref.jar.
         */
        cb = Util.getUrlAddr(isHttp, config, port) + Util.PREFERREDJarFile;

        /*
         * 2) for each preferred/non-preferred class do the following:
         *    - invoke Class.forName method passing system class loader,
         *    - invoke RMIClassLoader.loadClass method passing:
         *        codebase - string representation of url to
         *                   qa1-loader-pref.jar
         *        name - name of preferred/non-preferred class
         *        parent - ClassLoader.getSystemClassLoader()
         *    - verify that returned classes are equals for non-preferred
         *      classes and are not equals for preferred classes.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            boolean pref = Util.listClasses[item].pref;
            Class classDefault = null;
            Class classPreferred = null;

            try {
                classDefault = Class.forName(name, false, parent);
                classPreferred = RMIClassLoader.loadClass(cb, name, parent);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException.
                // Tests case with expected ClassNotFoundException
                // is LoadClassesClassNotFoundException
                message += "RMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  throws: " + e.toString() + "\n"
                         + "  expected: " + (pref ? str1 : str2);
                throw new TestException(message);
            } catch (MalformedURLException me) {
                // Do not expect MalformedURLException.
                // Tests case with expected MalformedURLException
                // is LoadClassesMalformedURLException
                message += "RMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  throws: " + me.toString() + "\n"
                         + "  expected: " + (pref ? str1 : str2);
                throw new TestException(message);
            } catch (SecurityException sex) {
                // Do not expect SecurityException.
                // Tests case with expected SecurityException
                // is LoadClassesSecurityException
                message += "RMIClassLoader.loadClass(" + cb + ", "
                         + name + ", false, loader)\n"
                         + "  throws: " + sex.toString() + "\n"
                         + "  expected: " + (pref ? str1 : str2);
                throw new TestException(message);
            }
            boolean expected = !pref;
            boolean returned = classDefault.equals(classPreferred);

            if (expected != returned) {
                message += "\nRMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  returned: " + (expected ? str1 : str2) + "\n"
                         + "  expected: " + (expected ? str2 : str1);
                throw new TestException(message);
            } else {
                String msg = ""
                           + "RMIClassLoader.loadClass(" + cb + ", "
                           + name + ", defaultLoader)\n"
                           + "  returned " + (expected ? str2 : str1)
                           + "  as expected";
                logger.log(Level.FINE, msg);
            }
        }

        /*
         *    3) for classes that can be found in the parent class loader
         *       (such as in the class path) but cannot be found in the
         *       preferred class loader do the following:
         *       - invoke Class.forName method passing system class loader,
         *       - invoke RMIClassLoader.loadClass method passing:
         *            codebase - string representation of url to
         *                       qa1-loader-pref.jar
         *            name - name of class
         *            parent - ClassLoader.getSystemClassLoader()
         *       - verify that returned classes are equals for all classes
         */
        for (int item = 0; item < Util.listLocalClasses.length; item++) {
            String name = Util.listLocalClasses[item].name;
            Class classDefault = null;
            Class classPreferred = null;

            try {
                classDefault = Class.forName(name, false, parent);
                classPreferred = RMIClassLoader.loadClass(cb, name, parent);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException.
                // Tests case with expected ClassNotFoundException
                // is LoadClassesClassNotFoundException
                message += "RMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  throws: " + e.toString() + "\n"
                         + "  expected: returned class";
                throw new TestException(message);
            } catch (MalformedURLException me) {
                // Do not expect MalformedURLException.
                // Tests case with expected MalformedURLException
                // is LoadClassesMalformedURLException
                message += "RMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  throws: " + me.toString() + "\n"
                         + "  expected: returned class";
                throw new TestException(message);
            } catch (SecurityException sex) {
                // Do not expect SecurityException.
                // Tests case with expected SecurityException
                // is LoadClassesSecurityException
                message += "\nClass.forName("
                         + name + ", false, loader)\n"
                         + "  throws: " + sex.toString() + "\n"
                         + "  expected: returned class";
                throw new TestException(message);
            }
            boolean returned = classDefault.equals(classPreferred);

            if (!returned) {
                message += "\nRMIClassLoader.loadClass(" + cb + ", "
                         + name + ", defaultLoader)\n"
                         + "  returned:" + str1 + "\n"
                         + "  expected:" + str2;
                throw new TestException(message);
            } else {
                String msg = ""
                           + "RMIClassLoader.loadClass(" + cb + ", "
                           + name + ", defaultLoader)\n"
                           + "  returned " + str2
                           + "  as expected";
                logger.log(Level.FINE, msg);
            }
        }

        if (message.length() > 0) {
            throw new TestException(message);
        }
    }
}
