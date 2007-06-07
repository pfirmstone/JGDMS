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
import com.sun.jini.start.SharedActivationGroupDescriptor;

/**
 * Check whether <code>jsk-policy.jar</code> is installed in the extensions
 * directory. For both the current VM and for the group VM (if any) load
 * <code>net.jini.security.policy.DynamicPolicyProvider</code> and verify that
 * it was loaded with the extension classloader by verifying that the parent
 * loader is the bootstrap loader (<code>null</code>).  The group VM is checked
 * only if the descriptors <code>getServerCommand()</code> method returns
 * non-<code>null</code>
 */
public class CheckJSKPolicy extends AbstractPlugin {

    private EnvCheck envCheck;
    private String codebase;
    private static String provider = 
	"net.jini.security.policy.DynamicPolicyProvider";

    /**
     * Perform the check both for the current VM, and for the group VM if a
     * <code>SharedActivationGroupDescriptor</code> is available from the plugin
     * container.
     */
    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	checkPolicy(null);
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	if (gd != null) {
	    checkPolicy(gd);
	}
    }

    /**
     * Check the policy for the command line or group. If <code>gd</code>
     * is <code>null</code>, the policy of the command line being analyzed
     * is checked.
     *
     * @param gd the group descriptor, or <code>null</code>
     */
    private void checkPolicy(SharedActivationGroupDescriptor gd) {
	String source = 
	    gd == null ? getString("vmsource")
	               : getString("groupsource", gd.getServerCommand());
	Object o = envCheck.launch(null, gd, taskName("JSKPolicyTask"));
	if (o instanceof Boolean) {
	    Message message;
	    if (((Boolean) o).booleanValue()) {
		message = new Message(Reporter.INFO,
				      getString("policyOK"),
				      getString("policyExp"));
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("policyBad"),
				      getString("missingPolicyExp"));
	    }
	    Reporter.print(message, source);
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}
    }

    /**
     * The task which checks the group VM.
     */
    public static class JSKPolicyTask implements SubVMTask {
	
	public Object run(String[] args) {
	    try {
		Class c = Class.forName(provider);
		if (c.getClassLoader().getParent() == null) {
		    return new Boolean(true);
		} else {
		    return new Boolean(false);
		}
	    } catch (ClassNotFoundException e) {
		return new Boolean(false);
	    }
	}
    }
}

