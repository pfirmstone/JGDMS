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

// java.io
import java.io.FilePermission;

// java.net
import java.net.SocketPermission;
import java.net.InetAddress;

// java.util
import java.util.Enumeration;

// java.security
import java.security.ProtectionDomain;
import java.security.PermissionCollection;
import java.security.Permission;
import java.security.CodeSource;

// instrumented preferred class loader
import com.sun.jini.test.spec.loader.util.Util;
import com.sun.jini.test.spec.loader.util.QATestPreferredClassLoader;

// test base class
import com.sun.jini.test.spec.loader.pref.AbstractTestBase;


/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies the behavior of the<br>
 * <code>protected PermissionCollection getPermissions(CodeSource
 *                                                           codeSource)</code>
 * method of the<br>
 * <code>net.jini.loader.pref.PreferredClassLoader</code> class:
 *
 * <br><blockquote>
 *  Return the permissions to be granted to code loaded from given code source.
 * </blockquote>
 *  <ul><lh>Parameters:</lh>
 *    <li>codeSource - given code source</li>
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
 *  For each preferred/non-preferred class this test will try to execute
 *  Class.forName passing {@link QATestPreferredClassLoader}.
 *  <br><br>
 *  If DownloadPermission is not granted in the policy file and requireDlPerm
 *  flag is set to <code>true</code>, then SecurityExeption is expected for
 *  preferred classes.
 *  <br><br>
 *  This test calls {@link QATestPreferredClassLoader#getPermissionsTest}
 *  passing the CodeSource coressponding with the returned class.
 *  <br><br>
 *  Again, if DownloadPermission is not granted in the policy file and
 *  requireDlPerm flag is set to <code>true</code>, then SecurityExeption is
 *  expected (for non-preferred classes in this case).
 *  <br><br>
 *  Then this test will verify returned PermissionCollection comparing
 *  returned permissions with the previously created FilePermission or
 *  SocketPermission object using the next rules:
 * <ol><lh>Pass/fail criteria:</lh>
 *  <li> for each preferred class we should get FilePermission
 *       or SocketPermission pointed to qa1-loader-pref.jar file;</li>
 *  <li> for each non-preferred classes we should get FilePermission
 *       pointed to qa1.jar file.</li>
 * </ol>
 *
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
 * <ol>
 *    <li> construct SocketPermission objects pointed to the qa1-loader-pref.jar
 *         file passing 'connect,accept,resolve' action.
 *    </li>
 *    <li> construct FilePermission object pointed to the qa1.jar file and
 *         construct FilePermission object pointed to the qa1-loader-pref.jar
 *         file passing the 'read' action.
 *    </li>
 *    <li> construct a {@link QATestPreferredClassLoader} with a single URL to
 *         the qa1-loader-pref.jar file and appropriate parameters.
 *    </li>
 *    <li> for each preferred/non-preferred class do the following:
 *     <ol>
 *       <li> invoke Class.forName method passing QATestPreferredClassLoader.
 *            If SecurityException is expected, then verify that
 *            SecurityException is thrown for preferred classes
 *            and go to begin of loop
 *       <li> get CodeSource for this class</li>
 *       <li> invoke loader.getPermissions() passing this CodeSource.
 *            If SecurityException is expected, then verify that
 *            SecurityException is thrown for non-preferred classes (for
 *            preferred classes SecurityException was thrown in 4.1) then go
 *            to begin of loop</li>
 *       <li> verify that we get expected PermissionCollection:
 *          <ol>
 *            <li>for each preferred class we should get FilePermission
 *            or SocketPermission pointed to qa1-loader-pref.jar file;</li>
 *            <li>for each non-preferred classes we should get FilePermission
 *            pointed to qa1.jar file</li>
 *          </ol></li>
 *     </ol>
 *    </li>
 * </ol>
 *
 */
public class GetPermissions extends AbstractTestBase {

    /** String that indicates fail status */
    String message = "";

    /** FilePermission object pointed to the qa1.jar file */
    FilePermission qa1filePermission = null;

    /** FilePermission object pointed to the qa1-loader-pref.jar file */
    FilePermission jarfilePermission = null;

    /** SocketPermission object pointed to the qa1-loader-pref.jar file */
    SocketPermission socketPermission = null;

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        /*
         * 1) construct SocketPermission objects pointed to the
         *    qa1-loader-pref.jar file passing 'connect,accept,resolve'
         *    action if isHttp flag is set.
         */
        String host = null;
        InetAddress inetAddress = InetAddress.getLocalHost();
        host = inetAddress.getHostName();
        socketPermission = new SocketPermission(host, "connect,accept,resolve");

        /*
         * 2) construct FilePermission object pointed to the qa1.jar file
         *    and construct FilePermission object pointed to
         *    the qa1-loader-pref.jar file  passing the 'read' action.
         */
        String path = Util.getJarsDir(config);
        String qa1path = path + "/" + Util.QAJarFile;
        String jarpath = path + "/" + Util.PREFERREDJarFile;
        qa1filePermission = new FilePermission(qa1path, "read");
        jarfilePermission = new FilePermission(jarpath, "read");
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
         * 3) construct a QATestPreferredClassLoader with a single URL
         *    to the "qa1-loader-pref.jar file.
         */
        createLoader(Util.PREFERREDJarFile);

        /*
         * 4) for each preferred/non-preferred class do the following:
         *    a) invoke Class.forName method passing
         *       QATestPreferredClassLoader.
         *       If SecurityException is expected, then verify that
         *       SecurityException is thrown for preferred classes
         *       then go to begin of loop.
         *    b) get CodeSource for this class.
         *    c) invoke loader.getPermissions() passing this CodeSource.
         *       If SecurityException is expected, then verify that
         *       SecurityException is thrown for non-preferred classes
         *       (for preferred classes SecurityException was thrown in 4.a)
         *       then go to begin of loop.
         *    d) verify that we get expected PermissionCollection:
         *       for each preferred class we should get FilePermission
         *       or SocketPermission pointed to qa1-loader-pref.jar file;
         *       for each non-preferred classes we should get FilePermission
         *       pointed to qa1.jar file.
         */
        for (int item = 0; item < Util.listClasses.length; item++) {
            String name = Util.listClasses[item].name;
            boolean pref = Util.listClasses[item].pref;
            Class classLoaded = null;

            try {
                classLoaded = Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                message += "\nClass not found: " + name;
                continue;
            } catch (SecurityException se) {
                if (!expectSecurityException) {
                    // non-expected SecurityException.
                    // Test failed.
                    message += "\nClass.forName("
                             + name + ", false, loader)\n"
                             + "  throws:" + se.toString() + "\n"
                             + "  expected:returned class";
                } else if (!pref) {
                    // Got SecurityException for non-preferred class.
                    // Test failed.
                    message += "\nGot SecurityException for non-preferred"
                             + " class. Class.forName("
                             + name + ", false, loader)\n"
                             + "  throws:" + se.toString() + "\n"
                             + "  expected:returned class";
                } else {
                    // Got SecurityException for preferred class
                    // as expected.
                    String msg = "Class.forName(" + name + ", false, loader)"
                               + "  throws:" + se.toString() + "  as expected";
                    logger.log(Level.FINE, msg);
                }

                if (message.length() > 0) {
                    // Fast fail approach
                    throw new TestException(message);
                }
                continue;
            }

            if (expectSecurityException && pref) {
                // SecurityException is not thrown for preferred class.
                // Test failed.
                message += "\nClass.forName("
                         + name + ", false, loader)\n"
                         + "  returned:" + classLoaded.toString() + "\n"
                         + "  expected:throws SecurityException";

                // Fast fail approach
                throw new TestException(message);
            } else if (expectSecurityException) {
                // Ok. Security exception should not be thrown for
                // non-preferred classes. Do additional tetsing:
                // try to invoke getPermissions() directly
                // for non-preferred classes with its codesource,
                // so we expect SecurityException in this case.
            }
            ProtectionDomain pd = classLoaded.getProtectionDomain();
            CodeSource cs = pd.getCodeSource();
            PermissionCollection pc = null;

            try {
                pc = loader.getPermissionsTest(cs);
            } catch (SecurityException ex) {
                if (!expectSecurityException) {
                    message += "\ngetPermissions("
                             + cs.toString() + ")\n"
                             + "  throws:" + ex.toString() + "\n"
                             + "  expected:PermissionCollection";

                    // Fast fail approach
                    throw new TestException(message);
                } else {
                    String msg = "getPermissions(" + cs.toString() + ")"
                               + " throws:" + ex.toString()
                               + " as expected";
                    logger.log(Level.FINE, msg);
                }
                continue;
            }

            if (expectSecurityException) {
                // SecurityException is not thrown for non-preferred class
                message += "\ngetPermissions("
                         + cs.toString() + ")\n"
                         + "  returned: PermissionCollection\n"
                         + "  expected: throws SecurityException";

                // Fast fail approach
                throw new TestException(message);
            }
            Enumeration permissions = pc.elements();

            while (permissions.hasMoreElements()) {
                Permission p = (Permission) permissions.nextElement();
                logger.log(Level.FINE, "Permission: " + p.toString());

                if (isHttp) {
                    if (Util.listClasses[item].pref) {
                        if (p.equals(socketPermission)) {
                            logger.log(Level.FINE, "OK: " + p.toString());
                        } else {
                            message += "\ngetPermissions("
                                     + cs.toString() + ")\n"
                                     + "  returned: " + p.toString() + "\n"
                                     + "  expected: "
                                     + socketPermission.toString();
                        }
                    } else {
                        if (p.equals(qa1filePermission)) {
                            logger.log(Level.FINE, "OK: " + p.toString());
                        } else {
                            message += "\ngetPermissions("
                                     + cs.toString() + ")\n"
                                     + "  returned: " + p.toString() + "\n"
                                     + "  expected: "
                                     + qa1filePermission.toString();
                        }
                    }
                } else {
                    if (Util.listClasses[item].pref) {
                        if (p.equals(jarfilePermission)) {
                            logger.log(Level.FINE, "OK: " + p.toString());
                        } else {
                            message += "\ngetPermissions("
                                     + cs.toString() + ")\n"
                                     + "  returned: " + p.toString() + "\n"
                                     + "  expected: "
                                     + jarfilePermission.toString();
                        }
                    } else {
                        if (p.equals(qa1filePermission)) {
                            logger.log(Level.FINE, "OK: " + p.toString());
                        } else {
                            message += "\ngetPermissions("
                                     + cs.toString() + ")\n"
                                     + "  returned: " + p.toString() + "\n"
                                     + "  expected: "
                                     + qa1filePermission.toString();
                        }
                    }
                }

                if (message.length() > 0) {
                    // Fast fail approach
                    throw new TestException(message);
                }
            }
        }
    }
}
