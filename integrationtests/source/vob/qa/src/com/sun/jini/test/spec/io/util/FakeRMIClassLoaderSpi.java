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
package com.sun.jini.test.spec.io.util;

import java.rmi.server.RMIClassLoaderSpi;
import java.rmi.server.RMIClassLoader;
import java.net.MalformedURLException;
import java.util.logging.Logger;
import java.util.Arrays;

/**
 * Provides a way for methods to return configurable values.
 * <p>
 * The loadClass and loadProxyClass methods check method
 * parameters and then throw a specified exceptions.  A test sets static
 * variables to communicate the expected parameters and exception-to-throw.
 * If a methods exception-to-throw is null, then the method delegates to
 * the default RMIClassLoader provider instance.
 * <p>
 * The getClassAnnotation method returns the value of a static variable
 * if that variable is non-null.
 */
public class FakeRMIClassLoaderSpi extends RMIClassLoaderSpi {

    Logger logger;
    RMIClassLoaderSpi defaultSpi;

    // static variable returned by getClassAnnotation method
    public static String getClassAnnotationReturn;

    // static variables used by loadClass method
    public static Throwable loadClassException;
    public static String expectedLoadClassCodebase;
    public static String expectedLoadClassName;
    public static ClassLoader expectedLoadClassLoader;

    // static variables used by loadProxyClass method
    public static Throwable loadProxyClassException;
    public static String expectedLoadProxyClassCodebase;
    public static String[] expectedLoadProxyClassInterfaces;
    public static ClassLoader expectedLoadProxyClassLoader;

    public static void initLoadClass(Throwable exc, String codebase, 
        String name, ClassLoader loader)
    {
        loadClassException               = exc;
        expectedLoadClassCodebase        = codebase;
        expectedLoadClassName            = name;
        expectedLoadClassLoader          = loader;
        loadProxyClassException          = null;
        expectedLoadProxyClassCodebase   = null;
        expectedLoadProxyClassInterfaces = null;
        expectedLoadProxyClassLoader     = null;
    }

    public static void initLoadProxyClass(Throwable exc, String codebase, 
        String[] interfaces, ClassLoader loader)
    {
        loadClassException               = null;
        expectedLoadClassCodebase        = null;
        expectedLoadClassName            = null;
        expectedLoadClassLoader          = null;
        loadProxyClassException          = exc;
        expectedLoadProxyClassCodebase   = codebase;
        expectedLoadProxyClassInterfaces = interfaces;
        expectedLoadProxyClassLoader     = loader;
    }

    public FakeRMIClassLoaderSpi() {
        super();
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        defaultSpi = RMIClassLoader.getDefaultProviderInstance();
    }

    public String getClassAnnotation(Class cl) {
        if (getClassAnnotationReturn == null) {
            return defaultSpi.getClassAnnotation(cl);
        } else {
            return getClassAnnotationReturn;
        }
    }

    public ClassLoader getClassLoader(String codebase)
        throws MalformedURLException 
    {
        return defaultSpi.getClassLoader(codebase);
    }

    public Class loadClass(String codebase, String name,
        ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        logger.entering(getClass().getName(),"loadClass");
        if (loadClassException == null) {
            return defaultSpi.loadClass(codebase, name, defaultLoader);
        } else {
            check(codebase,expectedLoadClassCodebase);
            check(name,expectedLoadClassName);
            check(defaultLoader,expectedLoadClassLoader);
            if (loadClassException instanceof MalformedURLException) {
                throw (MalformedURLException) loadClassException;
            } else if (loadClassException instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) loadClassException;
            } else if (loadClassException instanceof RuntimeException) {
                throw (RuntimeException) loadClassException;
            } else if (loadClassException instanceof Error) {
                throw (Error) loadClassException;
            } else {
                throw new AssertionError();
            } 
        }
    }

    public Class loadProxyClass(String codebase, String[] interfaces,
        ClassLoader defaultLoader)
        throws MalformedURLException, ClassNotFoundException
    {
        logger.entering(getClass().getName(),"loadProxyClass");
        if (loadProxyClassException == null) {
            return defaultSpi.loadProxyClass(codebase,interfaces,defaultLoader);
        } else {
            check(codebase,expectedLoadProxyClassCodebase);
            check(interfaces,expectedLoadProxyClassInterfaces);
            check(defaultLoader,expectedLoadProxyClassLoader);
            if (loadProxyClassException instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) loadProxyClassException;
            } else if(loadProxyClassException instanceof MalformedURLException){
                throw (MalformedURLException) loadProxyClassException;
            } else if (loadProxyClassException instanceof RuntimeException) {
                throw (RuntimeException) loadProxyClassException;
            } else if (loadProxyClassException instanceof Error) {
                throw (Error) loadProxyClassException;
            } else {
                throw new AssertionError();
            } 
        }
    }

    /**
     * Check that two string arrays are equivalent.  Throw AssertionError
     * if they're not.
     */
    private void check(String[] s1, String[] s2) throws AssertionError {
        logger.entering(getClass().getName(),"check(String[],String[])",
            new Object[] {s1,s2});
        if (! Arrays.equals(s1,s2)) {
            throw new AssertionError();
        }
    }

    /**
     * Check that two objects are equivalent.  Throw AssertionError
     * if they're not.
     */
    private void check(Object o1, Object o2) throws AssertionError {
        logger.entering(getClass().getName(),"check(Object,Object)",
            new Object[] {o1,o2});
        if (o1 == null || o2 == null) {
            if (! (o1 == o2)) {
                throw new AssertionError();
            }
            return;
        }
        if (! o1.equals(o2)) {
            throw new AssertionError();
        }
    }

}
