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
package org.apache.river.qa.harness;

import java.io.File;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;
import java.rmi.server.RMIClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.util.StringTokenizer;
import net.jini.loader.ClassLoading;

/**
 * The <code>TestDescription</code> is an object which describes a test;
 * it is a factory for the test object and provides support facilities
 * for setting up and executing the test.
 * <p>
 * The description contains of a set of properties. Some of these 
 * properties have well defined names which are used to construct
 * the execution environment of the test. These well defined names are:
 * <ul>
 * <li><code>testClass</code>, the fully qualified name of the test
 *     implementation class to be run. This class must implement the 
 *     <code>org.apache.river.qa.harness.LegacyTest</code> interface. A definition
 *     for this parameter must be included in the <code>Properties</code>
 *     object provided in the constructor for this class. 
 * <li><code>testCategories</code>, the test categories associated with this
 *     test. There must be at least one category defined.
 * <li><code>testCodebase</code>, the codebase to be used for objects
 *     exported by the test. While this value may be defined in the
 *     <code>TestDescription</code>, it is possible that a default value
 *     might be defined in a more global resource file, such as the
 *     component-level resource file.
 * <li><code>testPolicyfile</code>, the security policy file to be used for
 *     this test. While this value may be defined in the
 *     <code>TestDescription</code>, it is possible that a default value
 *     might be defined in a more global resource file, such as the
 *     component-level resource file.
 * <li><code>testClasspath</code> the classpath to be used for
 *     this test. While this value may be defined in the
 *     <code>TestDescription</code>, it is possible that a default value
 *     might be defined in a more global resource file, such as the
 *     component-level resource file.
 * <li><code>testjvm</code> the path to the VM to be used for
 *     this test. While this value may be defined in the
 *     <code>TestDescription</code>, it is more likely that this value
 *     would be specified by the user on the command line or in the
 *     configuration file.
 * <li><code>testjvmargs</code> arguments to be used for the VM for
 *     this test. While this value may be defined in the
 *     <code>TestDescription</code>, it is more likely that this value
 *     would be specified by the user on the command line or in the
 *     configuration file.
 * <li> <code>testArgs</code> the arguments to pass to a stand-alone test.
 *      This parameter is only accessed by <code>MainWrapper</code>.
 * </ul>
 * In addition to these properties, any other property value accessed by
 * a test can be defined in the test description.
 * <p>
 * An include mechanism is also provided, so that multiple tests which
 * share common properties can reference those shared properties in
 * this test description. If a parameter name 
 * <code>include</code> exists, its value is treated as a resource
 * bundle name and that bundle is loaded. Otherwise, a search is done for
 * parameters having names <code>include0</code>, <code>include1</code>, ...
 * until a parameter cannot be found. For those found, treat each
 * value as a resource bundle name and load that bundle starting
 * with the bundle named by <code>include0</code>. A warning is printed
 * if the resource bundle named by a parameter cannot be loaded.
 * Includes cannot be nested. Any include within a included file
 * is silently ignored. Parameters which already exist in the
 * test description are <b>NOT</b> overridden by an include file;
 * any existing parameter found in an include is silently ignored.
 *
 * The logger named <code>org.apache.river.qa.harness.test</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr> <td> FINER <td> the value returned by <code>getTestDirectory</code>
 *</table>
 * <p>
 */
public class TestDescription implements Serializable {

    /** the logger */
    private static final Logger logger = 
	Logger.getLogger("org.apache.river.qa.harness.test");

    /**  The name of this <code>TestDescription</code> */
    private final String name;

    /** The config object */
    private final QAConfig config;

    /** The properties stored by this test description */
    private final Properties properties;

    /** A flag to be returned by the <code>nextConfiguration</code> call */
    boolean nextConf = true;

    /** The test provided by this descriptor */
    TestEnvironment test;

    /**
     * Construct a test description for a test with the given <code>name</code>.
     * Initialize the internal properties object and resolve any includes
     * in the test description.
     *
     * @param name the name of the test description  (a file name)
     * @param properties the test description file properties
     * @param config the config object for this test
     *
     * @throws TestException if a problem occurs obtaining test properties,
                             or if an <code>include</code> entry is found, but
     *                       the named include resource is not found
     */
    public TestDescription(String name, Properties properties, QAConfig config) 
	throws TestException
    {
	this.name = name; // XXX replace \ with / ??
	this.config = config;
	this.properties = properties;
	resolveIncludes();
    }

    /**
     * Get the value of a property stored in this test description.
     *
     * @param key the name of the property
     * @return the property value, or <code>null</code> if the property
     *          is undefined
     */
    String getProperty(String key) {
	return properties.getProperty(key);
    }

    Properties getProperties() {
	return properties;
    }

    /**
     * Set the value of a property stored in this test description.
     *
     * @param key the name of the property
     * @param value the value to store for the property
     */
    void setProperty(String key, String value) {
	properties.setProperty(key, value);
    }

    /**
     * Resolve include file definitions. If a parameter name
     * <code>include</code> exists, treat its value as a resource bundle name
     * and load that bundle. Otherwise, search for parameters having names
     * <code>include0</code>, <code>include1</code>, ...  until a parameter
     * cannot be found. For those found, treat each value as a resource bundle
     * name and load that bundle starting with the bundle named by
     * <code>include0</code>. Values defined in higher numbered include files
     * take precedence over values defined lower numbered files. A warning is
     * printed if the resource bundle named by a parameter cannot be loaded.
     * Includes cannot be nested. Any include within a included file is silently
     * ignored. Parameters which already exist in the test description are
     * <code>NOT</code> overridden by an include file.
     *
     * @throws TestException if an <code>include</code> entry is found, but
     *         the the corresponding resource is not found
     */
    private void resolveIncludes() throws TestException {
	Properties p = loadIncludeFile("include");
	if (p == null) {
	    p = new Properties();
	    for(int i = 0; updateIncludeProps(p, "include" + i); i++);
	}
	updateDescriptionProps(p);
    }

    /**
     * Load an include file. <code>name</code> is property name in
     * the test description properties which may map to an include
     * resource name. If there is no entry <code>name</code> in the
     * test description, then <code>null</code> is returned.
     *
     * @param name property name for an include file
     * @return a <code>Properties</code> object containing the properties
     *         defined in the file, or <code>null</code> if <code>name</code>
     *         does not map to a value in the test description properties.
     *
     * @throws TestException if <code>name</code> mapped to a resource name, 
     *         but the resource to include is not found or is empty
     */
    private Properties loadIncludeFile(String name) throws TestException {
	Properties props = null;
	String includeFile = config.resolve(properties.getProperty(name));
	if (includeFile != null) {
	    URL includeURL = config.getComponentURL(includeFile, this);
	    props = config.loadProperties(includeURL);
	    if (props.size() == 0) {
		throw new TestException("Include file " 
				      + includeURL
				      + " is missing or empty");
	    }
	}
	return props;
    }

    /**
     * Add the given properties to the set of properties managed by
     * this test description. Properties are added only if they are
     * currently undefined; any entry that looks like an 
     * <code>include</code> directive is discarded.
     *
     * @throws TestException if the resource to include is not found
     *                       or is empty
     */
    private void updateDescriptionProps(Properties props) throws TestException {
	Enumeration keys = props.keys();
	while (keys.hasMoreElements()) {
	    String key = (String) keys.nextElement();
	    if (keyOK(key)) {
		properties.setProperty(key, (String) props.get(key));
	    }
	}
    }

    /**
     * Update the <code>includeProps</code> with properties obtained
     * from an include file. <code>name</code> is a property name
     * which may map to an include resource/file name in the test
     * description properties. The return value indicates whether
     * such a mapping exists. The a property with the same name
     * exists in both locations, the original value in <code>includeProps</code>
     * is overwritten.
     *
     * @return true if a property with the given <code>name</code> exists
     *
     * @throws TestException if the resource to include is not found
     *                       or is empty
     */
    private boolean updateIncludeProps(Properties includeProps, String name) 
	throws TestException
    {
	Properties props = loadIncludeFile(name);
	if (props == null) {
	    return false;
	}
	Enumeration keys = props.keys();
	while (keys.hasMoreElements()) {
	    String key = (String) keys.nextElement();
	    includeProps.setProperty(key, (String) props.get(key));
	}
	return true;
    }

    /**
     * Determine whether the include parameter with the given <code>name</code>
     * can be added to the existing set of parameters for this test description.
     * In order to be valid, the parameter name must not already exist,
     * and it must not be an <code>include</code> directive
     *
     * @return true if the parameter can be added to the test description
     */
    private boolean keyOK(String key) {

	/* if starts with include, check for include syntax */
	if (key.startsWith("include")) {
	    boolean allDigits = true;
	    char[] digits = key.substring("include".length()).toCharArray();
	    for (int i = 0; i < digits.length; i++) {
		if (!Character.isDigit(digits[i])) {
		    allDigits = false;
		}
	    }
	    if (allDigits) {
		return false;
	    }
	}

	/* not an 'include', so check for existance */
	return (properties.getProperty(key) == null);
    }

    /**
     * Check the validity of the test description. It must at least
     * contain the name of the test class to instantiate, and it must
     * contain at least one category.
     *
     * @return <code>null</code> if the test description is valid, or a string
     *         describing the reason the test description is invalid
     */
    public String checkValidity() {
	String reason = null;
	String className = getTestClassName();
	if (className == null) {
	    reason = "Test class name missing from descriptor";
	} else if (getCategories().length == 0) {
	    reason = "No test categories are defined in descriptor";
	}
	return reason;
    }

    /**
     * Get the test name associated with this descriptor.
     *
     * @return the test name
     */
    public String getName() {
	return name;
    }

    /**
     * Get the test categories associated with this test. The categories are
     * tagged in the descriptor with the identifier <code>testCategories</code>.
     *
     * @return an array of test categories. A zero length array
     *         is returned if no categories are defined.
     */

    public String[] getCategories() {
	String categories = getProperty("testCategories");
	if (categories == null) {
	    return new String[0];
	}
	StringTokenizer tok = new StringTokenizer(categories, " ,");
        String[] categoryList = new String[tok.countTokens()];
	for (int i = tok.countTokens(); --i >= 0; ) {
	    categoryList[i] = tok.nextToken();
	}
	return categoryList;
    }	    

    /**
     * Get an instance of the test represented by this descriptor.
     *
     * @return the test instance, or <code>null</code> if the test could not
     *          be instantiated
     */
    TestEnvironment getTest() {
	String className = getTestClassName();
	// this method is called from the test vm, so the config.getTestLoader
	// method must not be used, since it's value is null
	if (className != null) {
	    try {
		Class testClass = Class.forName(className);
		test = (TestEnvironment) testClass.newInstance();
	    } catch (Throwable e) {
		logger.log(Level.INFO, 
			   "Failed to instantiate " + className,
			   e);
	    }
        } else {
            logger.log(Level.INFO, "test class name was null");
        }
	return test;
    }

    /**
     * Return the name of the test implementation class.
     *
     * @return the implementation class name
     */
    public String getTestClassName() {
	String className = getProperty("testClass");
	if (className != null) {
	    if (className.indexOf(".") < 0) {
		String pkgName = name.substring(0, name.lastIndexOf("/"));
		pkgName = pkgName.replace('/', '.');
		className = pkgName + "." + className;
	    }
	}
	return className;
    }

    /**
     * Indicates whether some other object is equal to this one.
     * <code>obj</code> is considered equal to this object if it
     * is an instance of <code>TestDescription</code> and has a
     * name which is identical to this name.
     *
     * @param obj the object to test for equality
     * @return true if <code>obj</code> is equal to this object
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof TestDescription)) {
	    return false;
	}
	return ((TestDescription) obj).getName().equals(name);
    }

    /**
     * Return a hashcode value for the object.
     *
     * @return the hashcode
     */
    public int hashCode() {
	return name.hashCode();
    }

    /**
     * Return a string representation of this object.
     *
     * @return a string representing this object
     */
    public String toString() {
	return "TestDescription[" + name + "]";
    }

    /**
     * Returns the codebase for the test. The codebase is tagged (usually
     * in the test descriptor) by the parameter named <code>testCodebase</code>.
     * If this value is not defined, the codebase of the harness
     * is returned. All http URL's are replaced with httpmd URL's
     * if the test must support codebase integrity.
     *
     * @return the codebase for the test
     */
    String getCodebase() throws TestException {
        String codebase = config.getStringConfigVal("testCodebase", null);
	if (codebase == null) {
	    codebase = ClassLoading.getClassAnnotation(getClass());
	}
	return config.genIntegrityCodebase(codebase, null);
    }

    /**
     * Returns the policy file for the test. The policy file is tagged
     * (usually in the test descriptor) by the property name
     * <code>testPolicyfile</code>. The policy must be expressed as
     * an absolute path. It is expected that the path will be expressed
     * in a config file in terms of <code>org.apache.river.qa.home</code>.
     *
     * @return the policy file string 
     * @throws TestException if <code>testPolicyfile</code> is not defined
     */
    String getPolicyFile() throws TestException {
        String policyFile = config.getStringConfigVal("testPolicyfile", null);
	if (policyFile == null) {
	    throw new TestException("testPolicyfile property must be defined");
	}
	return config.getComponentURL(policyFile, this).toString();
    }

    /**
     * Returns the classpath for the test. The classpath is tagged
     * (usually in the test descriptor) by the property name
     * <code>testClasspath</code>. All components of the classpath
     * must be provided by this property.
     *
     * @return the classpath for the test
     * @throws TestException if <code>testClasspath</code> is undefined
     */
    String getClasspath() throws TestException {
	String classpath = config.getStringConfigVal("testClasspath", null);
	if (classpath == null) {
	    throw new TestException("testClasspath must be defined");
	}
	String addition = 
	    config.getStringConfigVal("testClasspathAddition", null);
	if (addition != null) {
	    addition = addition.trim();
	    if (addition.length() > 0) {
		classpath += File.pathSeparator + addition;
	    }
	}
	String globalcp = config.getStringConfigVal("globalclasspath", null);
	if (globalcp != null) {
	    globalcp = globalcp.trim();
	    if (globalcp.length() > 0) {
		classpath += File.pathSeparator + globalcp;
	    }
	}
	return classpath;
    }

    /**
     * Returns the jvm to use for the test. The VM is tagged by the
     * property name <code>testjvm</code>.  If this property is
     * undefined, then the return value is constructed by appending
     * <code>"/bin/java"</code> (on Unix) or <code>"\bin\java"</code>
     * (on windows) to the value of the property named
     * <code>java.home</code>.
     *
     * @return the classpath for the test VM
     */
    String getJVM() {
	String testJVM = 
	    config.getStringConfigVal("org.apache.river.jdk.home", null);
        if (testJVM == null) {
            testJVM = System.getProperty("java.home");
            if (testJVM == null) {
		// should never happen
		throw new IllegalArgumentException("java.home undefined");
	    }
	}
	testJVM = testJVM + File.separator + "bin" + File.separator + "java";
	return testJVM;
    }

    /**
     * Returns the VM arguments to use for the test. The arguments are tagged by
     * the property name <code>testjvmargs</code>, and are merged with the array
     * returned by calling getGlobalVMArgs. Additionally, if the wrapper to be
     * used is 'MainWrapper', then values are appended for the system properties
     * <code>test.src</code> and <code>test.classes</code>. Optional
     * properties are not discarded by this method. The string returned
     * by <code>testjvmargs</code> contains components separated by
     * ',' characters. There must be no cosmetic whitespace since filenames
     * may include whitespace.
     *
     * @return the VM arguments for the test, or a zero length array
     *         if undefined 
     */
    String[] getJVMArgs() {
	String[] vmArgs = config.getGlobalVMArgs();
	String argString = config.getStringConfigVal("testjvmargs", "");
	vmArgs = config.mergeArrays(vmArgs, 
				    config.parseArgList(argString));
	if (vmArgs == null) {
	    vmArgs = new String[0];
	}
	return vmArgs;
    }

    /**
     * Get the test arguments. This default implementation
     * assumes that <code>MasterTest</code> will be invoked, and
     * constructs an argument list consisting of the name of
     * the test. The original command line args will be passed
     * in the serialized config given to the wrapper.
     *
     * @return the completeVM argument list, which is never null
     */
    String[] getTestArgs() {
	String[] args = new String[]{getName()};
	return args;
    }

    /**
     * Return the wrapper class name for the test VM main class.  If this is the
     * master host, then the value returned is that obtained by searching the
     * configuration for the key <code>testWrapper</code>. If this key is not
     * found, the default value <code>org.apache.river.qa.harness.MasterTest</code>
     * is returned. If this is a slave host, then the value
     * <code>org.apache.river.qa.harness.SlaveTest</code> is returned.
     *
     * @return the class name of the test wrapper
     */
    String getWrapperClassName() {
	String wrapper = null;
	if (config.isMaster()) {
	    wrapper = 
	        config.getStringConfigVal("testWrapper",
					  "org.apache.river.qa.harness.MasterTest");
	} else {
	    wrapper = "org.apache.river.qa.harness.SlaveTest";
	}
	return wrapper;
    }

    /**
     * Generate the command line for running this test in a VM. The command
     * generated consists of:
     * <ul>
     * <li>The path to the VM obtained by calling <code>getJVM</code>
     * <li>if <code>getPolicyFile</code> returns a non-null value, a property 
     *     definition for <code>java.security.policy</code> using that value
     * <li>if <code>getCodebase</code> returns a non-null value, a property 
     *     definition for <code>java.server.rmi.codebase</code> using that value
     * <li>if the system property <code>java.util.logging.config.file</code>
     *     is defined, that property is defined for the test VM
     * <li>the token <code>-cp</code>
     * <li>the value returned by <code>getClasspath</code>, which must be
     *     non-null and must define the complete class path for the test
     * <li>the set of additional VM options returned by <code>getJVMArgs</code>
     * <li>the name of the class to run given by <code>getWrapperClassName</code>
     * <li>a set of arguments obtained by calling <code>getTestArgs</code>
     * </ul>
     * @return the array of command line tokens
     */
    String[] getCommandLine(Properties overrides) throws TestException {
	config.setOverrides(overrides);
	String testClass   = getTestClassName();
	ArrayList cmdList = new ArrayList(10);
	cmdList.add(getJVM());
        // Uncomment the following line if you want to debug permission requests
//        cmdList.add("-Djava.security.manager=org.apache.river.tool.SecurityPolicyWriter");
//        cmdList.add("-Djava.security.manager=java.lang.SecurityManager");
        cmdList.add("-Djava.security.manager=org.apache.river.api.security.CombinerSecurityManager");
	cmdList.add("-Djava.security.policy=" + getPolicyFile());
	if (getCodebase() != null) {
	    cmdList.add("-Djava.rmi.server.codebase=" + getCodebase());
	}
	cmdList.add("-cp");
	cmdList.add(getClasspath());
	// all options follow -cp, making them easy to find in the log
	String[] vmArgs = getJVMArgs();
	String[] options = config.extractOptions(vmArgs);
	for (int i = 0; i < options.length; i++) {
	    cmdList.add(options[i]);
        }
	// extractproperties will discard undefined optional properties
	// testhosts property is expected to be included here if defined
	boolean discardLoggingProps = false;
	String loggingProps = config.getStringConfigVal("testLoggingProperties", null);
	if (loggingProps != null) {
	    propAdd(cmdList, "java.util.logging.config.file", loggingProps);
	    discardLoggingProps = true;
	}
	String[] props = config.extractProperties(vmArgs);
	for (int i = 0; i < props.length; i += 2) {
	    if (discardLoggingProps && props[i].equals("java.util.logging.config.file")) {
		continue;
	    }
	    propAdd(cmdList, props[i], props[i + 1]);
        }
	cmdList.add(getWrapperClassName());
	String[] args = getTestArgs();
	for (int i = 0; i < args.length; i++) {
	    cmdList.add(args[i]);
 	}
	String[] cmdArray = new String[0];
	cmdArray = (String[]) cmdList.toArray(cmdArray);
	config.setOverrides(null);
	return cmdArray;
    }

    /**
     * Format the given name and value as a property definition and
     * add it to <code>cmdList</code>.
     *
     * @param cmdList the command list
     * @param name the property name
     * @param val the property value
     */
    private void propAdd(ArrayList cmdList, String name, String val) {
	cmdList.add("-D" + name + "=" + val);
    }

    /**
     * Return the working directory for test execution. A return value
     * of <code>null</code> indicates that the test should inherit the
     * working directory of the harness
     *
     * @return the working directory for test execution
     */
    File getWorkingDir() {
	return null;
    }

    public String getShortName() {
	String shortName = name.replace('\\', '/'); // xxx must I?
	int index = shortName.lastIndexOf('/');
	if (index >= 0) {
	    // XXX breaks if name terminates with /
	    shortName = shortName.substring(index + 1);
	}
	if (shortName.endsWith(".td")) {
	    shortName = shortName.substring(0, shortName.length() - 3);
	}
	return shortName;
    }
}
