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
package com.sun.jini.tool.classdepend;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Utility class for finding the names of packages in a class path. */
public class ClasspathPackages {

    /**
     * Prints the packages in the class path to standard output using the
     * default character encoding.  If an argument is specified, it is used as
     * the class path, otherwise the system class path is used.
     *
     * @param	args the arguments
     */
    public static void main(String[] args) {
	String classpath;
	if (args.length == 0) {
	    classpath = System.getProperty("java.class.path");
	} else if (args.length == 1) {
	    classpath = args[0];
	} else {
	    throw new IllegalArgumentException(
		"Usage: java " + ClasspathPackages.class.getName() +
		" [classpath]");
	}
	SortedSet packages = new TreeSet(compute(classpath));
        Iterator pkgIter = packages.iterator();
        
	while (pkgIter.hasNext()) {
            String pkg = (String) pkgIter.next();
	    System.out.println(pkg.length() == 0 ? "[empty package]" : pkg);
	}
    }

    /**
     * Computes the packages in the specified class path.  The class path is
     * interpreted as a list of file names, separated by the {@link
     * File#pathSeparator File.pathSeparator} character.  Empty names are
     * treated as the current directory, names ending in the {@link
     * File#separator File.separator} character are treated as directories, and
     * other names are treated as JAR files.  Directories or JAR files that
     * have errors when they are accessed will be ignored.
     *
     * @param	classpath the class path
     * @return	the package names
     */
    public static Set compute(String classpath) {
	if (classpath == null) {
	    throw new NullPointerException("The classpath cannot be null");
	}
	Set packages = new HashSet();
	StringTokenizer tokens =
	    new StringTokenizer(classpath, File.pathSeparator);
	while (tokens.hasMoreTokens()) {
	    String token = tokens.nextToken();
	    if (token.equals("")) {
		token = ".";
	    }
	    File file = new File(token);
	    if (!file.exists()) {
		continue;
	    } else if (file.isDirectory()) {
		String dir = file.getPath();
		if (!dir.endsWith(File.separator)) {
		    dir += File.separator;
		}
		addPackages(dir, dir, packages);
	    } else {
		JarFile jarFile;
		try {
		    jarFile = new JarFile(file);
		} catch (IOException e) {
		    continue;
		}
		try {
		    for (Enumeration entries = jarFile.entries();
			 entries.hasMoreElements(); )
		    {
                        JarEntry entry = (JarEntry) entries.nextElement();
			addPackage( entry, packages);
		    }
		} finally {
		    try {
			jarFile.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}
	return packages;
    }

    /**
     * Adds packages of classes recursively located in the directory, using the
     * top argument to specify the top level directory containing the package
     * hierarchy.
     */
    private static void addPackages(
	final String top, String dir, final Set packages)
    {
	File file = new File(dir);
	if (file.exists()) {
	    /* Collect the package names as a side effect */
	    file.listFiles(new FileFilter() {
		public boolean accept(File child) {
		    String path = child.getPath();
		    String name = path.substring(top.length());
		    if (name.endsWith(".class") && child.isFile()) {
			int sep = name.lastIndexOf(File.separatorChar);
			if (sep <= 0) {
			    packages.add("");
			} else {
			    packages.add(
				name.substring(0, sep).replace(
				    File.separatorChar, '.'));
			}
		    } else if (child.isDirectory()) {
			addPackages(top, path, packages);
		    }
		    return false;
		}
	    });
	}
    }

    /** Adds the package of the class named by the JAR entry, if any */
    private static void addPackage(JarEntry entry, Set packages) {
	String name = entry.getName();
	if (name.endsWith(".class")) {
	    name = name.substring(0, name.length() - 6);
	    int slash = name.lastIndexOf('/');
	    if (slash <= 0) {
		name = "";
	    } else {
		name = name.substring(0, slash);
		name = name.replace('/', '.');
	    }
	    packages.add(name);
	}
    }
}
