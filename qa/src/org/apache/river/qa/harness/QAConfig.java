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


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.ProxyPreparer;
import net.jini.url.httpmd.HttpmdUtil;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.Valid;
import org.apache.river.api.net.RFC3986URLClassLoader;
import org.apache.river.start.ClassLoaderUtil;
import org.apache.river.system.CommandLine.BadInvocationException;
import org.apache.river.system.MultiCommandLine;

/**
 * This class represents the environment for tests running in
 * the QA test harness. 
 * <p>
 * This class implements the search policy for obtaining test
 * parameter values. The sources of test parameters, in order 
 * of precedence, are:
 * <ul>
 * <li>the overrides, explicitly set by the <code>setOverrides</code> method
 * <li>the dynamic properties
 * <li>the system properties
 * <li>the command line arguments
 * <li>the test Configuration
 * <li>the test description
 * <li>the Configuration set property file
 * <li>the user provided property file
 * <li>the default property file 
 * <li>the default specified by the test
 * </ul>
 * This set of data sources is collectively referred to as the
 * <code>test properties</code>. The overrides exist to 
 * allow the harness to temporarily redefine the values for installation
 * properties such as <code>org.apache.river.jsk.home</code> or
 * <code>org.apache.river.qa.home</code>. This is needed when generating the
 * command line for the SlaveTest VM for local parameters that
 * must take precedence over the serialized <code>QAConfig</code>
 * instance provided by the master.
 * <p>
 * The dynamic properties exist to allow a test to create or override
 * a test property on-the-fly. This would most likely be used if
 * a test needed to construct a parameter needed by an admin, for
 * instance to provide a generated codebase. Dynamic properties are
 * automatically reset for every test, and are rarely used. The
 * other sources are treated as immutable objects.
 * <p>
 * The test description file generally provides the parameters used
 * by the harness to locate and execute a test, as well as test 
 * specific property values.
 * <p>
 * The net.jini.config.Configuration object associated with this test
 * configuration. This object may contain values which have relavance only to
 * particular configurations in a test run (for instance, the value of
 * <code>org.apache.river.qa.harness.integrityhash</code> is only meaningful for
 * secure configuations). The <code>ConfigurationFile</code> from which the
 * <code>Configuration</code> is derived is named by the
 * <code>testConfiguration</code> property in the test description.
 * <p>
 * The configuration set property file contains properties which should
 * be applied to all tests executing in a given configuration. This
 * property file is named <code>configSet.properties</code> and resides
 * in the root directory of the configuration set.
 * <p>
 * The command-line property file is the primary place for those who run
 * tests to provide installation values, or to override the defaults provided
 * by qaDefaults.
 * <p>
 * The default configuration file is named 
 * <code>org.apache.river.qa.resources.qaDefaults</code>.
 * <p>
 * Before any parameter value is returned, '$' substitutions
 * are performed (recursively). Any occurances of the
 * token <code>&lt;gethost&gt;</code> are replaced with the name of
 * the host on which this code is running. Any occurances of the
 * token <code>&lt;harnessJar&gt;</code> are replaced with the fully
 * qualified path of the harness JAR file. Any occurances of the
 * token <code>&lt;testJar&gt;</code> are replaced with the fully
 * qualified path of the test JAR file. 
 * <p>
 * Any tokens of the form &lt;url:foo&gt; will result in the generation
 * of a URL for <code>foo</code>. A search is performed for the file relative to
 * all of the root directories defined by the <code>searchPath</code>
 * property. If the file is found, a file URL for the fully qualified
 * name of the file is generated and returned as the token value. Otherwise,
 * test and harness JAR files are searched for <code>foo</code>, looking
 * first relative to the directory in which the test description file is 
 * located, then relative to the root of the test jar file and finally
 * relative to the root of the harness jar file. In this case, an appropriate
 * JAR file URL is generated and returned as the token value. If the
 * file is not found, a <code>TestException</code> is thrown.
 * <p>
 * Any tokens of the form &lt;file:foo&gt; will result in the generation of a
 * fully qualified path for <code>foo</code>. A search is performed for the file
 * relative to all of the root directories defined by the
 * <code>searchPath</code> property. If the file is found, the fully qualified
 * name of the file is generated and returned as the token value. If the file is
 * not found, a <code>TestException</code> is thrown.
 * <p>
 * This class is serializable. It's constructor is called only in
 * the master/slave harness VMs. The instance is serialized and
 * passed from the master harness to the master test through its System.in
 * stream. The master harness also passes a serialized copy to the
 * slave harness in a SlaveTestRequest object, which in
 * turn passes the copy to the slave test VM though its System.in stream.
 * <p>
 * The logger named <code>org.apache.river.qa.harness.config</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr><td>SEVERE <td>if a parsing error when doing string substitution, on
 *                   failure to find the component-level resource file, or
 *                   on failure to start a shared group
 *
 *<tr> <td> FINE <td>any calls to <code>logDebugText</code>
 *</table> <p>
 */
@AtomicSerial
public final class QAConfig implements Serializable {

    /** static ref for access by utils which don't accept a config arg */
    private static QAConfig harnessConfig;

    /*
     * This field is static for historical reasons. testLocatorConstraints is
     * lazily instantiated in response to the first call to 
     * <code>getConstrainedLocator</code> in this VM. Lazy instantiation
     * eliminates a premature instantiation problem in the master/slave
     * harnesses which do not have the installation properties
     * defined which are referenced by the configuration.
     */
    private static MethodConstraints testLocatorConstraints;

    /** A unique string which is used to generate unique group names. */
    private String uniqueString;

    /** the configuration override providers */
    private List overrideProviders;

    /** the failure analyzers */
    private List failureAnalyzers;

    /** Properties obtained from the user configuration file. */
    private Properties configProps;

    /** properties obtained from the configuration group file */
    private Properties configSetProps;

    /** Properties obtained from qaDefaults.properties. */
    private Properties defaultProps;

    /** The test <code>Configuration</code> object, not serializable*/
    private transient Configuration configuration;

    /** The TestDescription */
    private TestDescription td;

    /** Dynamic properties set on-the-fly by tests. */
    private Properties dynamicProps;

    /** Override properties, for cmd lines with non-default install props */
    private Properties propertyOverrides = null;

    /** The original command line arguments. */
    private String[] args;

    /** The configuration group tags for the test run */
    private String[] configTags;

    /** The currently active configuration group tag */
    private String currentTag;

    /** The key name to track */
    private String trackKey;

    /** The set of hosts participating in this test run */
    private List<String> hostList;

    /** The host index memory for the uniformrandom selection policy */
    private List selectedIndexes;

    /** The resolver */
    private Resolver resolver;

    /** the jar file containing the tests */
    private String testJar = null;

    /** the harness jar file name */
    private String harnessJar = null;

    /** the test class loader */
    private transient ClassLoader testLoader;

    /** the search list, usually empty */
    private String[] searchList = new String[0];

    /** lock object for suspend/resume run */
    private transient Object runLock = new Object();

    /** suspended state (for UI) */
    private boolean testSuspended = false;

    /** 
     * The host selection index for the selection policy.
     * It's intiialized to 1 in case the roundrobin policy
     * is being used, which begins with the first slave.
     */
    private int hostIndex = 1;

    /** The rerun pass counter */
    private int passCount;

    private boolean callAutot = false;
    
    private int testTotal = 0;

    private int testIndex = 0;

    private String resumeMessage;
    
    public QAConfig(GetArg arg) throws IOException{
	this(arg.get("uniqueString", null, String.class),
	    arg.get("overrideProviders", null, List.class),
	    arg.get("failureAnalyzers", null, List.class),
	    (Properties) Valid.copyMap(arg.get("configProps", null, Map.class), (Map) new Properties(), String.class, String.class, 1),
	    (Properties) Valid.copyMap(arg.get("configSetProps", null, Map.class),(Map) new Properties(), String.class, String.class, 1),
	    (Properties) Valid.copyMap(arg.get("defaultProps", null, Map.class), (Map) new Properties(), String.class, String.class, 1),
	    arg.get("td", null, TestDescription.class),
	    (Properties) Valid.copyMap(arg.get("dynamicProps", null, Map.class), (Map) new Properties(), String.class, String.class, 1),
	    (Properties) Valid.copyMap(arg.get("propertyOverrides", null, Map.class), (Map) new Properties(), String.class, String.class, 1),
	    arg.get("args", null, String[].class),
	    arg.get("configTags", null, String[].class),
	    arg.get("currentTag", null, String.class),
	    arg.get("trackKey", null, String.class),
	    arg.get("hostList", null, List.class),
	    arg.get("selectedIndexes", null, List.class),
	    arg.get("resolver", null, Resolver.class),
	    arg.get("testJar", null, String.class),
	    arg.get("harnessJar", null, String.class),
	    arg.get("searchList", null, String[].class),
	    arg.get("testSuspended", false),
	    arg.get("hostIndex", 0),
	    arg.get("passCount", 0),
	    arg.get("callAutot", false),
	    arg.get("testTotal", 0),
	    arg.get("testIndex", 0),
	    arg.get("resumeMessage", null, String.class)
	);
    }
    
    private QAConfig(
	    String uniqueString,
	    List overrideProviders,
	    List failureAnalyzers,
	    Properties configProps,
	    Properties configSetProps,
	    Properties defaultProps,
	    TestDescription td,
	    Properties dynamicProps,
	    Properties propertyOverrides,
	    String[] args,
	    String[] configTags,
	    String currentTag,
	    String trackKey,
	    List hostList,
	    List selectedIndexes,
	    Resolver resolver,
	    String testJar,
	    String harnessJar,
	    String[] searchList,
	    boolean testSuspended,
	    int hostIndex,
	    int passCount,
	    boolean callAutot,
	    int testTotal,
	    int testIndex,
	    String resumeMessage)
    {
	this.uniqueString = uniqueString;
	this.overrideProviders = overrideProviders;
	this.failureAnalyzers = failureAnalyzers;
	this.configProps = configProps;
	this.configSetProps = configSetProps;
	this.defaultProps = defaultProps;
	this.td = td;
	this.dynamicProps = dynamicProps;
	this.propertyOverrides = propertyOverrides;
	this.args = args;
	this.configTags = configTags;
	this.currentTag = currentTag;
	this.trackKey = trackKey;
	this.hostList = hostList;
	this.selectedIndexes = selectedIndexes;
	this.resolver = resolver;
	this.testJar = testJar;
	this.harnessJar = harnessJar;
	this.searchList = searchList;
	this.testSuspended = testSuspended;
	this.hostIndex = hostIndex;
	this.passCount = passCount;
	this.callAutot = callAutot;
	this.testTotal = testTotal;
	this.testIndex = testIndex;
	this.resumeMessage = resumeMessage;
    }

    /**
     * Static accessor for the config instance.
     *
     * @return the config object
     * @throws IllegalStateException if the config hasn't been built yet
     */
    public static QAConfig getConfig() {
        if (harnessConfig == null) {
            throw new IllegalStateException("harnessConfig not yet defined");
        }
        return harnessConfig;
    }

    /**
     * Return the path to the QA home (installation) directory
     *
     * @return the path to the QA kit
     */
    public String getKitHomeDir() {
	return getStringConfigVal("org.apache.river.qa.home", null);
    }

    /**
     * Return the path to the JSK home (installation) directory
     *
     * @return the path to the JSK
     */
    public String getJSKHomeDir() {
	return getStringConfigVal("org.apache.river.jsk.home", null);
    }

    /**
     * The logger, with an associated static initializer that creates a
     * org.apache.river.qa.harness level logger with a specialized handler which
     * logs to <code>System.out</code> and formats more concisely than
     * <code>SimpleFormatter</code>.
     */
    protected static Logger logger;
    private static ReportHandler reportHandler;
    static {
        logger = Logger.getLogger("org.apache.river.qa.harness");
	reportHandler = new ReportHandler();
        logger.addHandler(reportHandler);
        logger.setUseParentHandlers(false);
    }

    /**
     * Constructs a new instance of this class, and loads the configuration
     * files which are global to every test. Specifically:
     * <ul>
     * <li>the command-line configuration file is loaded
     * <li>the default configuration file is loaded
     * <li>mandatory user supplied parameters are validated
     * <li>the host list is built if running distributed
     * </ul>
     * The command-line configuration file must define the
     * following properties:
     * <ul>
     * <li>org.apache.river.jsk.home
     * <li>org.apache.river.qa.home
     * </ul>
     * and the values of these parameters must resolve to directories
     * which exist.
     * <p>
     * When the master/slave harnesses exec VMs, the values of these
     * parameters are supplied on the command line as system properties.
     * This allows the harnesses to override the default values. The
     * values of these properties must be provided as system properties
     * so they can be accessed from policy/configuration files. Since
     * the harness VMs do not run with these properties set, the
     * master/slave harnesses are expected to run with policy.all, and
     * cannot access configuration objects which have dependencies on
     * these system properties.
     * <p>
     * Note that the manager field is not initialized in the constructor.
     * The manager is not used by the master harness. The field
     * is initialized in the <code>readObject</code> method when this class is
     * deserialized in the slave harness and  master/slave test VMs.
     *
     * @param args  the command line arguments passed to the harness
     * 
     * @throws TestException if the configuration file identified
     *                       by <code>args[0]</code> is empty or missing or
     *                       if org.apache.river.jsk.home is undefined or if
     *                       the directory it names does not exist or
     *                       if org.apache.river.qa.home is undefined or if
     *                       the directory it names does not exist
     *
     */
    public QAConfig(String[] args) throws TestException {
	this.hostList = new ArrayList();
	this.dynamicProps = new Properties();
	this.defaultProps = new Properties();
	this.configSetProps = new Properties();
	this.failureAnalyzers = new ArrayList();
	this.overrideProviders = new ArrayList();
	this.args = args;
	resolver = new Resolver(this);
	configProps = loadProperties(args[0]);
	if (configProps.size() == 0) {
	    throw new TestException("Config file" 
				  + " " + args[0] + " "
				  + "is empty or missing");
	}
	String jskDir = getStringConfigVal("org.apache.river.jsk.home", null);
	if (jskDir == null) {
	    throw new TestException("org.apache.river.jsk.home is undefined");
	}
	File jskFile = new File(jskDir);
	if (!jskFile.exists()) {
	    throw new TestException("The directory "
		                   + jskFile.toString() + " identified by "
		                   + "org.apache.river.jsk.home does not exist");
	}
	String qaDir = getStringConfigVal("org.apache.river.qa.home", null);
	if (qaDir == null) {
	    throw new TestException("org.apache.river.qa.home is undefined");
	}
	File qaFile = new File(qaDir);
	if (!qaFile.exists()) {
	    throw new TestException("The directory "
                                   + qaFile.toString() + " identified by "
		                   + "org.apache.river.qa.home does not exist");
	}
	harnessJar = getHarnessJar();
	if (harnessJar != null) {
	    resolver.setToken("harnessJar", harnessJar);
	}
	testJar = getStringConfigVal("testJar", null);
	if (testJar != null) {
	    resolver.setToken("testJar", testJar);
	}
	buildSearchList(getStringConfigVal("searchPath", ""));
	/*
	 * The default file is loaded after the command-line
	 * file is loaded and installation properties validated
	 * since it's location is specified relative to
	 * the the harness root url.
	 */
	defaultProps = 
	    loadProperties("org/apache/river/qa/resources/qaDefaults.properties");
	trackKey = getParameterString("track");
	harnessConfig = this;
	setHostNameToken();
	// set the test class loader
	if (testJar == null) {
	    logger.log(Level.INFO, "Warning: testjar is not defined");
	    testLoader = getClass().getClassLoader();
	} else {
	    File testJarFile = new File(testJar);
	    if (! testJarFile.exists()) {
		throw new TestException("test jar found not found: " + testJar);
	    }
	    try {
		URL testJarURL = testJarFile.getCanonicalFile().toURI().toURL();
		testLoader = new RFC3986URLClassLoader(new URL[]{testJarURL},
						getClass().getClassLoader());
	    } catch (Exception e) {
		throw new TestException("Failed to create test loader", e);
	    }
	}
	String hostNames = 
	    getStringConfigVal("org.apache.river.qa.harness.testhosts",
			       null);
	if (hostNames != null) {
	    StringTokenizer tok = new StringTokenizer(hostNames, "|");
	    if (tok.countTokens() > 1) {
		while (tok.hasMoreTokens()) {
		    hostList.add(tok.nextToken());
		}
	    }
	}
    }

    /**
     * Set the token &lt;gethost&gt; to have the value of the local host
     * name. If the property <code>org.apache.river.qa.harness.useAddress</code> is
     * defined and has the value <code>true</code>, the token is set to the
     * local host IP address rather than its name. The slave harness must
     * have access to this method to fix up the local slave reference
     * in the serialized config supplied by the master.
     */
    void setHostNameToken() {
	boolean useAddress = 
	    getBooleanConfigVal("org.apache.river.qa.harness.useAddress", false);
	String hostName = (useAddress ? "127.0.0.1" : "localhost");
	try {
	    InetAddress address = InetAddress.getLocalHost();
	    hostName = (useAddress ? address.getHostAddress() 
			           : address.getHostName());
	} catch (UnknownHostException ex) { 
	    ex.printStackTrace();
	}
	resolver.setToken("gethost", hostName);
    }

    /**   
     * Build the search path used to resolve the &lt;url:&gt; and &lt;file:&gt;
     * tokens. The slave harness must have access to this method to fix up the
     * local slave search path in the serialized config supplied by the master.
     * 
     * @param searchPath a string containing a comma separated list of
     *                   paths
     */
    void buildSearchList(String searchPath) {
	ArrayList list = new ArrayList();
	StringTokenizer tok = new StringTokenizer(searchPath, ",");
	while (tok.hasMoreTokens()) {
	    String path = tok.nextToken();
	    if (path.trim().length() == 0 || list.contains(path)) {
		continue;
	    }
	    list.add(path);
	}
	String qaDir = getStringConfigVal("org.apache.river.qa.home", null);
	if (!list.contains(qaDir)) {
	    list.add(qaDir);
        }
	searchList = (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Perform custom deserialization actions. Set static and transient
     * fields. <code>OverrideProviders</code> referenced by
     * <code>loadTestConfiguration</code> are assumed to have been
     * installed before serialization was performed.
     */
    private void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	stream.defaultReadObject();
	harnessConfig = this;
	runLock = new Object();
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'int'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public int getIntConfigVal(String propertyName, int defaultVal) {
        int val = defaultVal;
        String strVal = getParameterString(propertyName);
	if (strVal != null) {
	    try {
		val = Integer.parseInt(strVal);
	    } catch (NumberFormatException ignore) { }
	}
        return val;
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'long'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public long getLongConfigVal(String propertyName, long defaultVal) {
        long val = defaultVal;
        String strVal = getParameterString(propertyName);
	if (strVal != null) {
	    try {
		val = Long.parseLong(strVal);
	    } catch (NumberFormatException ignore) { }
	}
        return val;
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'float'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public float getFloatConfigVal(String propertyName, float defaultVal) {
        float val = defaultVal;
        String strVal = getParameterString(propertyName);
	if (strVal != null) {
	    try {
		float tmpval = (Float.valueOf(strVal)).floatValue();
		if (!(Float.isInfinite(tmpval))&&!(Float.isNaN(tmpval)) ) {
		    val = tmpval;
		}
	    } catch (NumberFormatException ignore) { }
	}
        return val;
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'double'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public double getDoubleConfigVal(String propertyName, double  defaultVal) {
        double val = defaultVal;
        String strVal = getParameterString(propertyName);
	if (strVal != null) {
	    try {
		double tmpval = (Double.valueOf(strVal)).doubleValue();
		if (!(Double.isInfinite(tmpval))&&!(Double.isNaN(tmpval))){
		    val = tmpval;
		}
	    } catch (NumberFormatException ignore) { }
	}
        return val;
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'String'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public String getStringConfigVal(String propertyName,
                                     String defaultVal) {
        String strVal = getParameterString(propertyName);
	return (strVal != null ? strVal : defaultVal);
    }

    public String getString(String propertyName) {
        String strVal = getParameterString(propertyName);
	if (strVal == null) {
	    throw new IllegalArgumentException("test property undefined: " 
					       + propertyName);
	}
	return strVal;
    }

    /**
     * This method will retrieve from the locally stored property list,
     * the value of a property whose name corresponds to the string
     * contained in the input argument "propertyName" and whose value
     * is of type 'boolean'.
     * <p>
     * If the property list does not exist, or if the string value of the
     * property can not be "cast" to the expected data type, then the
     * default value will be returned.
     */
    public boolean getBooleanConfigVal(String propertyName,boolean defaultVal) {
        boolean val    = defaultVal;
        String  strVal = getParameterString(propertyName);
	if (strVal != null) {
	    val = (Boolean.valueOf(strVal)).booleanValue();
	}
        return val;
    }

    /**
     * Return the path to the harness JAR file. Each JAR file in the
     * classpath is examined. The first one found that contains
     * <code>QARunner.class</code> is assumed to be the harness JAR
     * file. 
     *
     * @return the path for the harness JAR file
     */
    private String getHarnessJar() {
	String classpath = System.getProperty("java.class.path");
	if (classpath == null) {
	    throw new IllegalStateException("no value for java.class.path");
	}
	StringTokenizer tok = 
	    new StringTokenizer(classpath, File.pathSeparator);
	while (tok.hasMoreTokens()) {
	    String path = tok.nextToken();
	    JarFile jarFile;
	    try {
		jarFile = new JarFile(path);
	    } catch (IOException e) {
		logger.log(Level.FINEST, "failed to open jar file " + path, e);
		continue;
	    }
	    if (jarFile.getEntry("org/apache/river/qa/harness/QARunner.class") 
		                  != null) 
	    {
		return path;
	    }
	}
	return null;
    }

    /**
     * Return the url associated with <code>entryName</code>. If
     * <code>entryName</code> is already a url, it is returned. If
     * <code>entryName</code> is the name of an existing file, an appropriate
     * file URL is returned. Otherwise search the harness and test jars for the
     * given <code>entryName</code> and return the URL string for the first
     * match. The search is performed relative to the test directory, then
     * relative to the root of the test jar, and finally relative to the root of
     * the harness jar.
     *
     * @param entryName the name of the entry to locate, expressed as a 
     *        relative file name
     */
    public URL getComponentURL(String entryName, TestDescription td) throws TestException {
	try {
	    return new URL(entryName);
	} catch (MalformedURLException ignore) {
	}
	File entryFile = getComponentFile(entryName, td);
	if (entryFile != null) { 
	    try {
		return entryFile.getCanonicalFile().toURI().toURL();
	    } catch (Exception e) {
		throw new TestException("problem converting file to url", e);
	    }
	}
	try {
	    if (td == null) {
		td = getTestDescription(); // if undefined, get current
	    }
	    if (td != null) {
		String tdDir = td.getName().replace('\\', '/');
		tdDir = tdDir.substring(0, tdDir.lastIndexOf("/"));
		
		String fqEntry = canonicalize(tdDir + "/" + entryName);
		logger.log(Level.FINEST, "checking test jar file for " + fqEntry);
		if (getJarEntry(testJar, fqEntry) != null) {
		    return new URL("jar:file:" + testJar.replace('\\', '/') + "!/" + fqEntry);
		}
	    }
	    if (getJarEntry(testJar, entryName) != null) {
		return new URL("jar:file:" + testJar.replace('\\', '/') + "!/" + entryName);
	    }
	    if (getJarEntry(harnessJar, entryName) != null) {
		return new URL("jar:file:" + harnessJar.replace('\\', '/') + "!/" + entryName);
	    }
	} catch (MalformedURLException e) {
	    throw new TestException("failed to construct entry URL", e);
	}
	throw new TestException("no jar entry found for " + entryName);
    }

    /**
     * Return the <code>File</code> associated with <code>entryName</code>. 
     * The working directory and components of the <code>searchPath</code>
     * are searched for a file having the name <code>enterName</code>.
     *
     * @param entryName the name of the entry to locate, expressed as a 
     *        relative file name
     * @return the associated <code>File</code>, or <code>null</code> if no
     *        file is found
     */
    public File getComponentFile(String entryName, TestDescription td) {
	File entryFile = new File(entryName);
	try {
	    if (entryFile.exists()) {
		return entryFile;
	    }
	} catch (SecurityException e) {
	}
	logger.log(Level.FINEST, "failed existance check on " + entryFile);
	for (int i = 0; i < searchList.length; i++) {
	    String base = searchList[i] + "/";
	    String fqName;
	    if (td == null) {
		td = getTestDescription();
	    }
	    if (td != null) {
		String tdDir = td.getName().replace('\\', '/');
		tdDir = tdDir.substring(0, tdDir.lastIndexOf("/"));
		
		fqName = canonicalize(base + tdDir + "/" + entryName);
		entryFile = new File(fqName);
		try {
		    if (entryFile.exists()) {
			return entryFile;
		    }
		} catch (SecurityException e) {
		    logger.log(Level.FINEST, "Can't access " + entryFile, e);
		}
		logger.log(Level.FINEST, 
			   "failed existance check on " + entryFile);
	    }
	    fqName = base + entryName;
	    entryFile = new File(fqName);
	    try {
		if (entryFile.exists()) {
		    return entryFile;
		}
	    } catch (SecurityException e) {
		logger.log(Level.FINEST, "Can't access " + entryFile, e);
	    }
	    logger.log(Level.FINEST, 
		       "failed existance check on " + entryFile);
	}
	return null;
    }


    /**
     * Canonicalize a path, eliminating relative references such
     * as '.' and '..'
     *
     * @param path the path to canonicalize
     * @return the new path
     */
    private String canonicalize(String path) {
	ArrayList list = new ArrayList();
	StringTokenizer tok = new StringTokenizer(path, "/\\");
	while (tok.hasMoreTokens()) {
	    String component = tok.nextToken();
	    if (component.equals(".")) {
		continue;
	    } else if (component.equals("..")) {
		if (list.size() == 0) {
		    return path; // bad path, just bail
		}
		list.remove(list.size() - 1);
	    } else {
		list.add(component);
	    }
	}
	StringBuffer buf = new StringBuffer();
	if (path.startsWith("/")) {
	    buf.append("/");
	}
	for (int i = 0; i < list.size(); i++) {
	    if (i > 0) {
		buf.append("/");
	    }
	    buf.append((String) list.get(i));
	}
	return buf.toString();
    }


    /**
     * Return a <code>ZipEntry</code> from a JAR file.
     *
     * @param jarName the name of the jar file
     * @param entryName the name of the entry to retrieve
     * @return the entry, or null if not found
     * @throws TestException if an error occurs accessing the jar file
     */
    ZipEntry getJarEntry(String jarName, String entryName) 
	throws TestException 
    {
	if (jarName == null) {
	    return null;
	}
	try {
	    logger.log(Level.FINEST, 
		       "getting jar entry " + jarName + ":" + entryName);
	    JarFile jarFile = new JarFile(jarName);
	    return jarFile.getEntry(entryName);
	} catch (IOException e) {
	    throw new TestException("cannot access jar file " + jarName, e);
	}
    }

    /**
     * Convert the given <code>path</code> to an absolute path resolved
     * relative to the given <code>baseDir</code> if the path is not
     * already absolute.
     *
     * @param path the path to convert to an absolute path
     * @param baseDir the base directory if <code>path</code> is relative
     * @return an absolute value for <code>path</code>
     */
     String relativeToAbsolutePath(String baseDir, String path) {
	 File pathFile = new File(path);
	 if (pathFile.isAbsolute()) {
	     return path;
	 }
	 return new File(baseDir, path).toString();
     }

    /**
     * Create a properties object and load it from the contents of
     * <code>propName</code>.  <code>propName</code> may be specified as a 
     * URL or a path. If it is a path, then it is converted into a URL
     * by calling getComponentURL. 
     *
     * @param propName the path of the resource to load
     * @return a properites object initialized with the contents of the source
     *         identified by <code>propName</code>, or an empty properties 
     *         object if the source cannot be found or is null
     */
    public Properties loadProperties(String propName) throws TestException {
	if (propName == null) {
	    return new Properties();
	}
	URL propURL= null;
	try {
	    propURL = new URL(propName);
	} catch (MalformedURLException e) {
	    propURL = getComponentURL(propName, null);
	}
	if (propURL == null) {
	    logger.log(Level.INFO, "could not locate properties: " + propName);
	    return new Properties();
	}
	return loadProperties(propURL);
    }

    /**
     * Return a properties initialized by the properties file referenced by
     * the given URL.
     * 
     * @param propURL to URL for the properties file
     * @return the initialized properites object, or an empty  properties object
     *         if propURL cannot be loaded
     */
    Properties loadProperties(URL propURL) {
	Properties props = new Properties();
	try {
	    InputStream s = propURL.openStream();
	    props.load(s);
	    logger.log(Level.FINEST, "loaded properties: " + propURL);
	} catch (IOException e) {
	    logger.log(Level.INFO, "could not load properties: " + propURL, e);
	}
	return props;
    }

    private Properties getPropFromURL(String urlString, String relativeName) {
        logger.log(Level.FINEST, "attempting to load " + relativeName  + " from url " + urlString);
	Properties p = new Properties();
	if (urlString == null) { 
	    return p;
	}
	urlString = urlString + "/" + relativeName;
	try {
	    URL url = new URL(urlString);
	    InputStream s = url.openStream();
	    p.load(s);
	} catch (Exception e) {
	    logger.log(Level.FINEST, 
		       "failed to load properties from " + urlString);
	}
	return p;
    }

    /**
     * Determine whether the current test run is distributed or not.
     * A test run is distributed if the
     * <code>org.apache.river.qa.harness.testhosts</code> property
     * has more than one element.
     *
     * @return true if this test run is distributed
     */
    public static boolean isDistributed() {
        boolean distributed = false;
        String hostList = AccessController.doPrivileged(
	    new PrivilegedAction<String>() {
		public String run() {
		    return 
			System.getProperty(
			    "org.apache.river.qa.harness.testhosts");
		}
            }
        );
//        String hostList =
//            System.getProperty("org.apache.river.qa.harness.testhosts");
        if (hostList != null) {
            StringTokenizer tok = new StringTokenizer(hostList, "|");
            if (tok.countTokens() > 1) {
                distributed = true;
            }
        }
	return distributed;
    }

    /**
     * Creates a directory that is guaranteed to be unique on
     * the file system and return it as a <code>File</code> object.
     * 
     * @param prefix  the prefix string to be used in generating the directory
     *                name; must be at least three characters long.
     * @param suffix  the suffix string to be used in generating the directory
     *                name; may be null, in which case the suffix ".tmp" 
     *                will be used.
     * @param path    the path under which the directory is to be
     *                created, or null if the default temporary-file directory
     *                is to be used.
     * @return        a directory that is unique on the file system.
     */
    public File createUniqueDirectory(String prefix, String suffix, String path)
	throws IOException, TestException
    {
	String directoryName = createUniqueFileName(prefix, suffix, path);
	File file = new File(directoryName);
	file.mkdirs();
	return file;
    }

    /**
     * Create an absolute filename that is guaranteed to be unique on the 
     * file system.
     * 
     * @param prefix  the prefix string to be used in generating the file's
     *                name; must be at least three characters long.
     * @param suffix  the suffix string to be used in generating the file's
     *                name; may be null, in which case the suffix ".tmp" will
     *                be used.
     * @param path    the directory in which the file is to be created, or
     *                null if the default temporary-file directory is to be used
     * @return        a file name that is unique on the file system.
     * 
     */
    public String createUniqueFileName(String prefix, 
				       String suffix, 
				       String path)
	throws TestException
    {
	for (int i = 0; i < 5; i++) {
	    try {
		File temp = null;
		if (path != null) {
		    File directory = new File(path);
		    temp = File.createTempFile(prefix, suffix, directory);
		} else {
		    temp = File.createTempFile(prefix, suffix);
		}	    
		String filenamePath = temp.getAbsolutePath();
		if (!temp.delete()) {
		    throw new TestException("createUniqueFileName didn't "
					    + "remove temp file");
		}
		return filenamePath;
	    } catch (IOException e) {
		String retrying = (i < 4) ? ", retrying" : "";
		logger.log(Level.INFO,
			   "createTempFile failed"
			   +  ", prefix = " + prefix 
			   +  ", suffix = " + suffix 
			   +  ", path = " + path
			   + retrying,
			   e);
	    }
	}
	throw new TestException("Could not create temp file after 5 tries");
    }

    /**
     * Obtain the value of a service property interpreted as an int. The
     * property is identified by three tokens:
     * <ul>
     * <li> <code>serviceName</code>, the identifier for the service
     * <li> <code>propertyName</code>, the property name for the service
     * <li> <code>serviceIndx</code>, an instance count for the service
     * </ul>
     * A configuration property name is constructed by constructing
     * the string:
     * <pre>
     *       serviceName + "." + propertyName + "." + serviceIndx
     * </pre>
     * If a value cannot be found, <code>serviceIndx</code> is decremented
     * and another search is performed. This is repeated until
     * the index reaches zero. If no match has been found, a search
     * is done omitting the index.
     *
     * @param serviceName  the service prefix identifier
     * @param propertyName the specific service property identifier
     * @param serviceIndx  the service instance count
     *
     * @return the property value interpreted as an int. 
     *         If the property value could not be found,
     *         a value of <code>Integer.MIN_VALUE</code> is returned.
     */
    public int getServiceIntProperty(String     serviceName,
                                     String     propertyName,
                                     int        serviceIndx)
    {
	int retVal = Integer.MIN_VALUE;
	String value = getServiceParameter(serviceName, 
					   propertyName,
					   serviceIndx);
	if (value != null) {
	    try {
		retVal = Integer.parseInt(value);
	    } catch (NumberFormatException ignore) {
	    }
	}
	return retVal;
    }

    /**
     * Obtain the value of a service property interpreted as an int. The
     * property is identified by three tokens:
     * <ul>
     * <li> <code>serviceName</code>, the identifier for the service
     * <li> <code>propertyName</code>, the property name for the service
     * <li> <code>serviceIndx</code>, an instance count for the service
     * </ul>
     * A configuration property name is constructed by constructing
     * the string:
     * <pre>
     *       serviceName + "." + propertyName + "." + serviceIndx
     * </pre>
     * If a value cannot be found, <code>serviceIndx</code> is decremented
     * and another search is performed. This is repeated until
     * the index reaches zero. If no match has been found, a search
     * is done omitting the index.
     *
     * @param serviceName  the service prefix identifier
     * @param propertyName the specific service property identifier
     * @param serviceIndx  the service instance count
     * @param defaultVal   the value to return if the parameter is undefined
     *
     * @return the property value interpreted as an int. 
     */
    public int getServiceIntProperty(String serviceName,
				     String propertyName,
				     int serviceIndx,
				     int defaultVal) 
    {
	int val = getServiceIntProperty(serviceName, propertyName, serviceIndx);
	if (val == Integer.MIN_VALUE) {
	    val = defaultVal;
	}
	return val;
    }

    /**
     * Obtain the value of a service property interpreted as a boolean. The
     * property is identified by three tokens:
     * <ul>
     * <li> <code>serviceName</code>, the identifier for the service
     * <li> <code>propertyName</code>, the property name for the service
     * <li> <code>serviceIndx</code>, an instance count for the service
     * </ul>
     * A configuration property name is constructed by constructing
     * the string:
     * <pre>
     *       serviceName + "." + propertyName + "." + serviceIndx
     * </pre>
     * If a value cannot be found, <code>serviceIndx</code> is decremented
     * and another search is performed. This is repeated until
     * the index reaches zero. If no match has been found, a search
     * is done omitting the index.
     *
     * @param serviceName  the service prefix identifier
     * @param propertyName the specific service property identifier
     * @param serviceIndx  the service instance count
     * @param defaultVal   the default value if the parameter is not found
     *
     * @return the property value interpreted as a boolean, 
     */
    public boolean getServiceBooleanProperty(String     serviceName,
					     String     propertyName,
					     int        serviceIndx,
					     boolean    defaultVal)
    {
	String value = getServiceParameter(serviceName, propertyName, serviceIndx);
	if (value == null) {
	    return defaultVal;
	}
	return Boolean.valueOf(value).booleanValue();
    }

    /**
     * Obtain the value of a service property as a string. The
     * property is identified by three tokens:
     * <ul>
     * <li> <code>serviceName</code>, the identifier for the service
     * <li> <code>propertyName</code>, the property name for the service
     * <li> <code>serviceIndx</code>, an instance count for the service
     * </ul>
     * A configuration property name is constructed by constructing
     * the string:
     * <pre>
     *       serviceName + "." + propertyName + "." + serviceIndx
     * </pre>
     * If a value cannot be found, <code>serviceIndx</code> is decremented
     * and another search is performed. This is repeated until
     * the index reaches zero. If no match has been found, a search
     * is done omitting the index.
     *
     * @param serviceName  the service prefix identifier
     * @param propertyName the specific service property identifier
     * @param serviceIndx  the service instance count
     *
     * @return the parameter string matching the service parameters.
     *         Return <code>null</code> if no match is found.
     */
    public String getServiceStringProperty(String serviceName,
                                           String propertyName,
                                           int    serviceIndx)
    {
	return getServiceParameter(serviceName, propertyName, serviceIndx);
    }

    /**
     * Get the <code>String</code> value of a service property. The search
     * is first qualified by the type, unless the search is for type.
     *
     * @param serviceName  the service name
     * @param propertyName the property name
     * @param index        the service instance count
     * @return             the value of the property, or <code>null</code> if
     *                     the property is undefined
     */
    private String getServiceParameter(String prefix, 
				       String propertyName, 
				       int index)
    {
	String keyBase = prefix + ".type";
	String type = null;
	String serviceProp = null;
	for (int i = index; i >= 0; i--) {
	    type = getStringConfigVal(keyBase + "." + i, null);
	    if (type != null) {
		break;
	    }
	} 
	if (type == null) {
	    type = getStringConfigVal(keyBase, null);
	}
	if (propertyName.equals("type")) {
	    return type;
	}
	keyBase = prefix + "." + type + "." + propertyName;
	for (int i = index; i >= 0; i--) {
	    serviceProp = getStringConfigVal(keyBase + "." + i, null);
	    if (serviceProp != null) {
		return serviceProp;
	    }
	}
	serviceProp = getStringConfigVal(keyBase, null);
	if (serviceProp == null) {
	    keyBase = prefix + "." + propertyName;
	    for (int i = index; i >= 0; i--) {
		serviceProp = getStringConfigVal(keyBase + "." + i, null);
		if (serviceProp != null) {
		    return serviceProp;
		}
	    }
	    serviceProp = getStringConfigVal(keyBase, null);
	}
	return serviceProp;
    }


    /**
     * Obtain the value of a service property as a string. The
     * property is identified by three tokens:
     * <ul>
     * <li> <code>serviceName</code>, the identifier for the service
     * <li> <code>propertyName</code>, the property name for the service
     * <li> <code>serviceIndx</code>, an instance count for the service
     * </ul>
     * A configuration property name is constructed by constructing
     * the string:
     * <pre>
     *       serviceName + "." + propertyName + "." + serviceIndx
     * </pre>
     * If a value cannot be found, <code>serviceIndx</code> is decremented
     * and another search is performed. This is repeated until
     * the index reaches zero. If no match has been found, a search
     * is done omitting the index.
     *
     * @param serviceName  the service name
     * @param propertyName the service property identifier
     * @param serviceIndx  the service instance count
     * @param defaultVal   the value to return if the parameter is undefined
     *
     * @return the parameter string matching the service parameters.
     */
    public String getServiceStringProperty(String serviceName,
					   String propertyName,
					   int serviceIndx,
					   String defaultVal) 
    {
	String val = getServiceStringProperty(serviceName, 
					      propertyName, 
					      serviceIndx);
	if (val == null) {
	    val = defaultVal;
	}
	return val;
    }

    /**
     * Parses a string in which the tokens of the string are separated by the
     * delimiter contained in the <code>delimiter</code> parameter. If the
     * <code>delimiter</code> parameter is <code>null</code>, then the 
     * default delimiter of white space (space, tab, newline, and return)
     * will be used when parsing the input <code>String</code>. The tokens
     * obtained from parsing the input <code>String</code> are returned
     * in a <code>String</code> array.
     *
     * @param str       <code>String</code> to parse
     * @param delimiter <code>String</code> containing the delimiters to
     *                  use in the parsing process. If this parameter is 
     *                  <code>null</code>, white space will be used as
     *                  delimiter
     *
     * @return <code>String</code> array in which each element contains the
     *         corresponding token from the input <code>String</code>, or
     *         <code>null</code> if <code>str</code> is <code>null</code> or
     *         has no tokens.
     */
    public String[] parseString(String str, String delimiter){
        String[] strArray = null;
	if (str != null) {
	    StringTokenizer st = null;
	    if(delimiter == null) {
		st = new StringTokenizer(str);
	    } else {
		st = new StringTokenizer(str, delimiter);
	    }
	    int n = st.countTokens();
	    if (n > 0) {
		strArray = new String[n];
		for (int i=0; i<n; i++) {
		    strArray[i] = st.nextToken();
		}
	    }
	}
	return strArray;
    }

    /**
     * Parse the parameter string into an array of command-line argument
     * strings. Arguments must be separated only with ',' characters so
     * that file names with embedded white space are parsed property.
     * A '+' character can be used to escape a ',' character which must
     * be included in an argument. Leading ','s are discarded; multiple
     * consecutive ','s are treated as a single ','.
     *
     * @param str the string to parse
     * @return the array of command-line arguments
     */
    public String[] parseArgList(String str) {
	str = (str == null) ? "" : str;
	ArrayList list = new ArrayList();
	while (str.trim().length() > 0) {
	    String buffer = "";
	    while (true) {
		int comma = str.indexOf(",");
		if (comma < 0) {
		    buffer += str;
		    str = "";
		    break;
		}
		if (comma > 0 && str.charAt(comma - 1) == '+') {
		    buffer += str.substring(0, comma - 1) + ",";
		    if (comma + 1 < str.length()) {
			str = str.substring(comma + 1);
			continue;
		    } else {
			str = "";
			break;
		    }
		    
		}
		buffer += str.substring(0, comma);
		if (comma + 1 < str.length()) {
		    str = str.substring(comma + 1);
		} else {
		    str = "";
		}
		break;
	    }
	    // ignore doubled or leading commas
	    if (buffer.length() > 0) {
		list.add(buffer);
	    }
	}
	return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Parses a <code>String</code> using white space (space, tab, newline,
     * and return) as the delimiter.
     *
     * @param str       <code>String</code> to parse
     *
     * @return <code>String</code> array in which each element contains the
     *         corresponding token from the input <code>String</code>, or
     *         <code>null</code> if <code>str</code> is <code>null</code> or
     *         has no tokens.
     */
    public String[] parseString(String str){
        return parseString(str,null);
    }

    /**
     * This method parses the given string and determines if a particular string
     * token is a group name or a locator. If a token is a group name, a
     * sub-string is appended that results in a group name that has a high
     * probability of being unique with respect to other tests being run
     * simultaneously, and returns a new <code>String</code>, identical to the
     * input <code>String</code> except that each group name is replaced with
     * the new group name. This method produces the same results for the same
     * value of <code>str</code> on repeated calls for all participants for a
     * given test. This method may therefore be called by tests which generate
     * group names before starting services.
     *
     * @param str the <code>String</code> to parse and modify.
     * @return    a new <code>String</code>, identical to the input
     *            <code>String</code> except that each occurrence a group name
     *            is replaced with a new name, derived from the original name,
     *            that has a high probability of being unique with respect to
     *            other tests being run simultaneously.
     */
    public String makeGroupsUnique(String str){
	String ret = str;
	if (ret != null) {
	    StringTokenizer tok = new StringTokenizer(str, ", ", true);
	    StringBuffer buf = new StringBuffer();
	    while (tok.hasMoreTokens()) {
		String fragment = tok.nextToken();
		if (!(fragment.equals(",") || fragment.equals(" "))) {
		    fragment = makeGroupUnique(fragment, 
					       getUniqueString());
		}
		buf.append(fragment);
	    }
	    ret = buf.toString();
	}
	return ret;
    }

     /**
     * Determines if the input <code>String</code> represents a group name or
     * a locator. If the input parameter represents a group name, this method
     * appends a sub-string that results in a group name that has a high
     * probability of being unique. If the input parameter represents a
     * locator, the original <code>String</code> is returned.
     *
     * @param baseStr   <code>String</code> containing group to make unique.
     * @param appendStr <code>String</code> to append to <code>baseStr</code>
     *                  parameter. 
     *
     * @return <code>String</code> identical to the input parameter if the
     *         input parameter represents a locator; otherwise, a new
     *         <code>String</code>, derived from the input parameter, in which
     *         a unique sub-string is appended.
     */
    private String makeGroupUnique(String baseStr, String appendStr) {
        if(!(baseStr == null || isLocator(baseStr) || specialGroup(baseStr))) {
	    baseStr = baseStr + "_" + appendStr;
	}
	return baseStr;
    }

    /**
     * Return a boolean indicating whether <code>group</code> is one of
     * the strings "none", "all", or "public"
     *
     * @param group the string to check for a special group name
     * @return <code>true</code> if <code>group</code> is a special name
     */
    private boolean specialGroup(String group) {
	return    group.equals("none") 
	       || group.equals("all") 
	       || group.equals("public");
    }

    /** 
     * Determines if <code>str</code> represents a <code>LookupLocator</code>.
     *
     * @param str <code>String</code> to analyze.
     *
     * @return <code>true</code> if the input parameter represents a 
     *         <code>LookupLocator</code>; <code>false</code> otherwise.
     */
    public boolean isLocator(String str) {
        if(str != null) {
	    try {
		new LookupLocator(str);
		return true;
	    } catch(MalformedURLException e) {
	    }
	}
	return false;
    }

    /** 
     * Generates a <code>String</code> that has a high probability of being
     * unique with respect to any other invocations of this method by other
     * tests on any machine.
     *
     * @return <code>String</code> that has a high probability of being
     *         unique with respect to any other invocations of this method
     *         by other tests.
     */
    private String getUniqueString() {
	if (uniqueString == null) {
	    initUniqueString();
	}
	return uniqueString;
    }

    /**
     * Initialize the value of the unique string.
     */
    private void initUniqueString() {
	uniqueString = getLocalHostName() + "_" + System.currentTimeMillis();
    }

    /**
     * Tests for the existence of the ActivationSystem. This method
     * will always make at least one attempt to verify the existence of a
     * a running ActivationSystem.
     *
     * @param n <code>int</code> value representing the number of additional
     *          attempts (beyond the initial attempt) to wait for the
     *          activation system to come up. This values translates
     *          directly to the number of seconds to wait for the
     *          activation system.
     *
     * @return <code>true</code> if the activation system is up and running;
     *         <code>false</code> otherwise
     */
     boolean activationUp(int n) {
        /* First attempt */
	Exception lastException = null;
        try {
            ActivationGroup.getSystem();
            return true;
        } catch (ActivationException e) { 
	    lastException = e;
	}
        /* Make a new attempt every second for n seconds */
        for(int i=0; i<n; i++) {
            try {
                ActivationGroup.getSystem();
                return true;
            } catch (ActivationException e) { 	  
		lastException = e;
	    }
            try {
                Thread.sleep(1000); // wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
        }
        }
	if (lastException != null) {
	    logger.log(Level.SEVERE, 
		       "Act System wouldn't start", 
		       lastException);
	}
        return false;
    }

    /**
     * Register a file for deletion upon completion of test execution.
     * A line containing the absolute path name of the given <code>File</code>
     * is appended to the file named <code>JinitestDeletionList.txt</code>
     * in the default temp directory.
     *
     * @param file the <code>File</code> to register for deletion
     */
    void registerDeletion(File file) {
	ArrayList list = new ArrayList();
	String listFileName = System.getProperty("java.io.tmpdir")
	                    + File.separator 
	                    + "JinitestDeletionList.txt";
	File listFile = new File(listFileName);
	BufferedReader r = null;
	if (listFile.exists()) {
	    try {
		r = new BufferedReader(new FileReader(listFileName));
		String s = null;
		while ((s = r.readLine()) != null) {
		    list.add(s);
		}
	    } catch (IOException e) {
		logger.log(Level.SEVERE, "deletion file load failed", e);
		return;
	    } finally {
		try {
		    r.close();
		} catch (Exception ignore) {
		}
	    }
	}
	list.add(file.getAbsolutePath());
	BufferedWriter w = null;
	try {
	    w = new BufferedWriter(new FileWriter(listFileName));
	    for (int i = 0; i < list.size(); i++) {
		w.write((String) list.get(i));
		w.newLine();
	    }
	    
	} catch (IOException e) {
	    logger.log(Level.SEVERE, "deletion file update failed", e);
	} finally {
	    try {
		w.close();
	    } catch (Exception ignore) {
	    }
	}
    }

    /**
     * Reads the contents of the file <code>JinitestDeletionList.txt</code>
     * located in the default temp directory, and attempts to delete every
     * file or directory named therein. Failures are silently ignored.
     */
     void deleteRegisteredFiles() {
	ArrayList files = new ArrayList();
	String listFileName = System.getProperty("java.io.tmpdir")
	                    + File.separator 
	                    + "JinitestDeletionList.txt";
	File listFile = new File(listFileName);
	BufferedReader r = null;
	if (listFile.exists()) {
	    try {
		r = new BufferedReader(new FileReader(listFileName));
		String s = null;
		while ((s = r.readLine()) != null) {
		    files.add(new File(s));
		}
	    } catch (IOException e) {
		logger.log(Level.SEVERE, "deletion file load failed", e);
		return;
	    } finally {
		try {
		    r.close();
		} catch (Exception ignore) {
		}
	    }
	}
	for (int i = 0; i < files.size(); i++) {
	    File f = (File) files.get(i);
	    deleteTree(f);
	}
	listFile.delete();
    }

    /**
     * Delete the given <code>File</code>. If the file is a directory, 
     * first call this method for each of the entries in that
     * directory.
     *
     * @param f the file or directory to delete
     */
    private void deleteTree(File f) {
	if (f.isDirectory()) {
	    File[] fileList = f.listFiles();
	    for (int i = 0; i < fileList.length; i++) {
		deleteTree(fileList[i]);
	    }
	}
	logger.log(Level.FINEST, "deleting " + f);
	f.delete();
    }

    /**
     * Return any options or properties which are required for all VMs.
     * The value of <code>org.apache.river.qa.harness.globalvmargs</code>
     * is returned as a string array, with components separated by
     * ',' characters. There must be no cosmetic whitespace in this
     * string since filenames may include whitespace. It is expected
     * that this property would be set in the configuration set property file.
     *
     * @return the array of global vm options/properties, or null if none
     *         are defined
     */
     String[] getGlobalVMArgs() {
	String vmArgs = 
	    getStringConfigVal("org.apache.river.qa.harness.globalvmargs", null);
	return parseArgList(vmArgs);
     }
     
     /**
     * Return an array of VM options extracted from the given array
     * of combined options and properties. These are structured
     * to be input to service starter descriptions, i.e. one
     * fully specified option per array element, including the
     * leading '-' character.
     *
     * @param vmArgs an array of vm options and properties
     * @return the subset of vm options. A zero length array is
     *         returned if there are none, or if <code>vmArgs</code>
     *         is <code>null</code>.
     */
    String[] extractOptions(String[] vmArgs) {
	if (vmArgs == null) {
	    return new String[0];
	}
	ArrayList list = new ArrayList();
	for (int i = 0; i < vmArgs.length; i++) {
	    if (! (vmArgs[i].startsWith("-D") || vmArgs[i].startsWith("-OD"))) {
		list.add(vmArgs[i]);
	    }
	}
	return (String[]) list.toArray(new String[list.size()]);
    }
	
    /**
     * Return an array of VM properties extracted from the given array
     * of combined options and properties. These are structured
     * to be input to service starter descriptions, i.e. alternating
     * property names and values, with the '-D' stripped from
     * the property names. The returned array should therefore always
     * contain an even number of elements. Optional properties
     * begin with '-OD'. If an optional property is found and it
     * has no value, then it will not be included in the list.
     *
     * @param vmArgs an array of vm options and properties
     * @return the subset of vm properties. A zero length array is
     *         returned if there are none, or if <code>vmArgs</code>
     *         is <code>null</code>.
     * @throws TestException if a parsing error occurs
     */
    String[] extractProperties(String[] vmArgs) throws TestException {
	if (vmArgs == null) {
	    return new String[0];
	}
	ArrayList list = new ArrayList();
	for (int i = 0; i < vmArgs.length; i++) {
	    if (vmArgs[i].startsWith("-D") || vmArgs[i].startsWith("-OD")) {
		String[] prop = parseProp(vmArgs[i]);
		if (prop != null) {
		    list.add(prop[0]);
		    list.add(prop[1]);
		}
	    }
	}
	return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Parse the given string of the form "-Dname=value" or "-ODname=value to
     * return a two element string array containing "name" and "value". If
     * "value" is omitted and the '-D' form was specified, it's return value is
     * a zero length string. If "value" is omitted and the '-OD' form was
     * specified, this method returns <code>null</code>.
     *
     * @param p the string to parse
     * @return a two element string array containing name and value, or
     *         <code>null</code> if there is not value and the '-OD'
     *         form was specified.
     * @throws TestException if <code>p</code> does not start with "-D",
     *                       or "-OD", or does not contain an "=" character, or
     *                       does not define a value for "name"
     */
    private String[] parseProp(String p) 
	throws TestException 
    {
	if (! (p.startsWith("-D") || p.startsWith("-OD"))) {
	    throw new TestException("String " 
				    + p 
				    + " is not a valid property definition");
	}
	int startIndex = ((p.startsWith("-D") ? 2 : 3));
	String pnew = p.substring(startIndex);
	int eq = pnew.indexOf("=");
	if (eq <= 0) {
	    throw new TestException("String " 
				    + p 
				    + " is not a valid property definition");
	}
	String[] ret = new String[2];
	ret[0] = pnew.substring(0, eq);
	ret[1] = "";
	if (pnew.length() > (eq + 1)) {
	    ret[1] = pnew.substring(eq + 1);
	}
	if (ret[1].length() == 0 && p.startsWith("-OD")) {
	    ret = null;
	}
	return ret;
    }
	
    /**
     * Convert an http codebase to an httpmd codebase.  If
     * <code>hash</code> is <code>null</code>, then a search is
     * performed for the key "org.apache.river.qa.harness.integrityhash" and
     * the returned value assigned to <code>hash</code>.  if
     * <code>hash</code> is not <code>null</code> and not
     * <code>"none"</code> then codebase integrity is assumed to be
     * required.  hash may contain multiple comma-separated hash function names;
     * one of the names will be randomly selected.
     * <code>codebase</code> may contain multiple URL's
     * separated by white space. If integrity is required, then each
     * URL which is an http URL will be converted to a fully specified
     * httpmd URL using the randomly selected hashing function name.
     *
     * @param codebase the codebase string to convert, which may be 
     *                 <code>null</code> and may contain multiple
     *                 URL's separated by whitespace
     * @param hash a comma separated list of hash function names, or null
     * @return the possibly modified codebase string
     * @throws TestException if codebase integrity is required and
     *         <code>codebase</code> contains a malformed URL 
     */
    public String genIntegrityCodebase(String codebase, String hash) 
	throws TestException
    {
	if (codebase == null) {
		return null;
	}
	if (hash == null) {
	    hash = getStringConfigVal("org.apache.river.qa.harness.integrityhash", 
				      null);
	}
	if (hash == null || hash.equals("none")) {
	    return codebase;
	}
	String[] hashNames = parseString(hash, ", \t");
	if (hashNames.length == 0) {
	    return codebase; // must have been just sep characters
	}
	if (hashNames.length > 1) {
	    hash = hashNames[new Random().nextInt(hashNames.length)];
	}
	URL[] urls = null;
	try {
	    urls = ClassLoaderUtil.getCodebaseURLs(codebase);
	} catch (MalformedURLException e) {
	    throw new TestException("Bad URL in codebase", e);
	}
	StringBuffer sb = new StringBuffer();
	for (int i = 0; i < urls.length; i++) {
	    if (i > 0) {
		sb.append(" ");
	    }
	    if (! urls[i].getProtocol().equals("http")) {
		sb.append(urls[i].toString());
	    } else {
		int port = urls[i].getPort();
		if (port == -1) {
		    port = 80;
		}
		String key = "org.apache.river.qa.harness.dldir." + port;
		String dir = getStringConfigVal(key, null);
		if (dir == null) {
		    throw new TestException("missing download directory"
					    + " identified by key "
					    + key);
		}
		try {
		    sb.append(HttpmdUtil.computeDigestCodebase(
					  dir,
			                  reformat(urls[i], hash)));
		} catch (FileNotFoundException e) {
		    logger.log(Level.WARNING, 
			       "WARNING: file not found for codebase " + urls[i]
			       + " in directory " + dir
			       + ", DISCARDING");
		} catch (IOException e) {
		    throw new TestException("Failed reformatting codebase" ,e);
		}
	    }
	}
	return sb.toString();
    }
 
    /**
     * Converts an http codebase to an httpmd codebase with a hash
     * value of zero. If the protocol
     * of the given codebase is anything other than <code>http</code>,
     * the original codebase is returned. The formatting is such that
     * the string can be used as input to the
     * <code>HttpmdUtil.computeDigestCodebase</code> method.
     *
     * @param url the <code>URL</code> to convert
     * @param hashType the name of the hashing function
     *
     * @return the converted codebase
     * @throws TestException if the input codebase is not a properly
     *         formatted URL
     */
    private String reformat(URL url, String hashType) 
	throws TestException 
    {
	if (! url.getProtocol().equals("http")) {
	    return url.toString();
	}
	StringBuffer sb = new StringBuffer();
	sb.append("httpmd://");
	sb.append(url.getHost());
	if (url.getPort() != -1) {
	    sb.append(":");
	    sb.append(Integer.toString(url.getPort()));
	}
	sb.append(url.getFile());
	sb.append(";");
	sb.append(hashType);
	sb.append("=0");
	return sb.toString();
    }

    /**
     * Merge two string arrays into a single array. Either
     * argument may be null, in which case the other argument
     * is used as the returned array. <code>null</code> is
     * returned if both arguments are <code>null</code>.
     *
     * @param a1 an array to merge
     * @param a2 an array to merge
     * @return an array containing the union of <code>a1</code> 
     *         and <code>a2</code>
     */
    String[] mergeArrays(String[] a1, String[] a2) {
	if (a1 == null) {
	    return a2;
	}
	if (a2 == null) {
	    return a1;
	}
	String[] ret = new String[a1.length + a2.length];
	System.arraycopy(a1, 0, ret, 0, a1.length);
	System.arraycopy(a2, 0, ret, a1.length, a2.length);
	return ret;
    }

    /**
     * Merge two sets of option strings. The options present
     * in the given string arrays are combined into a
     * single array, discarding duplicates. The original
     * ordering is not retained.
     *
     * @param o1 an array of option strings
     * @param o2 an array of option strings
     * @return the combined strings, discarding duplicates
     */
    String[] mergeOptions(String[] o1, String[] o2) {
	if (o1 == null && o2 == null) {
	    return new String[0];
	}
	if (o1 == null || o2 == null) {
	    return (o1 == null ? o2 : o1);
	}
	HashSet options = new HashSet();
	for (int i = 0; i < o1.length; i++) {
	    options.add(o1[i]);
	}
	for (int i = 0; i < o2.length; i++) {
	    options.add(o2[i]);
	}
	return (String[]) options.toArray(new String[options.size()]);
    }

    /**
     * Merge two sets of property definitions. The property key/value
     * pairs present in the given string arrays are combined into a
     * single array. If a property key is defined in both arrays, the
     * value in <code>p2</code> overrides the value in
     * <code>p1</code>. The original ordering is not retained.
     *
     * @param p1 an array of property key/value pairs
     * @param p2 an array of property key/value pairs
     * @return an array containing the combined propeties key/value pairs
     */
    String[] mergeProperties(String[] p1, String[] p2) {
	if (p1 == null && p2 == null) {
	    return new String[0];
	}
	if (p1 == null || p2 == null) {
	    return (p1 == null ? p2 : p1);
	}
	HashMap map = new HashMap();
	for (int i = 0; i < p1.length; i += 2) {
	    map.put(p1[i], p1[i + 1]);
	}
	for (int i = 0; i < p2.length; i += 2) {
	    map.put(p2[i], p2[i + 1]);
	}
	Set keys = map.keySet();
	Iterator it = keys.iterator();
	String[] ret = new String[keys.size() * 2];
	int i = 0;
	while (it.hasNext()) {
	    String key = (String) it.next();
	    String value = (String) map.get(key);
	    ret[i++] = key;
	    ret[i++] = value;
	}
	return ret;
    }

    /**
     * Add to the set of configuration override providers. This method must be
     * called prior to starting any service for which test supplied overrides
     * are required. 
     *
     * @param provider the override provider
     */
    public void addOverrideProvider(OverrideProvider provider) {
	overrideProviders.add(provider);
	if (isMaster()) { // probably unnecessary, but can't hurt
	    SlaveRequest request = new AddOverrideProviderRequest(provider);
	    SlaveTest.broadcast(request);
	}
    }

    /**
     * Get the configuration override providers. This method is called by admins
     * during service argument list generation to augment the set of 
     * configuration options provided to the service. If no providers have
     * been registered, an empty array is returned.
     *
     * @return the override providers
     */
    OverrideProvider[] getOverrideProviders() {
	OverrideProvider[] oa = new OverrideProvider[overrideProviders.size()];
	return  (OverrideProvider[]) overrideProviders.toArray(oa);
    }

    /**
     * Add a analyzer to the set of failure analyzers called if a 
     * test throws an exception.
     *
     * @param analyzer the <code>FailureAnalyzer</code> to add
     */
    public void addFailureAnalyzer(FailureAnalyzer analyzer) {
	failureAnalyzers.add(analyzer);
    }

    /**
     * Analyze a failure exception. All registered analyzers are 
     * called with the given exception in the order registered. If an
     * analyzer returns a value other than LegacyTest.UNKNOWN then that value
     * is returned by this method. If all analyzers return LegacyTest.UNKNOWN,
     * or if no analyzers are registered, then this method returns the
     * given default value.
     *
     * @param e the exception to analyze
     * @param defaultType the default failure type to return if all analyzers
     *                    return <code>LegacyTest.UNKNOWN</code>, or if there are no
     *                    registered analyzers
     * @return the failure type
     */
    int analyzeFailure(Throwable e, int defaultType) {
	for (int i = 0; i < failureAnalyzers.size(); i++) {
	    FailureAnalyzer fa = (FailureAnalyzer) failureAnalyzers.get(i);
	    int type = fa.analyzeFailure(e);
	    if (type != Test.UNKNOWN) {
		return type;
	    }
	}
	return defaultType;
    }

    /**
     * Split a dot-separated identifier by removing the last token and
     * discarding the separator. Return the resulting pair in a
     * two-element string array. Thus, if <code>key</code> has
     * the value "a.b.c", then element 0 will have the value "a.b"
     * and element 1 will have the value "c".
     *
     * @param key the identifier to split
     * @return the split pair, or null if <code>key</code> was not
     *         a properly formed identifier (such as the value "none")
     */
    String[] splitKey(String key) {
	if (key == null) {
	    return null;
	}
	int index = key.lastIndexOf('.');
	if (index <= 0 || index == (key.length() - 1)) {
	    return null;
	}
	String[] frags = new String[2];
	frags[0] = key.substring(0, index);
	frags[1] = key.substring(index + 1);
	return frags;
    }

    /**
     * Return an indication of whether this host is the master host. This
     * value is always computed rather than cached to avoid any consistancy
     * glitches when an instance of this class is deserialized in another VM.
     *
     * @return true if this host is the master host
     */
    boolean isMaster() {
	boolean isMaster = true;
	if (hostList.size() > 0) {
	    try {
		InetAddress thisAddr = InetAddress.getLocalHost(); //XXX multinic??
		String masterName = (String) hostList.get(0);
		InetAddress masterAddr = InetAddress.getByName(masterName);
		isMaster = masterAddr.equals(thisAddr);
	    } catch (UnknownHostException e) {
		logger.log(Level.SEVERE, "Unexpected exception", e);
	    }
	}
	return isMaster;
    }


    /**
     * Return an indication of whether the <code>hostName</code> is this host. 
     *
     * @return true if this <code>hostName</code> is this host
     */
    boolean isThisHost(String hostName) {
	try {
	    InetAddress thisAddr = InetAddress.getLocalHost(); //XXX multinic??
	    InetAddress hostAddr = InetAddress.getByName(hostName);
	    return hostAddr.equals(thisAddr);
	} catch (UnknownHostException e) {
	    logger.log(Level.SEVERE, "Unexpected exception", e);
	}
	return true;
    }

    /**
     * Return the name of the local host. If the name cannot be determined,
     * return "localhost".
     *
     * @return the host name
     */
    public String getLocalHostName() {
	String host = "localhost";
	try {
	    host = InetAddress.getLocalHost().getHostName();
	} catch (UnknownHostException ignore) {
	}
	return host;
    }

    /**
     * Internal utility method to intialize the locator constraints from
     * the <code>test.locatorConstraints</code> entry of the test
     * configuration file.
     */
    private static void initLocatorConstraints() {
	Configuration c = harnessConfig.configuration;
	try {
	    testLocatorConstraints = 
		(MethodConstraints) c.getEntry("test", 
					       "locatorConstraints",
					       MethodConstraints.class);
	} catch (ConfigurationException e) {
	    if (c instanceof QAConfiguration) {
		logger.log(Level.INFO, 
			   "Warning: couldn't init locator constraints",
			   e);
	    }
	}
    }

    /**
     * Get a <code>ConstrainableLookupLocator</code> for the given host and port
     * using constraints provided by the <code>test.locatorConstraints</code>
     * entry from the test configuration file. If the <code>none</code>
     * configuration is being run, the constraints will be null.
     *
     * @param host the host name
     * @param port the host port
     * @return the locator
     */
    public static LookupLocator getConstrainedLocator(String host, int port) 
    {
	if (testLocatorConstraints == null) {
	    initLocatorConstraints();
	}
	return new ConstrainableLookupLocator(getFQHostName(host), 
					      port, 
					      testLocatorConstraints);
    }

    /**
     * Get a <code>ConstrainableLookupLocator</code> for the host and port
     * provided by the given locator, using constraints provided by the
     * <code>test.locatorConstraints</code> entry from the test configuration
     * file.
     *
     * @param loc the <code>LookupLocator</code> of the target
     * @return the locator
     */
    public static LookupLocator getConstrainedLocator(LookupLocator loc) 
    {
	if (loc == null) {
	    return null;
	}
	return getConstrainedLocator(getFQHostName(loc.getHost()), 
				     loc.getPort());
    }

    /**
     * Get a <code>ConstrainableLookupLocator</code> for the given
     * <code>URL</code> , using constraints provided by the
     * <code>test.locatorConstraints</code> entry from the test configuration
     * file.
     *
     * @param url the <code>URL</code> for the locator
     * @return the locator
     * @throws MalformedURLException if <code>url</code> is not 
     *                               properly formatted
     */
    public static LookupLocator getConstrainedLocator(String url) 
	throws MalformedURLException
    {
	LookupLocator loc = new LookupLocator(url);
	return getConstrainedLocator(loc);
    }

    private static final CharSequence NXDOMAIN = "NXDOMAIN";
    private static final CharSequence nxdomain = "nxdomain";
    
    /**
     * Convert a host name to canonical form. If the name cannot
     * be converted for any reason, the original name is returned.
     *
     * @param hostName the name to convert
     * @return the converted name
     */
    private static String getFQHostName(String hostName) {
	try {
	    InetAddress hostAddr = InetAddress.getByName(hostName);
	    String fqHostName = hostAddr.getCanonicalHostName();
            if (fqHostName.contains(NXDOMAIN)) return hostName;
            if (fqHostName.contains(nxdomain)) return hostName;
            return fqHostName;
	} catch (UnknownHostException ignore) {
            logger.info("InetAddress threw unknown host exception: " + hostName);
	}
	return hostName;
    }

    /**
     * Implement the configuration value search policy for the harness.
     * The various sources of test parameters are searched in the following 
     * order:
     * <ul>
     * <li>the default property file
     * <li>the Configuration set property file
     * <li>the user provided configuration file
     * <li>the component level property file
     * <li>the named test property file (see below)
     * <li>the co-located test property file (see below)
     * <li>the test description
     * <li>the test Configuration
     * <li>the System properties
     * <li>the dynamic properties
     * <li>the overrides
     * </ul>
     * The last source which returns a non-null value is used, so
     * the search is done from lowest to highest precedence. Configration
     * definitions may be self-referential, causing a substitution
     * to be performed from lower precedence definitions. For example
     * assume the default property file had the following definition:
     * <pre>
     *    com.sun.foo=foo
     * </pre>
     * and assume that the test description contained the following:
     * <pre>
     *    com.sun.foo=${com.sun.foo} bar
     * </pre>
     * then the value retrieved by searching for <code>com.sun.foo</code>
     * would be <code>foo bar</code>.
     * <p>
     * Before the parameter value is returned, any '$' substitutions
     * are performed (recursively), and any occurances of the
     * token <code><gethost></code> are replaced with the name of
     * the host on which this code is running. If a parsing error
     * is encountered during symbol resolution, an error message
     * is written to the log, and the unresolved parameter value
     * is returned.
     * <p>
     * Any tokens of the form &lt;url:foo&gt; will result in the generation
     * of a URL for <code>foo</code>. A search is performed for the file relative to
     * all of the root directories defined by the <code>searchPath</code>
     * property. If the file is found, a file URL for the fully qualified
     * name of the file is generated and returned as the token value. Otherwise,
     * test and harness JAR files are searched for <code>foo</code>, looking
     * first relative to the directory in which the test description file is 
     * located, then relative to the root of the test jar file and finally
     * relative to the root of the harness jar file. In this case, an appropriate
     * JAR file URL is generated and returned as the token value. If the
     * file is not found, a <code>TestException</code> is thrown.
     * <p>
     * Any tokens of the form &lt;file:foo&gt; will result in the generation of a
     * fully qualified path for <code>foo</code>. A search is performed for the file
     * relative to all of the root directories defined by the
     * <code>searchPath</code> property. If the file is found, the fully qualified
     * name of the file is generated and returned as the token value. If the file is
     * not found, a <code>TestException</code> is thrown.
     * <p>
     * If an error is detected during the search/resolution process, it is
     * considered fatal. If the error were simply logged, an incorrect value
     * would be used and the error message could easily be missed in the
     * other test output. A <code>RuntimeException</code> is thrown rather
     * than a <code>TestException</code> to avoid the need to wrap the many
     * calls to this method in try/catch blocks.
     * <p>
     * When searching the test configuration, they key is split into
     * an entry type/entry name pair. If the key is a single token, such
     * as <code>testjvmargs</code>, then the entry type will be
     * "test" and the entry name will be the key (<code>testjvmargs</code>).
     * 
     * @param key the property name to search for
     *
     * @return the value of the property, or <code>null</code> if the
     *         property cannot be found.
     * @throws RuntimeException if a fatal error occurs while locating and
     *                          resolving the value.
     */
    String getParameterString(String key) {
	String previousVal = "";
	String val = defaultProps.getProperty(key);
        logger.log(Level.FINER, "defaultProps for key: {0} returned: {1}", new Object[]{key, val});
	String source = null;
	try {
	    if (val != null) {
		source = "qaDefault.properties";
		previousVal = resolver.resolveReference(val, key, previousVal);
	    }
	    val = configSetProps.getProperty(key);
            logger.log(Level.FINER, "configSetProps for key: {0} returned: {1}", new Object[]{key, val});
	    if (val != null) {
		source = "configurationSet property file";
		previousVal = resolver.resolveReference(val, key, previousVal);
	    }
	    val = configProps.getProperty(key);
            logger.log(Level.FINER, "configProps for key: {0} returned: {1}", new Object[]{key, val});
	    if (val != null) {
		source = "user configuration file";
		previousVal = resolver.resolveReference(val, key, previousVal);
	    }
	    if (td != null) {
		val = td.getProperty(key);
                logger.log(Level.FINER, "td property for key: {0} returned: {1}", new Object[]{key, val});
		if (val != null) {
		    source = "test description properties";
		    previousVal = 
			resolver.resolveReference(val, key, previousVal);
		}
	    }
	    if (configuration  != null) {
		String[] frags = splitKey(key);
		if (frags == null) {
		    frags = new String[]{"test", key};
		}
		try {
		    val = (String) configuration.getEntry(frags[0],
							  frags[1], 
							  String.class, 
							  null);
		    if (val != null) {
			source = "test configuration file";
			previousVal = 
			    resolver.resolveReference(val, 
						      key,
						      previousVal);
		    }
		} catch (Exception ignore) {
		}
	    }
	    MultiCommandLine mcl = new MultiCommandLine(args);
	    try {
		val = mcl.getString(key, null);
		if (val != null) {
		    source = "command line";
		    previousVal = resolver.resolveReference(val, 
							    key,
							    previousVal);
		}
	    } catch (BadInvocationException e) {
	    }
	    val = System.getProperty(key);
	    if (val != null) {
		source = "System property";
		previousVal = resolver.resolveReference(val, key, previousVal);
	    }
	    val = dynamicProps.getProperty(key);
	    if (val != null) {
		source = "dynamic property";
		previousVal = resolver.resolveReference(val, key, previousVal);
	    }
	    if (propertyOverrides != null) {
		val = propertyOverrides.getProperty(key);
		if (val != null) {
		    source = "propertyOverrides";
		    previousVal = resolver.resolveReference(val, key, previousVal);
		}
	    }
	    if (previousVal.equals("")) {
		val = null;
	    } else {
		val = resolver.resolve(previousVal);
	    }
	    if (key.equals(trackKey)) {
		logger.log(Level.INFO, 
			   "Value for " + key 
			   + " obtained from " + source
			   + " is " + val);
	    }
	} catch (TestException e) {
	    throw new RuntimeException("Error resolving key " + key + " obtained from source " + source, e);
	}
	if (val != null) {
	    val = val.trim();
	}
	return val;
    }

    /**
     * Sets a dynamic test parameter. These parameters are intended for use
     * by tests which must perform special manipulations of property values,
     * such as setting a codebase string on-the-fly. In a distributed run, 
     * a <code>SlaveRequest</code> will be broadcast to all slaves to
     * set their dynamic test parameters as well, to retain consistancy. 
     * As a result, this method should not be called until all slave tests
     * are running.
     *
     * @param key    a <code>String</code> identifying the parameter
     * @param value  the parameter value to set
     */
    public void setDynamicParameter(String key, String value) {
        dynamicProps.setProperty(key, value);
	//XXX the harnessJar/testJar special cases feels like a hack
	if (key.equals("harnessJar")) {
	    resolver.setToken(key, value);
	    harnessJar = value;
	}
	if (key.equals("testJar")) {
	    testJar = value;
	    resolver.setToken(key, value);
	}
	if (isMaster()) {
	    SlaveRequest request = new SetDynamicParameterRequest(key, value);
	    SlaveTest.broadcast(request);
	}
    }

    /**
     * Returns the name to use for the <code>ConfigurationFile</code>. If the
     * name is not provided by the <code>testConfiguration</code> parameter in
     * the test config, or if the current configuration tag is 'none', then it
     * is assumed that no test specific configuration file exists for this test;
     * in this case, this method returns "-".
     *
     * @return the name of the configuration file to
     *         be used by this test expressed as a jar file URL
     */
    private String getConfigurationName() throws TestException {
	String name = "-";
        logger.log(Level.FINER, "currentTag: {0}", currentTag);
	if (!currentTag.equals("none")) {
	    name = getStringConfigVal("testConfiguration", "-");
        }
	if (! name.equals("-")) {
	    name = getComponentURL(name, null).toString();;
	}
	return name;
    }

    /**
     * Install <code>OverrideProviders</code> defined by the test
     * description property <code>testOverrideProviders</code>. The value
     * of this propery may be a list of providers, separated by commas
     * or white space. Any previously registered providers are discarded.
     *
     * @throws TestException if a specified provider does not exist
     *         or an exception is thrown while instantiating the provider
     */
    private void registerOverrideProviders() throws TestException {
	overrideProviders = new ArrayList();
	String[] providers = 
	    parseString(getStringConfigVal("testOverrideProviders", null),
			", \t");

	/*
	 * Note that any providers are added directly to the provider list
	 * rather than calling the <code>addOverrideProvider</code> method.
	 * That method broadcasts to slaves, which is undesirable at this
	 * point. Any providers registered here will be included in the
	 * serialized config passed to the slaves.
	 */
	if (providers != null) {
	    for (int i = 0; i < providers.length; i++) {
		try {
		    Class c = Class.forName(providers[i], true, testLoader);
		    OverrideProvider op = (OverrideProvider) c.newInstance();
		    overrideProviders.add(op);
		} catch (Exception e) {
		    throw new TestException("Bad OverrideProvider", e);
		}
	    }
	}
    }
		    
    /**
     * Load the test <code>Configuration</code>.  Any test overrides obtained
     * from the list of installed <code>OverrideProviders</code> will be
     * included in the options list. The <code>LookupLocator</code> constraints
     * maintained by this class is also updated after loading the configuration.
     * If the current configuration tag is "none", this method creates
     * a <code>Configuration</code> which only contains the overrides. Else
     * it creates an instance of <code>QAConfiguration</code> containing
     * the defaults for this configuration as well as the overrides.
     *
     * @throws TestException if an error occurs while loading the configuration
     *                       or initializing the constraints
     */
     void loadTestConfiguration() throws TestException {
	String configName = null;
	try {
	    String[] overrides = getTestOverrides();
	    configName = getConfigurationName();
            logger.log(Level.FINE, "Configuration name: {0}", configName);
	    String[] options = new String[overrides.length + 1];
	    options[0] = configName;
	    System.arraycopy(overrides, 0, options, 1, overrides.length);
	    logger.log(Level.FINER, "Test Configuration options:");
	    for (int i = 0, l = options.length; i < l; i++) {
		logger.log(Level.FINER, "   {0}", options[i]);
	    }
	    if (currentTag.equals("none")) {
		configuration = new ConfigurationFile(options);
	    } else {
		configuration = new QAConfiguration(options, this);
	    }
            logger.log(Level.FINE, "Test configuration loaded: {0}", configuration);
	} catch (ConfigurationException e) {
	    if (getBooleanConfigVal("testConfigurationOptional",false)) {
		logger.log(Level.FINE, 
		    "Unable to load optional configuration " + configName);
	    } else {
	        throw new TestException(
		    "Unable to load configuration " + configName, e);
	    }
	}
    }

    /**
     * Obtain the test overrides from the set of installed overrider
     * providers. LegacyTest overrides have a <code>serviceName</code> of
     * <code>null</code>.
     *
     * @return a (possibly zero length) array of overrides
     */
    private String[] getTestOverrides() throws TestException {
	ArrayList list = new ArrayList();
	for (int i = 0; i < overrideProviders.size(); i++) {
	    OverrideProvider provider = 
		(OverrideProvider) overrideProviders.get(i);
	    String[] overrides = provider.getOverrides(this, null, 0);
	    for (int j = 0; j < overrides.length; j += 2) {
		list.add(overrides[j] + " = " + overrides[j + 1]);
	    }
	}
	return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Factory method which returns the <code>TestDescription</code> 
     * corresponding to the named
     * test description file. If <code>testName</code> ends with the
     * string ".java", a <code>MainTestDescription</code> is constructed.
     * Otherwise, a <code>QATestDescription</code> is constructed. 
     *
     * @param testName the name of the test description file
     * @param p the test description file properties
     * @return the corresponding <code>TestDescription</code>
     * @throws TestException if problems occur during test initialization
     */
    public TestDescription getTestDescription(String testName, Properties p) 
	throws TestException 
    {
	if (testName.endsWith(".java")) {
	    return new MainTestDescription(testName, p, this);
	} else {
	    TestDescription td = new TestDescription(testName, p, this);
	    return td;
	}
    }

    /**
     * Get the test description of the current test. 
     *
     * @return the current test description
     */
    public TestDescription getTestDescription() {
	return td;
    }

    /**
     * Provides the <code>net.jini.config.Configuration</code>
     * object bound to this test.
     *
     * @return the configuration
     * @throws IllegalStateException if the configuration is undefined
     */
    public Configuration getConfiguration() {
	if (configuration == null) {
	    throw new IllegalStateException("configuration is undefined");
	}
	return configuration;
    }

    /**
     * Returns the array of tags identifying configuration sets.
     * 
     * @return the array of tags
     * @throws TestException if any configuration set identified
     *         by the tags does not exist
     */
    String[] getConfigTags() throws TestException {
	String configString = 
	    getStringConfigVal("org.apache.river.qa.harness.configs", null);
	configTags = parseString(configString, ",");
	if (configTags == null || configTags.length == 0) {
	    throw new TestException("Missing configuration tags");
	}
	return configTags;
    }

    /**
     * Set up the current test for the next configuration to run.
     * This method:
     * <ul>
     * <li>initializes the current test description 
     * <li>reinitializes the dynamic properties object
     * <li>reinitializes the <code>selectedIndexes</code> list
     * <li>generates a new values for the unique string
     * <li>sets the value of the current configuration tag resolver token
     *     named &lt;config$gt;. This allows generic test property
     *     files to specify strings with configuration dependencies
     * <li>loads the <code>Configuration</code> for the test
     * </ul>
     *
     * @param configTag a string naming the configuration info to load
     * @param td the test description for the current test
     * @throws TestException if a fatal configuration error was detected
     *                       while loading the configuration, or if
     *                       the configuration identified by
     *                       <code>configTag</code> does not exist.
     */
    void doConfigurationSetup(String configTag, TestDescription td) 
	                                             throws TestException 
    {
	currentTag = configTag;
	initUniqueString(); // so each test uses a new unique value
	this.td = td;
        dynamicProps = new Properties();
	selectedIndexes = new ArrayList();
	trackKey = getParameterString("track");
        propertyOverrides = null; // paranoid, probably unnecessary
	resolver.setToken("config", currentTag);
	// <config> must be defined for the next loadProperies call
	if (!currentTag.equals("none")) {
	    configSetProps = loadProperties(getStringConfigVal("org.apache.river.qa.harness.configSet", null));
	} else {
	    configSetProps = new Properties();
	}
        // do this last in case test configuration overrides are defined
        registerOverrideProviders();
        loadTestConfiguration();
    }

    /**
     * Return the name of the configuration being tested.
     *
     * @return the configuration name
     */
    String getConfigurationTag() {
	return currentTag;
    }

    /**
     * Convenience method to prepare an object using the named preparer
     * which must be present in the test configuration. Returns the
     * original object if the 'none' configuration is current.
     * 
     * @param preparerName the name of the preparer to use
     * @param target the object to prepare
     * @return the prepared object
     * @throws TestException if a configuration error occurs, if
     *                         the preparer entry does not exist, or
     *                         if a <code>RemoteException</code>
     *                         is thrown
     */
    public Object prepare(String preparerName, Object target) 
	throws TestException 
    {
	Configuration c = getConfiguration();
	if (! (c instanceof QAConfiguration)) { // if true, none configuration
	    return target;
	}
	String[] entryTokens = splitKey(preparerName);
	if (entryTokens == null) {
	    throw new TestException("Illegal Preparer name: " + preparerName);
	}
	try {
	    ProxyPreparer p = (ProxyPreparer) c.getEntry(entryTokens[0],
							 entryTokens[1],
							 ProxyPreparer.class);
	    return p.prepareProxy(target);
	} catch (ConfigurationException e) {
	    throw new TestException("Configuration Error preparing "
				    + target, e);
	} catch (RemoteException e) {
	    throw new TestException("Remote Exception preparing " 
				    + target, e);
	}
    }

    /**
     * Get the list of participating test hosts. The first element in
     * the list is the master host. The returned value is never null,
     * but may be of size zero.
     *
     * @return the host list
     */
    public List getHostList() {
	return hostList;
    }

    /**
     * Returns the name of the host to run a service on, or null if the
     * service is to be run on the local host. 
     * If the test is not distributed or if this host is a slave,
     * then null is returned. If the service property "host" is 
     * defined, its value is interpreted as follows:
     * <ul>
     *   <li>if the value is "master", return <code>null</code>
     *   <li>if the value is "slave", modify the selection policy
     *       to always select a slave by converting roundrobin
     *       to remoteroundrobin or random to remoterandom
     *   <li>if the value is "slaveN" where "N" is an integer,
     *       use the Nth entry in the host list (modulo the host
     *       list size). If the index is 0, it is reset to 1.
     *   <li>otherwise, assume the value is the name of the host
     *       to use. If the value is not in the list, a
     *       <code>TestException</code> is thrown.
     * <ul>
     * The selection policy is obtained from the test property
     * <code>org.apache.river.qa.harness.servicehostpolicy</code>, which
     * may have one of the following values:
     * <ul>
     *   <li>random - host is selected at random from all participants
     *   <li>remoterandom - host is selected at random from slaves
     *   <li>roundrobin - service host is selected in sequence from
     *                    the ordered list of participants. The master
     *                    is the last host chosen.
     *   <li>remoteroundrobin - service host is selected in sequence
     *                          from the ordered list of slaves.
     * </ul>
     * The default policy is 'remoterandom'. Any unrecognized policy
     * keyword also silently falls back to the default.
     * <p>
     * Some tests predetermine the host that a service is to be run
     * on. The <code>forceHost</code> parameter may be used by a test
     * to identify such a host. If this parameter is non-null, it 
     * overrides the selection policy.
     *
     * @param serviceName the name of the service being started
     * @param counter the service instance count
     * @param forceHost if non-null, the host to run the service on
     * @return the host name (if a slave is selected and this is
     *         the master host) or null if the local host is selected
     * @throws TestException if the <code>host</code> service property
     *                       specifies a host which is not a participant
     * 
     */
    public String getServiceHost(String serviceName, 
				 int counter, 
				 String forceHost) 
	throws TestException
    {
	// slaves always run services locally
	logger.log(Level.FINE, "Selecting service host");
	if (hostList.size() < 2 || !isMaster()) {
	    logger.log(Level.FINE, "Not distributed - selecting this host");
	    return null;
	}
	if (forceHost != null) {
	    logger.log(Level.FINE, "Forcing " + forceHost);
	    if (isThisHost(forceHost)) {
		return null;
	    }
	    return forceHost;
	}
	String name = getServiceStringProperty(serviceName,
					       "host",
					       counter);
	String policy = 
	    getStringConfigVal("org.apache.river.qa.harness.servicehostpolicy",
			       "remoterandom");
	int selectedIndex;
	if (name != null) {
	    if (name.equals("master")) {
		return null;
	    }
	    if (name.equals("slave")) {
		if (policy.equals("random")) {
		    policy = "remoterandom";
		} 
		if (policy.equals("roundrobin")) {
		    policy = "remoteroundrobin";
		}
		if (policy.equals("uniformrandom")) {
		    policy = "remoteuniformrandom";
		}
		name = null;
	    } else if (name.startsWith("slave")) {
		try {
		    selectedIndex = Integer.parseInt(name.substring(5));
		    selectedIndex %= hostList.size();
		    if (selectedIndex == 0) {
			selectedIndex = 1;
		    }
		    name = (String) hostList.get(selectedIndex);
		} catch (NumberFormatException ignore) {
		    // the name begins with "slave" but the remainder
		    // of the name is not an int, so just leave
		    // name unchanged
		}
	    }
	    if (name != null) {
		logger.log(Level.FINER, "host chosen by property is " + name);
		for (int i = 0; i < hostList.size(); i++) {
		    String host = (String) hostList.get(i);
		    if (host.equals(name)) {
			return name;
		    }
		}
		throw new TestException("host " + name + " is not in hostList");
	    }
	}
	if (policy.equals("random")) {
	    Random r = new Random();
	    selectedIndex = r.nextInt(hostList.size());
	} else if (policy.equals("uniformrandom")) {
	    selectedIndex = nextIndex(true); // include master
	} else if (policy.equals("remoteuniformrandom")) {
	    selectedIndex = nextIndex(false); // exclude master
	} else if (policy.equals("roundrobin")) {
	    selectedIndex = hostIndex++;
	    if (hostIndex >= hostList.size()) {
		hostIndex = 0;
	    }
	} else if (policy.equals("remoteroundrobin")) {
	    selectedIndex = hostIndex++;
	    if (hostIndex >= hostList.size()) {
		hostIndex = 1;
	    }
	} else { // use "remoterandom"
	    if (!policy.equals("remoterandom")) {
		logger.log(Level.INFO, 
			   "Bad selection policy: " + policy 
			   + " using remoterandom");
	    }
	    Random r = new Random();
	    selectedIndex = r.nextInt(hostList.size() - 1) + 1;
	}
	name = (String) hostList.get(selectedIndex);
	logger.log(Level.FINE, 
		   "Host chosen by " + policy + " policy is " + name);
	if (selectedIndex == 0) {
	    name = null;
	}
	return name;
    }

    /**
     * Return the next host index selected at random, but exhausting
     * the list before reusing a host. The <code>selectedIndexes</code>
     * list is updated, and reset to empty when all hosts have
     * been selected.
     *
     * @param includeMaster if <code>true</code>, include the master
     *                      among the set of returned indices
     */
    private int nextIndex(boolean includeMaster) {
	ArrayList available = new ArrayList();
	int i = (includeMaster ? 0 : 1);
	while (i < hostList.size()) {
	    Integer iInt = new Integer(i++);
	    if (! selectedIndexes.contains(iInt)) {
		available.add(iInt);
	    }
	}
	if (available.size() == 0) {
	    throw new IllegalStateException("Can't find an available index");
	}
	int randIndex = new Random().nextInt(available.size());
	Integer selected = (Integer) available.get(randIndex);
	selectedIndexes.add(selected);
	int maxSize = (includeMaster ? hostList.size() : (hostList.size() - 1));
	if (selectedIndexes.size() == maxSize) {
	    selectedIndexes.clear();
	}
	return selected.intValue();
    }

    /**
     * Set the override properties to be used in a property search.
     * 
     * @param propertyOverrides the collection of override properties, or null
     *                  to disable overrides
     */
    void setOverrides(Properties propertyOverrides) {
	this.propertyOverrides = propertyOverrides;
    }

    /**
     * Returns the default exporter for the test. Used to obtain
     * an exporer when the 'none' configuration is selected.
     *
     * @return a default jeri exporter
     */
    public static Exporter getDefaultExporter() {
	return new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
				     new BasicILFactory());
    }

    /**
     * Set the test execution rerun counter.
     *
     * @param passCount the value to set
     */
    void setRerunPassCount(int passCount) {
	this.passCount = passCount;
    }

    /**
     * Get the test execution rerun counter.
     *
     * @return rerun counter value
     */
    int getRerunPassCount() {
	return passCount;
    }

    public ClassLoader getTestLoader() {
	return testLoader;
    }

    String resolve(String propValue) throws TestException {
	if (propValue == null) {
	    return null;
	}
	return resolver.resolve(propValue);
    }

    public void suspendRun(String msg) {
	if (msg == null) {
	    msg = "test suspended";
	}
	setTestStatus(msg, true);
	synchronized (runLock) {
	    testSuspended = true;
	    while (testSuspended) {
		try {
		    runLock.wait();
		} catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
		}
	    }
	}
	if (resumeMessage == null) {
	    resumeMessage = "test running";
	}
	setTestStatus(resumeMessage);
    }

    public void resumeRun(String msg) {
	resumeMessage = msg;
	synchronized (runLock) {
	    testSuspended = false;
	    runLock.notifyAll();
	}
    }

    public boolean isSuspended() {
	synchronized (runLock) {
	    return testSuspended;
	}
    }

    void enableTestHostCalls(boolean val) {
	callAutot = val;
    }

    void callTestHost(OutboundAutotRequest request) {
	if (callAutot) {
	    try {
		AutotRequest.callTestHost(request);
	    } catch (Exception e) {
		logger.log(Level.SEVERE, "Call to test host failed", e);
	    }
	}
    }

    public void setTestStatus(String msg, boolean suspended) {
	callTestHost(new TestStatusRequest(msg, suspended));
    }

    public void setTestStatus(String msg) {
	callTestHost(new TestStatusRequest(msg, false));
    }

    void setTestTotal(int total) { // set by master harness
	testTotal = total;
    }

    void setTestIndex(int index) { // set by master harness
	testIndex = index;
    }

    int getTestTotal() { // retrieved by master test
	return testTotal;
    }

    int getTestIndex() { // retrieved by master test
	return testIndex;
    }
}
