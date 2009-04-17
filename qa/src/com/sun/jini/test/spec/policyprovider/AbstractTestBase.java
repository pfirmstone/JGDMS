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
package com.sun.jini.test.spec.policyprovider;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

// java.net
import java.net.URL;
import java.net.InetAddress;

// java.io
import java.io.File;

// java.util
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Enumeration;

// java.security
import java.security.Principal;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.PermissionCollection;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Item;
import com.sun.jini.test.spec.policyprovider.util.Util;


/**
 * This class is base class for all com.sun.jini.test.spec.policyprovider tests.
 * This class sets up the testing environment and
 * has some helper methods.
 */
public abstract class AbstractTestBase extends QATest {

    /** java.security.policy property string */
    protected static final String SECURITYPOLICY = "java.security.policy";

    /** String to format test status string */
    protected String msg = null;

    /** String to format test status string */
    protected static final String SNULL = "null";

    /** String to format test status string */
    protected static final String NOException = "no Exception";

    /** String to format test status string */
    protected static final String SE = "SecurityException";

    /** String to format test status string */
    protected static final String NPE = "NullPointerException";

    /** String to format test status string */
    protected static final String PIE = "PolicyInitializationException";

    /** String to format test status string */
    protected static final String IAE = "IllegalArgumentException";

    /** The QAConfig object */
    protected QAConfig config;

    /** Class loaders to load classes */
    protected ClassLoader[] classLoaders = null;

    /** Loaded classes */
    protected Class[] classes = null;

    /** Loaded classes per class loaders */
    protected Class[][] classAA = null;

    /** ProtectionDomains of loaded classes */
    protected ProtectionDomain[] protectionDomains = null;

    /**
     * Http port to download preferred classes and resources via
     * com.sun.jini.qa.port
     */
    protected int port;

    /**
     * Sets up the testing environment.
     *
     * @param args Arguments from the runner for setup.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        // mandatory call to parent
        super.setup(sysConfig);

        // set up parent's config.
        this.config = (QAConfig) getConfig();

        /*
         * set up testing environment
         *
         * port - http port for the http based url
         *
         */
        port = config.getIntConfigVal("com.sun.jini.test.port", 8082);
    }

    /**
     * Reset java.security.policy to new policy file.
     *
     * @param newPolicyFile name of new policy file.
     *
     * @throws TestException if failed
     *
     */
    protected void setPolicyFile(String newPolicyFile) throws TestException {
	String newPolicy = config.getStringConfigVal(newPolicyFile, null);
	if (newPolicy == null) {
	    throw new TestException("no policy file found named " 
		                   + newPolicyFile);
	}
//        String newPolicy = getPolicyDir() + newPolicyFile;
        System.setProperty(SECURITYPOLICY, newPolicy);

        if (!System.getProperty(SECURITYPOLICY, "").equals(newPolicy)) {
            throw new TestException("Cannot set property:" + SECURITYPOLICY);
        }
        logger.log(Level.FINE, "Reset " + SECURITYPOLICY + " to " + newPolicy);
    }

    /**
     * Create class loaders then load classes from qa1.jar file using
     * system class loader and load classes from qa1-policy-provider.jar
     * file using PreferredClassLoader and http protocol.
     * Store class loaders, loaded classes and ProtectionDomains for
     * loaded classes into arrays.
     *
     * @throws TestException if failed
     *
     */
    protected void loadClasses() throws TestException {
        // Util.listClasses array contains name of classes to be loaded.
        int length = Util.listClasses.length;

        // Create loaders to load classes and store them to array.
        classLoaders = createLoaders();

        // Load classes using created class loaders and store them to array.
        // First class should be null.
        classAA = new Class[classLoaders.length][length];
        classes = new Class[length * classLoaders.length + 1];
        classes[0] = null;

        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;

            try {
                for (int i = 0; i < classLoaders.length; i++) {
                    Class cl = Class.forName(name, false, classLoaders[i]);
                    classes[item + length * i + 1] = cl;
                    classAA[i][item] = cl;
                }
            } catch (Exception e) {
                msg = "Unable to load class " + name;
                throw new TestException(Util.fail(msg, e, NOException));
            }
        }

        // Store ProtectionDomains for loaded classes into array.
        // Avoid to duplicates.
        ArrayList l = new ArrayList();
        ProtectionDomain pdNext = null;
        ProtectionDomain pdPrev = null;

        for (int i = 0; i < classes.length; i++) {
            if (classes[i] != null) {
                pdNext = classes[i].getProtectionDomain();

                if (pdNext.equals(pdPrev)) {
                    continue;
                }
                pdPrev = pdNext;
            } else {
                pdPrev = pdNext = null;
            }
            l.add(pdNext);
        }
        protectionDomains = (ProtectionDomain[]) l.toArray(new
                ProtectionDomain[l.size()]);
    }

    /*
     * Create class loaders to load classes.
     *
     * throws TestException if failed
     *
     */
    private ClassLoader[] createLoaders() throws TestException {
        // Get url for qa1-policy-provider.jar file.
        URL[] urls = null;

        try {
            urls = new URL[] {
                new URL("http://" + InetAddress.getLocalHost().getHostName() +
                        ":" + port + "/" + Util.POLICYJar) };
        } catch (Exception e) {
            msg = "Unable to create url for " + Util.POLICYJar;
            throw new TestException(Util.fail(msg, e, NOException));
        }
        logger.log(Level.FINE, "got url:" + urls[0].toExternalForm());

        // Get system class loader to load classes.
        ClassLoader ldr01 = ClassLoader.getSystemClassLoader();

        // Create preferred class loader to load classes.
        PreferredClassLoader ldr02 = new PreferredClassLoader(urls, ldr01,
                (String) null, false);

        // Create preferred class loader to load classes.
        PreferredClassLoader ldr03 = new PreferredClassLoader(urls, ldr02,
                (String) null, false);

        // return array of class loaders to load classes.
        return new ClassLoader[] { ldr01, ldr02, ldr03 };
    }

    /**
     * Convert ProtectionDomain to string to format log/fail message.
     *
     * @param pd ProtectionDomain to be converted.
     *
     * @return string represenation of ProtectionDomain.
     */
    protected String str(ProtectionDomain pd) {
        if (pd == null) {
            return SNULL;
        }
        CodeSource csO = pd.getCodeSource();
        ClassLoader clO = pd.getClassLoader();
        Principal[] prO = pd.getPrincipals();
        String d = ", ";
        String cs = (csO != null) ? csO.toString() : SNULL;
        String pm = str(pd.getPermissions());
        String cl = (clO != null) ? clO.toString() : SNULL;
        String pr = (prO != null) ? Arrays.asList(prO).toString() : SNULL;
        return "ProtectionDomain(" + cs + d + pm + d + cl + d + pr + ")";
    }

    /**
     * Convert PermissionCollection to string to format log/fail message.
     *
     * @param pc PermissionCollection to be converted.
     *
     * @return string represenation of PermissionCollection.
     */
    protected String str(PermissionCollection pc) {
        if (pc == null) {
            return SNULL;
        }
        ArrayList l = new ArrayList();
        Enumeration e = pc.elements();

        while (e.hasMoreElements()) {
            l.add(e.nextElement());
        }
        return l.toString();
    }
}
