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
import java.io.FileNotFoundException;
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

/** Utility class for finding the names of the classes in a set of packages. */
public class PackageClasses {

    /** The names of directories in the classpath. */
    private final Set directories = new HashSet();

    /** The names of files in JAR files in the classpath. */
    private final Set jarContents = new HashSet();

    /**
     * Prints the classes in a package in the class path to standard output
     * using the default character encoding.  If second argument is specified,
     * it is used as the class path, otherwise the system class path is used.
     *
     * @param	args the arguments
     * @throws	IllegalArgumentException if less than one or more than two
     *		arguments are provided
     * @throws	IOException if an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
	String classpath;
	if (args.length == 1) {
	    classpath = System.getProperty("java.class.path");
	} else if (args.length == 2) {
	    classpath = args[1];
	} else {
	    throw new IllegalArgumentException(
		"Usage: java " + PackageClasses.class.getName() +
		" package [classpath]");
	}
	PackageClasses pc = new PackageClasses(classpath);
	SortedSet classes = new TreeSet(pc.compute(args[0]));
        Iterator classesIter = classes.iterator();
	while (classesIter.hasNext()) {
	    System.out.println(classesIter.next());
	}
    }

    /**
     * Creates an instance with the specified class path.  The class path is
     * interpreted as a list of file names, separated by the {@link
     * File#pathSeparator File.pathSeparator} character.  Empty names are
     * treated as the current directory, names ending in the {@link
     * File#separator File.separator} character are treated as directories, and
     * other names are treated as JAR files.
     *
     * @param	classpath the class path
     * @throws	IOException if a problem occurs accessing files in the class
     *		path
     */
    public PackageClasses(String classpath) throws IOException {
	if (classpath == null) {
	    throw new NullPointerException("The classpath cannot be null");
	}
	StringTokenizer tokens =
	    new StringTokenizer(classpath, File.pathSeparator);
	while (tokens.hasMoreTokens()) {
	    String token = tokens.nextToken();
	    if (token.equals("")) {
		token = ".";
	    }
	    File file = new File(token);
	    if (!file.exists()) {
		throw new FileNotFoundException(
		    "File or directory not found: " + file);
	    } else if (file.isDirectory()) {
		String path = file.getPath();
		if (!path.endsWith(File.separator)) {
		    path += File.separator;
		}
		directories.add(path);
	    } else {
		JarFile jarFile;
		try {
		    jarFile = new JarFile(file);
		} catch (IOException e) {
		    IOException e2 = new IOException(
			"Problem accessing file or directory: " + file);
		    e2.initCause(e);
		    throw e2;
		}
		try {
		    for (Enumeration entries = jarFile.entries();
			 entries.hasMoreElements(); )
		    {
			JarEntry entry = (JarEntry) entries.nextElement();
			jarContents.add(entry.getName());
		    }
		} finally {
		    try {
			jarFile.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}
    }

    /**
     * Returns a set of the fully qualified names of classes in the specified
     * packages, not including classes in subpackages of those packages.
     *
     * @param	packages the packages
     * @return	the class names
     * @throws	IOException if a problem occurs accessing files in the class
     *		path
     */
    public Set compute(String[] packages)
	throws IOException
    {
	return compute(false, packages);
    }
    
    public Set compute(String packAge)
            throws IOException
    {
        String [] packages = {packAge};
        return compute(false, packages);
    }

    /**
     * Returns a set of the fully qualified names of classes in the specified
     * packages, optionally including classes in subpackages of those packages.
     *
     * @param	recurse if <code>true</code>, find classes in subpackages of
     *		the specified packages
     * @param	packages the packages
     * @return	the class names
     * @throws	IOException if a problem occurs accessing files in the class
     *		path
     */
    public Set compute(boolean recurse, String[] packages)
	throws IOException
    {
	if (packages == null) {
	    throw new NullPointerException(
		"The packages argument cannot be null");
	}
	Set classes = new HashSet();
        int l = packages.length;
	for (int i = 0 ; i < l ; i++) {
            String pkg = packages[i];
	    if (pkg == null) {
		throw new NullPointerException(
		    "Elements of the packages argument cannot be null");
	    }
	    String pkgFileName = pkg.replace('.', File.separatorChar);
	    if (!"".equals(pkgFileName)) {
		pkgFileName += File.separatorChar;
	    }
            Iterator dirIter = directories.iterator();
	    while (dirIter.hasNext()) {
                String dir = (String) dirIter.next();
		File file = new File(dir, pkgFileName);
		if (file.exists()) {
		    collectClasses(file, recurse, pkg, classes);
		}
	    }
	    /* JAR files always use forward slashes */
	    if (File.separatorChar != '/') {
		pkgFileName = pkgFileName.replace(File.separatorChar, '/');
	    }
            Iterator jarContentIter = jarContents.iterator();
	    while (jarContentIter.hasNext()) {
                String file = (String) jarContentIter.next();
		if (file.startsWith(pkgFileName) && file.endsWith(".class")) {
		    file = removeDotClass(file);
		    if (recurse ||
			file.indexOf('/', pkgFileName.length() + 1) < 0)
		    {
			/*
			 * Include the file if it is in a subdirectory only if
			 * recurse is true.  Otherwise, check that the class
			 * file matches the package name exactly.
			 * -tjb@sun.com (06/07/2006)
			 */
			classes.add(file.replace('/', '.'));
		    }
		}
	    }
	}
	return classes;
    }
    
    /**
     * Returns a set of the fully qualified names of classes in the specified
     * packages, optionally including classes in subpackages of those packages.
     *
     * @param	recurse if <code>true</code>, find classes in subpackages of
     *		the specified package
     * @param	packAge the package
     * @return	the class names
     * @throws	IOException if a problem occurs accessing files in the class
     *		path
     */
    public Set compute(boolean recurse, String packAge)
	throws IOException
    {
        String [] packages = {packAge};
        return compute(recurse, packages);
    }
    
    /**
     * Adds the names of classes in the directory to the set of classes,
     * recursiving into subdirectories if requested.
     */
    private static void collectClasses(File directory,
				       final boolean recurse,
				       final String pkg,
				       final Set classes)
	throws IOException
    {
	final IOException[] failed = { null };
	File[] result = directory.listFiles(new FileFilter() {
	    public boolean accept(File child) {
		String name = child.getName();
		if (name != null) {
		    if (name.endsWith(".class") && child.isFile()) {
			String classname = removeDotClass(name);
			if (!"".equals(pkg)) {
			    classname = pkg + '.' + classname;
			}
			classes.add(classname);
		    } else if (recurse && child.isDirectory()) {
			String subpackage =
			    "".equals(pkg) ? name : pkg + '.' + name;
			try {
			    collectClasses(
				child, recurse, subpackage, classes);
			} catch (IOException e) {
			    failed[0] = e;
			}
		    }
		}
		return false;
	    }
	});
	if (failed[0] != null) {
	    throw failed[0];
	} else if (result == null) {
	    throw new IOException(
		"A problem occurred accessing directory: " + directory);
	}
    }

    /** Strips the .class suffix from a string. */
    private static String removeDotClass(String s) {
	return s.substring(0, s.length() - 6);
    }
}
