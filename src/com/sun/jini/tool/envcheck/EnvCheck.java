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
package com.sun.jini.tool.envcheck;

import com.sun.jini.resource.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;

import java.lang.reflect.Modifier;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.ResourceBundle;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;

import com.sun.jini.start.ServiceDescriptor;
import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;

import com.sun.jini.tool.envcheck.Reporter.Message;

/**
 * Tool used to perform validity checks on the run-time environment of a client
 * or service. The output of this tool is a report; command-line options
 * control the verbosity and severity level at which report entries are
 * generated. A simple plugin architecture is implemented; a set of plugins
 * implementing a variety of checks is bundled with the tool, and support is
 * provided to allow additional plugins to be supplied by the user.
 * <p>
 * The following items are discussed below:
 * <ul>
 * <li><a href="#running">Running the Tool</a>
 * <li><a href="#processing">Processing Options</a>
 * <li><a href="#examples">Examples</a>
 * <li><a href="#plugins">Bundled Plugins</a>
 * </ul>
 *
 * <a name="running"></a>
 * <h3>Running the Tool</h3>
 *
 * This tool primarily validates the system properties and configuration
 * files used when starting the target client or service. This is accomplished
 * by providing the unmodified command line for launching the component as
 * arguments to the tool. Thus, for a service designed to be run by the
 * service starter having the hypothetical original command line:
 * <blockquote><pre>
 * java -Djava.security.policy=<var><b>my_policy</b></var> \
 *      -jar <var><b>install_dir</b></var>/lib/start.jar mystart.config
 * </pre></blockquote>
 * the simplest invocation of the tool would be:
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/envcheck.jar \
 *      java -Djava.security.policy=<var><b>my_policy</b></var> \
 *           -jar <var><b>install_dir</b></var>/lib/start.jar mystart.config
 * </pre></blockquote>
 * Note that the entire command line, including the <code>java</code> command,
 * is supplied as arguments to the tool. The <code>java</code> command used to
 * run the tool may be different than the <code>java</code> command invoked by
 * the command line under test. The first token in the command line being
 * analyzed must not begin with a '-' and must end with the string "java".
 *
 * <a name="processing"></a>
 * <h3>Processing Options</h3>
 * <p>
 * <dl>
 * <dt><b><code>-traces</code></b>
 * <dd>The implementation of a validity check may detect success or failure by
 *     handling an expected exception. By default, an error message will be
 *     generated in these cases, but the stack trace will be inhibited. This
 *     option is a hint that stack traces are desired. It is the responsibility
 *     of the individual plugin implementation to honor this option.
 * </dd>
 * <p>
 * <dt><b><code>-explain</code></b>
 * <dd>By default, the output of a validity check will be a short message with
 *     enough detail to allow a knowledgeable user to interpret it; however, it
 *     may not be understandable to a novice user. The <code>-explain</code>
 *     option is a hint that may result in the generation of additional output
 *     describing the purpose and context of the check. An explanation is output
 *     the first time its associated message is output, and is not repeated. It
 *     is the responsibility of the individual plugin implementation to honor
 *     this option.
 * </dd>
 * <p>
 * <dt><b><code>-level</code> <var>info|warning|error</var></b>
 * <dd>The tool supports three severity levels for message generation. 
 * <p>
 *     <dl>
 *     <dt><var>info</var>
 *     <dd>'success' messages or other non-failure oriented configuration data
 *     <dt><var>warning</var>
 *     <dd>a condition or value has been detected that is 'legal', but which
 *         could result in unintended behavior. An example is the use of
 *         <code>localhost</code> in a codebase annotation.
 *     <dt><var>error</var>
 *     <dd>a condition has been detected that is likely due to an error
 *         on the command line or in a configuration file. An example is
 *         assigning the value of a non-existant file name to the
 *         <code>java.util.logging.config.file</code> system property.
 *     </dl>
 * <p>
 *     This option is used to set the level at which message records are
 *     generated. The default value is <var>warning</var>.
 * </dd>
 * <p>
 * <dt><b><code>-plugin</code> <var>file</var></b>

 * <dd>Identifies a JAR file containing user supplied plugins that will be run
 *     after the standard plugins are run. All of the necessary support classes
 *     and resources required to support the plugins must be included in this
 *     file. The file must also include a resource named
 *     <code>META-INF/services/com.sun.jini.tool.envcheck.Plugin</code>, which
 *     contains the class names of the plugins to run listed one per line.
 *     Every class listed must implement the
 *     <code>com.sun.jini.tool.envcheck.Plugin</code> interface. This option
 *     may be supplied zero or more times.
 * </dd>
 * <p>
 * <dt><b><code>-security</code></b>
 * <dd>A plugin specific option that is recognized by one of the bundled
 *     plugins.  Specifying this option will activate a number of JAAS and JSSE
 *     checks. User supplied plugins wishing to recognize this option must
 *     implement the <code>isPlugOption</code> method of the 
 *     <code>Plugin</code> interface.
 * </dd>
 * </dl>
 * <p>
 * <a name="examples"></a>
 * <h3>Examples</h3>

 * The following example will analyze a command line used to start a user
 * service. The command being analyzed defines a classpath and two system
 * properties, and names a class containing a <code>main</code> method:

 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/envcheck.jar -level info -explain -traces \
 *      java -cp <var><b>mylib_dir</b></var>/myservice.jar:/jini/lib/jsk-platform.jar \
 *           -Djava.security.policy=<var><b>my_policy</b></var> \
 *           -Djava.server.rmi.codebase=http://myhost/myservice-dl.jar \
 *           myservice.MyServiceImpl
 * </blockquote></pre>
 * In this case, the tool is limited to performing validity checks on the
 * classpath, policy, and codebase values identified by the system properties
 * and options provided on the service command line. The <code>-level</code>,
 * <code>-explain</code>, and <code>-traces</code> options supplied will result
 * in the most verbose output possible from the tool.
 * <p>
 * The following example will analyze a command line used to start reggie using
 * the service starter. The command being analyzed uses a policy and service
 * starter configuration located in the working directory:
 * <p>
 * <blockquote><pre>
 * java -jar <var><b>install_dir</b></var>/lib/envcheck.jar -level error \
 *      java -Djava.security.policy=<var><b>my_starterpolicy</b></var> \
 *           -jar <var><b>install_dir</b></var>/lib/start.jar reggie.config
 * </blockquote></pre>
 * The tool can perform many more checks in this case because the
 * bundled plugins include built-in knowledge about the service starter
 * and its public configuration entries. The tool options used will minimize
 * the output produced by the tool.
 * <p>
 * <a name="plugins"></a>
 * <h3>Bundled Plugins</h3>
 * A set of plugins are loaded automatically by the tool to perform some
 * basic analysis. 
 * <p>
 * If the command line being analyzed invokes the service starter, the tool will
 * create a <code>Configuration</code> from the arguments of the command line
 * being analyzed. Failure to create the <code>Configuration</code> will result
 * in termination of the tool, otherwise the
 * <code>com.sun.jini.start.ServiceDescriptor</code>s provided by the
 * <code>Configuration</code> are examined. The following checks are done for
 * each <code>ServiceDescriptor</code>:
 * <ul>
 *  <li>Verify that the <code>getPolicy</code> method returns a reference to a
 *      policy file that is valid and accessible
 *  <li>Check whether that policy grants <code>AllPermissions</code> to all
 *      protection domains
 * </ul>
 * The following checks are done for each
 * <code>NonActivatableServiceDescriptor</code> and each
 * <code>SharedActivatableServiceDescriptor</code>
 * <ul>
 *  <li>Verify that calling <code>getServerConfigArgs</code> does not return
 *      <code>null</code> or an empty array
 *  <li>Verify that a <code>Configuration</code> can be constructed from those
 *      args
 *  <li>Verify that any entry in that <code>Configuration</code> named
 *      <code>initialLookupGroups</code> does not have a value of
 *      <code>ALL_GROUPS</code>
 *  <li>Verify that the export codebase is defined. For each component in the
 *      export codebase:
 *   <ul>
 *     <li>Verify that the URL is not malformed
 *     <li>If it is an HTTPMD URL, verify that the necessary protocol handler
 *         is installed
 *     <li>Check that domain names are fully qualified
 *     <li>Warn if an md5 HTTPMD URL is being used (md5 has a security hole)
 *     <li>Verify that the host name in the URL can be resolved
 *     <li>Verify that the host name does not resolve to a loopback address
 *     <li>Verify that it's possible to open a connection using the URL
 *   </ul>
 *   <li>If the <code>-security</code> option was specified:
 *     <ul>
 *        <li>Verify that <code>javax.net.ssl.trustStore</code> is defined and
 *            that the trust store it references is accessible
 *        <li>Check whether <code>com.sun.jini.discovery.x500.trustStore</code>
 *            is defined, and if so that the trust store it references is 
 *            accessible
 *        <li>Check whether <code>javax.net.ssl.keyStore</code> is defined, and
 *            if so that the key store it references is accessible
 *        <li>Verify that a login configuration is defined and that it is 
 *            accessible and syntactically correct
 *      </ul>
 * </ul>
 * The following checks are done for each 
 * <code>SharedActivatableServiceDescriptor</code>:
 * <ul>
 *   <li>Verify that any entry in that <code>Configuration</code> named
 *       <code>persistenceDirectory</code> refers to either an empty directory 
 *       or a non-existant directory
 * </ul>
 * The following checks are done for each 
 * <code>SharedActivationGroupDescriptor</code>:
 * <ul>
 *   <li>Verify that the activation system is running
 *   <li>Verify that the virtual machine (VM) created by that command is at 
 *       least version 1.4
 *   <li>Verify that <code>jsk-policy.jar</code> is loaded from the extensions
 *       directory
 *     
 *   <li>Verify that <code>jsk-platform.jar</code> is in the classpath 
 *
 * 
 *   <li>If <code>java.util.logging.config.file</code> is defined in the
 *       properties returned by calling <code>getServerProperties</code> then 
 *       verify that the file it references is accessible
 * </ul>
 *  A subset of these checks are performed on the command line being analyzed:
 *  <ul>
 *    <li>Codebase/URL checks based on the value of
 *        <code>java.rmi.server.codebase</code>
 *    <li>Policy file checks based on the value of
 *        <code>java.security.policy</code>
 *    <li>Check for <code>jsk-policy</code> being loaded by the extension
 *        class loader
 *    <li>Check for <code>jsk-platform</code> in the classpath 
 *    <li>Security checks if <code>-security</code> was specified
 *    <li>The logging config file check
 * </ul>
 * In all cases, check that the local host name does not resolve to the 
 * loopback address.
 */

public class EnvCheck {

    /** the list of plugins instances to run */
    private ArrayList pluginList = new ArrayList();

    /** flag controlling the display of stack traces in the output */
    private boolean printStackTraces = false;

    /** the command line arguments of the command being analyzed */
    private String[] args;

    /** the <code>ServiceDescriptor</code>s obtained from the starter config */
    private ServiceDescriptor[] descriptors = new  ServiceDescriptor[0];

    /** the localization resource bundle */
    private static ResourceBundle bundle;

    /** the java command on the command line being checked */
    private String javaCmd;

    /** the options on the command line being checked (never null) */
    String[] options = new String[0];

    /** the properties on the command line being checked (never null) */
    Properties properties = new Properties();

    /** the classpath on the command line being checked */
    String classpath = null;

    /** the main class on the command line being checked */
    String mainClass = null;

    /** the executable JAR file on the command line being checked */
    String jarToRun = null;

    /** the list of plugins supplied via the -plugin option */
    static ArrayList pluginJarList = new ArrayList();

    /** the class loader for loading plugins */
    ClassLoader pluginLoader = EnvCheck.class.getClassLoader();

    /** the classpath of the tool, including the plugins (updated in run) */
    static String combinedClasspath = System.getProperty("java.class.path");

    /**
     * The entry point for the tool. The localization resource bundle is
     * located, the plugins are loaded, and the checks are performed. The system
     * property <code>java.protocol.handler.pkgs</code> for the tool VM is set
     * to <code>net.jini.url</code> to ensure that the tool can manipulate
     * HTTPMD URLs.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
	System.setProperty("java.protocol.handler.pkgs", "net.jini.url");
	bundle = Util.getResourceBundle(EnvCheck.class);
	if (args.length == 0) {
	    usage();
	    System.exit(1);
	}
        findPlugins(args);
	new EnvCheck().run(args);
    }

    /** 
     * Output the usage message.
     */
    private static void usage(){
	System.err.println(Util.getString("envcheck.usage", bundle));
    }

    /**
     * Helper to print a localized string
     *
     * @param key the resource key
     */
    private String getString(String key) {
	return Util.getString(key, bundle);
    }

    /**
     * Helper to print a localized string
     *
     * @param key the resource key
     * @param val the value parameter expected by the message
     */
    private String getString(String key, String val) {
	return Util.getString(key, bundle, val);
    }

    /**
     * Search the command line for user supplied plugin definitions
     * and place them in the internal plugin list.
     *
     * @param cmdLine the original command line args
     */
    private static void findPlugins(String[] cmdLine) {
	int index = 0;
	String arg;
	while ((arg = cmdLine[index++]).startsWith("-")) {
	    if (arg.equals("-plugin")) {
		String pluginName = cmdLine[index++];
		if (!(new File(pluginName).exists())) {
		    System.err.println(Util.getString("envcheck.noplugin", 
				       bundle,
				       pluginName));
		    System.exit(1);
		}
		combinedClasspath += File.pathSeparator + pluginName;
		try {
		    pluginJarList.add(new URL("file:" + pluginName));
		} catch (MalformedURLException e) { // should never happen
		    e.printStackTrace();
		    System.exit(1);
		}
	    } else if (arg.equals("-level")) {
		index++;
	    }
	}
    }

    /**
     * Parse the command line, identifying options for the tool and parsing
     * the VM, system properties, options, JAR/main class and arguments
     * for the command line being tested. The <code>-plugin</code> option
     * is ignored by this parser since they must have been processed
     * earlier.
     *
     * @param cmdLine the original command line arguments
     */
    private void parseArgs(String[] cmdLine) {
	int index = 0;
	try {
	    while (true) {
		String arg = cmdLine[index++];
		if (arg.equals("-traces")) {
		    printStackTraces = true;
		    Reporter.setPrintTraces(true);
		} else if (arg.equals("-explain")) {
		    Reporter.setExplanation(true);
		} else if (arg.equals("-level")) {
		    String val = cmdLine[index++];
		    if (val.equals("info")) {
			Reporter.setLevel(Reporter.INFO);
		    } else if (val.equals("warning")) {
			Reporter.setLevel(Reporter.WARNING);
		    } else if (val.equals("error")) {
			Reporter.setLevel(Reporter.ERROR);
		    } else {
			System.err.println(getString("envcheck.badlevel"));
			usage();
			System.exit(1);
		    }
	        } else if (arg.equals("-plugin")) {
		    index++; // already processed the plugin
		} else if (isPluginOption(arg)) { //no additional work to do
		} else if (arg.startsWith("-")) {
		    System.err.println(getString("envcheck.illegalopt", arg));
		    usage();
		    System.exit(1);
		} else if (arg.endsWith("java")) {
		    javaCmd = arg;
		    break;
		} else {
		    System.err.println(getString("envcheck.nojavacmd"));
		    usage();
		    System.exit(1);
		}
	    }
	} catch (ArrayIndexOutOfBoundsException e) {
	    System.err.println(getString("envcheck.nocmdargs"));
	    usage();
	    System.exit(1);
	}
	try {
	    ArrayList optList = new ArrayList();
	    while (true) {
		String opt = cmdLine[index++];
		if (!opt.startsWith("-")) {
		    mainClass = opt;
		    break;
		} else if (opt.startsWith("-D")) {
		    int valueIndex = opt.indexOf("=");
		    String key = opt.substring(2, valueIndex);
		    String value = opt.substring(valueIndex + 1);
		    properties.put(key, value);
		} else if (opt.equals("-cp") || opt.equals("-classpath")) {
		    classpath = cmdLine[index++];
		} else if (opt.equals("-jar")) {
		    jarToRun = cmdLine[index++];
		    break;
		} else {
		    optList.add(opt);
		}
	    }
	    options = 
		(String[])  optList.toArray(new String[optList.size()]);
	} catch (ArrayIndexOutOfBoundsException e) {
	    System.err.println(getString("noexecutable"));
	    usage();
	    System.exit(1);
	}
	ArrayList argList = new ArrayList();
	while (index < cmdLine.length) {
	    argList.add(cmdLine[index++]);
	}
	args = (String[]) argList.toArray(new String[argList.size()]);
    }
	    
    /**
     * Return the <code>ServiceDescriptor</code>s contained in the service
     * starter configuration. If the command being analyzed does not invoke the
     * service starter, a zero-length array will be returned.
     * 
     * @return the descriptors in the starter configuration or an empty array
     */
    public ServiceDescriptor[] getDescriptors() {
	return descriptors;
    }

    /**
     * Return the <code>SharedActivationGroupDescriptor</code> contained in the
     * service starter configuration. Returns <code>null</code> if there is no
     * such descriptor, or if the command being analyzed does not invoke the
     * service starter.
     *
     * @return the <code>SharedActivationGroupDescriptor</code> or 
     *         <code>null</code>
     */
    public SharedActivationGroupDescriptor getGroupDescriptor() {
	for (int i = 0; i < descriptors.length; i++) {
	    if (descriptors[i] instanceof SharedActivationGroupDescriptor) {
		return (SharedActivationGroupDescriptor) descriptors[i];
	    }
	}
	return null;
    }

    /**
     * Perform the runtime checks. If any user plugins were supplied, construct
     * a class loader capable of loading them and modify
     * <code>combinedClasspath</code> to make the classes available to subtasks.
     * Load the plugins and parse the command line. The plugins must be
     * available to the parser so that plugin specific options can be checked
     * for. Load the service starter configuration if the service starter is
     * being invoked. Execute all of the plugin classes in the order loaded.
     *
     * @param cmdLine the original command line arguments
     */
    private void run(String[] cmdLine) {
	if (pluginJarList.size() > 0) {
	    URL[] urls = 
		(URL[]) pluginJarList.toArray(new URL[pluginJarList.size()]);
	    pluginLoader = new URLClassLoader(urls, pluginLoader);
	}
	loadPlugins();
	parseArgs(cmdLine);
	if (jarToRun == null && classpath == null) {
	    System.err.println(getString("envcheck.missingclasspath"));
	    System.exit(1);
	}
	loadConfiguration();
	Iterator plugins = pluginList.iterator();
	while (plugins.hasNext()) {
	    Plugin plugin = (Plugin) plugins.next();
	    try {
		plugin.run(this);
	    } catch (SecurityException e) { // limp on if permission check fails
		e.printStackTrace();
	    }
	}
	int warningCount = Reporter.getWarningCount();
	int errorCount = Reporter.getErrorCount();
	if ((warningCount + errorCount) > 0) {
	    System.out.println();
	    System.out.println();
	    System.out.println(getString("envcheck.summaryseparator"));
	}
	if (warningCount > 0) {
	    System.out.println(getString("envcheck.warningheader")
			       + warningCount);
	}
	if (errorCount > 0) {
	    System.out.println(getString("envcheck.errorheader") + errorCount);
	}
	    
    }

    /**
     * Check whether <code>arg</code> is a plugin specific option. The
     * <code>isPluginOption</code> method of every plugin is called in
     * case an option is shared among multiple plugins. 
     *
     * @param arg the argument to check
     * @return <code>true</code> if any plugin's <code>isPluginOption</code> 
     *         method returns <code>true</code>
     */
    private boolean isPluginOption(String arg) {
	boolean gotOne = false;
	Iterator plugins = pluginList.iterator();
	while (plugins.hasNext()) {
	    Plugin plugin = (Plugin) plugins.next();
	    if (plugin.isPluginOption(arg)) {
		gotOne = true;
	    }
	}
	return gotOne;
    }
	    
    /**
     * Instantiate the service starter configuration if the command line being
     * analyzed runs the service starter.  This is assumed to be true if the
     * command line specified an executable JAR file named <code>start.jar</code> and
     * if that file resides in the same directory as a file named
     * <code>jsk-platform.jar</code>. Extract and save all of the service
     * descriptors and service destructors from the configuration. If a
     * <code>ConfigurationException</code> is thrown the tool will exit. If
     * there are no <code>ServiceDescriptor</code> or
     * <code>ServiceDestructor</code> entries supplied by the configuration a
     * error is generated and a normal return is done. The instantiation of
     * the configuration is done in a child process using the VM, properties,
     * options and arguments supplied in the command line under test.
     */
    private void loadConfiguration() {
	if (jarToRun != null) {
	    if (!jarToRun.endsWith("start.jar")) {
		return;
	    }
	    //XXX do existence check in parser
	    File starterFile = new File(jarToRun);
	    File jskLibDir = starterFile.getParentFile();
	    File jskplatform = new File(jskLibDir, "jsk-platform.jar");
	    if (!jskplatform.exists()) {
		return; // presumably a user file coincidentally named start.jar
	    }
	} else {
	    if (!mainClass.equals("com.sun.jini.start.ServiceStarter")) {
		return;
	    }
	}
	//XXX need to check validity of javaCmd and arg list length
	Object lobj = 
	    launch("com.sun.jini.tool.envcheck.EnvCheck$GetDescriptors", args);
	if (lobj instanceof ServiceDescriptor[]) {
	    descriptors = (ServiceDescriptor[]) lobj;
	    if (descriptors.length == 0) {
		Reporter.print(new Message(Reporter.ERROR, 
					   getString("envcheck.emptyconfig"), 
					   null));
	    }
	} else if (lobj instanceof ConfigurationException) {
	    System.err.println(getString("envcheck.ssdescfailed"));
	    ((Throwable) lobj).printStackTrace();
	    System.exit(1);
	} else {
	    System.err.println(getString("envcheck.subtaskex", lobj.toString()));
	    ((Throwable) lobj).printStackTrace();
	    System.exit(1);
	}
    }

    /**
     * Load the plugin classes. All of the resources named
     * <code>META-INF/services/com.sun.jini.tool.envcheck.Plugin</code> are
     * read; each such resource is expected to contain a list of plugin class
     * names, one per line (white space is trimmed). The corresponding classes
     * are loaded and saved. Any other exceptions thrown while processing
     * the plugin JAR files will cause the tool to exit.
     */
    private void loadPlugins() {
	Package pkg = getClass().getPackage();
	String pkgName = "";
	if (pkg != null) {
	    pkgName = pkg.getName();
	}
	pkgName = pkgName.replace('.', '/');
	if (pkgName.length() > 0) {
	    pkgName += "/";
	}
	Iterator plugins = 
	    Service.providers(com.sun.jini.tool.envcheck.Plugin.class,
			      pluginLoader);
	while (plugins.hasNext()) {
	    pluginList.add(plugins.next());
	}
    }

    /**
     * Get the command line arguments of the command being analyzed.
     *
     * @return the args
     */
    // unused, but supplied to support user developed plugins
    public String[] getArgs() {
	return args;
    }

    /**
     * Return the flag indicating whether to output stack traces that
     * result from a check.
     */
    public boolean printStacks() {
	return printStackTraces;
    }

    /**
     * Launch a child VM using the <code>java</code> command, properties, and
     * options supplied on the command line being analyzed. If an executable JAR
     * file was specified, the classpath of the child VM consists of the JAR
     * file name augmented with the classpath of the tool and plugins. If a main
     * class was specified, the classpath of the child VM consists of the
     * <code>-cp/-classpath</code> option value of the command line being
     * analyzed augmented with the classpath of the tool and plugins.
     *
     * @param task the class name of the task to launch, which must implement
     *             the <code>SubVMTask</code> interface
     * @param args the arguments to pass to the main method of the task
     * @return the result or exception returned by the subtask supplied as a
     *         serialized object written on the subtask's
     *         <code>System.out</code> stream.
     */
    public Object launch(String task, String[] args) {
	String cp;
	if (jarToRun != null) {
	    cp = jarToRun + File.pathSeparator;
	} else {
	    cp = classpath + File.pathSeparator;
	}
	cp += combinedClasspath;
	String[] opts = new String[options.length + 2];
	opts[0] = "-cp";
	opts[1] = cp;
	System.arraycopy(options, 0, opts, 2, options.length);
	if (args == null) {
	    args = new String[0];
	}
	String[] subvmArgs = new String[args.length + 1];
	subvmArgs[0] = task;
	System.arraycopy(args, 0, subvmArgs, 1, args.length);
	return launch(javaCmd, properties, opts, subvmArgs);
    }

    /**
     * Launch a child VM using the <code>java</code> command, properties, and
     * options supplied on the command line. If an executable JAR file was
     * specified, the classpath of the child VM consists of the JAR file name
     * augmented with the classpath of the tool and plugins. If a main class was
     * specified, the classpath of the child VM consists of the
     * <code>-cp/-classpath</code> option value of the command line being
     * analyzed augmented with the classpath of the tool and plugins.
     *
     * @param task the class name of the task to launch, which must implement
     *             the <code>SubVMTask</code> interface
     * @return the result or exception returned by the subtask supplied as a
     *         serialized object written on the subtask's
     *         <code>System.out</code> stream.
     */
    public Object launch(String task) {
	return launch(task, null);
    }

    /**
     * Return a property value that was specified on the command line being
     * analyzed. Only properties explicitly defined on the command line will
     * resolve to a value.
     *
     * @param key the name of the property
     * @return the property value, or <code>null</code> if undefined
     */
    public String getProperty(String key) {
	return properties.getProperty(key);
    }

    /**
     * Return a copy of the properties that were specified on the
     * command line being analyzed. The caller may modify the returned
     * properties object.
     * 
     * @return the properties, which may be empty
     */
    public Properties getProperties() {
	return (Properties) properties.clone();
    }

    /**
     * Launch a subtask VM using the <code>java</code> command given by
     * <code>javaCmd</code>.  If <code>javaCmd</code> is <code>null</code>, the
     * command to run is derived from the value of the <code>java.home</code>
     * property of the tool VM.  The first value in <code>args</code> must name
     * a class that implements the <code>SubVMTask</code> interface. The
     * <code>props</code> and <code>opts</code> arrays must contain fully
     * formatted command line values for properties and options
     * (i.e. "-Dfoo=bar" or "-opt"). <code>opts</code> must include a
     * <code>-cp</code> or <code>-classpath</code> option and its value must
     * completely specify the classpath required to run the subtask.
     *
     * @param javaCmd the <code>java</code> command to execute, or
     *                <code>null</code> to create another instance of the
     *                tool VM
     * @param props   properties to define, which may be <code>null</code> or
     *                empty
     * @param opts    options to define, which must include a classpath 
     *                definition
     * @param args    arguments to pass to the child VM
     * @return        the result or exception returned by the subtask supplied
     *                as a serialized object written on the subtask's
     *                <code>System.out</code> stream.
     *
     * @throws IllegalArgumentException if <code>opts</code> does not
     *         include a classpath definition, or if <code>args[0]</code>
     *         does not contain the name of a class that implements 
     *         <code>SubVMTask</code>
     */
    public Object launch(String javaCmd, 
			 Properties props, 
			 String[] opts,
			 String[] args)
    {
	if (args == null || args.length == 0) {
	    throw new IllegalArgumentException("No class name in args[0]");
	}
	// make sure subtask is valid to avoid obscure failures at exec time
	String taskName = args[0];
	try {
	    Class taskClass = Class.forName(taskName, true, pluginLoader);
	    if (!SubVMTask.class.isAssignableFrom(taskClass)) {
		throw new IllegalArgumentException(taskName 
						   + " does not implement "
						   + "SubVMTask");
	    }
	    // make sure inner classes are static
	    if (taskClass.getDeclaringClass() != null) {
		if ((taskClass.getModifiers() & Modifier.STATIC) == 0) {
		    throw new IllegalArgumentException(taskName
						   + " must be a static class");
		}
	    }
	} catch (ClassNotFoundException e) {
	    throw new IllegalArgumentException("Class not found: " + taskName);
	}
	if (javaCmd == null) {
	    javaCmd = System.getProperty("java.home");
	    javaCmd += File.separator + "bin" + File.separator + "java";
	}
	ArrayList cmdList = new ArrayList();
	cmdList.add(javaCmd);
	boolean gotClasspath = false;
	if (opts != null) {
	    for (int i = 0; i < opts.length; i++) {
		if (opts[i].equals("-cp") || opts[i].equals("-classpath")) 
		    if (i + 1 == opts.length) {
			throw new IllegalArgumentException("classpath option "
							   + "does not have "
							   + "a value");
		    }
		gotClasspath = true;
		cmdList.add(opts[i]);
	    }
	}
	if (!gotClasspath) {
	    throw new IllegalArgumentException("no classpath defined");
	}
	if (props != null) {
	    Enumeration en = props.propertyNames();
	    while (en.hasMoreElements()) {
		String key = (String) en.nextElement();
		String value = props.getProperty(key);
		cmdList.add("-D" + key + "=" + value);
	    }
	}
	cmdList.add("com.sun.jini.tool.envcheck.SubVM");
	for (int i = 0; i < args.length; i++) {
	    cmdList.add(args[i]);
	}
	try {
	    String[] argList = 
		(String[]) cmdList.toArray(new String[cmdList.size()]);
//  	    for (int i = 0; i < argList.length; i++) {
//  		System.out.print(argList[i] + " ");
//  	    }
//  	    System.out.println();
	    Process p = Runtime.getRuntime().exec(argList);
	    Pipe pipe = new Pipe("errorPipe", 
				 p.getErrorStream(), 
				 System.err, 
				 "Child VM: ");
	    ObjectInputStream s = new ObjectInputStream(p.getInputStream());
	    Object o = s.readObject();
	    pipe.waitTillEmpty(5000);
	    return o;
	} catch (Exception e) {
	    return e;
	}
    }

    /**
     * Launch a subtask using the environment defined by the given service
     * descriptors. Calling this method is equivalent to calling
     * <code>launch(d, g, taskName, null)</code>.
     *
     * @param d the services descriptor, which may be <code>null</code>
     * @param gd the group descriptor, which may be <code>null</code
     * @param taskName the name of the subtask to run
     * @return the result or exception returned by the subtask supplied as a
     *         serialized object written on the subtask's
     *         <code>System.out</code> stream.
     */
    public Object launch(NonActivatableServiceDescriptor d, 
			 SharedActivationGroupDescriptor gd,
			 String taskName)
    {
	return launch(d, gd, taskName, null);
    }

    /**
     * Launch a subtask using the environment defined by the given service
     * descriptors. 
     * <p>
     * If <code>d</code> and <code>gd</code> are both <code>null</code>, then
     * calling this method is equivalent to calling
     * <code>launch(taskName, args)</code>.
     * <p>

     * If <code>d</code> is <code>null</code> and <code>gd</code> is
     * non-<code>null</code> then the properties are taken from
     * <code>gd.getServerProperties()</code> and the
     * <code>java.security.policy</code> property is added or replaced with the
     * value of <code>gd.getPolicy()</code>.  The options are taken from
     * <code>gd.getServerOptions()</code>, but any <code>-cp/-classpath</code>
     * option is discarded; a <code>-cp</code> option is added that is the value
     * of <code>gd.getClasspath()</code> augmented with the classpath of the
     * tool and plugins.  If <code>gd.getServerCommand()</code> is
     * non-<code>null</code>, its value is used to invoke the child VM;
     * otherwise the <code>java</code> command of the command line being
     * analyzed is used. The arguments passed to the child VM consist of an
     * array whose first element is <code>taskName</code> and whose remaining
     * elements are taken from <code>args</code>.

     * <p>

     * If <code>d</code> is not <code>null</code>, but <code>gd</code> is
     * <code>null</code>, then if <code>d</code> is an instance of
     * <code>SharedActivatableServiceDescriptor</code> an
     * <code>IllegalArgumentException</code> is thrown. Otherwise the properties
     * and options are taken from the command line being analyzed. The
     * <code>java.security.policy</code> property is added or replaced using the
     * value of <code>d.getPolicy()</code>.  The <code>-cp/-classpath</code>
     * option is replaced with the value of <code>d.getImportCodebase()</code>
     * augmented with the classpath of the tool and plugins.  The arguments
     * passed to the child VM consist of <code>taskName</code> followed by
     * <code>args</code> if <code>args</code> is non-<code>null</code>, or
     * followed by <code>d.getServerConfigArgs()</code> otherwise.  The VM is
     * invoked using the <code>java</code> command of the command line being
     * analyzed.

     * <p>

     * if <code>d</code> and <code>gd</code> are both non-<code>null</code> then
     * if <code>d</code> is an instance of
     * <code>SharedActivatableServiceDescriptor</code> then the properties,
     * options, and <code>java</code> command are taken from
     * <code>gd.getServerProperties()</code>,
     * <code>gd.getServerOptions()</code>, and
     * <code>gd.getServerCommand()</code>; however, if the value of
     * <code>gd.getServerCommand()</code> is <code>null</code>, the
     * <code>java</code> command is taken from the command line being
     * analysed. If <code>d</code> is not an instance of
     * <code>SharedActivatableServiceDescriptor</code> then the properties,
     * options, and <code>java</code> command are taken from the command line
     * being analyzed.  In all cases the <code>java.security.policy</code>
     * property is added or replaced using the value of
     * <code>d.getPolicy()</code>.  The <code>-cp/-classpath</code> option is
     * added or replaced with the value of <code>d.getImportCodebase()</code>
     * augmented with the value of the classpath of the tool and plugins.  The
     * arguments passed to the child VM consist of <code>taskName</code>
     * followed by <code>args</code> if <code>args</code> is
     * non-<code>null</code>, or followed by
     * <code>d.getServerConfigArgs()</code> otherwise.

     * <p>
     *
     * @param d the service descriptor, which may be <code>null</code>
     * @param gd the group descriptor, which may be <code>null</code
     * @param taskName the name of the subtask to run
     * @param args the arguments to pass to the child VM, which may be 
     *             <code>null</code>
     * @return the result or exception returned by the subtask supplied as a
     *         serialized object written on the subtask's
     *         <code>System.out</code> stream.
     */
    public Object launch(NonActivatableServiceDescriptor d, 
			 SharedActivationGroupDescriptor gd,
			 String taskName,
			 String[] args)
    {
	if (d == null && gd == null) {
	    return launch(taskName, args);
	}
	//build taskArgs array
	if (args == null) {
	    if (d != null) {
		args = d.getServerConfigArgs();
		if (args == null || args.length == 0) {
		    return new IllegalArgumentException("No configuration args "
							+ "in descriptor");
		}
	    } else {
		args = new String[0];
	    }
	}
	String[] taskArgs = new String[args.length + 1];
	taskArgs[0] = taskName;
	System.arraycopy(args, 0, taskArgs, 1, args.length);
	
	if (d == null && gd != null) {
	    Properties props = gd.getServerProperties();
	    props.put("java.security.policy", gd.getPolicy());
	    ArrayList l = new ArrayList();
	    String[] opts = gd.getServerOptions();
	    if (opts != null) {
		for (int i = 0; i < opts.length; i++ ) {
		    if (opts[i].equals("-cp")) {
			i++; // bump past value
		    } else {
			l.add(opts[i]);
		    }
		}
	    }
	    l.add("-cp");
	    l.add(gd.getClasspath() + File.pathSeparator + combinedClasspath);
	    opts = (String[]) l.toArray(new String[l.size()]);
	    String cmd = gd.getServerCommand();
	    if (cmd == null) {
		cmd = javaCmd;
	    }
	    return launch(cmd, props, opts, taskArgs);
	} else if (d != null && gd == null) {
	    if (d instanceof SharedActivatableServiceDescriptor) {
		throw new IllegalArgumentException("no group for service");
	    }
	    Properties props = getProperties();
	    props.put("java.security.policy", d.getPolicy());
	    ArrayList l = new ArrayList();
	    for (int i = 0; i < options.length; i++ ) {
		if (options[i].equals("-cp") 
		    || options[i].equals("-classpath")) {
		    i++; // bump past value
		} else {
		    l.add(options[i]);
		}
	    }
	    l.add("-cp");
	    l.add(d.getImportCodebase() 
		    + File.pathSeparator 
		    + combinedClasspath);
	    String[] opts = (String[]) l.toArray(new String[l.size()]);
	    return launch(javaCmd, props, opts, taskArgs);
	} else if (d != null && gd != null) {
	    Properties props = getProperties();
	    String[] opts = options;
	    String vm = null;
	    if (d instanceof SharedActivatableServiceDescriptor) {
		props = gd.getServerProperties();
		if (props == null) {
		    props = new Properties();
		}
		opts = gd.getServerOptions();
		vm = gd.getServerCommand();
	    }
	    if (vm == null) {
		vm = javaCmd;
	    }
	    props.put("java.security.policy", d.getPolicy());
	    ArrayList l = new ArrayList();
	    if (opts != null) {
		for (int i = 0; i < opts.length; i++ ) {
		    if (opts[i].equals("-cp")
		     || opts[i].equals("-classpath")) {
			i++; // bump past value
		    } else {
			l.add(opts[i]);
		    }
		}
	    }
	    l.add("-cp");
	    l.add(d.getImportCodebase() 
		  + File.pathSeparator 
		  + combinedClasspath);
	    opts = (String[]) l.toArray(new String[l.size()]);
	    return launch(vm, props, opts, taskArgs);
	} else {
	    throw new IllegalStateException("Should never get here");
	}
    }

    /** 
     * Return the <code>java</code> command for the command line being analyzed.
     *
     * @return the <code>java</code> command
     */
    public String getJavaCmd() {
	return javaCmd;
    }

    /**
     * Check for the existence of a file identified by a property
     * supplied on the command line being analyzed.
     *
     * @param prop the name of the property
     * @param desc a brief description of the file
     * @return the property value, or <code>null</code> if undefined
     */
    public String checkFile(String prop, String desc) {
	String name = getProperty(prop);
	if (name == null) {
	    return getString("util.undef", desc);
	}
	return Util.checkFileName(name, desc);
    }

    /**
     * Return the name of the executable JAR file supplied on the
     * command line being analyzed.
     *
     * @return the JAR file name, or <code>null</code> if the command line
     *         did not specify one.
     */
    public String getJarToRun() {
        return jarToRun;
    }

    /**
     * Get the classpath provided by the command line being analyzed.  If
     * <code>getJarToRun()</code> returns a non-<code>null</code> value then
     * its value is returned. Otherwise the value supplied by the command
     * line <code>-cp/-classpath</code> option is returned.
     *
     * @return the classpath supplied on the command line being analyzed.
     */
    public String getClasspath() {
	if (jarToRun != null) {
	    return jarToRun;
	}
	return classpath;
    }

    /**
     * A subtask which returns the service descriptors and service
     * destructors supplied by the service starter configuration constructed
     * from <code>args</code>.
     */
     static class GetDescriptors implements SubVMTask {

	public Object run(String[] args) {
	    Configuration starterConfig = null;
	    try {
		starterConfig = ConfigurationProvider.getInstance(args);
	    } catch (ConfigurationException e) {
		return e;
	    }
	    try {
		ServiceDescriptor[] d1 =  (ServiceDescriptor[])
		    starterConfig.getEntry("com.sun.jini.start",
					   "serviceDescriptors",
					   ServiceDescriptor[].class, 
					   new ServiceDescriptor[0]);
		ServiceDescriptor[] d2 =  (ServiceDescriptor[])
		    starterConfig.getEntry("com.sun.jini.start",
					   "serviceDestructors",
					   ServiceDescriptor[].class, 
					   new ServiceDescriptor[0]);
		ServiceDescriptor[] descriptors = 
		    new ServiceDescriptor[d1.length + d2.length];
		System.arraycopy(d1, 0, descriptors, 0, d1.length);
		System.arraycopy(d2, 0, descriptors, d1.length, d2.length);
		return descriptors;
	    } catch (ConfigurationException e) {
		return e;
	    }
	}
    }

    /**
     * An I/O redirection pipe. A daemon thread copies data from an input
     * stream to an output stream. An optional annotation may be provided
     * which will prefix each line of the copied data with a label which
     * can be used to identify the source. 
     */
    private class Pipe implements Runnable {

	/** the line separator character */
	private final static byte SEPARATOR = (byte) '\n';

	/** output line buffer */
	private ByteArrayOutputStream bufOut = new ByteArrayOutputStream();

	/** the input stream */
	private InputStream in;

	/** the output PrintStream */
	private PrintStream stream;

	/** the output stream annotation */
	private String annotation;

	/** the thread to process the data */
	private Thread outThread;
	
	/**
	 * Create a new Pipe object and start the thread to handle the data.
	 *
	 * @param name the name to assign to the thread
	 * @param in input stream from which pipe input flows
	 * @param stream the stream to which output will be sent
	 * @param a the annotation for prepending text to logged lines
	 */
	Pipe(String name, 
		    InputStream in, 
		    PrintStream stream, 
		    String a) 
	{
	    this.in = in;
	    this.stream = stream;
	    this.annotation = a;
	    outThread = new Thread(this, name);
	    outThread.setDaemon(true);
	    outThread.start();
	}

	/**
	 * Wait until the run method terminates due to reading EOF on input
	 *
	 * @param timeout max time to wait for the thread to terminate
	 */
	void waitTillEmpty(int timeout) {
	    try {
		outThread.join(timeout);
	    } catch (InterruptedException ignore) {
	    }
	}

	/**
	 * Read and write data until EOF is detected. Flush any remaining data to
	 * the output steam and return, terminating the thread.
	 */
	public void run() {
	    byte[] buf = new byte[256];
	    int count;
	    try {
		/* read bytes till there are no more. */
		while ((count = in.read(buf)) != -1) {
		    write(buf, count);
		}

		/*  If annotating, flush internal buffer... may not have ended on a
		 *  line separator, we also need a last annotation if 
		 *  something was left.
		 */
		String lastInBuffer = bufOut.toString();
		bufOut.reset();
		if (lastInBuffer.length() > 0) {
		    if (annotation != null) {
			stream.print(annotation);
		    }
		    stream.println(lastInBuffer);
		}
	    } catch (IOException e) {
	    }
	}
	
	/**
	 * Write each byte in the give byte array.
	 *
	 * @param b the array of input bytes
	 * @param len the number data bytes in the array
	 */
	private void write(byte b[], int len) throws IOException {
	    if (len < 0) {
		throw new ArrayIndexOutOfBoundsException(len);
	    }
	    for (int i = 0; i < len; i++) {
		write(b[i]);
	    }
	}

	/**
	 * If not annotated, write the byte to the stream immediately. Otherwise,
	 * write a byte of data to the internal buffer. If we have matched a line
	 * separator, then the currently buffered line is sent to the output writer
	 * with a prepended annotation string.
	 */
	private void write(byte b) throws IOException {

	    bufOut.write(b);
	    
	    // write buffered line if line separator detected
	    if (b == SEPARATOR) {
		String s = bufOut.toString();
		bufOut.reset();
		if (annotation != null) {
		    stream.print(annotation);
		}
		stream.print(s);
	    }
	}
    }
}
