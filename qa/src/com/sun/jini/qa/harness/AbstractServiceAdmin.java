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
package com.sun.jini.qa.harness;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.StringTokenizer;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.EmptyConfiguration;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.ConstrainableLookupLocator;
import net.jini.lookup.DiscoveryAdmin;
import net.jini.security.ProxyPreparer;
import org.apache.river.api.net.Uri;

/**
 * An abstract class supporting admins which use strings to identify
 * services (<code>serviceName</code>), and <code>index</code> to
 * specify an instance count for a given service, and a collection of
 * well known tokens which are appended to the <code>serviceName</code>
 * to create property names used to search the test properties for
 * service configuration values.
 * <p>
 * This class provides two sets of accessors. The first set consists
 * of a collection of protected methods with names of the form
 * <code>getServiceFoo</code>, where <code>Foo</code> is a service
 * parameter such as codebase or classpath. Subclasses should call
 * these methods when generating the service descriptors or command
 * lines needed to run the service. There are also a parallel
 * set of public method with names of the form <code>getFoo</code>,
 * which return the cached values of results previously returned
 * by their <code>getServiceFoo</code> counterparts. The latter set
 * of methods are intended for debug output, and to allow tests to
 * obtain the parameters actually used to start services. This class
 * provides accessors for the following well-known tokens:
 * <p>
 * <table align=center>
 *    <tr><td>type    <td>the service type
 *    <tr><td>impl    <td>the back-end implementation class name of the service
 *    <tr><td>codebase <td>the codebase for the service
 *    <tr><td>component <td>the component name used to reference entries
 *                          in the services <code>Configuration</code>
 *    <tr><td>host <td>the host the service should run on when running in
 *                     distributed mode. May be an explicit host name or
 *                     one of 'master', 'slave', or 'slaveN' where N is
 *                     an integer. If an explicit host is specified, that
 *                     host name must be present in the system property
 *                     <code>com.sun.jini.qa.harness.testhosts</code>.
 *    <tr><td>classpath <td>the classpath for the service
 *    <tr><td>policyfile <td>the security policy file for the service
 *    <tr><td>serverjvm     <td>the path for the jvm to execute for the service
 *    <tr><td>serverjvmargs <td>options and properties for the service vm
 *    <tr><td>activationhost <td>the host running the activation system
 *    <tr><td>activationport <td>the port for contacting the activation system
 *    <tr><td>serviceConfiguration<td>the configuration file for the service
 *    <tr><td>starterConfiguration<td>the configuration file for the service
 *                                    starter
 *    <tr><td>preparername <td>the fully qualified name of the entry for the
 *                             <code>ProxyPreparer</code> for the service proxy
 *                             in the test ConfigurationFile
 *    <tr><td>dir          <td>a directory specifier (only used by class server)
 *    <tr><td>port         <td>a port number (only used by reggie)
 *    <tr><td>tojoin       <td>the set of groups and locators a service should 
 *                             join
 *    <tr><td>membergroups <td>the set of groups a lookup service should
 *                             advertise
 *    <tr><td>log          <td>a token incorporated into the name of
 *                             a services persistence log
 *    <tr><td>integrityhash <td>if <code>true</code>, the service codebase
 *                              is to be converted to an httpmd URL. This
 *                              property is not generally defined, allowing
 *                              the global 
 *                              <code>com.sun.jini.qa.harness.integrityHhash</code>
 *                              property to control this behavior.
 *    <tr><td>autoDestroy   <td>if defined and <code>false</code>, inhibit
 *                              the automatic termination of the service in
 *                              the test teardown method. Rarely used to
 *                              eliminate spurious stack traces in tests which
 *                              use the service's <code>DestroyAdmin</code>
 *                              directly.
 * </table>
 * <p>
 * The logger named <code>com.sun.jini.qa.harness.service</code> is used
 * to log debug information
 *
 * <table border=1 cellpadding=5>
 *
 *<tr> <th> Level <th> Description
 *
 *<tr> <td> FINE <td> parameter values used to start the service
 *</table> <p>
 */
public abstract class AbstractServiceAdmin implements Admin {

    /** The logger */
    protected final static Logger logger =
	Logger.getLogger("com.sun.jini.qa.harness");

    /** The config object for this test */
    protected final QAConfig config;

    /** The name of the service controlled by this admin */
    protected final String serviceName;

    /** The instance number of this service in this test */
    protected final int index;

    /** The codebase for the service */
    private volatile String codebase;

    /** The implementation class name of this service */
    private volatile String impl;

    /** The policy file applied to this service */
    private volatile String policyFile;

    /** The classpath for this service */
    private volatile String classpath;

    /** The vm to be used to run this service (uaually null) */
    private volatile String jvm;

    /** The command line options for the service VM */
    private volatile String[] options;

    /** The system properties for the service VM */
    private volatile String[] properties;

    /** The activation host (usually null) */
    private volatile String activationHost;

    /** The activation port (only used for non-null act host) */
    private volatile int activationPort;

    /** The name of the service configuration file */
    private volatile String serviceConfigFile;

    /** The name of the service starter configuration file */
    private volatile String starterConfig;

    /** The service directory (e.g. the doc directory for the class server */
    private volatile String dir;

    /** The port associated with the service (e.g. the class server port */
    private volatile int port;

    /** Flag indicating a port was defined */
    private volatile boolean gotPort = false;

    /** Groups associated with the service */
    private volatile String[] groups;

    /** Locators associated with the service, expected to be non-null */
    private volatile LookupLocator[] locators = new LookupLocator[0];

    /** Member groups associated with a lookup service */
    private volatile String[] memberGroups;

    /** The name of the proxy preparer */
    private volatile String preparerName;

    /** The name of the persistence directory */
    private volatile String logDirName; //XXX merge with dir?

    /** The service type (for selecting the admin class) */
    private volatile String type;

    /** The transformer for munging the service descriptor */
    protected volatile ServiceDescriptorTransformer transformer = null;

    /** The component name in the configuration file */
    private volatile String component;

    /**
     * Construct an <code>AbstractServicerAdmin</code>.
     *
     * @param config         the test properties 
     * @param serviceName    the service name
     * @param index	     the instance number for this service
     */
    public AbstractServiceAdmin(QAConfig config, 
				String serviceName, 
				int index)
    {
	this.config = config;
	this.serviceName = serviceName;
	this.index = index;
    }

    /**
     * Return the component name. The test properties are searched for
     * a value for the key <code>serviceName</code> + ".component".
     * 
     * @return the component name
     * @throws TestException if the property is not defined
     */
    protected String getServiceComponent() throws TestException {
	if (component == null) {
	    component = getMandatoryParameter("component");
	}
	return component;
    }

    /**
     * Return the service component name originally returned by the 
     * <code>getServiceComponent</code> method.
     *
     * @return the service type
     */
    public String getComponent() {
	return component;
    }

    /**
     * Return the service type. The test properties is searched for
     * a value for the key <code>serviceName</code> + ".type".
     * 
     * @return the type identifier
     * @throws TestException if the property is not defined
     */
    protected String getServiceType() throws TestException {
	type = getMandatoryParameter("type");
	return type;
    }

    /**
     * Return the service type originally returned by the 
     * <code>getServiceType</code> method.
     *
     * @return the service type
     */
    public String getType() {
	return type;
    }

    /**
     * Return the service <code>ProxyPreparer</code> entry name. The
     * test properties is searched for a value for the key
     * <code>serviceName</code> + ".preparername".  If the this
     * property is undefined, <code>null</code> is returned.
     * 
     * @return the preparer entry name 
     */
    protected String getServicePreparerName() {
	preparerName = config.getServiceStringProperty(serviceName,
						       "preparername",
						       index);
	return preparerName;
    }

    /**
     * Return the service ProxyPreparer entry name originally returned by the 
     * <code>getServicePreparerName</code> method.
     *
     * @return the service proxy preparer name
     */
    public String getPreparerName() {
	return preparerName;
    }

    /**
     * Return the service implementation class name. The environment
     * must contain a value for the key <code>serviceName</code> + ".impl".
     * 
     * @return the impl class name
     * @throws TestException if the impl class name cannot be found
     */
    protected String getServiceImpl() throws TestException {
	impl = getMandatoryParameter("impl");
	return impl;
    }

    /**
     * Return the service implementation class name originally returned by the 
     * <code>getServiceImpl</code> method.
     *
     * @return the service implementation class name
     */
    public String getImpl() {
	return impl;
    }

    /**
     * Return the codebase for the service. The environment must contain
     * a value for the key <code>serviceName</code> + ".codebase". The
     * returned codebase URL is converted into a URL supporting codebase
     * integrity if appropriate (that is, if the service specific or
     * generic integrityhash property is set).
     * 
     * @return the codebase for the service
     * @throws TestException if the codebase value cannot be found, or
     *         if codebase integrity is required and a problem occurs
     *         converting the URL
     */
    protected final String getServiceCodebase() throws TestException {
	codebase = getMandatoryParameter("codebase");
	String cb = fixCodebase(codebase);
        try {
            codebase = uriToCodebaseString(pathToURIs(cb));
        } catch (URISyntaxException e){
            logger.log(Level.FINE,"Codebase not URI compliant: "+ cb, e);
            codebase = cb;
        }
	return  codebase;
    }
    
    /**
     * Convert a string containing a space-separated list of URL Strings into a
     * corresponding array of URI objects, throwing a URIsyntaxException
     * if any of the URLs are invalid.  This method returns null if the
     * specified string is null.
     *
     * @param path the string path to be converted to an array of urls
     * @return the string path converted to an array of URLs, or null
     * @throws MalformedURLException if the string path of urls contains a
     *         mal-formed url which can not be converted into a url object.
     */
    private static URI[] pathToURIs(String path) throws URISyntaxException {
	if (path == null) {
	    return null;
	}
        URI[] urls = null;
	StringTokenizer st = new StringTokenizer(path);	// divide by spaces
	urls = new URI[st.countTokens()];
	for (int i = 0; st.hasMoreTokens(); i++) {
            String uri = st.nextToken();
            uri = Uri.fixWindowsURI(uri);
            urls[i] = Uri.uriToURI(Uri.parseAndCreate(uri));
//            urls[i] = UriString.normalise(new URI(UriString.escapeIllegalCharacters(uri)));
	}
	return urls;
    }

    /**
     * Convert URI[] to a space delimited code base string.
     * @param uri
     * @return Code base String.
     */
    private static String uriToCodebaseString(URI[] uri){
        if (uri == null) throw new NullPointerException("uri was null");
        StringBuilder sb = new StringBuilder(200);
        int l = uri.length;
        for (int i = 0; i < l; i++){
            if (uri[i] != null){
                sb.append(uri[i]);
                if ( i != l - 1) sb.append(" ");
            }
        }
        return sb.toString();
    }
    
    /**
     * Return the codebase originally returned by the 
     * <code>getServiceCodebase</code> method.
     *
     * @return the service codebase
     */
    public String getCodebase() {
	return  codebase;
    }

    /**
     * Return the service policy file name. The environment
     * must contains a value for the key 
     * <code>serviceName</code> + ".policyfile".
     * 
     * @return the policy file name
     * @throws TestException if the policy file name cannot be found
     */
    protected String getServicePolicyFile() throws TestException {
	policyFile = getMandatoryParameter("policyfile");
	return policyFile;
    }

    /**
     * Return the policy file name originally returned by the 
     * <code>getServicePolicyFile</code> method. 
     *
     * @return the service policy file name
     */
    public String getPolicyFile() {
	return policyFile;
    }

    /**
     * Return the service classpath. The environment
     * must contains a value for the key 
     * <code>serviceName</code> + ".classpath".
     * 
     * @return the classpath
     * @throws TestException if the classpath cannot be found
     */
    protected String getServiceClasspath() throws TestException {
	classpath = getMandatoryParameter("classpath");
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
     * Return the classpath originally returned by the 
     * <code>getServiceClasspath</code> method. 
     *
     * @return the service classpath
     */
    public String getClasspath() {
	return classpath;
    }

    /**
     * Return the service vm to invoke. The environment is searched for
     * a value for the key <code>serviceName</code> + ".serverjvm".
     * 
     * @return the vm to run, or <code>null</code> if not found
     */
    protected String getServiceJVM() {
	jvm = config.getServiceStringProperty(serviceName,
					      "serverjvm",
					      index);
	return jvm;
    }

    /**
     * Return the jvm originally returned by the 
     * <code>getServiceJVM</code> method. 
     *
     * @return the service vm
     */
    public String getJVM() {
	return jvm;
    }
  
    /**
     * Return a directory name to be used by the service. 
     * The environment is searched for
     * a value for the key <code>serviceName</code> + ".dir".
     * 
     * @return a directory name, or <code>null</code> if not found
     * @throws TestException if the parameter is not found
     */
    protected String getServiceDir() throws TestException {
	dir = getMandatoryParameter("dir");
	return dir;
    }

    /**
     * Return the directory name originally returned by the 
     * <code>getServiceDir</code> method. 
     *
     * @return the service vm
     */
    public String getDir() {
	return dir;
    }
  
    /**
     * The global VM args are merged with the value returned by
     * the services <code>serverjvmargs</code> property and are 
     * returned as a string array, separated by ',' characters
     * and having no cosmetic whitespace, since filenames may
     * include whitespace.
     *
     * @return the array of service vm options/properties, or null if
     *         none are defined
     */
    private String[] getServiceVMArgs() {
	String[] vmArgs = config.getGlobalVMArgs();
	String svcArgs = 
	    config.getServiceStringProperty(serviceName, 
					    "serverjvmargs", 
					    index);
	vmArgs = config.mergeArrays(vmArgs, config.parseArgList(svcArgs));
	return vmArgs;
    }

    /**
     * Return any command line options to be included when starting the
     * service. The options returned are the union of the following
     * sources:
     * <ul>
     * <li>the options returned by calling 
     *     <code>config.getGlobalVMArgs()</code>
     * <li>the options parsed from the string obtained from the environment
     *     as a result of searching for the key
     *     <code>serviceName</code> + ".serverjvmargs"
     * </ul>
     *
     * @return a <code>String</code> array of command line options, or 
     *         <code>null</code> if none were found
     */
    protected String[] getServiceOptions() {
	options = config.extractOptions(getServiceVMArgs());
	return options;
    }

    /**
     * Return the command line options originally returned by the 
     * <code>getServiceOptions</code> method. 
     *
     * @return the service options
     */
    public String[] getOptions() {
	return options;
    }
  
    /**
     * Return any command line properties to be included when starting the
     * service. The returned array contains alternating 
     * property-name/property-value pairs. The '-D', '-OD' and '=' strings are
     * not present. As a result, the array must contain an even number
     * of elements. The properties returned are the union of the following
     * sources:
     * <ul>
     * <li>the properties contained in the array returned by calling 
     *     <code>config.getGlobalVMArgs()</code>
     * <li>the properties parsed from the string obtained from the environment
     *     as a result of searching for the key 
     *     <code>serviceName</code> + <code>".serverjvmprops"</code>
     * </ul>
     * The harness treats the definition '-ODname=value' specially. This
     * represents a property to be defined only if a value is provided.
     * Thus, any property resolved as '-ODname=' will be discarded. This
     * is intended to propogate a property from the parent to the child
     * VM only if that property is defined in the parent VM.
     * 
     * @return a <code>String</code> array of command line options, or 
     *         <code>null</code> if none were found
     * @throws TestException if the returned array would contain an odd
     *         number of elements
     */
    protected String[] getServiceProperties() throws TestException {
        properties = config.extractProperties(getServiceVMArgs());
	if (properties != null) {
	    if (properties.length % 2 == 1) {
		throw new TestException("Properties array has odd count");
	    }
	}
	return properties;
    }

    /**
     * Return the command line properties originally returned by the 
     * <code>getServiceProperties</code> method. This method is
     * provided primarily providing information to logging methods.
     * If the <code>getServiceProperties</code> is overridden, then
     * this method should be overridden as well.
     *
     * @return the service properties
     */
    public String[] getProperties() {
	return properties;
    }

    /**
     * Return the host name of the activation system to contact.  The
     * environment is search for a value for the key 
     * <code>serviceName</code> + ".activationhost". 
     * 
     * @return the host name, or <code>null</code> if undefined
     */
    protected String getServiceActivationHost() {
	activationHost = config.getServiceStringProperty(serviceName,
					                 "activationhost", 
							 index);
	return activationHost;
    }

    /**
     * Return the activation host originally returned by the 
     * <code>getActivationHost</code> method.
     *
     * @return the service activation host
     */
    public String getActivationHost() {
	return activationHost;
    }
	
    /**
     * Return the port of the activation system to contact.  The
     * environment is searched for a value for the key 
     * <code>serviceName</code> + ".activationport". 
     * 
     * @return the port number, or <code>0</code> if undefined
     */
    protected int getServiceActivationPort() {
	activationPort = config.getServiceIntProperty(serviceName,
						       "activationport", 
						       index);
	return activationPort;
    }

    /**
     * Return the activation port originally returned by the 
     * <code>getServiceActivationPort</code> method. 
     *
     * @return the service activation port
     */
    public int getActivationPort() {
	return activationPort;
    }
	
	
    /**
     * Return the port associated with the service.  The
     * environment is searched for a value for the key 
     * <code>serviceName</code> + ".port". 
     * 
     * @return the port number, or <code>0</code> if undefined
     */
    protected int getServicePort() {
	gotPort = true;
	port = config.getServiceIntProperty(serviceName,
					    "port", 
					    index);
        if (port == Integer.MIN_VALUE) {
	    gotPort = false;
	    port = 0;
	}
	return port;
    }

    /**
     * Return the port originally returned by the 
     * <code>getServicePort</code> method.
     *
     * @return the service port
     */
    public int getPort() {
	return port;
    }

    /**
     * Return the name of the <code>ConfigurationFile</code> for the service.
     * If the current configuration tag is 'none', then the value "-" is
     * returned.  Otherwise, the test properties are searched for a value for
     * <code>serviceName</code> + ".serviceConfiguration". If the value is "-",
     * that value is returned. Otherwise the value is
     * converted to an absolute path name relative to the current configuration
     * root directory.
     *
     * @return the service configuration file name
     * @throws TestException if the configuration file is undefined
     */
    protected String getServiceConfigurationFileName() throws TestException {
        serviceConfigFile = "-";
	if (!config.getConfigurationTag().equals("none")) {
	    serviceConfigFile = 
		config.getServiceStringProperty(serviceName,
						"serviceConfiguration",
						index);
	    if (serviceConfigFile == null) {
		throw new TestException("ConfigurationFile is undefined");
	    }
        }
	return serviceConfigFile;
    }

    /**
     * Add the name of the service configuration file to the given list.
     * The test properties must contain a value
     * for <code>serviceName</code> + ".serviceConfiguration". The value
     * obtained from the test properties is converted to an absolute path
     * name relative to the current configuration root directory.
     *
     * @param list the list to append the configuration file name
     * @throws TestException if the configuration file is not defined
     */
    protected void addServiceConfigurationFileName(List list) 
	throws TestException
    {
	list.add(getServiceConfigurationFileName());
    }

    /**
     * Return the configuration file name originally obtained by the 
     * <code>getServiceConfigurationFileName</code> method.
     *
     * @return the service configuration file name
     */
    public String getConfigurationFileName() {
	return serviceConfigFile;
    }

    /**
     * Obtain the <code>Configuration</code> object associated with the service
     * configuration file. If the 'none' configuration was selected, then
     * <code>EmptyConfiguration.INSTANCE</code> is returned. Otherwise, the test
     * properties must contain a value for <code>serviceName</code> +
     * ".serviceConfiguration". The value obtained from the test properties is
     * converted to an absolute path name relative to the current configuration
     * root directory. A <code>Configuration</code> instance is generated from
     * the file.
     *
     * @return the configuration object for the service
     * @throws TestException if generation of the configuration object fails
     *                       or the configuration file is undefined
     */
    protected Configuration getServiceConfiguration() throws TestException {
	Configuration configuration = EmptyConfiguration.INSTANCE;
	String name = getServiceConfigurationFileName();
	if (!name.equals("-")) {
	    try {
		configuration = 
		    ConfigurationProvider.getInstance(new String[]{name});
	    } catch (ConfigurationException e) {
		throw new TestException("Service configuration problem", e);
	    }
	}
	return configuration;
    }

    /**
     * Return the name of the <code>ConfigurationFile</code> for the service's
     * starter. If the 'none' configuration is selected, return '-'. Otherwise,
     * the test properties must contain a value for <code>serviceName</code> +
     * ".starterConfiguration". If the value is "-", that value is returned.
     * Otherwise the value obtained from the test properties is
     * converted to an absolute path name relative to the current configuration
     * root directory.
     *
     * @return the service configuration file name
     * @throws TestException if the starter configuration is undefined
     */
    protected String getServiceStarterConfigurationFileName() 
	throws TestException 
    {
	starterConfig = "-";
	if (!config.getConfigurationTag().equals("none")) {
	    starterConfig = config.getServiceStringProperty(serviceName,
							"starterConfiguration",
							index);
	    if (starterConfig == null) {
		throw new TestException("Starter Configuration undefined");
	    }
        }
	return starterConfig;
    }

    /**
     * Return the configuration file name originally obtained by the 
     * <code>getServiceStarterConfigurationFileName</code> method.
     *
     * @return the service's starter configuration file name
     */
    public String getStarterConfigurationFileName() {
	return starterConfig;
    }

    /**
     * Obtain the <code>Configuration</code> object associated with the
     * service's starter configuration file. If the 'none' configuration is
     * selected, <code>EmptyConfiguration.INSTANCE</code> is
     * returned. Otherwise, the test properties must contain a value for
     * <code>serviceName</code> + ".starterConfiguration". The value obtained
     * from the test properties is converted to an absolute path name relative
     * to the current configuration root directory. A <code>Configuration</code>
     * instance is generated from the file.
     *
     * @return the configuration object for the service's starter
     * @throws TestException if generation of the configuration object fails
     *                       or the start configuration file is undefined
     */
    protected Configuration getStarterConfiguration() throws TestException {
	Configuration configuration = EmptyConfiguration.INSTANCE;
	String name = getServiceStarterConfigurationFileName();
	if (!name.equals("-")) {
	    try {
		configuration = 
		    ConfigurationProvider.getInstance(new String[]{name});
	    } catch (ConfigurationException e) {
		throw new TestException("Starter configuration problem", e);
	    }
	}
	return configuration;
    }

    /**
     * Convert an http codebase to an httpmd codebase if the service requires
     * codebase integrity. The test properties are searched for the key
     * <code>serviceName</code> + ".integrityhash". On failure, a search
     * is performed for the key "com.sun.jini.qa.harness.integrityhash".
     * If either search is successful and the corresponding value is
     * not <code>"none"</code>, then codebase integrity is assumed and
     * the property value is interpreted as the secure hash algorithm to use.
     * The given codebase may contain multiple URLs separated by white space.
     *
     * @param codebase the codebase to fix, which may be <code>null</code>
     * @return the possibly modified codebase
     * @throws TestException if codebase integrity is required and
     *         the codebase URL is malformed or is not an http URL
     * 
     */
    protected String fixCodebase(String codebase) throws TestException {
	String hash = config.getServiceStringProperty(serviceName, 
						      "integrityhash",
						      index);
	/*
	 * if hash is null, this will fall back to searching the
	 * test properties for com.sun.jini.qa.harness.integrityhash
	 */
        return config.genIntegrityCodebase(codebase, hash);
    }

    /**
     * Convenience method to return the value of a key which must exist
     * in the test properties.
     *
     * @param property the service property to search for (without 
     *                 the leading '.')
     * @return the corresponding test properties value
     * @throws TestException if the value is not found
     */
    protected String getMandatoryParameter(String property) 
	throws TestException 
    {
        String val = config.getServiceStringProperty(serviceName, 
						     property, 
						     index);
	if (val == null) {
	    throw new TestException("Missing parameter " + property 
				    + " for " + serviceName);
	}
	return val;
    }

    /**
     * Generate logging output displaying the parameters which were
     * provided by the accessors of this class. If a subclass provides
     * additional accessor methods, that class should provide an
     * override of this method, first calling 
     * <code>super.logServiceParameters</code> and then log records for the
     * accessor values added by the subclass should be written. The
     * source information is set to <code>null</code> in the log records
     * to improve the readability of the output. In most cases, output
     * for a parameter is generated only if that parameter was initialized
     * through its <code>getServiceXXX</code> accessor.
     *
     * @throws TestException if the type property is undefined.
     */
    protected void logServiceParameters() throws TestException {
	String suffix = ((index == 0) ? "(.0)" : ("." + index));
	logger.logp(Level.FINE, null, null, "");
	logger.logp(Level.FINE,  null, null,
		   "Parameters for " + serviceName + suffix + ":");
	String val = getType();
	if (val == null) {
	    val = getServiceType();
	}
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     type              : " + val);
	}
	val = getCodebase();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     codebase          : " + val);
	}
	val = getImpl();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     impl              : " + val);
	}
	val = getComponent();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     component name    : " + val);
	}
	val = getPolicyFile();
	if (val != null) {
	    logger.logp(Level.FINE, null, null, 
			"     policy file       : " + val);
	}
	val = getClasspath();
	if (val != null) {
	    logger.logp(Level.FINE, null, null, 
			"     classpath         : " + val);
        }
	val = getJVM();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     vm                : " + val);
	}
	val = getDir();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     directory         : " + val);
	}
	val = stringifyOptions(getOptions());
	if ( val != null) {
	    logger.logp(Level.FINE, null, null,
			"     options           : " + val);
	}
	String[] vArray = stringifyProperties(getProperties());
	if (vArray != null) {
	    logger.logp(Level.FINE, null, null,
			"     properties        : " + vArray[0]);
	    for (int i = 1; i < vArray.length; i++) {
		logger.logp(Level.FINE, null, null,
			"                       : " + vArray[i]);
	    }
	}
        val = getConfigurationFileName();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     service conf file : " + val);
	}
        val = getStarterConfigurationFileName();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     starter conf file : " + val);
	}
	val = getActivationHost();
	if (val != null) {
	    logger.logp(Level.FINE, null, null,
			"     activation host   : " + val);
	}
	int intVal = getActivationPort();
	if (intVal > 0) {
	    logger.logp(Level.FINE, null, null, 
			"     activation port   : " + intVal);
	}
	if (preparerName != null) {
	    logger.logp(Level.FINE, null, null,
			"     proxy preparer    : " + preparerName);
	}
    }

    /**
     * Log the option arguments passed to the service. 
     *
     * @param overrides the set of overrides to log
     */
    protected void logOverrides(String[] overrides) {
	if (overrides != null) {
	    for (int i = 0; i < overrides.length; i++) {
		logger.logp(Level.FINE, null, null, 
			    "     option args " + i + "     : " + overrides[i]);
	    }
	}
	logger.logp(Level.FINE, null, null, ""); // trailing blank line
    }

    /**
     * Convert an array of option strings into the
     * single string which would appear on the command line to
     * pass those options.
     *
     * @param options the options to stringify, or <code>null</code>
     * @return the command-line representation of the options
     */
    private String stringifyOptions(String[] options) {
	if (options == null) {
	    return null;
	}
	StringBuffer sb = new StringBuffer();
	for (int i = 0; i < options.length; i++) {
	    if (i > 0) {
		sb.append(" ");
	    }
	    sb.append(options[i]);
	}
	return sb.toString();
    }

    /**
     * Convert an array of property definitions into an array 
     * of strings formatted as they would appear on the command line to
     * pass those properties. The <code>properties</code> passed
     * to the method must contain an even number of elements.
     *
     * @param properties the properties to stringify, or <code>null</code>
     * @return the command-line representation of the properties
     */
    private String[] stringifyProperties(String[] properties) {
	if (properties == null || properties.length == 0) {
	    return null;
	}
	String[] ret = new String[properties.length/2];
	for (int i = 0; i < properties.length; i += 2) {
	    ret[i/2] = "-D" + properties[i] + "=" + properties[i + 1];
	}
	return ret;
    }

    /**
     * Add configuration override values to <code>list</code>
     * specifying initial unicast discovery port.
     *
     * @param list the command-line list
     * @throws TestException if the component name is undefined for this service
     */
    protected void addServiceUnicastDiscoveryPort(ArrayList list) 
	throws TestException
    {
	getServicePort();
	if (gotPort) {
	    list.add(getServiceComponent() 
		     + ".initialUnicastDiscoveryPort=" + port);
	}
    }

    /**
     * Add configuration override values to <code>list</code>
     * specifying the initial groups and initial locators the service
     * is to use.  The <code>tojoin </code> service property is
     * retrieved and overrides are generated for the well-known entry
     * names <code>initialLookupGroups</code> and
     * <code>initialLookupLocators</code>.  The values are
     * formatted such that the entries returned from the configuration
     * file will be arrays of <code>LookupLocators</code> or arrays of
     * <code>Strings</code>, as appropriate. Group names are made
     * unique. If the 'none' configuration was selected, the locators
     * created are LookupLocators; otherwise, the locators created
     * are ConstrainableLookupLocators.
     *
     * @param list the overrides list
     */
    protected void addServiceGroupsAndLocators(ArrayList list) 
	throws TestException
    {
	String tojoin = config.getServiceStringProperty(serviceName,
							"tojoin", index);
	if (tojoin != null) {
	    if (doRandom()) {
		tojoin = config.makeGroupsUnique(tojoin);
	    }
	    ArrayList groupList = new ArrayList();
	    ArrayList locatorList = new ArrayList();
	    StringTokenizer tok = new StringTokenizer(tojoin, ", \t");
	    while (tok.hasMoreTokens()) {
		String s = tok.nextToken();
		if (config.isLocator(s)) {
		    locatorList.add(s);
		} else {
		    groupList.add(s);
		}
	    }
	    if (locatorList.size() > 0) {
		Configuration c = getServiceConfiguration();
		boolean configDefined = c != EmptyConfiguration.INSTANCE;
		StringBuffer b = new StringBuffer();
		b.append(getServiceComponent());
		b.append(".initialLookupLocators = new net.jini.core.discovery.LookupLocator[] {");
		for (int i = 0; i < locatorList.size(); i++) {
		    if (i > 0) {
			b.append(",");
		    }
		    b.append(locatorString((String) locatorList.get(i), 
					   configDefined));
		}
		b.append("}");
		list.add(b.toString());
		// get the locator constraints
		BasicMethodConstraints constraints = null;
		if (configDefined) {
		    try {
			constraints = (BasicMethodConstraints) 
			              c.getEntry(getServiceComponent(),
						 "locatorConstraints",
						 BasicMethodConstraints.class);
		    } catch (ConfigurationException e) {
			logger.log(Level.INFO, "Missing locatorConstraints", e);
		    }
		}
		locators = new LookupLocator[locatorList.size()];
		for (int i = 0; i < locators.length; i++) {
		    try {
			if (configDefined) {
			    locators[i] = new ConstrainableLookupLocator(
				                   (String) locatorList.get(i),  
						   constraints);
			} else {
			    locators[i] = new LookupLocator(
						   (String) locatorList.get(i));
			}
		    } catch (MalformedURLException e) {
			throw new TestException("Bad URL", e);
		    }
		}
	    }
	    addGroupList(list, 
			 groupList, 
			 "initialLookupGroups");
	    groups = getGroupArray(groupList);
	}
    }

    /**
     * Generate a string which is the <code>ConfigurationFile</code> language
     * fragment required to instantiate a lookup locator. If useConfig is true,
     * constrainable locator definitions are created. For
     * ConstrainableLookupLocators, the constraints are expected to be defined
     * by an entry named locatorConstraints for the service component. For
     * example, in the case where locators are being generated for mercury, if
     * the given locator were "jini://foo", then if constrainable is false, the
     * following String is generated:
     * <pre>
     *   new LookupLocator("jini://foo")
     * </pre>
     * and if constrainable is true:
     * <pre>
     *   new ConstrainableLookupLocator("jini://foo", 
     *                                  com.sun.jini.mercury.locatorConstraints)
     * </pre>
     * is generated.
     *
     * @param locator the locator
     * @param constrainable if true, generate constrainable locators
     * @return the generated ConfigurationFile language fragment
     */
    private String locatorString(String locator, boolean constrainable) 
	throws TestException
    {
	StringBuffer b = new StringBuffer("new ");
	if (constrainable) {
	    b.append("net.jini.discovery.Constrainable");
	} else {
            b.append("net.jini.core.discovery.");
        }
	b.append("LookupLocator(");
	b.append("\"").append(locator).append("\"");
	if (constrainable) {
	    b.append(", ")
	     .append(getServiceComponent())
	     .append(".locatorConstraints");
	}
	b.append(")");
	return b.toString();
    }

    /**
     * Return the set of groups originally generated by a previous call
     * to <code>addServiceGroupsAndLocators</code>.
     *
     * @return the array of groups
     */
    public String[] getGroups() {
	return groups;
    }

    /**
     * Return the set of locators originally generated by a previous call
     * to <code>addServiceGroupsAndLocators</code>.
     *
     * @return the array of locators
     */
    public LookupLocator[] getLocators() {
	return locators;
    }

    /**
     * Add configuration override values to <code>list</code>
     * specifying the member groups the service is to use.  The
     * <code>membergroups </code> service property is retrieved and
     * overrides are generated for the well-known entry name
     * <code>initialMemberGroups</code>.  The values are
     * formatted such that the entries returned from the configuration
     * file will be arrays of <code>Strings</code>. The groups are
     * randomized.
     *
     * @param list the options list to append the overrides to 
     */
    protected void addServiceMemberGroups(ArrayList list) throws TestException {
	String memberstring = config.getServiceStringProperty(serviceName,
							      "membergroups", 
							      index);
	if (memberstring != null) {
	    if (doRandom()) {
		memberstring = config.makeGroupsUnique(memberstring);
	    }
	    ArrayList groups = new ArrayList();
	    StringTokenizer tok = new StringTokenizer(memberstring, ", \t");
	    while (tok.hasMoreTokens()) {
		String s = tok.nextToken();
		groups.add(s);
	    }
	    addGroupList(list, 
			 groups, 
			 "initialMemberGroups");
	    memberGroups = getGroupArray(groups);
	}
    }

    /**
     * Add a group override to the override list. If groups.size() == 0,
     * this method does nothing. Otherwise, an override string is generated
     * from the contents of the group list. Groups 'all' and 'none'
     * override named groups, and 'public' is replaced with the empty string.
     *
     * @param list the override list
     * @param groups the group list
     * @param entryName the unqualified entry name for the override definition
     */
    private void addGroupList(ArrayList list, 
			      ArrayList groups, 
			      String entryName) throws TestException
    {
	if (groups.size() > 0) {
	    StringBuffer gbuf = new StringBuffer("new String[]{");
	    String terminalString = "}";
	    String g = null;
	    for (int i = 0; i < groups.size(); i++) {
		g = (String) groups.get(i);
		if (g.equals("all")) {
		    gbuf = new StringBuffer("null");
		    terminalString = "";
		    break;
		} else if (g.equals("none")){
		    gbuf = new StringBuffer("new String[]{");
		    break;
		} else if (g.equals("public")) {
		    g = "";
		}
		if (i > 0) {
		    gbuf.append(", ");
		}
		gbuf.append("\"" + g + "\"");
	    }
	    gbuf.append(terminalString);
	    list.add(getServiceComponent() + "." + entryName + " = " + gbuf);
	}
    }

    /**
     * Return a group string array corresponding to the groups
     * contained in the given list. This must correspond to the
     * set of groups defined as an override by the addGroupList
     * method. Therefore, if groups.size() == 0, the returned
     * value must specify the public group, the default assumed
     * by services (except reggie). Otherwise, the returned
     * array should mirror the override generated by the
     * addGroupList method.
     *
     * @param groups the group list
     * @return the array equivalent to the corresponding override
     */
    private String[] getGroupArray(ArrayList groups) {
	if (groups.size() == 0) {
	    return new String[]{""};
	}
	StringBuffer gbuf = new StringBuffer();
	String g = null;
	for (int i = 0; i < groups.size(); i++) {
	    g = (String) groups.get(i);
	    if (g.equals("all")) {
		return null;
	    } 
	    if (g.equals("none")){
		return new String[0];
	    } 
	    if (g.equals("public")) {
		g = "";
		groups.set(i, g);
	    }
	}
	return (String[]) groups.toArray(new String[0]);
    }

    /**
     * Return the set of member groups originally generated by a previous
     * call to <code>addServiceMemberGroups</code>.
     *
     * @return the set of membergroups
     */
    public String[] getMemberGroups() {
	return memberGroups;
    }

    /**
     * Add a configuration override for the persistence log. The
     * <code>log</code> service property value is retrieved and is used
     * to generate a unique directory name.  The value is
     * formatted as a string literal containing the fully qualified
     * name of the log directory assigned to the configuration value
     * named <code>persistenceDirectory</code>. If the value
     * of <code>log</code> is <code>"none"</code>, then no persistence
     * log override is added to the list. If the value of <code>log</code>
     * is provided as an absolute path name, then that value is used
     * directly
     *
     * @param list options list to append to
     * @throws TestException if the directory cannot be created, or
     *                       if the log name was absolute and the named
     *                       directory exists
     */
    protected void addServicePersistenceLog(ArrayList list) throws TestException {
	String log = getServicePersistenceLog();
	if (log != null) {
	    list.add(getServiceComponent() 
		     + ".persistenceDirectory = " + "\"" + log + "\"");
	}
    }

    /**
     * Get the name of the service persistence log. The
     * <code>log</code> service property value is retrieved and is used
     * to generate a unique directory name. If the value
     * of <code>log</code> is <code>"none"</code>, then
     * this method returns <code>null</code>. If the value of <code>log</code>
     * is provided as an absolute path name, then that value is returned.
     * The expected usage is that the property value will be a simple
     * token used to generate the name of a persistence directory
     * in the temp file system. If the property is undefined, a
     * default value of 'log' is used.
     *
     * @return the log directory expressed as an absolute path name
     * @throws TestException if the directory cannot be created, or
     *                       if the log name was absolute and the named
     *                       directory exists
     */
    protected String getServicePersistenceLog() throws TestException {
	File logDir = null;
	String log = config.getServiceStringProperty(serviceName,
						     "log",
						     index);
	if (log == null) {
	    log = "log";
	}
	if (log.equals("none")) {
	    return null;
	}
	logDir = new File(log);
	if (!logDir.isAbsolute()) {
	    try {
		logDir = config.createUniqueDirectory(log,
						      "dir",
						      null);
		logDir.delete();
	    } catch (IOException e) {
		throw new TestException("failure creating persistence dir", 
					e);
	    }
	}
	if (logDir.exists()) {
	    throw new TestException("persistence directory exists" 
				   +  logDir.getAbsolutePath());
	}
	// believe it or not, converts '\' to '\\' so that ConfigurationFile
	// doesn't treat a single '\' as an escape
	logDirName = logDir.getAbsolutePath().replaceAll("\\\\","\\\\\\\\");
	return logDirName;
    }

    /**
     * Add the service exporter override to the given list. The exporter name is
     * retrieved from the configuration by getting the entry named
     * "exporter.name". The value of this entry should be the name which the
     * service uses to obtain it's exporter. The exporter is defined as one of
     * "exporter.activatableExporter", "exporter.persistentExporter", or
     * "exporter.transientExporter", as determined by the <code>type</code>
     * parameter for this service. The configuration is presumed to contain
     * actual exporter entries which map to these names. If there is no entry
     * named "exporter.name", or if the 'none' configuration is selected, this
     * method logs that fact and returns without modifying the list. This may be
     * appropriate for remote test objects with hard-coded exports.
     * 
     * @param list the override list into which to place the override
     * @throws TestException if the configuration is undefined
     */
    protected void addServiceExporter(ArrayList list) throws TestException{
	Configuration c = getServiceConfiguration();
	if (type == null) {
	    getServiceType();
	}
	try {
	    String name = 
		(String) c.getEntry("exporter", "name", String.class);
	    list.add(name + " = exporter." + type + "Exporter");
	} catch (ConfigurationException e) {
	    logger.log(Level.FINER, "no exporter definition provided");
	}
    }

    /**
     * Return the persistence log directory name originally generated by 
     * a previous call to <code>addServicePersistenceLog</code>.
     *
     * @return the log name
     */
    public String getLogDir() {
	return logDirName;
    }

    /**
     * Add any test defined overrides to the argument list. The set of
     * registered <code>OverrideProvider</code>s is retrieved from the
     * test properties and all overrides returned by the provider are appended
     * to the list. Any returned override with an entry name identical to
     * one already present in the list will replace that entry. Therefore,
     * this method should be the final method called when setting up the
     * overrides.
     *
     * @param list the options argument list
     * @throws TestException if an error occurs in the call to a providers
     *                       <code>getOverrides</code> method.
     */
    protected void addRegisteredOverrides (List list) throws TestException {
	OverrideProvider[] ops = config.getOverrideProviders();
	for (int i = 0; i < ops.length; i++) {
	    String[] overrides = ops[i].getOverrides(config, serviceName, index);
	    if (overrides == null) {
		continue;
	    }
	    for (int j = 0; j < overrides.length; j += 2) {
		// remove old override matching this overrides name
		for (int k = 0; k < list.size(); k++) {
		    String oldOverride = (String) list.get(k);
		    int index = oldOverride.indexOf('=');
		    if (index >= 0) {
			String oldOverrideName = 
			    oldOverride.substring(0, index).trim();
			if (overrides[j].equals(oldOverrideName)) {
			    list.remove(k);
			    break;
			}
		    }
		}
		list.add(overrides[j] + "=" + overrides[j + 1]);
	    }
	}
    }

    /**
     * Prepare the service proxy. The name of the preparer must have
     * been obtained by an earlier call to <code>getServicePreparerName</code>.
     * If the preparer name is non-null, a proxy preparer having that name must
     * be present in the test configuration; that preparer is used to
     * prepare the given service proxy, and the prepared proxy is returned.
     * If the preparer name is null, the original proxy is returned. If the
     * configuration is undefined (as result of the "none" config tag), 
     * the original object is returned.
     *
     * @param proxy the proxy to prepare
     * @throws TestException if preparation fails, or if the earlier call to 
     *                       <code>getServicePreparerName</code>
     *                       found a preparer name, but no entry having that 
     *                       name could be found in the test configuration
     */
    protected Object doProxyPreparation(Object proxy) throws TestException {
	String[] entryTokens = config.splitKey(preparerName);
	if (entryTokens != null) {
	    try {
		ProxyPreparer p;
		Configuration c = config.getConfiguration();
		if (!(c instanceof QAConfiguration)) { // the 'none' configuration
		    return proxy;
		}
		p = (ProxyPreparer) 
		    c.getEntry(entryTokens[0],
			       entryTokens[1],
			       ProxyPreparer.class,
			       null);
		if (p == null) {
		    throw new TestException("No preparer found for entry " 
					    + preparerName);
		}
		proxy = p.prepareProxy(proxy);
	    } catch (ConfigurationException e) {
		throw new TestException("Configuration problem", e);
	    } catch (RemoteException e) {
		throw new TestException("Remote Exception preparing proxy", e);
	    }
	}
	return proxy;
    }

    /**
     * Register a <code>ServiceDescriptorTransformer</code> to be called
     * immediately before the <code>create</code> method is called. this
     * method must be called before the <code>start</code> method is called.
     *
     * @param t the transformer
     */
    public void registerDescriptorTransformer(ServiceDescriptorTransformer t) {
	this.transformer = t;
    }

    /**
     * Determine whether randomization of groups is to be done. Some
     * admins (RunningServiceAdmin) require non-randomized groups.
     *
     * @return true if groups are to be randomized
     */
    protected boolean doRandom() {
	return true;
    }

    /**
     * Return the service name.
     *
     * @return the name
     */
    public String getName() {
	return serviceName;
    }
}


