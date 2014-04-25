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
package com.sun.jini.test.spec.loader.pref.requireDlPermProvider;

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
import java.net.MalformedURLException;

// java.rmi.server
import java.rmi.server.RMIClassLoader;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// com.sun.jini.test.spec.loader.util
import com.sun.jini.test.spec.loader.util.Item;
import com.sun.jini.test.spec.loader.util.Util;

// test base class
import com.sun.jini.test.spec.loader.pref.AbstractTestBase;
import net.jini.loader.ClassLoading;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>net.jini.loader.pref.RequireDlPermProvider</code> class:
 *
 * <br><blockquote>
 *  Only permit class downloading from codebases that have been
 *  granted download permission.
 * </blockquote>
 *
 * <b>Test Description</b><br><br>
 *
 *  This test uses RequireDlPermProvider with policy.loaderNoDlPerm policy file.
 *  <br>
 *  policy.loaderNoDlPerm policy file does not grant needed download permissions
 *  <br><br>
 *
 *  RequireDlPermProvider should be configured as
 *  java.rmi.server.RMIClassLoaderSpi, so that RMIClassLoader.loadClass
 *  calls RequireDlPermProvider.loadClass method.
 *  <br><br>
 *
 *  This test should be run with appropriate parameters.
 *  All parameters should be set via config (properties) file.
 *  <ul><lh>Possible parameters are:</lh>
 *  <li>isHttp: if <code>true</code> then will download classes via http,
 *              otherwise will download classes via file based url</li>
 *  <li>httpPort: port do download classes via http</li>
 *  </ul>
 *
 *
 *  This test iterates over a set of preferred/non-preferred
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
 *
 *  For each preferred/non-preferred class this test will try to execute
 *  RMIClassLoader.loadClass(codebase, name, parent) passing:
 *  <ul>
 *  <li>codebase - string representation of url to qa1-loader-pref.jar file</li>
 *  <li>name - name of preferred/non-preferred class</li>
 *  <li>parent - ClassLoader.getSystemClassLoader()</li>
 *  </ul>
 *
 *  Then this test will verify that SecurityException is thrown for
 *  preferred classes only.
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ol><lh>This test requires the following infrastructure:</lh>
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
 *    <li> run test passing appropriate parameters and RequireDlPermProvider as
 *         java.rmi.server.RMIClassLoaderSpi.
 *    </li>
 *    <li> for each preferred/non-preferred class do the following:
 *     <ol>
 *       <li> invoke ClassLoading.loadClass method passing:
 *         <ul>
 *          <li>codebase - string representation of url to
 *                         qa1-loader-pref.jar</li>
 *          <li>name - name of preferred/non-preferred class</li>
 *          <li>parent - ClassLoader.getSystemClassLoader().</li>
 *         </ul></li>
 *       <li> if SecurityException is thrown verify that SecurityException is
 *            thrown for preferred class and go to begin of loop.</li>
 *       <li> invoke Class.forName method passing system class loader.</li>
 *       <li> verify that returned classes are equals because Class.forName
 *            method should return without throwing a SecurityException
 *            for non-preferred classes only.</li>
 *     </ol>
 *    </li>
 * </ol>
 *
 */
public class LoadClassesSecurityException extends AbstractTestBase {

    /* String to format message string */
    static final String str1 = "preferred class";

    /* String to format message string */
    static final String str2 = "non-preferred class";

    /* String to format message string */
    static final String str3 = "SecurityException";

    /**
     *
     * run test passing RequireDlPermProvider as
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
         *  - invoke ClassLoading.loadClass method passing:
         *    codebase - string representation of url to qa1-loader-pref.jar
         *    name - name of preferred/non-preferred class
         *    parent - ClassLoader.getSystemClassLoader()
         *  - if SecurityException is thrown verify that SecurityException
         *    is thrown for preferred class and go to begin of loop
         *  - invoke Class.forName method passing system class loader.
         *  - verify that returned classes are equals because Class.forName
         *    method should return without throwing a SecurityException
         *    for non-preferred classes only.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            boolean pref = Util.listClasses[item].pref;
            Class classPreferred = null;

            try {
                classPreferred = ClassLoading.loadClass(cb, name, parent, false, null);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException.
                message += "ClassLoading.loadClass(" + cb + ", "
                         + name + ", defaultLoader, false, null)\n"
                         + "  throws: " + e.toString() + "\n"
                         + "  expected: " + (pref ? str3 : str2);
                break;
            } catch (MalformedURLException me) {
                // Do not expect MalformedURLException.
                message += "ClassLoading.loadClass(" + cb + ", "
                         + name + ", defaultLoader, false, null)\n"
                         + "  throws: " + me.toString() + "\n"
                         + "  expected: " + (pref ? str3 : str2);
                break;
            } catch (SecurityException sex) {
                // Expect SecurityException for preferred classes only
                if (pref) {
                    String msg = ""
                               + "ClassLoading.loadClass(" + cb + ", "
                               + name + ", defaultLoader, false, null)"
                               + "  throws " + sex.toString()
                               + "  as expected";
                    logger.log(Level.FINEST, msg);
                } else {
                    message += "ClassLoading.loadClass(" + cb + ", "
                             + name + ", defaultLoader, false, null)\n"
                             + "  returned: " + sex.toString() + "\n"
                             + "  expected: " + str2;
                    break;
                }
                continue;
            }

            if (pref) {
                message += "ClassLoading.loadClass(" + cb + ", "
                         + name + ", defaultLoader, false, null)\n"
                         + "  returned: " + str1 + "\n"
                         + "  expected: " + str3;
                break;
            } else {
                String msg = ""
                           + "ClassLoading.loadClass(" + cb + ", "
                           + name + ", defaultLoader, false, null)\n"
                           + "  returned " + str2
                           + "  as expected";
                logger.log(Level.FINEST, msg);
            }
            Class classDefault = null;

            try {
                classDefault = Class.forName(name, false, parent);
            } catch (ClassNotFoundException e) {
                // Do not expect ClassNotFoundException for
                // non-preferred classes.
                message += "\nClass.forName("
                         + name + ", false, defaultLoader)\n"
                         + "  throws:" + e.toString() + "\n"
                         + "  expected: returned class";
                break;
            } catch (SecurityException sex) {
                // Do not expect SecurityException for
                // non-preferred classes.
                message += "\nClass.forName("
                         + name + ", false, defaultLoader)\n"
                         + "  throws:" + sex.toString() + "\n"
                         + "  expected: returned class";
                break;
            }

            /*
             * Verify that returned classes are equals because
             * Class.forName method should return without throwing a
             * SecurityException for non-preferred classes only.
             */
            if (!classDefault.equals(classPreferred)) {
                message += "ClassLoading.loadClass(" + cb + ", "
                         + name + ", defaultLoader, false, null)\n"
                         + "  returned: " + str1 + "\n"
                         + "  expected: " + str2;
                break;
            }
        }

        if (message.length() > 0) {
            throw new TestException(message);
        }
    }
}
