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
package com.sun.jini.start;

import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/** 
 * This class provides useful utilities for creating and
 * manipulating class loaders.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class ClassLoaderUtil {
    /** Configure logger */
    static final Logger logger = 
        Logger.getLogger("org.apache.river.start.ClassLoaderUtil");
    
    // Private constructor to prevent instantiation
    private ClassLoaderUtil() { }

    /**
     * Utility method that converts the components of a <code>String</code>
     * representing a classpath into file <code>URL</code>(s).
     *
     * @param classpath <code>String</code> containing components separated
     *                  by path separators that represent the components
     *                  making up a classpath
     *
     * @return a <code>URL[]</code> where 
     *         each element of the array corresponds to one of the components
     *         in the <code>classpath</code> parameter. The path components
     *         are (potentially) expanded via 
     *         <code>File.getCanonicalFile()</code> before converting to a
     *         <code>URL</code> format.
     *                       
     * @throws java.net.MalformedURLException 
     *         If the path cannot be parsed as a URL
     * @throws IOException 
     *         If an I/O error occurs, 
     *         which is possible because the construction of 
     *         the canonical pathname may require filesystem queries 
     */
    public static URL[] getClasspathURLs(String classpath)
     throws IOException, MalformedURLException
    {
        return org.apache.river.start.ClassLoaderUtil.getClasspathURLs(classpath);
    }//end getClasspathURLs

    /**
     * Utility method that converts the components of a <code>String</code>
     * representing a codebase into standard <code>URL</code>(s).
     *
     * @param codebase  <code>String</code> containing components separated
     *                  by spaces in which each component is in 
     *                  <code>URL</code> format.
     *
     * @return a <code>URL[]</code> where
     *         each element of the array corresponds to one of the components
     *         in the <code>codebase</code> parameter
     *                       
     * @throws java.net.MalformedURLException 
     */
    public static URL[] getCodebaseURLs(String codebase)
        throws MalformedURLException
    {
        return org.apache.river.start.ClassLoaderUtil.getCodebaseURLs(codebase);
    }//end getCodebaseURLs
    
    /**
     * Utility method that converts the components of a <code>String</code>
     * representing a codebase or classpath into <code>URL</code>(s).
     *
     * @param importCodebase <code>String</code> assumed (in order) to be either
     *            1) a space delimited set of <code>URL</code>(s)
     *            representing a codebase or
     *            2) a <code>File.pathSeparator</code> delimited set 
     *            of class paths.
     *
     * @return a <code>URL[]</code> where 
     *         each element of the array corresponds to one of the components
     *         in the <code>importCodebase</code> parameter
     *                       
     * @throws java.net.MalformedURLException 
     *         If the path cannot be parsed as a URL
     * @throws IOException 
     *         If an I/O error occurs, 
     *         which is possible because the construction of 
     *         the canonical pathname may require filesystem queries 
     */
    public static URL[] getImportCodebaseURLs(String importCodebase)
     throws IOException, MalformedURLException
    {
       return org.apache.river.start.ClassLoaderUtil.getImportCodebaseURLs(importCodebase);
    }//end getImportCodebaseURLs
    
   
    /**
     * Utility method that displays the class loader delegation tree for
     * the current context class loader. For each class loader in the tree,
     * this method displays the locations from which that class loader
     * will retrieve and load requested classes.
     * <p>
     * This method can be useful when debugging problems related to the
     * receipt of exceptions such as <code>ClassNotFoundException</code>.
     */
    public static void displayContextClassLoaderTree() {
        org.apache.river.start.ClassLoaderUtil.displayContextClassLoaderTree();
    }//end displayCurClassLoaderTree

    /**
     * Utility method that displays the class loader delegation tree for
     * the given class loader. For each class loader in the tree, this
     * method displays the locations from which that class loader will
     * retrieve and load requested classes.
     * <p>
     * This method can be useful when debugging problems related to the
     * receipt of exceptions such as <code>ClassNotFoundException</code>.
     *
     * @param classloader <code>ClassLoader</code> instance whose delegation
     *                    tree is to be displayed
     */
    public static void displayClassLoaderTree(ClassLoader classloader) {
        org.apache.river.start.ClassLoaderUtil.displayClassLoaderTree(classloader);
    }//end displayClassLoaderTree
     
}//end class ClassLoaderUtil
