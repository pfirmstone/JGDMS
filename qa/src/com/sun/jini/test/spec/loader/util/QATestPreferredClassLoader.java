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

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;

// com.sun.jini.qa
import com.sun.jini.qa.harness.QATestEnvironment;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// java.net
import java.net.URL;

// java.io
import java.io.IOException;

// java.security
import java.security.PermissionCollection;
import java.security.CodeSource;

// davis packages
import net.jini.loader.pref.PreferredClassLoader;


/**
 * This class is instrumented PreferredClassLoader.
 * <br>
 * This class extends PreferredClassLoader, overrides findClass() and
 * loadClass() methods and sets various flags for additional checking before
 * super.loadClass() and super.findClass() calls. This class has some helper
 * methods which are useful for testing davis.loader and davis.loader.pref
 * packages.
 */
public class QATestPreferredClassLoader extends PreferredClassLoader {

    /** Flag to indicate that loadClass is invoked */
    public volatile boolean loadClassIsInvoked;

    /** Flag to indicate that findClass is invoked */
    public volatile boolean findClassIsInvoked;

    /** The URLs from which to load classes and resources */
    private URL[] urls;

    /** The parent class loader for delegation */
    private ClassLoader parent;

    /**
     * Location from where third party virtual
     * machines should load classes loaded by this loader. exportAnnotation is
     * ignored when null.
     */
    private String exportAnnotation;

    /**
     * If true, prevent downloading of classes with a code source which was
     * not granted DownloadPermission
     */
    private boolean requireDlPerm;

    /**
     * Create a preferred class loader with the given urls, parent
     * class loader, annotator, and security flags.
     */
    public QATestPreferredClassLoader(URL[] urls, ClassLoader parent,
            String exportAnnotation, boolean requireDlPerm) {
        super(urls, parent, exportAnnotation, requireDlPerm);
        this.urls = urls;
        this.parent = parent;
        this.exportAnnotation = exportAnnotation;
        this.requireDlPerm = requireDlPerm;
        clearAllFlags();
    }

    /**
     * Convert an array of URL objects into a corresponding string
     * containing a space-separated list of URLs.
     * <br>
     * Note that this method do the same string as
     * PreferredClassLoader.urlsToPath().
     */
    public String urlsToPath() {
        if (this.urls.length == 0) {
            return null;
        } else {
            String str = "";

            for (int i = 0; i < this.urls.length; i++) {
                if (i > 0) {
                    str += " ";
                }
                str += this.urls[i].toExternalForm();
            }
            return str;
        }
    }

    /**
     * Call protected superclass method: isPreferredResource.
     *
     * @param name the name of the resource for which a preferred
     *        value should be obtained
     * @param isClass true if the named parameter refers to a class
     *        resource
     *
     * @return Exception if Exception was thrown, otherwize - null.
     */
    public Exception isPreferredResourceEx(String name, boolean isClass) {
        Exception exception = null;

        try {
            boolean isPreffered = super.isPreferredResource(name, isClass);
        } catch (IOException e) {
            exception = e;
        }
        return exception;
    }

    /**
     * Call protected superclass method: isPreferredResource.
     *
     * @param name the name of the resource for which a preferred
     *        value should be obtained
     * @param isClass true if the named parameter refers to a class
     *        resource
     * @return whether or not the resource named resource is marked as
     *         preferred
     * @throws TestException if IOException was thrown.
     *
     */
    public boolean isPreferredResourceTest(String name, boolean isClass)
            throws TestException {
        boolean isPreffered = false;

        try {
            isPreffered = super.isPreferredResource(name, isClass);
        } catch (IOException e) {
            throw new TestException("Cannot call isPreferredResource()", e);
        }
        return isPreffered;
    }

    /**
     * Call protected superclass method: getPermissions.
     *
     * @return the permissions to be granted to code loaded from the
     *         given code source
     */
    public PermissionCollection getPermissionsTest(CodeSource codeSource) {
        return super.getPermissions(codeSource);
    }

    /**
     * Clear all boolean flags that indicates whether or not the method
     * was called:
     * <br>
     * {@link #loadClassIsInvoked}
     * <br>
     * {@link #findClassIsInvoked}
     */
    public void clearAllFlags() {
        loadClassIsInvoked = false;
        findClassIsInvoked = false;
    }

    /**
     * Overrides loadClass(String name, boolean resolve) in the
     * PreferredClassLoader and set {@link #loadClassIsInvoked} flag to true
     * 
     * This was synchronized, but since it sets a volatile variable then
     * calls the superclass which is synchronized anyway, we can safely remove 
     * it.
     */
    protected Class loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        loadClassIsInvoked = true;
        return super.loadClass(name, resolve);
    }

    /**
     * Overrides findClass(String name, boolean resolve) in the
     * PreferredClassLoader and set {@link #findClassIsInvoked} flag to true
     * 
     * This method was synchronized, but we experienced ClassLoader 
     * deadlock on freebsd OpenJDK6, so have removed it to reduce locking. 
     */
    protected Class findClass(String name)
            throws ClassNotFoundException {
        findClassIsInvoked = true;
        return super.findClass(name);
    }
}
