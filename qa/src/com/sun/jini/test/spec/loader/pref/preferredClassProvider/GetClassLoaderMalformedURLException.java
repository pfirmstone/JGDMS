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
 *  <ul><lh>throws:</lh>
 *    <li>MalformedURLException - if <code>codebase</code> is
 *        non-<code>null</code> and contains an invalid URL, or
 *        if <code>codebase</code> is <code>null</code> and the system
 *        property <code>java.rmi.server.codebase</code> contains an
 *        invalid URL</li>
 *  </ul>
 *
 * <b>Test Description</b><br><br>
 *
 *  This test uses {@link QATestPreferredClassProvider} that is created
 *  passing various parameters to parent PreferredClassProvider constructor.
 *  <br><br>
 *
 *  {@link QATestPreferredClassProvider} should be configured as
 *  java.rmi.server.RMIClassLoaderSpi, so that RMIClassLoader.getClassLoader
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
 *  This test will try to get PreferredClassLoader invoking
 *  RMIClassLoader.getClassLoader method passing codebase string and verify
 *  that MalformedURLException is thrown.
 *  <br>
 *  Codebase string can be set via config (properties) file and should
 *  contain malformed url to emulate MalformedURLException.
 *  <br>
 *  If codebase string is not set (is equal to null) then test case
 *  resets java.rmi.server.codebase property with pre-defined invalid codebase.
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
 *   obtain malformed codebase or if codebase is null then reset
 *   java.rmi.server.codebase to predefined invalid codebase.
 *  </li>
 *  <li>
 *   call RMIClassLoader.getClassLoader method and verify that
 *   MalformedURLException is thrown.
 *  </li>
 * </ol>
 *
 */
public class GetClassLoaderMalformedURLException extends AbstractTestBase {

    /**
     *
     * run test passing {@link QATestPreferredClassProvider} as
     *     java.rmi.server.RMIClassLoaderSpi
     */
    public void run() throws Exception {
        String message = "";
        String cb = null;
        ClassLoader classLoader = null;
        boolean pass = false;

        /*
         * 2) Obtain malformed codebase or if codebase is null then reset
         *    java.rmi.server.codebase to predefined invalid codebase.
         */
        cb = obtainMalformedCodebase();

        /*
         * 3) call RMIClassLoader.getClassLoader method and verify that
         *    MalformedURLException is thrown.
         */
        try {
            classLoader = RMIClassLoader.getClassLoader(cb);
        } catch (MalformedURLException me) {
            // Expect MalformedURLException
            String msg = "RMIClassLoader.getClassLoader(" + cb + ")\n"
                       + "  throws " + me.toString() + " as expected";
            logger.log(Level.FINEST, msg);
            pass = true;
        } catch (SecurityException se) {
            // Do not expect SecurityException.
            // Tests case with expected SecurityException
            // is GetClassLoaderSecurityException
            message += "RMIClassLoader.getClassLoader("
                     + cb + ")\n"
                     + "  throws: " + se.toString() + "\n"
                     + "  expected: MalformedURLException";
            throw new TestException(message);
        }

        if (!pass) {
            message += "RMIClassLoader.getClassLoader("
                     + cb + ")\n"
                     + "  returned: " + classLoader.toString() + "\n"
                     + "  expected: MalformedURLException";
        }

        if (message.length() > 0) {
            throw new TestException(message);
        }
    }
}
