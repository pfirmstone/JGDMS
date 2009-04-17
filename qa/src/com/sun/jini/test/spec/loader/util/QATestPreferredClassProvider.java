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
package com.sun.jini.test.spec.loader.util;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.net
import java.net.MalformedURLException;
import java.net.URL;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// java.rmi.server
import java.rmi.server.RMIClassLoaderSpi;
import java.rmi.server.RMIClassLoader;

// davis packages
import net.jini.loader.pref.PreferredClassProvider;


/**
 * This class is instrumented PreferredClassProvider.
 * <br>
 * This class extends PreferredClassProvider, overrides
 * loadClass() methods. This class has some helper
 * methods which are useful for testing davis.loader and davis.loader.pref
 * packages.
 */
public class QATestPreferredClassProvider extends PreferredClassProvider {

    /** the logger */
    private static Logger logger =
            Logger.getLogger("com.sun.jini.qa.harness.test");

    /**
     * Obtains requireDlPerm parameters from system
     * properties then calls superclass constructor passing
     * requireDlPerm.
     */
    public QATestPreferredClassProvider() {
        super(getRequireDlPerm());
    }

    /**
     * Log the codebase and name of class to be loaded
     * then call super.loadClass.
     */
    public Class loadClass(String codebase, String name,
            ClassLoader defaultLoader)
            throws MalformedURLException, ClassNotFoundException {
        // Log the codebase and name of class to be loaded
        logger.log(Level.FINE, "codebase: " + codebase + ", name :" + name);
        return super.loadClass(codebase, name, defaultLoader);
    }

    /**
     * Obtains requireDlPerm parameter from system
     * properties to be passed to superclass constructor.
     */
    public static boolean getRequireDlPerm() {
        boolean requireDlPerm =
                QAConfig.getConfig().getBooleanConfigVal(
                "loader.requireDlPerm", false);
        logger.log(Level.FINE, "requireDlPerm: " + requireDlPerm);
        return requireDlPerm;
    }
}
