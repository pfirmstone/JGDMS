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

import sun.tools.java.BinaryClass;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.ClassFile;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassPath;
import sun.tools.java.Constants;
import sun.tools.java.Environment;
import sun.tools.java.Identifier;
import sun.tools.java.MemberDefinition;
import sun.tools.java.Package;
import sun.tools.java.Type;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Tool used to analyze a set of classes and determine on what other classes
 * they directly or indirectly depend. Typically this tool is used to
 * compute the necessary and sufficient set of classes to include in a JAR
 * file, for use in the class path of a client or service, or for use in the
 * codebase of a client or service. The tool starts with a set of "root"
 * classes and recursively computes a dependency graph, finding all of the
 * classes referenced directly by the root classes, finding all of the
 * classes referenced in turn by those classes, and so on, until no new
 * classes are found or until classes that are not of interest are
 * found. The normal output of the tool is a list of all of the classes in
 * the dependency graph. The output from this command can be used as input
 * to the <code>jar</code> tool, to create a JAR file containing precisely
 * those classes.
 * <p>
 * The following items are discussed below:
 * <ul>
 * <li><a href="#running">Running the Tool</a>
 * <li><a href="#processing">Processing Options</a>
 * <li><a href="#output">Output Options and Arguments</a>
 * <li><a href="#examples">Examples</a>
 * </ul>
 *
 * <a name="running"></a>
 * <h3>Running the Tool</h3>
 *
 * The command line for running the tool has the form:
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>input_classpath</b></var> <var><b>processing_options</b></var> <var><b>output_options</b></var>
 * </pre></blockquote>
 * <p>
 * where <var><b>install_dir</b></var> is the directory where the Apache River release
 * is installed.
 * Note that the options for this tool can be specified in any order, and
 * can be intermixed.
 *
 * <p>
 * The <code>-cp</code> class path value,
 * <var><b>input_classpath</b></var>,
 * is an argument to the <code>ClassDep</code> tool itself and should
 * include all of the classes that might need to be included in the
 * dependency analysis. Typically this will include all of your application
 * classes, classes from the Apache River release, and any other classes on which
 * your classes might depend. It is safe to include more classes than are
 * actually necessary (since the purpose of this tool is, after all, to
 * determine which subset of these classes is actually necessary), but it is
 * not necessary to include any classes that are part of the Java 2 SDK.
 * The class path should be in the form of a list of directories or JAR
 * files, delimited by a colon (":") on UNIX platforms and a semi-colon
 * (";") on Microsoft Windows platforms. The order of locations in the path
 * does not matter. If you use JAR files, any <code>Class-Path</code>
 * manifest entries in those JAR files are ignored, so you must include the
 * values of those manifest entries explicitly in the path, or errors may
 * result. For example, if you include <code>jini-ext.jar</code> then you
 * should explicitly include <code>jini-core.jar</code> as well, because
 * <code>jini-core.jar</code> is in the <code>Class-Path</code> manifest
 * entry of <code>jini-ext.jar</code>.</dd>
 *
 * <a name="processing"></a>
 * <h3>Processing Options</h3>
 *
 * The root classes of the dependency graph can be specified by any
 * combination of individual classes and directories of classes. Each of
 * these options can be used any number of times, and are illustrated in the
 * <a href="#examples">Examples</a> section of this page.
 * <p>
 * In general, you only need to specify concrete classes as roots, not
 * interface types. When analyzing classes for the class path of an
 * application, you typically need to include the top-level class (the one
 * with the <code>main</code> method). When analyzing classes for the
 * codebase of a service, you typically need to include the top-level proxy
 * classes used by the service, any trust verifier classes for those
 * proxies, and any custom entry classes used by the service for lookup
 * service attributes. Also when analyzing classes for the codebase of a
 * service, if the service's proxy can return leases, registration objects,
 * or other proxies, or if your service generates events, and if those
 * objects are only referenced by interface type in the top-level proxy, not
 * by concrete class, then you also need to include the concrete classes of
 * those other objects. In all cases, you typically need to include any stub
 * classes that you generated with the <code>rmic</code> tool.
 * <p>
 * <dl>
 * <dt><b><var>class</var></b>
 * <dd>This option specifies the fully qualified name of an individual class
 * to include as a root of the dependency graph. This option can be
 * specified zero or more times. Each class you specify with this option
 * needs to be in a package that is defined to be "inside" the graph (as
 * described further below).</dd>
 * <p>
 * <dt><b><var>directory</var></b>
 * <dd>This option specifies the root directory of a tree of compiled class
 * files, all of which are to be included as roots of the dependency
 * graph. This option can be specified zero or more times. The directory
 * must be one of the directories specified in
 * <var><b>input_classpath</b></var>,
 * or a subdirectory of one, and must contain at least one filename
 * separator character. Each class in the tree needs to be in a package that
 * is defined to be "inside" the graph (as described further below).
 * <p>
 * The <code>-prune</code> option can be used to exclude particular subtrees
 * from the set of roots.
 * <p>
 * <dl>
 * <dt><b><code>-prune</code> <var>package-prefix</var></b>
 * <dd>Specifies a package namespace to exclude when selecting roots from
 * directory trees. Within the directory trees, any classes that are in the
 * given package or a subpackage of it are not treated as roots. Note that
 * this option has <i>no</i> effect on whether the classes in question end
 * up "inside" or "outside" the dependency graph (as defined further below);
 * it simply controls their use as roots. This option can be specified zero
 * or more times. Note that the argument to <code>-prune</code> is a package
 * namespace (delimited by "."), not a directory.</dd>
 * </dl>
 * <p>
 * The <code>-skip</code> option (described further below) can be used to
 * exclude specific classes from the set of roots.
 * </dd>
 * </dl>
 * <p>
 * Starting with the root classes, a dependency graph is constructed by
 * examining the compiled class file for a class, finding all of the classes
 * it references, and then in turn examining those classes.  The extent of
 * the graph is determined by which packages are defined to be "inside" the
 * graph and which are defined to be "outside" the graph.  If a referenced
 * class is in a package that is defined to be outside the graph, that class
 * is not included in the graph, and none of classes that it references are
 * examined. All of the root classes must be in packages that are defined to
 * be "inside" the graph.
 * <p>
 * The inside and outside packages are specified by using the following
 * options. Each of these options may be specified zero or more times.  Some
 * variations are illustrated in the <a href="#examples">Examples</a> section
 * of this page.
 * <p>
 * <dl>
 * <dt><b><code>-in</code> <var>package-prefix</var></b>
 * <dd>Specifies a namespace of "inside" packages. Any classes in this
 * package or a subpackage of it are included in the dependency graph (and
 * hence are to be included in your JAR file), unless they are explicitly
 * excluded using <code>-out</code> or <code>-skip</code> options. This
 * option can be specified zero or more times. If no <code>-in</code>
 * options are specified, the default is that all packages are considered to
 * be inside packages.  Note that the argument to <code>-in</code> is a
 * namespace, so none of its subpackages need to be specified as an argument
 * to <code>-in</code>.
 * <p>
 * If you use this option, you will likely need to use it multiple
 * times. For example, if your application classes are in the
 * <code>com.corp.foo</code> namespace, and you also use some classes in the
 * <code>com.sun.jini</code> and <code>net.jini</code> namespaces, then you
 * might specify:
 * <pre>-in com.corp.foo -in com.sun.jini -in net.jini</pre>
 * </dd>
 * <p>
 * <dt><b><code>-out</code> <var>package-prefix</var></b>
 * <dd>Specifies a namespace of "outside" packages. Any classes in this
 * package or a subpackage of it are excluded from the dependency graph (and
 * hence are to be excluded from your JAR file). This option can be
 * specified zero or more times.  If you specify <code>-in</code> options,
 * then each <code>-out</code> namespace should be a subspace of some
 * <code>-in</code> namespace. Note that the argument to <code>-out</code>
 * is a namespace, so none of its subpackages need to be specified as an
 * argument to <code>-out</code>.
 * <p>
 * If you use this option, you will likely need to use it multiple
 * times. For example, if you do not specify any <code>-in</code> options,
 * then all packages are considered inside the graph, including packages
 * defined in the Java 2 SDK that you typically want to exclude, so you
 * might exclude them by specifying:
 * <pre>-out java -out javax</pre>
 * As another example, if you have specified <code>-in com.corp.foo</code>
 * but you don't want to include any of the classes in the
 * <code>com.corp.foo.test</code> or <code>com.corp.foo.qa</code> namespaces
 * in the dependency graph, then you would specify:
 * <pre>-out com.corp.foo.test -out com.corp.foo.qa</pre>
 * </dd>
 * <p>
 * <dt><b><code>-skip</code> <var>class</var></b>
 * <dd>Specifies the fully qualified name of a specific class to exclude
 * from the dependency graph. This option allows an individual class to be
 * considered "outside" without requiring the entire package it is defined
 * in to be considered outside.  This option can be specified zero or more
 * times.
 * </dd> 
 * <p>
 * <dt><b><code>-outer</code></b>
 * <dd>By default, if a static nested class is included in the dependency
 * graph, all references from that static nested class to its immediate
 * lexically enclosing class are ignored, to avoid inadvertent inclusion of
 * the enclosing class. (The default is chosen this way because the compiled
 * class file of a static nested class always contains a reference to the
 * immediate lexically enclosing class.) This option causes all such
 * references to be considered rather than ignored.  Note that this option
 * is needed very infrequently.</dd>
 * </dl>
 *
 * <a name="output"></a>
 * <h3>Output Options and Arguments</h3>
 * 
 * The following options and arguments determine the content and format of
 * the output produced by this tool. These options do not affect the
 * dependency graph computation, only the information displayed in the
 * output as a result of the computation. Most of these options may be
 * specified multiple times. Some variations are illustrated in the
 * <a href="#examples">Examples</a> section of this page.
 * <dl>
 * <dt><b><code>-edges</code></b>
 * <dd>By default, the classes which are included in the dependency graph
 * are displayed in the output. This option specifies that instead, the
 * classes which are excluded from the dependency graph, but which are
 * directly referenced by classes in the dependency graph, should be
 * displayed in the output. These classes form the outside "edges" of the
 * dependency graph.
 * <p>
 * For example, you might exclude classes from the Java 2 SDK from the
 * dependency graph because you don't want to include them in your JAR file,
 * but you might be interested in knowing which classes from the Java 2 SDK
 * are referenced directly by the classes in your JAR file. The
 * <code>-edges</code> option can be used to display this information.
 * </dd>
 * <p>
 * <dt><b><code>-show</code> <var>package-prefix</var></b>
 * <dd>Displays the classes that are in the specified package or a
 * subpackage of it. This option can be specified zero or more times. If no
 * <code>-show</code> options are specified, the default is that all classes
 * in the dependency graph are displayed (or all edge classes, if
 * <code>-edges</code> is specified). Note that the argument to
 * <code>-show</code> is a namespace, so none of its subpackages need to be
 * specified as an argument to <code>-show</code>.
 * <p>
 * For example, to determine which classes from the Java 2 SDK your
 * application depends on, you might not specify any <code>-in</code>
 * options, but limit the output by specifying:
 * <pre>-show java -show javax</pre></dd>
 * <p>
 * <dt><b><code>-hide</code> <var>package-prefix</var></b>
 * <dd>Specifies a namespace of packages which should not be displayed. Any
 * classes in this package or a subpackage of it are excluded from the
 * output. This option can be specified zero or more times. If you specify
 * <code>-show</code> options, then each <code>-hide</code> namespace should
 * be a subspace of some <code>-show</code> namespace. Note that the
 * argument to <code>-hide</code> is a namespace, so none of its subpackages
 * need to be specified as an argument to <code>-hide</code>.
 * <p>
 * For example, to determine which non-core classes from the
 * <code>net.jini</code> namespace you use, you might specify:
 * <pre>-show net.jini -hide net.jini.core</pre></dd>
 * <p>
 * <dt><b><code>-files</code></b>
 * <dd>By default, fully qualified class names are displayed, with package
 * names delimited by ".". This option causes the output to be in filename
 * format instead, with package names delimited by filename separators and
 * with ".class" appended. For example, using this option on Microsoft
 * Windows platforms would produce output in the form of
 * <code>com\corp\foo\Bar.class</code> instead of
 * <code>com.corp.foo.Bar</code>. This option should be used to generate
 * output suitable as input to the <code>jar</code> tool.
 * <p>
 * For more information on the <code>jar</code> tool, see:
 * <ul>
 * <li><a href="http://java.sun.com/j2se/1.4/docs/tooldocs/solaris/jar.html">
 * http://java.sun.com/j2se/1.4/docs/tooldocs/solaris/jar.html</a>
 * <li><a href="http://java.sun.com/j2se/1.4/docs/tooldocs/windows/jar.html">
 * http://java.sun.com/j2se/1.4/docs/tooldocs/windows/jar.html</a>
 * <li><a href="http://java.sun.com/j2se/1.4/docs/guide/jar/jar.html">
 * http://java.sun.com/j2se/1.4/docs/guide/jar/jar.html</a>
 * </ul>
 * </dd>
 * <p>
 * <dt><b><code>-tell</code> <var>class</var></b>
 * <dd>Specifies the fully qualified name of a class for which dependency
 * information is desired. This option causes the tool to display
 * information about every class in the dependency graph that references the
 * specified class. This information is sent to the error stream of the
 * tool, not to the normal output stream.  This option can be specified zero
 * or more times. If this option is used, all other output options are
 * ignored, and the normal class output is not produced. This option is
 * useful for debugging.
 * </dd>
 * </dl>
 * 
 * <a name="examples"></a>
 * <h3>Examples</h3>
 *
 * (The examples in this section assume you ran the Apache River release installer
 * with an "Install Set" selection that created the top-level
 * <code>classes</code> directory. Alternatively, if you have compiled from
 * the source code, substitute <code>source/classes</code> for
 * <code>classes</code> in the examples.)
 * <p>
 * The following example computes the classes required for a codebase JAR
 * file, starting with a smart proxy class and a stub class as roots, and
 * displays them in filename format. (A more complete example would include
 * additional roots for leases, registrations, events, and lookup service
 * attributes, and would exclude platform classes such as those in
 * <code>jsk-platform.jar</code>.)
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>install_dir</b></var>/classes
 *      com.sun.jini.reggie.RegistrarProxy com.sun.jini.reggie.RegistrarImpl_Stub
 *      -in com.sun.jini -in net.jini
 *      -files
 * </pre></blockquote>
 * <p>
 * The following example computes the classes required for a classpath JAR
 * file, starting with all of the classes in a directory as roots, and
 * displays them in class name format. (A more complete example would exclude
 * platform classes such as those in <code>jsk-platform.jar</code>.)
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>install_dir</b></var>/classes
 *      <var><b>install_dir</b></var>/classes/com/sun/jini/reggie
 *      -in com.sun.jini -in net.jini
 * </pre></blockquote>
 * <p>
 * The following example computes the <code>com.sun.jini</code> classes used
 * by a service implementation, and displays the <code>net.jini</code>
 * classes that are immediately referenced by those classes.
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>install_dir</b></var>/classes
 *      com.sun.jini.reggie.RegistrarImpl
 *      -in com.sun.jini
 *      -edges
 *      -show net.jini
 * </pre></blockquote>
 * <p>
 * The following example computes all of the classes used by a service
 * implementation that are not part of the Java 2 SDK, and displays the
 * classes that directly reference the class <code>java.awt.Image</code>.
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>install_dir</b></var>/classes
 *      com.sun.jini.reggie.RegistrarImpl
 *      -out java -out javax
 *      -tell java.awt.Image
 * </pre></blockquote>
 * <p>
 * The following example computes all of the classes to include in
 * <code>jini-ext.jar</code> and displays them in filename format.  Note
 * that both <code>-out</code> and <code>-prune</code> options are needed
 * for the <code>net.jini.core</code> namespace; <code>-out</code> to
 * exclude classes from the dependency graph, and <code>-prune</code> to
 * prevent classes from being used as roots.
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar
 *      -cp <var><b>install_dir</b></var>/classes
 *      -in net.jini -out net.jini.core -in com.sun.jini
 *      <var><b>install_dir</b></var>/classes/net/jini -prune net.jini.core
 *      -files
 * </pre></blockquote>
 *
 * @author Sun Microsystems, Inc.
 */
public class ClassDep {
    /**
     * Container for all the classes that we have seen.
     */
    private final HashSet seen = new HashSet();
    /**
     * Object used to load our classes.
     */
    private Env env;
    /**
     * If true class names are printed using
     * the system's File.separator, else the 
     * fully qualified class name is printed.
     */
    private boolean files = false;
    /**
     * Set of paths to find class definitions in order to determine 
     * dependencies.
     */
    private String classpath = "";
    /**
     * Flag to determine whether there is interest
     * in dependencies that go outside the set of 
     * interested classes. If false then outside,
     * references are ignored, if true they are noted.
     * i.e, if looking only under <code>net.jini.core.lease</code>
     * a reference to a class in <code>net.jini</code> is found it
     * will be noted if the flag is set to true, else
     * it will be ignored. <p>
     * <b>Note:</b> these edge case dependencies must be
     * included in the classpath in order to find their
     * definitions.
     */
    private  boolean edges = false;
    /**
     * Static inner classes have a dependency on their outer
     * parent class. Because the parent class may be really
     * big and may pull other classes along with it we allow the
     * choice to ignore the parent or not. If the flag is set to
     * true we pull in the parent class. If it is false we don't
     * look at the parent. The default is is to not include the 
     * parent. <p>
     * <b>Note:</b> This is an optimization for those who plan
     * on doing work with the output of this utility. It does
     * not impact this utility, but the work done on its 
     * generated output  may have an impact. 
     */
    private  boolean ignoreOuter = true;
    /**
     * Package set that we have interest to work in.
     */
    private  final ArrayList inside  = new ArrayList();
    /**
     * Package set to not work with. This is useful if
     * there is a subpackage that needs to be ignored.
     */
    private  final ArrayList outside = new ArrayList();
    /**
     * Class set to look at for dependencies. These are
     * fully qualified names, ie, net.jini.core.lease.Lease.
     * This is a subset of the values in
     * <code>inside</code>.
     */
    private  final ArrayList classes = new ArrayList();
    /**
     * Set of directories to find dependencies in.
     */
    private  final ArrayList roots   = new ArrayList();
    /**
     * Set of packages to skip over in the processing of dependencies.
     * This can be used in conjunction with <em>-out</em> option.
     */
    private  final ArrayList prunes  = new ArrayList();
    /**
     * Set of package prefixes to skip over in the processing 
     * of dependencies. 
     */
    private  final ArrayList skips   = new ArrayList();
    /**
     * Given a specific fully qualified classes, what other classes
     * in the roots list depend on it. This is more for debugging
     * purposes rather then normal day to day usage.
     */
    private  final ArrayList tells   = new ArrayList();
    /**
     * Only display found dependencies that fall under the provided
     * <code>roots</code> subset.
     */
    private  final ArrayList shows   = new ArrayList();
    /**
     * Suppress display of found dependencies that are under
     * the provided package prefixes subset.
     */
    private  final ArrayList hides   = new ArrayList();
    /**
     * Container for found dependency classes.
     */
    private final ArrayList results = new ArrayList();

    /**
     * No argument constructor. The user must fill in the
     * appropriate fields prior to asking for the processing
     * of dependencies.
     */
    public ClassDep() {
    }

    /**
     * Constructor that takes command line arguments and fills in the
     * appropriate fields. See the description of this class
     * for a list and description of the acceptable arguments.
     */
    public ClassDep(String[] cmdLine){
	setupOptions(cmdLine);
    }

    /**
     * Take the given argument and add it to the provided container.
     * We make sure that each inserted package-prefix is unique. For
     * example if we had the following packages:
     * <ul>
     *     <li>a.b
     *     <li>a.bx
     *     <li>a.b.c
     * </ul>
     * Looking for <code>a.b</code> should not match 
     * <code>a.bx</code> and <code>a.b</code>, 
     * just <code>a.b</code>.
     * 
     * @param arg  the package-prefix in string form
     * @param elts container to add elements to
     *
     */
    private static void add(String arg, ArrayList elts) {
	if (!arg.endsWith("."))
	    arg = arg + '.';
	if (".".equals(arg))
	    arg = null;
	elts.add(arg);
    }

    /**
     * See if the provided name is covered by package prefixes.
     *
     * @param n    the name
     * @param elts the list of package prefixes
     *
     * @return the length of the first matching package prefix
     * 
     */
    private static int matches(String n, ArrayList elts) {
	for (int i = 0; i < elts.size(); i++) {
	    String elt = (String)elts.get(i);
	    /*
	     * If we get a null element then see if we are looking
	     * at an anonymous package.
	     */
	    if (elt == null) {
		int j = n.indexOf('.');
		/*
		 * If we did not find a dot, or we have a space 
		 * at the beginning then we have an anonymous package.
		 */
		if (j < 0 || n.charAt(j + 1) == ' ')
		    return 0;
	    } else if (n.startsWith(elt))
		return elt.length();
	}
	return -1;
    }

    /**
     * See if a name is covered by in but not excluded by out.
     *
     * @param n   the name
     * @param in  the package prefixes to include
     * @param out the package prefixes to exclude
     * @return true if covered by in but not excluded by out
     */
    private static boolean matches(String n, ArrayList in, ArrayList out) {
	int i = in.isEmpty() ? 0 : matches(n, in);
	return i >= 0 && matches(n, out) < i;
    }

    /**
     * Recursively traverse a given path, finding all the classes that
     * make up the set to work with. We take into account skips,
     * prunes, and out sets defined.
     *
     * @param path path to traverse down from
     *
     */
    private void traverse(String path) {
	String apath = path;
	/*
 	 * We append File.separator to make sure that the path
	 * is unique for the matching that we are going to do
	 * next. 
	 */
	if (!apath.startsWith(File.separator))
	    apath = File.separator + apath;
	for (int i = 0; i < prunes.size(); i++) {
	    /*
	     * If we are on a root path that needs to be 
	     * pruned leave this current recursive thread.
	     */
	    if (apath.endsWith((String)prunes.get(i)))
		return;
	}

	/*
 	 * Get the current list of files at the current directory 
	 * we are in. If there are no files then leave this current
	 * recursive thread.
	 */
	String[] files = new File(path).list();
	if (files == null)
	    return;
    outer:
	/*
 	 * Now, take the found list of files and iterate over them.
	 */
	for (int i = 0; i < files.length; i++) {
	    String file = files[i];
	    /*
	     * Now see if we have a ".class" file.
	     * If we do not then we lets call ourselves again.
	     * The assumption here is that we have a directory. If it
	     * is a class file we would have already been throw out
	     * by the empty directory contents test above.
	     */
	    if (!file.endsWith(".class")) {
		traverse(path + File.separatorChar + file);
	    } else {
		/*
		 * We have a class file, so remove the ".class" from it
		 * using the pattern:
		 * 
		 *     directory_name + File.Separator + filename = ".class"
		 *
		 * At this point the contents of the skip container follow
		 * the pattern of:
		 *
		 *     "File.Separator+DirectoryPath"
		 *
		 * with dots converted to File.Separators
		 */
		file = apath + File.separatorChar +
		       file.substring(0, file.length() - 6);
		/*
		 * See if there are any class files that need to be skipped.
		 */
		for (int j = 0; j < skips.size(); j++) {
		    String skip = (String)skips.get(j);
		    int k = file.indexOf(skip);
		    if (k < 0)
			continue;//leave this current loop.
		    k += skip.length();
		    /*
		     * If we matched the entire class or if we have
		     * a class with an inner class, skip it and go
		     * on to the next outer loop.
		     */
		    if (file.length() == k || file.charAt(k) == '$')
			continue outer;
		}
		/*
		 * things to do:
		 * prune when outside.
		 * handle inside when its empty.
		 *
		 * Now see if we have classes within our working set "in".
		 * If so add them to our working list "classes".
		 */
		for (int j = 0; j < inside.size(); j++) {
		    int k = file.indexOf(File.separatorChar +
					 ((String)inside.get(j)).replace(
						     '.', File.separatorChar));
		    if (k >= 0) {
				/*
				 * Insert the class and make sure to replace
				 * File.separators into dots.
				 */
			classes.add(file.substring(k + 1).replace(
						   File.separatorChar, '.'));
		    }
		}
	    }
	}
    }

    /**
     * Depending on the part of the class file
     * that we are on the class types that we are
     * looking for can come in several flavors.
     * They can be embedded in arrays, they can
     * be labeled as Identifiers, or they can be
     * labeled as Types. This method handles 
     * Types referenced by Identifiers. It'll take
     * the Type and proceed to get its classname 
     * and then continue with the processing it
     * for dependencies.
     */
    private void process(Identifier from, Type type) {
	while (type.isType(Constants.TC_ARRAY))
	    type = type.getElementType();
	if (type.isType(Constants.TC_CLASS))
	    process(from, type.getClassName(), false);
    }

    /**
     * Depending on the part of the class file
     * that we are on the class types that we are
     * looking for can come in several flavors.
     * This method handles Identifiers and 
     * Identifiers referenced from other Identifiers.
     * <p>
     * Several actions happen here with the goal of 
     * generating the list of dependencies within the domain
     * space provided by the user.
     * These actions are:
     * <ul>
     *    <li> printing out "-tell" output if user asks for it.
     *    <li> extracting class types from the class file.
     *         <ul>
     *             <li> either in arrays or by
     *             <li> themselves
     *         </ul>
     *    <li> noting classes we have already seen.
     *    <li> traversing the remainder of the class file.
     *    <li> resolving and looking for dependencies in
     *         inner classes.
     *    <li> saving found results for later use.
     * </ul>
     *
     * @param from the Identifier referenced from <code>id</code>
     * @param id   the Identifier being looked at
     * @param top ignored
     */
    private void process(Identifier from, Identifier id, boolean top) {
	/*
 	 * If <code>from</code> is not null see if the "id" that
	 * references it is in our "tells" container. If there
	 * is a match show the class. This is for debugging purposes,
	 * in case you want to find out what classes use a particular class.
	 */	
	if (from != null) {
	    for (int i = 0; i < tells.size(); i++) {
		if (id.toString().equals((String)tells.get(i))) {
		    if (tells.size() > 1)
			print("classdep.cause", id, from);
		    else
			print("classdep.cause1", from);
		}
	    }
	}

	/*
 	 * Having taken care of the "-tells" switch, lets
	 * proceed with the rest by getting the id's string
	 * representation.
	 */	
	String n = id.toString();

	/*
 	 * Remove any array definitions so we can get to the
	 * fully qualified class name that we are seeking.
	 */
	if (n.charAt(0) == '[') {
	    int i = 1;
	    while (n.charAt(i) == '[')
		i++;
	    /*
	     * Now that we have removed possible array information 
	     * see if we have a Class definition e.g Ljava/lang/Object;.
	     * If so, remove the 'L' and ';' and call ourselves
	     * with this newly cleaned up Identifier.
	     */
	    if (n.charAt(i) == 'L')
		process(from,
			Identifier.lookup(n.substring(i + 1, n.length() - 1)),
			false);
	    /*
	     * Pop out of our recursive path, since the real work
	     * is being down in another recursive thread.
	     */
	    return;
	}

	/*
 	 * If we have already seen the current Identifier, end this
	 * thread of recursion.
	 */	
	if (seen.contains(id))
	    return;

	/*
 	 * See if we have an empty set OR the Identifier is in our
	 * "inside" set and the matched Identifier is not on the
	 * "outside" set.
	 *
	 * If we are not in the "inside" set and we are not asking
	 * for edges then pop out of this recursive thread.
	 */	
	boolean in = matches(n, inside, outside);
	if (!in && !edges)
	    return;

	/*
 	 * We have an actual Identifier, so at this point mark it
	 * as seen, so we don't create another recursive thread if
	 * we see it again.
	 */	
	seen.add(id);

	/*
	 * This is the test that decides whether this current
	 * Identifier needs to be added to the list of dependencies
	 * to save.
	 *
 	 * "in" can be true in the following cases:
	 * <ul>
	 *   <li>the in set is empty
	 *   <li>the Identifier is in the "in" set and not on the "out" set.
	 * </ul>
	 */       
	if (in != edges && matches(n, shows, hides))
	    results.add(Type.mangleInnerType(id).toString());

	/*
 	 * If we are not in the "inside" set and we want edges
	 * pop out of our recursive thread.
	 */	
	if (!in && edges)
	    return;

	/*
 	 * At this point we have either added an Identifier
	 * to our save list, or we have not. In either case
	 * we need get the package qualified name of this so
	 * we can see if it has any nested classes.
	 */	
	id = env.resolvePackageQualifiedName(id);
	BinaryClass cdef;
	try {
	    cdef = (BinaryClass)env.getClassDefinition(id);
	    cdef.loadNested(env);
	} catch (ClassNotFound e) {
	    print("classdep.notfound", id);
	    return;
	} catch (IllegalArgumentException e) {
	    print("classdep.illegal", id, e.getMessage());
	    return;
	} catch (Exception e) {
	    print("classdep.failed", id);
	    e.printStackTrace();
	    return;
	}

	/*
 	 * If the user asked to keep the outer parent for an
	 * inner class then we'll get the list of dependencies
	 * the inner class may have and iterate over then by
	 * "processing" them as well.
	 */
	Identifier outer = null;
	if (ignoreOuter && cdef.isInnerClass() && cdef.isStatic())
	    outer = cdef.getOuterClass().getName();
	for (Enumeration deps = cdef.getDependencies();
	     deps.hasMoreElements(); )
	{
	    Identifier dep = ((ClassDeclaration)deps.nextElement()).getName();
	    /*
	     * If we dont' want the outer parent class of an inner class
	     * make this comparison.
	     */
	    if (outer != dep)
		process(id, dep, false);
	}


	/*
 	 * Now we are going to walk the rest of the class file and see
	 * if we can find any other class references.
	 */
	for (MemberDefinition mem = cdef.getFirstMember();
	     mem != null;
	     mem = mem.getNextMember())
	{
	    if (mem.isVariable()) {
		process(id, mem.getType());
	    } else if (mem.isMethod() || mem.isConstructor()) {
		Type[] args = mem.getType().getArgumentTypes();
		for (int i = 0; i < args.length; i++) {
		    process(id, args[i]);
		}
		process(id, mem.getType().getReturnType());
	    }
	}
    }

    private static class Compare implements Comparator {
	public int compare(Object o1, Object o2) {
	    if (o1 == null)
		return o2 == null ? 0 : 1;
	    else
		return o2 == null ? -1 : ((Comparable) o2).compareTo(o1);
	}
    }

    /**
     * Method that takes the user provided switches that
     * logically define the domain in which to look for
     * dependencies. 
     */
    public String[] compute() {
	/* sort ins and outs, longest first */
	Comparator c = new Compare();
	Collections.sort(inside, c);
	Collections.sort(outside, c);
	Collections.sort(shows, c);
	Collections.sort(hides, c);
	/*
 	 * Create the environment from which we are going to be
	 * loading classes from.
	 */
	env = new Env(classpath);

	/*
 	 * Traverse the roots i.e the set of handed directories.
	 */
	for (int i = 0; i < roots.size(); i++) {
	    /*
	     * Get the classes that we want do to dependency checking on.
	     */
	    traverse((String)roots.get(i));
	}
	for (int i = 0; i < classes.size(); i++) {
	    process(null, Identifier.lookup((String)classes.get(i)), true);
	}
	if (!tells.isEmpty())
	    return new String[0];
	String[] vals = (String[])results.toArray(new String[results.size()]);
	Arrays.sort(vals);
	return vals;
    }

    /**
     * Print out the usage for this utility.
     */
    public static void usage() {
	print("classdep.usage", null);
    }

    /**
     * Set the classpath to use for finding our class definitions.
     */
    public void setClassPath(String classpath) {
	this.classpath = classpath;
    }

    /**
     * Determines how to print out the fully qualified
     * class names. If <code>true</code> it will use
     * <code>File.separator</code>, else <code>.</code>'s
     * will be used. 
     * If not set the default is <code>false</code>.
     */
    public void setFiles(boolean files) {
	this.files = files;
    }

    /**
     * Add an entry into the set of package prefixes that
     * are to remain hidden from processing.
     */
    public void addHides(String packagePrefix) {
	add(packagePrefix, hides);
    }

    /**
     * Add an entry into the working set of package prefixes 
     * that will make up the working domain space.
     */
    public void addInside(String packagePrefix) {
	add(packagePrefix, inside);
    }

    /**
     * Determines whether to include package references
     * that lie outside the declared set of interest.
     * <p>
     * If true edges will be processed as well, else
     * they will be ignored. If not set the default
     * will be <code>false</code>.
     * <p>
     * <b>Note:</b> These edge classes must included
     * in the classpath for this utility.
     */
    public void setEdges(boolean edges) {
	this.edges = edges;
    }

    /**
     * Add an entry into the set of package prefixes
     * that will bypassed during dependency checking.
     * These entries should be subsets of the contents
     * on the inside set.
     */
    public void addOutside(String packagePrefix) {
	add(packagePrefix, outside);
    }

    /**
     * Add an entry into the set of package prefixes
     * that will be skipped as part of the dependency
     * generation.
     */
    public void addPrune(String packagePrefix) {
	String arg = packagePrefix;
	if (arg.endsWith(".")) 
	    arg = arg.substring(0, arg.length() - 1);
	/*
	 * Convert dots into File.separator for later usage.
	 */
	arg = File.separator + arg.replace('.', File.separatorChar);
	prunes.add(arg);
    }

    /**
     * Add an entry into the set of package prefixes
     * that we want to display.
     * This applies only to the final output, so this
     * set should be a subset of the inside set with
     * edges, if that was indicated.
     */
    public void addShow(String packagePrefix) {
	add(packagePrefix, shows);
    }

    /**
     * Add an entry into the set of classes that
     * should be skipped during dependency generation.
     */
    public void addSkip(String packagePrefix){
	String arg = packagePrefix;
	if (arg.endsWith("."))
	    arg = arg.substring(0, arg.length() - 1);
	else
	    seen.add(Identifier.lookup(arg));
	/*
	 * Convert dots into File.separator for later usage.
	 */
	arg = File.separator + arg.replace('.', File.separatorChar);
	skips.add(arg);
    }

    /**
     * Add an entry in to the set of classes whose dependents
     * that lie with the inside set are listed. This in
     * the converse of the rest of the utility and is meant
     * more for debugging purposes.
     */
    public void addTells(String packagePrefix) {
	tells.add(packagePrefix);
    }

    /**
     * Add an entry into the set of directories to
     * look under for the classes that fall within
     * the working domain space as defined by the
     * intersection of the following sets:
     * inside,outside,prune,show, and hide.
     */
    public void addRoots(String rootName) {
	if (rootName.endsWith(File.separator))
	    //remove trailing File.separator
	    rootName = rootName.substring(0, rootName.length() - 1);
	//these are directories.
	roots.add(rootName);
    }

    /**
     * Add an entry into the set of classes that
     * dependencies are going to be computed on.
     */
    public void addClasses(String className) {
	classes.add(className);
    }

    /**
     * If true classnames will be separated using
     * File.separator, else it will use dots.
     */
    public boolean getFiles() {
	return files;
    }

    /**
     * Accessor method for the found dependencies.
     */
    public String[] getResults() {
	String[] vals = (String[])results.toArray(new String[results.size()]);
	Arrays.sort(vals);
	return vals;
    }

    /**
     * Convenience method for initializing an instance with specific
     * command line arguments. See the description of this class
     * for a list and description of the acceptable arguments.
     */
    public void setupOptions(String[] args) {
	for (int i = 0; i < args.length ; i++ ) {
	    String arg = args[i];
	    if (arg.equals("-cp")) {
		i++;
		setClassPath(args[i]);
	    } else if (arg.equals("-files")) {
		setFiles(true);
	    } else if (arg.equals("-hide")) {
		i++;
		addHides(args[i]);
	    } else if (arg.equals("-in")) {
		i++;
		addInside(args[i]);
	    } else if (arg.equals("-edges")) {
		setEdges(true);
	    } else if (arg.equals("-out")) {
		i++;
		addOutside(args[i]);
	    } else if (arg.equals("-outer")) {
		ignoreOuter = false;
	    } else if (arg.equals("-prune")) {
		i++;
		addPrune(args[i]);
	    } else if (arg.equals("-show")) {
		i++;
		addShow(args[i]);
	    } else if (arg.equals("-skip")) {
		i++;
		addSkip(args[i]);
	    } else if (arg.equals("-tell")) {
		i++;
		addTells(args[i]);
	    } else if (arg.indexOf(File.separator) >= 0) {
		addRoots(arg);
	    } else if (arg.startsWith("-")) {
		usage();
	    } else {
		addClasses(arg);
	    }
	}

    }

    private static ResourceBundle resources;
    private static boolean resinit = false;

    /**
     * Get the strings from our resource localization bundle.
     */
    private static synchronized String getString(String key) {
	if (!resinit) {
	    resinit = true;
	    try {
		resources = ResourceBundle.getBundle
		    ("com.sun.jini.tool.resources.classdep");
	    } catch (MissingResourceException e) {
		e.printStackTrace();
	    }
	}
	if (resources != null) {
	    try {
		return resources.getString(key);
	    } catch (MissingResourceException e) {
	    }
	}
	return null;
    }

    /**
     * Print out string according to resourceBundle format.
     */
    private static void print(String key, Object val) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	System.err.println(MessageFormat.format(fmt, new Object[]{val}));
    }

    /**
     * Print out string according to resourceBundle format.
     */
    private static void print(String key, Object val1, Object val2) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0} {1}";
	System.err.println(MessageFormat.format(fmt,
						new Object[]{val1, val2}));
    }

    /**
     * Command line interface for generating the list of classes that
     * a set of classes depends upon. See the description of this class
     * for a list and description of the acceptable arguments.
     */
    public static void main(String[] args) {
	if (args.length == 0) {
	    usage();
	    return;
	}
	ClassDep dep = new ClassDep();
	//boolean files = false;
	dep.setupOptions(args);
	String[] vals = dep.compute();
	for (int i = 0; i < vals.length; i++) {
	    if (dep.getFiles())
		System.out.println(vals[i].replace('.', File.separatorChar) +
				   ".class");
	    else
		System.out.println(vals[i]);
	}
    }

    /**
     * Private class to load classes, resolve class names and report errors. 
     *
     * @see sun.tools.java.Environment
     */
    private static class Env extends Environment {
	private final ClassPath noPath = new ClassPath("");
	private final ClassPath path;
	private final HashMap packages = new HashMap();
	private final HashMap classes = new HashMap();

	public Env(String classpath) {
	    for (StringTokenizer st =
		     new StringTokenizer(System.getProperty("java.ext.dirs"),
					 File.pathSeparator);
		 st.hasMoreTokens(); )
	    {
		String dir = st.nextToken();
		String[] files = new File(dir).list();
		if (files != null) {
		    if (!dir.endsWith(File.separator)) {
			dir += File.separator;
		    }
		    for (int i = files.length; --i >= 0; ) {
			classpath =
			    dir + files[i] + File.pathSeparator + classpath;
		    }
		}
	    }
	    path = new ClassPath(System.getProperty("sun.boot.class.path") +
				 File.pathSeparator + classpath);
	}

	/**
 	 * We don't use flags so we override Environments and
	 * simply return 0.
	 */
	public int getFlags() {
	    return 0;
	}
	/**
	 * Take the identifier and see if the class that represents exists.
	 * <code>true</code> if the identifier is found, <code>false</code>
	 * otherwise.
	 */
	public boolean classExists(Identifier id) {
	    if (id.isInner())
		id = id.getTopName();
	    Type t = Type.tClass(id);
	    try {
		ClassDeclaration c = (ClassDeclaration)classes.get(t);
		if (c == null) {
		    Package pkg = getPackage(id.getQualifier());
		    return pkg.getBinaryFile(id.getName()) != null;
		}
		return c.getName().equals(id);
	    } catch (IOException e) {
		return false;
	    }
	}

	public ClassDeclaration getClassDeclaration(Identifier id) {
	    return getClassDeclaration(Type.tClass(id));
	}

	public ClassDeclaration getClassDeclaration(Type t) {
	    ClassDeclaration c = (ClassDeclaration)classes.get(t);
	    if (c == null) {
		c = new ClassDeclaration(t.getClassName());
		classes.put(t, c);
	    }
	    return c;
	}

	public Package getPackage(Identifier pkg) throws IOException {
	    Package p = (Package)packages.get(pkg);
	    if (p == null) {
		p = new Package(noPath, path, pkg);
		packages.put(pkg, p);
	    }
	    return p;
	}

	BinaryClass loadFile(ClassFile file) throws IOException {
	    DataInputStream in =
		new DataInputStream(new BufferedInputStream(
						     file.getInputStream()));
	    try {
		return BinaryClass.load(new Environment(this, file), in,
					ATT_ALLCLASSES);
	    } catch (ClassFormatError e) {
		throw new IllegalArgumentException("ClassFormatError: " +
						   file.getPath());
	    } catch (Exception e) {
		e.printStackTrace();
		return null;
	    } finally {
		in.close();
	    }
	}

	/**
 	 * Overridden method from Environment
	 */
	public void loadDefinition(ClassDeclaration c) {
	    Identifier id = c.getName();
	    if (c.getStatus() != CS_UNDEFINED)
		throw new IllegalArgumentException("No file for: " + id);
	    Package pkg;
	    try {
		pkg = getPackage(id.getQualifier());
	    } catch (IOException e) {
		throw new IllegalArgumentException("IOException: " +
						   e.getMessage());
	    }
	    ClassFile file = pkg.getBinaryFile(id.getName());
	    if (file == null)
		throw new IllegalArgumentException("No file for: " +
						   id.getName());
	    BinaryClass bc;
	    try {
		bc = loadFile(file);
	    } catch (IOException e) {
		throw new IllegalArgumentException("IOException: " +
						   e.getMessage());
	    }
	    if (bc == null)
		throw new IllegalArgumentException("No class in: " +
						   file);
	    if (!bc.getName().equals(id))
		throw new IllegalArgumentException("Wrong class in: " +
						   file);
	    c.setDefinition(bc, CS_BINARY);
	    bc.loadNested(this, ATT_ALLCLASSES);
	}

	private static ResourceBundle resources;
	private static boolean resinit = false;

	/**
	 * Get the strings from javac resource localization bundle.
	 */
	private static synchronized String getString(String key) {
	    if (!resinit) {
		try {
		    resources = ResourceBundle.getBundle
			("sun.tools.javac.resources.javac");
		    resinit = true;
		} catch (MissingResourceException e) {
		    e.printStackTrace();
		}
	    }
	    if (key.startsWith("warn."))
		key = key.substring(5);
	    try {
		return resources.getString("javac.err." + key);
	    } catch (MissingResourceException e) {
		return null;
	    }
	}

	public void error(Object source,
			  long where,
			  String err,
			  Object arg1,
			  Object arg2,
			  Object arg3)
	{
	    String fmt = getString(err);
	    if (fmt == null)
		fmt = "no text found: \"" + err + "\" {0} {1} {2}";
	    output(MessageFormat.format(fmt, new Object[]{arg1, arg2, arg3}));
	}

	public void output(String msg) {
	    System.err.println(msg);
	}
    }
}
