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
package com.sun.jini.tool;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 * Class to provide the <code>main</code> method referenced by the
 * <code>Main-Class</code> entry in the manifest of the <code>classdep</code>
 * JAR file. <code>ClassDep</code> relies on <code>sun.tools</code>
 * classes which may not be available in the boot or extension classloader;
 * this class provides support for finding the JDK <code>tools.jar</code> file
 * at runtime and running <code>ClassDep</code> from a classloader which
 * includes that file.
 */
class ClassDepLoader {

    /**
     * Entry point for an executable JAR file for <code>ClassDep</code>.  Search
     * for <code>tools.jar</code> in <code>$java.home/../lib/</code>. If the
     * file is found, construct a classloader using <code>URL</code>s generated
     * from the union of the value of <code>java.class.path</code> and the path
     * to the <code>tools.jar</code> file with the extension classloader as
     * parent. Then reflectively call the <code>main</code> method of the
     * <code>ClassDep</code> class obtained from that loader.
     * <p>
     * If no <code>tool.jar</code> file is found, <code>ClassDep.main</code>
     * is called directly.
     * 
     * @param args the command line arguments
     */
    public static void main(String[] args) {
	File javaHome = new File(System.getProperty("java.home"));
	String jarPath = "lib" + File.separator + "tools.jar";
	File toolsJar = new File(javaHome.getParent(), jarPath);
	if (!toolsJar.exists()) {
	    try {
		Class.forName("sun.tools.java.Constants");
	    } catch (ClassNotFoundException e) {
		print("classdep.notools", toolsJar.toString());
		return;
	    }
	    ClassDep.main(args);
	} else {
	    try {
		String classpath = System.getProperty("java.class.path");
		classpath += File.pathSeparator + toolsJar;
		StringTokenizer st = new StringTokenizer(classpath, 
							 File.pathSeparator);
		URL[] urls = new URL[st.countTokens()];
		for (int i=0; st.hasMoreTokens(); i++) {
		    urls[i] = new File(st.nextToken()).toURI().toURL();
		}
                ClassLoader cl = ClassLoader.getSystemClassLoader();
		if (cl != null) {
		    cl = cl.getParent();
		}
		ClassLoader loader = new URLClassLoader(urls, cl);
		Class classdepClass = 
		    Class.forName("com.sun.jini.tool.ClassDep", true, loader);
		Method mainMethod = 
		    classdepClass.getMethod("main",
					    new Class[] {String[].class});
		mainMethod.invoke(null, new Object[]{args});
	    } catch (Throwable e) {
		print("classdep.loadfailed", null);
		e.printStackTrace();
	    }
	}
    }
    
    /** print a localized message */
    private static void print(String key, String v1) {
	Object[] vals = (v1 == null) ? null : new Object[]{v1};
	try {
	    String name = "com.sun.jini.tool.resources.classdep";
	    ResourceBundle resources = ResourceBundle.getBundle(name);
	    String fmt = resources.getString(key);
	    System.err.println(MessageFormat.format(fmt, vals));
	} catch (MissingResourceException e) {
	    e.printStackTrace();
	}
    }
}
