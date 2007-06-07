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
package com.sun.jini.tool.envcheck.plugins;

import com.sun.jini.start.SharedActivationGroupDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;

import com.sun.jini.tool.envcheck.AbstractPlugin;
import com.sun.jini.tool.envcheck.EnvCheck;
import com.sun.jini.tool.envcheck.Plugin;
import com.sun.jini.tool.envcheck.Reporter;
import com.sun.jini.tool.envcheck.Reporter.Message;
import com.sun.jini.tool.envcheck.SubVMTask;
import com.sun.jini.tool.envcheck.Util;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;

import net.jini.config.ConfigurationException;
import net.jini.security.policy.DynamicPolicyProvider;

//explanations need tuning (warnings vs errors)

/**
 * Checks whether various security oriented configuration files exist and are
 * accessible.  content verification is done only for the login configuration,
 * and is limited to verifying that
 * <code>javax.security.auth.login.Configuration.getConfiguration()</code> can
 * be called successfully. The <code>-security</code> must be included on the
 * command line for these checks to be done. The checks performed include:
 * <ul>
 * <li>verify that the security provider used is an instance of 
 *     <code>DynamicPolicyProvider</code>
 * <li>verify that the property <code>javax.net.ssl.trustStore</code> is defined
 *     and its value is the name of a readable non-directory file
 * <li>check whether <code>com.sun.jini.discovery.x500.trustStore</code> is
 *     defined, and if so that its value is the name of a readable non-directory
 *     file
 * <li>check whether <code>javax.net.ssl.keyStore</code> is defined, and if
 *     so that its value is the name of a readable non-directory file
 * <li>obtain the list of login configuration files which the system will will
 *     attempt to load (the value of the
 *     <code>java.security.auth.login.config</code> system property and the
 *     <code>login.config.url.[n]</code> entries in the security properties, or
 *     the users <code>.java.login.config</code> file if no other source is
 *     defined).  Verify that at least one such entry exists. Verify that all
 *     defined configuration files exists, are readable, and are not
 *     directories.  Verify that
 *     <code>javax.security.auth.login.Configuration.getConfiguration()</code>
 *     can be called successfully.
 * </ul>
 * These checks are performed for the command line being analyzed and
 * for the activation group if one exists.
 */
public class CheckJsseProps extends AbstractPlugin {

    /** reference to the plugin container */
    EnvCheck envCheck;

    /** flag indicating whether to run this plugin */
    private static boolean doChecks = false;

    String fileAccessTask = FileAccessCheckTask.class.getName();

    // inherit javadoc
    public boolean isPluginOption(String opt) {
	if (opt.equals("-security")) {
	    doChecks = true;
	    return true;
	}
	return false;
    }

    /**
     * Check the security files for the current VM and for the group
     * VM if there is a <code>SharedActivationGroupDescriptor</code>.
     *
     * @param envCheck a reference to the plugin container
     */
    public void run(EnvCheck envCheck) {
	if (!doChecks) {
	    return;
	}
	this.envCheck = envCheck;
	checkProvider(null);
	checkTrustStore(null);
	checkDiscoveryStore(null);
	checkKeyStore(null);
	checkLoginConfigs(null);
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	if (gd != null) {
	    checkProvider(gd);
	    checkTrustStore(gd);
	    checkDiscoveryStore(gd);
	    checkKeyStore(gd);
	    checkLoginConfigs(gd);
	}
    }

    /**
     * Get the source string identifying the activation group (if 
     * <code>gd</code> is not <code>null</code>) or the command line
     * (if <code>gd</code> is <code>null</code>).
     *
     * @param gd the group descriptor
     * @return the source text
     */
    private String getSource(SharedActivationGroupDescriptor gd) {
	return gd == null ? getString("cmdlineVM") : getString("groupVM");
    }

    /**
     * Return a string array representing the given arguments.
     *
     * @param s1 first array object
     * @param s2 second array object
     * @return the array
     */
    private String[] args(String s1, String s2) {
	return new String[]{s1, s2};
    }

    /**
     * Check the validity of the trust store definition for the command line
     * or group.
     * 
     * @param gd the group descriptor, or <code>null</code> to test the 
     *        command line
     */
    private void checkTrustStore(SharedActivationGroupDescriptor gd) {
	String source = getSource(gd);
	String name = "javax.net.ssl.trustStore"; // the property name
	String phrase =  getString("truststore"); // brief description
	if (checkExistance(gd, name, phrase, source)) {
	    Message message;
	    Object lobj = 
		envCheck.launch(fileAccessTask, args(name, phrase));
	    if (lobj == null) {
		message = new Message(Reporter.INFO,
				      getString("truststoreOK"),
				      getString("configExp", phrase, name));
	    } else if (lobj instanceof String) {
		message = new Message(Reporter.ERROR,
				      (String) lobj,
				      getString("configExp", phrase, name));
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("accessexception", 
						phrase,
						name),
				      (Throwable) lobj,
				      getString("configExp", phrase, name));
	    }
	    Reporter.print(message, source);
	}
    }

    /**
     * Check the validity of the discovery trust store definition for the
     * command line or group.
     * 
     * @param gd the group descriptor, or <code>null</code> to test the 
     *        command line
     */
    private void checkDiscoveryStore(SharedActivationGroupDescriptor gd) {
	String source = getSource(gd);
	String name = "com.sun.jini.discovery.x500.trustStore";
	String phrase = getString("discoverystore");
	if (checkExistance(gd, name, phrase, source)) {
	    Message message;
	    Object lobj = 
		envCheck.launch(null, gd, fileAccessTask, args(name, phrase));
	    if (lobj == null) {
		message = new Message(Reporter.INFO,
				      getString("discoverystoreOK"),
				      getString("dsExp"));
	    } else if (lobj instanceof String) {
		message = new Message(Reporter.ERROR,
				      (String) lobj,
				      getString("dsExp"));
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("accessexception", 
						phrase,
						name),
				      (Throwable) lobj,
				      getString("dsExp"));
	    }
	    Reporter.print(message, source);
	}
    }

    /**
     * Check the validity of the key store definition for the command line
     * or group.
     * 
     * @param gd the group descriptor, or <code>null</code> to test the 
     *        command line
     */
    private void checkKeyStore(SharedActivationGroupDescriptor gd) {
	String source = getSource(gd);
	String name = "javax.net.ssl.keyStore";
	String phrase = getString("keystore");
	if (checkExistance(gd, name, phrase, source)) {
	    Message message;
	    Object lobj = 
		envCheck.launch(null, gd, fileAccessTask, args(name, phrase));
	    if (lobj == null) {
		message = new Message(Reporter.INFO,
				      getString("keystoreOK"),
				      getString("configExp", phrase, name));
	    } else if (lobj instanceof String) {
		message = new Message(Reporter.ERROR,
				      (String) lobj,
				      getString("configExp", phrase, name));
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("accessexception", 
						phrase,
						name),
				      (Throwable) lobj,
				      getString("configExp", phrase, name));
	    }
	    Reporter.print(message, source);
	}
    }

    /**
     * Check the validity of the login configuration for the command line
     * or group.
     * 
     * @param gd the group descriptor, or <code>null</code> to test the 
     *        command line
     */
    private void checkLoginConfigs(SharedActivationGroupDescriptor gd) {
	String source = getSource(gd);
	Object lobj = 
	    envCheck.launch(null, gd, taskName("GetGroupLoginConfigs"));
	if (lobj instanceof Throwable) {
	    handleUnexpectedSubtaskReturn(lobj, source);
	    return;
	}
	Message message;
	ArrayList configs = (ArrayList) lobj;
	if (configs.size() == 0) {
	    message = new Message(Reporter.WARNING,
				  getString("noconfigfiles"),
				  getString("loginConfigExp"));
	    Reporter.print(message, source);
	}
	for (int i = 0; i < configs.size(); i += 2) {
	    String errorMsg;
	    String desc = (String) configs.get(i + 1);
	    if (configs.get(i) instanceof URL) {
		URL url = (URL) configs.get(i);
		errorMsg = Util.checkURL(url, desc);
	    } else {
		errorMsg = (String) configs.get(i);
	    }
	    if (errorMsg == null) {
		message = new Message(Reporter.INFO,
				      getString("loginconfigOK"),
				      getString("loginConfigExp"));
	    } else {
		message = new Message(Reporter.ERROR,
				      errorMsg,
				      getString("loginConfigExp"));
	    }
	    Reporter.print(message, source + " " + desc);
	}
	lobj = envCheck.launch(null, gd, taskName("CheckLoginConfigInit"));
	if (lobj == null) {
	    message = new Message(Reporter.INFO,
				  getString("loginInitOK"),
				  getString("loginConfigExp"));
	    Reporter.print(message, source);
	} else {
	    Throwable cause = ((Throwable) lobj).getCause();
	    if (cause instanceof IOException) {
		message = new Message(Reporter.INFO,
				      getString("loginInitBad"),
				      cause,
				      getString("loginConfigExp"));
		Reporter.print(message, source);
	    } else {  // unexpected exception
		handleUnexpectedSubtaskReturn(lobj, source);
	    }
	}
    }

    /** 
     * Get the names of the login configuration files which will be accessed
     * when the login configuration is constructed. If
     * <code>java.security.auth.login.config</code> is defined with a '==', then
     * it's value is the sole configuration file.  Otherwise, search the
     * security properties for property names of the form
     * <code>login.config.url.[n]</code>, starting with <code>n</code> of one
     * until there is a break in the sequence. Merge the resulting list with the
     * value of <code>java.security.auth.login.config</code> if it was defined
     * (with a single '='). If the resulting list is not empty, return it;
     * otherwise, check for the existence of a file named
     * <code>.java.login.config</code> in the users home directory. If found,
     * place this value in the list.
     * 
     * @return the list of login configuration files which will apply to
     *         the calling VM, or an empty list if there are not such files.
     */
    private static ArrayList getLoginConfigs() {
	ResourceBundle bundle = Util.getResourceBundle(CheckJsseProps.class);
	ArrayList list = new ArrayList();
	String source;
	boolean override = false;
	String propDefined = 
	    System.getProperty("java.security.auth.login.config");
	if (propDefined != null) {
	    source = Util.getString("fromProp", bundle);
	    if (propDefined.indexOf('=') == 0) {
		override = true;
		propDefined = propDefined.substring(1);
		source += " " + Util.getString("withOverride", bundle);
	    }
	    try {
		list.add(new URL("file:" + propDefined));
	    } catch (MalformedURLException e) {
		list.add(Util.getString("badname", bundle, propDefined));
	    }
	    list.add(source);
	    if (override) {
		return list;
	    }
	}
	int n = 1;
        String config_url;
        while ((config_url = java.security.Security.getProperty
                                        ("login.config.url." + n)) != null) {
	    try {
		list.add(new URL(config_url));
	    } catch (MalformedURLException e) {
		list.add(Util.getString("malformedurl", bundle, config_url));
	    }
	    list.add(Util.getString("secprop", bundle, "login.config.url." + n));
	    n++;
	}
	if (list.size() == 0) {
	    String userFile = System.getProperty("user.home");
	    userFile += File.separator + ".java.login.config";
	    if (new File(userFile).exists()) {
		try {
		    list.add(new URL("file:" + userFile));
		    list.add(Util.getString("userfile", bundle));
		} catch (MalformedURLException e) { // should never happen
		    e.printStackTrace();
		}
	    }
	}
	return list;
    }

    /**
     * Check the existence of a property definition in the group or
     * command line.
     *
     * @param gd the group descriptor, or <code>null</code> to check
     *           the command line
     * @param propName the property name to check for
     * @param desc phrase describing the property
     * @param source the source descriptive text
     * @return <code>true</code> if the property is defined
     */
    private boolean checkExistance(SharedActivationGroupDescriptor gd,
				   String propName, 
				   String desc, 
				   String source) {
	
	Properties p = (gd == null ? System.getProperties()
			           : gd.getServerProperties());
	if (p == null || p.getProperty(propName) == null) {
	    Message message = 
		new Message(Reporter.WARNING,
			    getString("noprop", propName, desc),
			    getString("configExp", desc, propName));
	    Reporter.print(message, source);
	    return false;
	}
	return true;
    }

    /**
     * Check that the security provider is an instance of
     * <code>DynamicPolicyProvider</code>. Done for the tool VM and for the
     * group VM if a <code>SharedActivationGroupDescriptor</code> exists.
     */
    private void checkProvider(SharedActivationGroupDescriptor gd) {
	String source = getSource(gd);
	Object lobj = envCheck.launch(null, gd, taskName("CheckProviderTask"));
	if (lobj instanceof Boolean) {
	    Message message;
	    if (((Boolean) lobj).booleanValue()) {
		message = new Message(Reporter.INFO,
				      getString("providerOK"),
				      getString("providerExp"));
	    } else {
		message = new Message(Reporter.WARNING,
				      getString("providerBad"),
				      getString("providerExp"));
	    }
	    Reporter.print(message, source);
	} else {
	    handleUnexpectedSubtaskReturn(lobj, source);
	}
    }

   /**
     * Checks the existence and accessibility of the login configuration.
     */
    public static class CheckLoginConfigInit implements SubVMTask {

	public Object run(String[] args) {
	    try {
		javax.security.auth.login.Configuration.getConfiguration();
		return null;
	    } catch (SecurityException e) {
		return e;
	    }
	}

    }

    /**
     * Checks the policy provider of the group.
     */
    public static class CheckProviderTask implements SubVMTask {

	public Object run(String[] args) {
	    try {
		if (Policy.getPolicy() instanceof DynamicPolicyProvider) {
		    return new Boolean(true);
		} else {
		    return new Boolean(false);
		}
	    } catch (SecurityException e) {
		return e;
	    }
	}

    }

    /**
     * Gets login configuration urls of the group.
     */
    public static class GetGroupLoginConfigs implements SubVMTask {

	public Object run(String[] args) {
	    return getLoginConfigs();
	}

    }
}
