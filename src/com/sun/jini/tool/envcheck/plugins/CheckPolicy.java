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

import com.sun.jini.tool.envcheck.AbstractPlugin;
import com.sun.jini.tool.envcheck.Plugin;
import com.sun.jini.tool.envcheck.EnvCheck;
import com.sun.jini.tool.envcheck.Reporter;
import com.sun.jini.tool.envcheck.Reporter.Message;
import com.sun.jini.tool.envcheck.SubVMTask;
import com.sun.jini.tool.envcheck.Util;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.rmi.RMISecurityManager;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Policy;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Enumeration;
import com.sun.jini.start.ServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import com.sun.jini.start.NonActivatableServiceDescriptor;

/**
 * Check the security policy for existence, for valid syntax, and that it does
 * not grant <code>AllPermissions</code> to all protection domains.
 */
public class CheckPolicy extends AbstractPlugin {

    /** the plugin container */
    private EnvCheck envCheck;

    /**
     * Perform policy file checks for the current VM and all service
     * descriptors.
     */
    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	checkProperty();
	ServiceDescriptor[] d = envCheck.getDescriptors();
	for (int i = 0; i < d.length; i++) { 
	    checkDescriptor(d[i]);
	}
    }

    /** 
     * Check existence and accessibility of the policy file. If accessible,
     * check for syntax errors. If none, check for <code>AllPermission</code>
     * being granted. The syntax errors (and allpermissions) are checked
     * in a subtask to ensure that the policy has not already been loaded.
     */
    private void checkProperty() {
	String policyName = envCheck.getProperty("java.security.policy");
	if (policyAccessible(policyName, getString("policyprop"))) {
	    Object o = envCheck.launch(taskName("AllPermissionsTask"));
	    Message message;
	    String source = getString("cmdpolicy", policyName);
	    if (o instanceof String) {
		message = new Message(Reporter.ERROR,
				      getString("parseerror", o),
				      null);
		Reporter.print(message, source);
	    } else if (o instanceof Boolean) {
		if (((Boolean) o).booleanValue()) { // true => all permissions
		    message = new Message(Reporter.WARNING,
					  getString("grantsall"),
					  getString("allExp"));
		    Reporter.print(message, source);
		}
	    } else {
		handleUnexpectedSubtaskReturn(o, source);
	    }
	}
    }

    /**
     * Load the policy and capture any error text generated. The call
     * to <code>getPolicy</code> must be the first one made in the
     * VM since subsequent calls to <code>getPolicy</code> are silent
     *
     * @return the error text produced, or a zero-length string if none
     */
    private static String loadPolicy() {
	PrintStream oldErr = System.err;
	ByteArrayOutputStream s = new ByteArrayOutputStream();
	System.setErr(new PrintStream(s));
	Policy policy = Policy.getPolicy();
	System.setErr(oldErr);
	return s.toString();
    }

    /**
     * Check accessibility of the policy file.
     *
     * @param policy the name of the policy file
     * @param source source of the policy file
     * @return <code>true</code> if accessible
     */
    private boolean policyAccessible(String policy, String source) {
	Message message;
	boolean ret;
	if (policy == null) {
	    message = new Message(Reporter.WARNING,
				  getString("nopolicy"),
				  getString("policyExp"));
	    ret = false;
	} else {
	    String errorMsg = 
		Util.checkFileName(policy, getString("policyfile"));
	    if (errorMsg != null) {
		message = new Message(Reporter.ERROR,
				      errorMsg,
				      getString("policyExp"));
		ret = false;
	    } else {
		message = new Message(Reporter.INFO,
				      getString("policyOK"),
				      getString("policyExp"));
		ret = true;
	    }
	}
	Reporter.print(message, source);
	return ret;
    }

    /**
     * Check the policy file provided in any
     * <code>ServiceDescriptor</code>
     *
     * @param d the descriptor
     */
    private void checkDescriptor(ServiceDescriptor d) {
	String policy;
	NonActivatableServiceDescriptor nad = null;
	SharedActivationGroupDescriptor gd = null;
	String source = null;
	if (d instanceof SharedActivationGroupDescriptor) {
	    gd = (SharedActivationGroupDescriptor) d;
	    policy = gd.getPolicy();
	    source = getString("for", 
			      policy,
			      "SharedActivationGroupDescriptor");
	} else {
	    nad = (NonActivatableServiceDescriptor) d;
            gd = envCheck.getGroupDescriptor();
	    policy = nad.getPolicy();
	    source = getString("for", 
			       policy,
			       nad.getImplClassName());
	}
	if (!policyAccessible(policy, source)) {
	    return; 
	}
	Object o = envCheck.launch(nad, gd, taskName("AllPermissionsTask"));
	if (o instanceof String) {
	    Message message = new Message(Reporter.ERROR,
					  getString("parseerror", o),
					  null);
	    Reporter.print(message, source);
	} else if (o instanceof Boolean) {
	    if (((Boolean) o).booleanValue()) { // true means all permissions
		Message message = new Message(Reporter.WARNING,
					      getString("grantsall"),
					      getString("allExp"));
		Reporter.print(message, source);
	    }
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}
    }

    /**
     * Task the check the policy in a child VM. 
     */
    public static class AllPermissionsTask implements SubVMTask {

	/**
	 * Perform the syntax check and the <code>AllPermission</code> check
	 *
	 * @return a <code>String</code> containing the error message if there
	 *         was a syntax error in the policy file, a
	 *         <code>Throwable</code> if an unexpected exception is
	 *         thrown, a <code>Boolean(true)</code> if
	 *         <code>AllPermission</code> is granted, or a
	 *         <code>Boolean(false)</code> if not granted.
	 */
	public Object run(String[] args) {
	    String errMsg = loadPolicy();
	    if (errMsg.length() > 0) {
		return errMsg;
	    }
	    try {
		Policy policy = Policy.getPolicy();
		PermissionCollection permCol = 
		    policy.getPermissions(
                                new CodeSource(new URL("file:/foo"),
				(java.security.cert.Certificate[]) null));
		if (permCol.implies(new AllPermission("", ""))) {
		    return new Boolean(true); // true => allpermissions
		} 
	    } catch (SecurityException e) { // can't be all permissions 
	    } catch (Throwable t) {
		return t;
	    }
	    return new Boolean(false);
	}
    }
}
