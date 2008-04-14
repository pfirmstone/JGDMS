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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationProvider;

/**
 * Checks the format of the source for a {@link ConfigurationFile}. The source
 * is specified with either a file, URL, or standard input, as well as with
 * override options. The checks include syntax and static type checking, and
 * require access to any application types mentioned in the source. <p>
 *
 * The following items are discussed below:
 *
 * <ul>
 * <li> <a href="#entry_desc">Entry description files</a>
 * <li> {@linkplain #main Command line options}
 * <li> <a href="#examples">Examples for running CheckConfigurationFile</a>
 * </ul> <p>
 *
 * <a name="entry_desc">
 * <h3>Entry description files</h3>
 * </a>
 *
 * Checking of the source can be controlled by specifying one or more entry
 * description files, each listing the names and types of entries that are
 * allowed to appear in the source. Each entry description file is treated as a
 * {@link Properties} source file, where each key is the fully qualified name
 * of an entry (<code><i>component</i>.<i>name</i></code>) and each value
 * specifies the expected type for that entry. Types should be specified in
 * normal source code format, except that whitespace is not permitted between
 * tokens. Types in the <code>java.lang</code> package may be unqualified, but
 * fully qualified names must be used for other types (<code>import</code>
 * statements are not supported).  If any entry description files are supplied,
 * then any public entry that appears in the source being checked, whose fully
 * qualified name does not appear in any entry description file, or whose
 * actual type is not assignable to the expected type, is treated as an
 * error. <p>
 *
 * Entry description files for all of the Apache River release services and utilities
 * are provided in the <code>configentry</code> subdirectory beneath the
 * top-level directory of the Apache River release installation. <p>
 *
 * Here is a sample entry description file:
 *
 * <blockquote>
 * <pre>
 * comp.foo Integer[]
 * comp.bar net.jini.core.constraint.MethodConstraints
 * comp.baz long
 * </pre>
 * </blockquote>
 *
 * Here is an associated sample configuration file:
 *
 * <blockquote>
 * <pre>
 * import net.jini.constraint.*;
 * import net.jini.core.constraint.*;
 * comp {
 *     foo = new Integer[] { new Integer(3) };
 *     bar = new BasicMethodConstraints(
 *               new InvocationConstraints(Integrity.YES, null));
 *     baz = 33L;
 * }
 * </pre>
 * </blockquote> <p>
 *
 * <a name="examples">
 * <h3>Examples for running CheckConfigurationFile</h3>
 * </a>
 *
 * This utility can be run from the {@linkplain #main command line}, or by
 * calling the {@link #check(String, ClassLoader, String[], String,
 * PrintStream)} or {@link #check(ConfigurationFile, Properties, ClassLoader,
 * PrintStream)} methods. <p>
 *
 * An example command line usage is:
 *
 * <blockquote>
 * <pre>
 * java -jar <var><b>install_dir</b></var>/lib/checkconfigurationfile.jar \
 *      -cp <var><b>install_dir</b></var>/lib/norm.jar:<var><b>install_dir</b></var>/lib/jsk-platform.jar \
 *      -entries <var><b>install_dir</b></var>/configentry/norm-transient \
 *      <var><b>your-norm.config</b></var>
 * </pre>
 * </blockquote>
 * 
 * where <var><b>install_dir</b></var> is the directory where the Apache River release
 * is installed, and <var><b>your-norm.config</b></var> is a configuration
 * source file intended for use with the transient {@linkplain
 * com.sun.jini.norm Norm} service implementation. This command will print out
 * any problems that it detects in the configuration file, including entries
 * that are not recognized or have the wrong type for the Norm service.
 *
 * @author Sun Microsystems, Inc.
 * @see ConfigurationFile
 * @since 2.0
 */
public class CheckConfigurationFile  {

    /** The primitive types */
    private static final Class[] primitives = {
	Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE,
	Long.TYPE, Float.TYPE, Double.TYPE
    };

    private static final String RESOURCE =
	"META-INF/services/" + Configuration.class.getName();

    private static ResourceBundle resources;
    private static boolean resinit = false;

    /* This class is not instantiable */
    private CheckConfigurationFile() { }

    /**
     * Command line interface for checking the format of source and override
     * options for a {@link ConfigurationFile}, printing messages to
     * <code>System.err</code> for any errors found. If errors are found,
     * continues to check the rest of the source and overrides, and then calls
     * <code>System.exit</code> with a non-<code>zero</code> argument. <p>
     *
     * The command line arguments are:
     *
     * <pre>
     * [ -cp <var><b>classpath</b></var> ] [ -entries <var><b>entrydescs</b></var> ] <var><b>location</b></var> [ <var><b>option</b></var>... ]
     * </pre>
     * or
     * <pre>
     * [ -cp <var><b>classpath</b></var> ] [ -entries <var><b>entrydescs</b></var> ] -stdin [ <var><b>location</b></var> [ <var><b>option</b></var>... ] ]
     * </pre>
     * or
     * <pre>
     * -help
     * </pre>
     *
     * If the only argument is <code>-help</code>, a usage message is
     * printed. <p>
     *
     * The <var><b>classpath</b></var> value for the <code>-cp</code> option
     * specifies one or more directories and zip/JAR files, separated by the
     * {@linkplain File#pathSeparatorChar path separator character}, where the
     * application classes are located.  A class loader that loads classes from
     * this path will be created, with the extension class loader as its
     * parent. If this option is not specified, the system class loader is used
     * instead. <p>
     *
     * The <var><b>entrydescs</b></var> value for the <code>-entries</code>
     * option specifies one or more entry description files, separated by the
     * path separator character. <p>
     *
     * The <var><b>location</b></var> argument specifies the source file to be
     * checked. If the <code>-stdin</code> option is used, then the actual
     * source data will be read from standard input, and any
     * <var><b>location</b></var> argument is simply used for identification
     * purposes in error messages. <p>
     *
     * The remaining arguments specify any entry override values that should be
     * passed to the <code>ConfigurationFile</code> constructor. <p>
     *
     * The class loader obtained above is used to resolve all expected types
     * specified in the entry description files, and to obtain the
     * configuration provider. The configuration provider class is found from
     * the class loader in the same manner as specified by {@link
     * ConfigurationProvider}.  The resulting class must be {@link
     * ConfigurationFile} or a subclass; if it is a subclass, it must have a
     * public constructor with three parameters of type: {@link Reader},
     * <code>String[]</code>, and {@link ClassLoader}. An instance of the
     * provider is created by passing that constructor a <code>Reader</code>
     * for the source file to be checked, the location and entry override
     * values, and the class loader.
     */
    public static void main(String[] args) {
	if (args.length == 0) {
	    usage();
	} else if (args.length == 1 && "-help".equals(args[0])) {
	    print(System.err, "checkconfig.usage", File.pathSeparator);
	    return;
	}
	String classPath = null;
	String entriesPath = null;
	boolean stdin = false;
	int i = 0;
	while (i < args.length) {
	    if ("-cp".equals(args[i])) {
		if (args.length < i + 2) {
		    usage();
		}
		classPath = args[i + 1];
		i += 2;
	    } else if ("-entries".equals(args[i])) {
		if (args.length < i + 2) {
		    usage();
		}
		entriesPath = args[i + 1];
		i += 2;
	    } else if ("-stdin".equals(args[i])) {
		stdin = true;
		i++;
	    } else {
		break;
	    }
	}
	if (!stdin && i == args.length) {
	    usage();
	}
	ClassLoader loader = ClassLoader.getSystemClassLoader();
	if (classPath != null) {
	    loader = loader.getParent();
	}
	String[] configOptions = new String[args.length - i];
	System.arraycopy(args, i, configOptions, 0, configOptions.length);
	boolean ok = check(classPath, loader, stdin, configOptions,
			   entriesPath, System.err);
	if (!ok) {
	    System.exit(1);
	}
    }

    private static void usage() {
	print(System.err, "checkconfig.usage", File.pathSeparator);
	System.exit(1);
    }

    /**
     * Returns information about the specified permitted entries, returning
     * null if there is a problem loading properties from the files.
     */
    private static Properties getEntries(String files, PrintStream err) {
	Properties entries = new Properties();
	StringTokenizer tokens =
	    new StringTokenizer(files, File.pathSeparator);
	while (tokens.hasMoreTokens()) {
	    String file = tokens.nextToken();
	    InputStream in = null;
	    try {
		in = new BufferedInputStream(new FileInputStream(file));
		entries.load(in);
	    } catch (FileNotFoundException e) {
		print(err, "checkconfig.notfound", file);
		return null;
	    } catch (Throwable t) {
		print(err, "checkconfig.read.err",
		      new String[]{file, t.getClass().getName(),
				   t.getLocalizedMessage()});
		t.printStackTrace(err);
		return null;
	    } finally {
		if (in != null) {
		    try {
			in.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}
	return entries;
    }

    /**
     * Checks the format of a configuration source file. Returns
     * <code>true</code> if there are no errors, and <code>false</code>
     * otherwise. <p>
     *
     * The <code>classPath</code> argument specifies one or more directories
     * and zip/JAR files, separated by the {@linkplain File#pathSeparatorChar
     * path separator character}, where the application classes are located.  A
     * class loader that loads classes from this path will be created, with
     * <code>loader</code> as its parent. The <code>ConfigurationFile</code> is
     * created with this class loader, and all expected types specified in
     * entry description files are resolved in this class loader. If
     * <code>classPath</code> is <code>null</code>, then <code>loader</code> is
     * used instead. <p>
     *
     * The class loader is used to resolve all expected types specified in the
     * entry description files, and to obtain the configuration provider. The
     * configuration provider class is found from the class loader in the same
     * manner as specified by {@link ConfigurationProvider}.  The resulting
     * class must be {@link ConfigurationFile} or a subclass; if it is a
     * subclass, it must have a public constructor with three parameters of
     * type: {@link Reader}, <code>String[]</code>, and {@link
     * ClassLoader}. An instance of the provider is created by passing that
     * constructor a <code>Reader</code> for the source file to be checked,
     * the location and entry override values, and the class loader.
     *
     * @param classPath the search path for application classes, or
     * <code>null</code> to use the specified class loader
     * @param loader the parent class loader to use for application classes if
     * <code>classPath</code> is not <code>null</code>, otherwise the class
     * loader to use for resolving application classes
     * @param configOptions the configuration source file to check, plus any
     * entry overrides
     * @param entriesPath one or more entry description files, separated by the
     * path separator character, or <code>null</code>
     * @param err the stream to use for printing errors
     * @return <code>true</code> if there are no errors, <code>false</code>
     * otherwise
     * @throws NullPointerException if <code>loader</code>,
     * <code>configOptions</code>, or <code>err</code> is <code>null</code>
     * @see #check(ConfigurationFile, Properties, ClassLoader, PrintStream)
     */
    public static boolean check(String classPath,
				ClassLoader loader,
				String[] configOptions,
				String entriesPath,
				PrintStream err)
    {
	if (loader == null || configOptions == null || err == null) {
	    throw new NullPointerException();
	}
	return check(classPath, loader, false, configOptions, entriesPath,
		     err);
    }

    /**
     * Checks the format of a configuration source file, using classes loaded
     * from classPath and loader, requiring entries to match the names and
     * values from entriesPath (unless entriesPath is null).  If stdin is
     * true, reads the configuration source from standard input.
     */
    private static boolean check(String classPath,
				 ClassLoader loader,
				 boolean stdin,
				 String[] configOptions,
				 String entriesPath,
				 PrintStream err)
    {
	if (classPath != null) {
	    StringTokenizer st = new StringTokenizer(classPath,
						     File.pathSeparator);
	    URL[] urls = new URL[st.countTokens()];
	    for (int i = 0; st.hasMoreTokens(); i++) {
		String elt = st.nextToken();
		try {
		    urls[i] = new File(elt).toURI().toURL();
		} catch (MalformedURLException e) {
		    print(err, "checkconfig.classpath", elt);
		    return false;
		}
	    }
	    loader = URLClassLoader.newInstance(urls, loader);
	}
	Properties entries = null;
	if (entriesPath != null) {
	    entries = getEntries(entriesPath, err);
	    if (entries == null) {
		return false;
	    }
	}
	String location;
	if (configOptions.length == 0) {
	    location = "(stdin)";
	} else {
	    configOptions = (String[]) configOptions.clone();
	    location = configOptions[0];
	    configOptions[0] = "-";
	}
	Constructor configCons = getProviderConstructor(loader, err);
	if (configCons == null) {
	    return false;
	}
	try {
	    InputStream in;
	    if (stdin) {
		in = System.in;
	    } else if ("-".equals(location)) {
		in = new ByteArrayInputStream(new byte[0]);
	    } else {
		try {
		    URL url = new URL(location);
		    in = url.openStream();
		} catch (MalformedURLException e) {
		    in = new FileInputStream(location);
		}
	    }
	    Object config;
	    try {
		config = configCons.newInstance(
				  new Object[]{new InputStreamReader(in),
					       configOptions,
					       loader});
	    } finally {
		if (!stdin) {
		    try {
			in.close();
		    } catch (IOException e) {
		    }
		}
	    }
	    return check(config, entries, loader, err);
	} catch (FileNotFoundException e) {
	    print(err, "checkconfig.notfound", location);
	} catch (Throwable t) {
	    print(err, "checkconfig.read", location, t);
	}
	return false;
    }

    /**
     * Returns the (Reader, String[], ClassLoader) constructor for the
     * resource-specified configuration provider in loader.
     */
    private static Constructor getProviderConstructor(ClassLoader loader,
						      PrintStream err)
    {
	URL resource = null;
	try {
	    for (Enumeration providers = loader.getResources(RESOURCE);
		 providers.hasMoreElements(); )
	    {
		resource = (URL) providers.nextElement();
	    }
	} catch (IOException e) {
	    print(err, "checkconfig.resources", "", e);
	}
	String classname = (resource == null ?
			    ConfigurationFile.class.getName() :
			    getProviderName(resource, err));
	if (classname == null) {
	    return null;
	}
	try {
	    Class provider = Class.forName(classname, false, loader);
	    try {
		if (!Class.forName(ConfigurationFile.class.getName(),
				   false, loader).isAssignableFrom(provider))
		{
		    print(err, "checkconfig.notsubclass", classname);
		    return null;
		}
		return provider.getConstructor(new Class[]{Reader.class,
							   String[].class,
							   ClassLoader.class});
	    } catch (ClassNotFoundException e) {
		print(err, "checkconfig.notsubclass", classname);
	    } catch (NoSuchMethodException e) {
		print(err, "checkconfig.noconstructor", classname);
	    }
	} catch (ClassNotFoundException e) {
	    print(err, "checkconfig.noprovider", classname);
	} catch (Throwable t) {
	    print(err, "checkconfig.provider", classname, t);
	}
	return null;
    }

    /**
     * Returns the configuration provider class name specified in the contents
     * of the URL.
     */
    private static String getProviderName(URL url, PrintStream err) {
	InputStream in = null;
	try {
	    in = url.openStream();
	    BufferedReader reader =
		new BufferedReader(new InputStreamReader(in, "utf-8"));
	    String result = null;
	    while (true) {
		String line = reader.readLine();
		if (line == null) {
		    break;
		}
		int commentPos = line.indexOf('#');
		if (commentPos >= 0) {
		    line = line.substring(0, commentPos);
		}
		line = line.trim();
		int len = line.length();
		if (len != 0) {
		    if (result != null) {
			print(err, "checkconfig.multiproviders",
			      url.toString());
			return null;
		    }
		    result = line;
		}
	    }
	    if (result == null) {
		print(err, "checkconfig.missingprovider", url.toString());
		return null;
	    }
	    return result;
	} catch (IOException e) {
	    print(err, "configconfig.read", url.toString(), e);
	    return null;
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Checks the format of a <code>ConfigurationFile</code>. Returns
     * <code>true</code> if there are no errors, and <code>false</code>
     * otherwise.
     *
     * @param config the <code>ConfigurationFile</code> to check
     * @param entries the entry descriptions to use (where each key is a fully
     * qualified entry name and each value is the expected type), or
     * <code>null</code>
     * @param loader the class loader to use for resolving type names used in
     * the entry descriptions
     * @param err the stream to use for printing errors
     * @return <code>true</code> if there are no errors, <code>false</code>
     * otherwise
     * @throws NullPointerException if <code>config</code>,
     * <code>loader</code>, or <code>err</code> is <code>null</code>
     * @see #check(String, ClassLoader, String[], String, PrintStream)
     */
    public static boolean check(ConfigurationFile config,
				Properties entries,
				ClassLoader loader,
				PrintStream err)
    {
	if (config == null || loader == null || err == null) {
	    throw new NullPointerException();
	}
	return check((Object) config, entries, loader, err);
    }

    /**
     * Checks the entries in config against the descriptions in entries,
     * resolving expected types in loader.
     */
    private static boolean check(Object config,
				 Properties entries,
				 ClassLoader loader,
				 PrintStream err)
    {
	Method getType;
	String[] entryNames;
	try {
	    getType = config.getClass().getMethod("getEntryType",
						  new Class[]{String.class,
							      String.class});
	    Method getNames = config.getClass().getMethod("getEntryNames",
							  null);
	    Set entrySet = (Set) getNames.invoke(config, new Object[0]);
	    entryNames =
		(String[]) entrySet.toArray(new String[entrySet.size()]);
	} catch (Throwable t) {
	    print(err, "checkconfig.unexpected", "", t);
	    return false;
	}
	Arrays.sort(entryNames);
	boolean ok = true;
	for (int i = 0; i < entryNames.length; i++) {
	    String entryName = entryNames[i];
	    String expectedTypeName = entries != null
		? entries.getProperty(entryName) : null;
	    if (entries != null && expectedTypeName == null) {
		print(err, "checkconfig.unknown", entryName);
		ok = false;
	    }
	    try {
		int dot = entryName.lastIndexOf('.');
		String component = entryName.substring(0, dot);
		String name = entryName.substring(dot + 1);
		Class type = (Class) getType.invoke(config,
						    new Object[]{component,
								 name});
		if (expectedTypeName != null) {
		    try {
			Class expectedType =
			    findClass(expectedTypeName, loader);
			if (!isAssignableFrom(expectedType, type)) {
			    print(err, "checkconfig.mismatch",
				  new String[]{entryName, typeName(type),
					       typeName(expectedType)});
			    ok = false;
			}
		    } catch (ClassNotFoundException e) {
			print(err, "checkconfig.expect.fail",
			      new String[]{entryName, e.getMessage()});
			ok = false;
		    } catch (Throwable t) {
			print(err, "checkconfig.expect.err",
			      new String[]{entryName, expectedTypeName,
					   t.getClass().getName(),
					   t.getLocalizedMessage()});
			t.printStackTrace(err);
			ok = false;
		    }
		}
	    } catch (Throwable t) {
		print(err, "checkconfig.actual", entryName, t);
		ok = false;
	    }
	}
	return ok;
    }

    /**
     * Returns the type with the specified name, including primitives and
     * arrays, and checking the java.lang package for unqualified names.  This
     * method supports both internal names ('[I', '[Ljava.lang.Integer;',
     * 'java.util.Map$Entry') and source level name ('int[]', 'Integer[]',
     * 'java.util.Map.Entry').
     */
    private static Class findClass(String name, ClassLoader loader)
	throws ClassNotFoundException
    {
	if (name.indexOf('.') < 0) {
	    for (int i = primitives.length; --i >= 0; ) {
		if (name.equals(primitives[i].getName())) {
		    return primitives[i];
		}
	    }
	}
	try {
	    return Class.forName(name, false, loader);
	} catch (ClassNotFoundException notFound) {
	    int bracket = name.indexOf('[');
	    if (bracket > 0) {
		/* Array */
		int dims = 0;
		int len = name.length();
		for (int i = bracket; i < len; i += 2, dims++) {
		    if (name.charAt(i) != '['
			|| i + 1 >= len
			|| name.charAt(i + 1) != ']')
		    {
			/* Invalid array class name */
			throw notFound;
		    }
		}
		try {
		    Class base = findClass(name.substring(0, bracket), loader);
		    return Array.newInstance(base, new int[dims]).getClass();
		} catch (ClassNotFoundException e) {
		}
	    } else if (name.indexOf('.') < 0) {
		/* Try java.lang package */
		try {
		    return findClass("java.lang." + name, loader);
		} catch (ClassNotFoundException e) {
		}
	    } else {
		/* Try substituting '$' for '.' to look for nested classes */
		int dot;
		while ((dot = name.lastIndexOf('.')) >= 0) {
		    name = name.substring(0, dot) + '$' +
			name.substring(dot + 1);
		    try {
			return Class.forName(name, false, loader);
		    } catch (ClassNotFoundException e) {
		    }
		}
	    }
	    throw notFound;
	}
    }

    /** Returns the name of a type, in source code format. */
    private static String typeName(Class type) {
	if (type == null) {
	    return "null";
	}
	StringBuffer buf = new StringBuffer();
	if (type.isArray()) {
	    Class component;
	    while ((component = type.getComponentType()) != null) {
		buf.append("[]");
		type = component;
	    }
	}
	return type.getName().replace('$', '.') + buf;
    }

    /**
     * Checks if an object of type source can be assigned to a variable of type
     * dest, where source is null for a null object.
     */
    private static boolean isAssignableFrom(Class dest, Class source) {
	if (dest.isPrimitive()) {
	    return source == dest;
	} else {
	    return source == null || dest.isAssignableFrom(source);
	}
    }

    /**
     * Returns a message from the resource bundle.
     */
    private static synchronized String getString(String key, PrintStream err) {
	if (!resinit) {
	    try {
		resinit = true;
		resources = ResourceBundle.getBundle(
			       "com.sun.jini.tool.resources.checkconfig");
	    } catch (MissingResourceException e) {
		e.printStackTrace(err);
	    }
	}
	try {
	    return resources != null ? resources.getString(key) : null;
	} catch (MissingResourceException e) {
	    return null;
	}
    }

    /**
     * If t is a ConfigurationException, uses the key keyPrefix+".fail",
     * with source as the value for {0} and the localized message of t
     * as the value for {1}. Otherwise, uses the key keyPrefix+".err",
     * with source as the value for {0}, the exception class name as the
     * value for {1}, and the localized message of t as the value for {2}.
     */
    private static void print(PrintStream err,
			      String keyPrefix,
			      String source,
			      Throwable t)
    {
	if (t instanceof InvocationTargetException) {
	    t = t.getCause();
	}
	if (t.getClass().getName().equals(
				      ConfigurationException.class.getName()))
	{
	    if (t.getCause() == null) {
		print(err, keyPrefix + ".fail",
		      new String[]{source, t.getLocalizedMessage()});
		return;
	    } else {
		t = t.getCause();
	    }
	}
	print(err, keyPrefix + ".err",
	      new String[]{source, t.getClass().getName(),
			   t.getLocalizedMessage()});
	t.printStackTrace(err);
    }

    private static void print(PrintStream err, String key, String val) {
	print(err, key, new String[]{val});
    }

    private static void print(PrintStream err, String key, String[] vals) {
	String fmt = getString(key, err);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	err.println(MessageFormat.format(fmt, vals));
    }
}
