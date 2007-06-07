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
import java.net.InetAddress;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.rmi.RMISecurityManager;
import java.security.AccessControlException;
import net.jini.config.ConfigurationException;
import net.jini.config.NoSuchEntryException;
import com.sun.jini.start.ServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;

/**
 * Plugin which checks the liveness of the activation system. The
 * check will be performed if either:
 *
 * <ul>
 * <li>the <code>Configuration</code> for the tool includes an entry
 *     named <code>activation</code> which is a boolean and has a
 *     value of <code>true</code>.
 * <li>a service starter configuration is being checked and it contains
 *     an entry of type <code>SharedActivatableServiceDescriptor</code>.
 * </ul>
 * The check is performed by calling <code>ActivationGroup.getSystem()</code>.
 * A non-exceptional return indicates liveness.
 */
public class CheckActivationSystem extends AbstractPlugin {

    /** reference to the container */
    private EnvCheck envCheck;

    /** flag indicating whether to perform the check */
    private static boolean doCheck = false;

    /**
     * Test whether to unconditionally test for the presence of the
     * activation system. This will be the case if <code>opt</code> is
     * the command-line option <code>-activation</code>.
     *
     * @param opt the command-line option
     * @return true if the option is recognized by this plugin
     */
    public boolean isPluginOption(String opt) {
	if (opt.equals("-activation")) {
	    doCheck = true;
	    return true;
	}
	return false;
    }

    /**
     * Determine whether to perform this check, and perform the 
     * check if appropriate.
     */
    // XXX add support for non-default host/port values
    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	if (doCheck || envCheck.getGroupDescriptor() != null) {
	    checkAvailability();
	}
    }

    /**
     * Performs the check 
     */
    private void checkAvailability() {
	Message message;
	String taskName = taskName("CheckActivationTask");
	// OK if envCheck.getGroupDescriptor() returns null
	Object launchReturn = envCheck.launch(null, 
					      envCheck.getGroupDescriptor(),
					      taskName);
	if (launchReturn == null) {
	    message = new Message(Reporter.INFO,
				  getString("running"),
				  getString("explanationString"));
	} else {
	    Throwable ex = (Throwable) launchReturn;
	    Throwable t = ex.getCause();
	    if (t == null) {
		t = ex;
	    }
	    if (t instanceof java.rmi.ConnectException) {
		message = new Message(Reporter.WARNING,
				      getString("notRunning"),
				      t,
				      getString("explanationString"));
	    } else if (t instanceof AccessControlException) {
		message = new Message(Reporter.WARNING,
				      getString("nopermission"),
				      t,
				      getString("noPermExplanationString"));
	    } else {
		message = new Message(Reporter.WARNING,
				      getString("unexpectedException"),
				      ex,
				      getString("unexpectedExplanationString"));
	    }
	}
	Reporter.print(message);
    }

    /** subtask to perform the actual check */
    public static class CheckActivationTask implements SubVMTask {
	
	public Object run(String[] args) {
//          don't make the test sensitive to policy settings for now
//  	    System.setSecurityManager(new RMISecurityManager());
	    try {
		ActivationGroup.getSystem();
		return null;
	    } catch (Throwable e) {
		return e;
	    }
	}
    }
}
