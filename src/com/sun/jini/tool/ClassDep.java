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

import com.sun.jini.tool.classdepend.ClassDepend;
import com.sun.jini.tool.classdepend.ClassDependParameters;
import com.sun.jini.tool.classdepend.ClassDependParameters.CDPBuilder;
import com.sun.jini.tool.classdepend.ClassDependencyRelationship;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
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
 * <dt><b><var>directory</var></b>
 * <dd>This option specifies the root directory of a tree of compiled class
 * files that are considered as root for the dependency checking. This option
 * can be specified zero or more times. The actual behavior depends on whether
 * the <code>-newdirbehavior</code> options is specified. The directory must
 * contain at least one filename separator character and be one of the
 * directories specified in <var><b>input_classpath</b></var>, or when the old
 * behavior is effective it can be a subdirectory of one of the directories on
 * the <var><b>input_classpath</b></var>. Each class in the tree needs to be in
 * a package that is defined to be "inside" the graph (as described further
 * below).
 * <p>
 * When the <code>-newdirbehavior</code> options is set the <code>-inroot</code>
 * and <code>-outroot</code> options can be used to include/exclude particular
 * subtrees from the potential set of roots. When the old behavior is effective
 * all classes are considered as root, but can be excluded through the
 * <code>-prune</code> option.
 * <p>
 * <dl>
 * <dt><b><code>-inroot</code> <var>package-prefix</var></b> (only valid with
 * <code>-newdirbehavior</code>)
 * <dd>Specifies a package namespace to include when selecting roots from the
 * directory trees. Any classes found in this package or a subpackage of it are
 * considered as root for the dependency checking, unless they are explicitly
 * excluded using <code>-out</code>, <code>-skip</code> or <code>-outroot</code>
 * options. If not specified all found classes are considered roots. This option
 * can be specified zero or more times. Note that the argument to
 * <code>-inroot</code> is a package namespace (delimited by "."), not a
 * directory.</dd>
 * </dl>
 * <p>
 * <dl>
 * <dt><b><code>-outroot</code> <var>package-prefix</var></b> (only valid with
 * <code>-newdirbehavior</code>)
 * <dd>Specifies a package namespace to exclude when selecting roots from the
 * directory trees. Within the directory trees as specified by the
 * <code>rootdir</code> element, any classes that are in the given package or a
 * subpackage of it are not treated as roots. This option can be specified zero
 * or more times. Note that the argument to <code>-outroot</code> is a package
 * namespace (delimited by "."), not a directory.</dd>
 * </dl>
 * <p>
 * <dl>
 * <dt><b><code>-prune</code> <var>package-prefix</var></b> (old behavior only)
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
 * lexically enclosing class are ignored (except when the static nested class
 * extends its outer class), to avoid inadvertent inclusion of the enclosing
 * class. (The default is chosen this way because the compiled class file of a
 * static nested class always contains a reference to the immediate lexically
 * enclosing class.) This option causes all such references to be considered
 * rather than ignored.  Note that this option is needed very infrequently.</dd>
 * </dl>
 * <p>
 * <dt><b><code>-newdirbehavior</code></b>
 * <dd>This option causes the utility to select classes, to serve as root for
 * the dependency checking, from the directory argument based on the
 * <code>-inroot</code> and <code>-outroot</code> options specified. When
 * this option is set subdirectories of the specified directory are no longer
 * considered as root for finding classes. When this option is not set, the
 * default, the utility maintains the old behavior with respect to the semantics
 * for the directories passed in and the <code>-prune</code> option must be
 * used. You are advised to set this option as there are some edge cases for
 * which the old behavior won't work as expected, especially when no
 * <code>-in</code> options are set.</dd>
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
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
 *      -cp <var><b>install_dir</b></var>/classes \
 *      com.sun.jini.reggie.RegistrarProxy com.sun.jini.reggie.RegistrarImpl_Stub \
 *      -in com.sun.jini -in net.jini \
 *      -files
 * </pre></blockquote>
 * <p>
 * The following example computes the classes required for a classpath JAR
 * file, starting with all of the classes in a directory as roots, and
 * displays them in class name format. (A more complete example would exclude
 * platform classes such as those in <code>jsk-platform.jar</code>.)
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
 *      -cp <var><b>install_dir</b></var>/classes \
 *      <var><b>install_dir</b></var>/classes/com/sun/jini/reggie \
 *      -in com.sun.jini -in net.jini
 * </pre></blockquote>
 * <p>
 * The following example computes the <code>com.sun.jini</code> classes used
 * by a service implementation, and displays the <code>net.jini</code>
 * classes that are immediately referenced by those classes.
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
 *      -cp <var><b>install_dir</b></var>/classes \
 *      com.sun.jini.reggie.RegistrarImpl \
 *      -in com.sun.jini \
 *      -edges \
 *      -show net.jini
 * </pre></blockquote>
 * <p>
 * The following example computes all of the classes used by a service
 * implementation that are not part of the Java 2 SDK, and displays the
 * classes that directly reference the class <code>java.awt.Image</code>.
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
 *      -cp <var><b>install_dir</b></var>/classes \
 *      com.sun.jini.reggie.RegistrarImpl \
 *      -out java -out javax \
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
 * java -jar <var><b>install_dir</b></var>/lib/classdep.jar \
 *      -cp <var><b>install_dir</b></var>/classes \
 *      -in net.jini -out net.jini.core -in com.sun.jini \
 *      <var><b>install_dir</b></var>/classes/net/jini -prune net.jini.core \
 *      -files
 * </pre></blockquote>
 *
 * @author Sun Microsystems, Inc.
 */
public class ClassDep {
    private ClassDepend cd;
     
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
     * 
     * This will have to be implemented in ClassDepend note the above
     * description conflicts with the variable name.
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
     * Indicates whether the root directories specified for finding classes
     * for dependency checking must be traversed in the 'old' way, or that the
     * new behavior must be effective.
     */
    private boolean newRootDirBehavior;
    /**
     * Set of packages to include when classes are found through the specified
     * root directories.
     */
    private  final ArrayList insideRoots  = new ArrayList();
    /**
     * Set of packages to exclude when classes are found through the specified
     * root directories.
     */
    private  final ArrayList outsideRoots  = new ArrayList();
    /**
     * Set of classes to skip over in the processing of dependencies.
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
    private final SortedSet results = new TreeSet();

    /**
     * Indicates whether a failure has been encountered during deep dependency
     * checking.
     */
    private boolean failed;

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
     * @param cmdLine 
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
	if (!arg.endsWith(".")) {
	    arg = arg + '.';
        }
	if (".".equals(arg)) {
	    arg = null;
        }
	elts.add(arg);
    }

    /**
     * Recursively traverse a given path, finding all the classes that
     * make up the set to work with. We take into account skips,
     * prunes, and out sets defined.
     * <p>
     * This implementation is here to maintain the old behavior with regard
     * to how root directories were interpreted.
     *
     * @param path path to traverse down from
     */
    private void traverse(String path) {
	String apath = path;
	/*
 	 * We append File.separator to make sure that the path
	 * is unique for the matching that we are going to do
	 * next. 
	 */
	if (!apath.startsWith(File.separator)) {
	    apath = File.separator + apath;
        }
	for (int i = 0; i < prunes.size(); i++) {
	    /*
	     * If we are on a root path that needs to be 
	     * pruned leave this current recursive thread.
	     */
	    if (apath.endsWith((String)prunes.get(i))) {
		return;
	}
	}

	/*
 	 * Get the current list of files at the current directory 
	 * we are in. If there are no files then leave this current
	 * recursive thread.
	 */
	String[] files_ = new File(path).list();
	if (files_ == null) {
	    return;
        }
    outer:
	/*
 	 * Now, take the found list of files and iterate over them.
	 */
	for (int i = 0; i < files_.length; i++) {
	    String file = files_[i];
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
		    if (k < 0) {
                        continue; 
                    }//leave this current loop.
		    k += skip.length();
		    /*
		     * If we matched the entire class or if we have
		     * a class with an inner class, skip it and go
		     * on to the next outer loop.
		     */
		    if (file.length() == k || file.charAt(k) == '$') {
			continue outer;
		}
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
		    if (inside.get(j) == null) {
			continue;
                    }
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
     * Recursively traverse a given path, finding all the classes that make up
     * the set of classes to work with. We take into account inroot and outroot
     * sets, skips, and the in and out sets defined.
     *
     * @param path path to traverse down from
     * @param rootPath path to the directory that serves as the root for the
     *        class files found, any path component below the root is part of
     *        the full qualified name of the class
     */
    private void traverse(String path, String rootPath) {
	// get the current list of files at the current directory we are in. If
	// there are no files then leave this current recursive thread.
	String[] files_ = new File(path).list();
	if (files_ == null) {
	    return;
        }

	// determine the package name in File.Separators notation
	String packageName = path.substring(rootPath.length(), path.length())
				+ File.separatorChar;
    outer:
	//take the found list of files and iterate over them
	for (int i = 0; i < files_.length; i++) {
	    String file = files_[i];
	    // see if we have a ".class" file. If not then we call ourselves
	    // again, assuming it is a directory, if not the call will return.
	    if (!file.endsWith(".class")) {
		traverse(path + File.separatorChar + file, rootPath);
	    } else {
		// when we have in roots defined verify whether we are inside
		if (!insideRoots.isEmpty()) {
		    boolean matched = false;
		    for (int j = 0; j < insideRoots.size(); j++) {
			if (packageName.startsWith(
				(String) insideRoots.get(j))) {
			    matched = true;
			    break;
			}
		    }
		    if (!matched) {
			continue;
		    }
		}

		// when we have out roots and we are at this level outside we
		// can break the recursion
		if (!outsideRoots.isEmpty()) {
		    for (int j = 0; j < outsideRoots.size(); j++) {
			if (packageName.startsWith(
				(String) outsideRoots.get(j))) {
			    return;
			}
		    }
		}

		// determine the fully qualified class name, but with dots
		// converted to File.Separators and starting with a
		// File.Separators as well
		String className = packageName
		    			+ file.substring(0, file.length() - 6);
		// see if there are any class files that need to be skipped, the
		// skip classes are in the above notation as well
		for (int j = 0; j < skips.size(); j++) {
		    String skip = (String) skips.get(j);
		    if (!className.startsWith(skip)) {
			continue;
		    }
		    // if we matched the entire class or if we have a class with
		    // an inner class, skip it and go on to the next outer loop
		    if (className.length() == skip.length()
			    || className.charAt(skip.length()) == '$') {
			continue outer;
		}
		}

		// we found a class that satisfy all the criteria, convert it
		// to the proper notation
		classes.add(className.substring(1).replace(File.separatorChar,
							   '.'));
	    }
	}
    }

    private static class Compare implements Comparator {
	public int compare(Object o1, Object o2) {
	    if (o1 == null) {
		return o2 == null ? 0 : 1;
            }
	    else {
		return o2 == null ? -1 : ((Comparable) o2).compareTo(o1);
	}
    }
    }

    /**
     * Method that takes the user provided switches that
     * logically define the domain in which to look for
     * dependencies.
     * <p>
     * Whether a failure has occurred during computing the dependent classes
     * can be found out with a call to {@link #hasFailed()}.
     *
     * @return array containing the dependent classes found in the format as
     * specified by the options passed in
     */
    public String[] compute() {
	failed = false;

	if (!newRootDirBehavior) {
	    if (!insideRoots.isEmpty()) {
		failed = true;
		print("classdep.invalidoption", "-newdirbehavior", "-inroot");
	    }
	    if (!outsideRoots.isEmpty()) {
		failed = true;
		print("classdep.invalidoption", "-newdirbehavior", "-outroot");
	    }
	    if (failed) {
		return new String[0];
	    }
	}

	/* sort ins and outs, longest first */
	Comparator c = new Compare();
	Collections.sort(inside, c);
	Collections.sort(outside, c);
	Collections.sort(shows, c);
	Collections.sort(hides, c);

	/*
 	 * Traverse the roots i.e the set of handed directories.
	 */
	for (int i = 0; i < roots.size(); i++) {
	    /*
	     * Get the classes that we want do to dependency checking on.
	     */
	    if (newRootDirBehavior) {
		traverse((String)roots.get(i), (String)roots.get(i));
	    }
	    else {
		traverse((String)roots.get(i));
	    }
	}
        
        // Now use ClassDepend to perform dependency computation
        if (classpath.length() == 0) { classpath = null; }
        try {
            cd = ClassDepend.newInstance(classpath, null, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map classDependencyRelationMap = null;
        try{
            classDependencyRelationMap = cd.getDependencyRelationshipMap(classes, true); // get the reference to Collection<Class>
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }
         
        if (tells.isEmpty()){
            // Here's where we should build the parameter list and call the filter
            //Ready the Parameter Builder for ClassDepend
            // .ignoreOuterParentClass(ignoreOuter) isn't implemented in ClassDepend yet.
            CDPBuilder cdpb = new CDPBuilder();
            ClassDependParameters cdp = cdpb.addOutsidePackagesOrClasses(addClassesRecursively(outside))
                    .addOutsidePackagesOrClasses(skips)
                    .addInsidePackages(addClassesRecursively(inside))
                    .addShowPackages(addClassesRecursively(shows))
                    .addHidePackages(addClassesRecursively(hides))
                    .ignoreOuterParentClass(ignoreOuter)                   
                    .excludePlatformClasses(false)
                    .edges(edges)
                    .build();
            Set result = cd.filterClassDependencyRelationShipMap
                    (classDependencyRelationMap, cdp);
            Iterator itr = result.iterator();
            while (itr.hasNext()) {
                results.add(itr.next().toString());
	}
        }else{
        
            Iterator iter = tells.iterator();
            while (iter.hasNext()){
                String name = (String) iter.next();
                if ( classDependencyRelationMap.containsKey(name)){
                    ClassDependencyRelationship provider = (ClassDependencyRelationship) classDependencyRelationMap.get(name);
                    Set dependants = provider.getDependants();
                    if (!dependants.isEmpty()) {
                        Iterator it = dependants.iterator();
                        while (it.hasNext()) {
                            ClassDependencyRelationship dependant = (ClassDependencyRelationship) it.next();
                            if (tells.size() > 1) {
                                print("classdep.cause", provider, dependant);
        }
                            else {
                                print("classdep.cause1", dependant);
                            }                 
                        }
                    }
                }
            }
        }
        // cannot change the return type or we break the API backward compatibility.
	return (String[])results.toArray(new String[results.size()]);
    }

    /**
     * Add all classes in Packages and subpackages recursively by appending **
     * as per the syntax requirements of the ClassDepend API.
     */
    private List addClassesRecursively(List list){
        for ( int i = 0, l = list.size() ; i < l ; i++){
            list.set(i , list.get(i) + "**");
        }
        return list;
    }
    
    /**
     * Print out the usage for this utility.
     */
    public static void usage() {
	print("classdep.usage", null);
    }

    /**
     * Set the classpath to use for finding our class definitions.
     * @param classpath 
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
     * @param files 
     */
    public void setFiles(boolean files) {
	this.files = files;
    }

    /**
     * Add an entry into the set of package prefixes that
     * are to remain hidden from processing.
     * @param packagePrefix 
     */
    public void addHides(String packagePrefix) {
	add(packagePrefix, hides);
    }

    /**
     * Add an entry into the working set of package prefixes 
     * that will make up the working domain space.
     * @param packagePrefix 
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
     * @param edges 
     */
    public void setEdges(boolean edges) {
	this.edges = edges;
    }

    /**
     * Add an entry into the set of package prefixes
     * that will bypassed during dependency checking.
     * These entries should be subsets of the contents
     * on the inside set.
     * @param packagePrefix 
     */
    public void addOutside(String packagePrefix) {
	add(packagePrefix, outside);
    }

    /**
     * Add an entry into the set of package prefixes
     * that will be skipped as part of the dependency
     * generation.
     * <p>
     * This method has no impact if the new behavior is effective for the
     * interpretation of the root directories for finding class files to
     * include for dependency checking.
     * @param packagePrefix 
     */
    public void addPrune(String packagePrefix) {
	String arg = packagePrefix;
	if (arg.endsWith(".")) {
	    arg = arg.substring(0, arg.length() - 1);
        }
	/*
	 * Convert dots into File.separator for later usage.
	 */
	arg = File.separator + arg.replace('.', File.separatorChar);
	prunes.add(arg);
    }

    /**
     * Controls whether the behavior for finding class files in the specified
     * directories, if any, must be based on the old behavior (the default) or
     * the new behavior that solves some of the problems with the old behavior.
     * @param newBehavior 
     */
    public void setRootDirBehavior(boolean newBehavior) {
	newRootDirBehavior = newBehavior;
    }

    /**
     * Adds an entry into the set of package prefixes for which classes found
     * through the specified root directories will be considered for dependency
     * generation.
     * <p>
     * Adding entries without a call to {@link #setRootDirBehavior(boolean)}
     * with <code>true</code> as the argument will cause {@link #compute()} to
     * fail.
     * @param packagePrefix 
     */
    public void addInsideRoot(String packagePrefix) {
	String arg = packagePrefix;
	if (arg.endsWith(".")) {
	    arg = arg.substring(0, arg.length() - 1);
        }
	/*
	 * Convert dots into File.separator for later usage.
	 */
	if (arg.trim().length() == 0) {
	    arg = File.separator;
        }
	else {
            arg = File.separator + arg.replace('.', File.separatorChar) + File.separator;
        }
	insideRoots.add(arg);
    }

    /**
     * Adds an entry into the set of package prefixes for which classes found
     * through the specified root directories, and that are part of the inside
     * root namespace, will be skipped as part of the dependency generation.
     * <p>
     * Adding entries without a call to {@link #setRootDirBehavior(boolean)}
     * with <code>true</code> as the argument will cause {@link #compute()} to
     * fail.
     * @param packagePrefix 
     */
    public void addOutsideRoot(String packagePrefix) {
	String arg = packagePrefix;
	if (arg.endsWith(".")) {
	    arg = arg.substring(0, arg.length() - 1);
        }
	/*
	 * Convert dots into File.separator for later usage.
	 */
	if (arg.trim().length() == 0) {
	    arg = File.separator;
        }
	else {
            arg = File.separator + arg.replace('.', File.separatorChar) + File.separator;
        }
	outsideRoots.add(arg);
    }

    /**
     * Add an entry into the set of package prefixes
     * that we want to display.
     * This applies only to the final output, so this
     * set should be a subset of the inside set with
     * edges, if that was indicated.
     * @param packagePrefix 
     */
    public void addShow(String packagePrefix) {
	add(packagePrefix, shows);
    }

    /**
     * Add an entry into the set of classes that
     * should be skipped during dependency generation.
     * @param packagePrefix 
     */
    public void addSkip(String packagePrefix){
	String arg = packagePrefix;
	if (arg.endsWith(".")) {
	    arg = arg.substring(0, arg.length() - 1);
        }
        /* No Longer required as ClassDepend will be passed the skips array
	else {
	    seen.add(Identifier.lookup(arg));
        }
         */
        
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
     * @param className 
     */
    public void addTells(String className) {
	tells.add(className);
    }

    /**
     * Add an entry into the set of directories to
     * look under for the classes that fall within
     * the working domain space as defined by the
     * intersection of the following sets:
     * inside,outside,prune,show, and hide.
     * @param rootName 
     */
    public void addRoots(String rootName) {
	if (rootName.endsWith(File.separator)) {
	    //remove trailing File.separator
	    rootName = rootName.substring(0, rootName.length() - 1);
        }
	//these are directories.
	roots.add(rootName);
    }

    /**
     * Add an entry into the set of classes that
     * dependencies are going to be computed on.
     * @param className 
     */
    public void addClasses(String className) {
	classes.add(className);
    }

   /**
     * If true classnames will be separated using
     * File.separator, else it will use dots.
    * @return true or false
     */
    public boolean getFiles() {
	return files;
    }

    /**
     * Accessor method for the found dependencies.
     * @return String[] dependencies
     */
    public String[] getResults() {
	String[] vals = (String[])results.toArray(new String[results.size()]);
	Arrays.sort(vals);
	return vals;
    }

    /**
     * Indicates whether computing the dependent classes as result of the last
     * call to {@link #compute()} resulted in one or more failures.
     *
     * @return <code>true</code> in case a failure has happened, such as a
     * class not being found, <code>false</code> otherwise
     */
    public boolean hasFailed() {
	return failed;
    }

    /**
     * Convenience method for initializing an instance with specific
     * command line arguments. See the description of this class
     * for a list and description of the acceptable arguments.
     * @param args 
     */
    public void setupOptions(String[] args) {
	for (int i = 0; i < args.length ; i++ ) {
	    String arg = args[i];
	    if (arg.equals("-newdirbehavior")) {
		newRootDirBehavior = true;
	    }
	    else if (arg.equals("-cp")) {
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
	    } else if (arg.equals("-inroot")) {
		i++;
		addInsideRoot(args[i]);
	    } else if (arg.equals("-outroot")) {
		i++;
		addOutsideRoot(args[i]);
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
	if (fmt == null) {
	    fmt = "no text found: \"" + key + "\" {0}";
        }
	System.err.println(MessageFormat.format(fmt, new Object[]{val}));
    }

    /**
     * Print out string according to resourceBundle format.
     */
    private static void print(String key, Object val1, Object val2) {
	String fmt = getString(key);
	if (fmt == null) {
	    fmt = "no text found: \"" + key + "\" {0} {1}";
        }
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
	    if (dep.getFiles()) {
                System.out.println(vals[i].replace('.', File.separatorChar) + ".class");
            }
	    else {
		System.out.println(vals[i]);
	}
    }
		    }
	}
