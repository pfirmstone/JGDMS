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
import java.io.File;
import com.sun.jini.start.SharedActivationGroupDescriptor;

/**
 * Check that the Java platform is at least version 1.4. This check is applied
 * to the VM invoked by the command line being analyzed and the VM invoked by
 *  a <code>SharedActivationGroupDescriptor</code> if the
 * descriptor's <code>getServerCommand()</code> method returns non-null. 
 */
public class CheckJDK1_4 extends AbstractPlugin {

    /** reference to the plugin  container */
    private EnvCheck envCheck;

    /** name of task to execute */
    private String taskName = taskName("JDK1_4Task");

    /**
     * Check the validity of the activation group VM (if there is one)
     *
     * @param envCheck the container
     */
    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	checkMainVM();
	checkGroupVM();
    }

    /**
     * Check the activation group VM. If there is a
     * <code>SharedActivationGroupDescriptor</code> available from the
     * container, invoke a subtask which verifies that the VM used to run that
     * group is at least version 1.4.
     */
    private void checkGroupVM() {
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	if (gd != null) {
	    String source;
	    String serverCommand = gd.getServerCommand();
	    if (serverCommand == null) {
		source = getString("cmdlinejava", envCheck.getJavaCmd());
	    } else {
		source = getString("groupjava", serverCommand);
	    }
	    processReturn(envCheck.launch(null, gd, taskName), source);
	}
    }

    /**
     * Check the vm invoked by the command-line java command.
     */
    private void checkMainVM() {
	String source = getString("mainsource", envCheck.getJavaCmd());
	processReturn(envCheck.launch(taskName), source);
    }

    /**
     * Process the object returned by the subtask. Prints a success or
     * failure message based on the type and contents of <code>o</code>.
     *
     * @param o the object returned by the subtask
     * @param source the source description
     */
    private void processReturn(Object o, String source) {
	if (o instanceof Boolean) {
	    Message message;
	    if (((Boolean) o).booleanValue()) {
		message = new Message(Reporter.INFO,
				      getString("goodjdk"),
				      getString("jdkExp"));
	    } else {
		message = new Message(Reporter.INFO,
				      getString("badjdk"),
				      getString("jdkExp"));
	    }
	    Reporter.print(message, source);
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}
    }

    /**
     * Subtask to check the VM version. If it is possible to load
     * <code>java.rmi.server.RMIClassLoaderSpi</code>, then this VM is presumed
     * to be at least version 1.4. The run method return a
     * <code>Boolean(true)</code> if the version is OK.
     */
    public static class JDK1_4Task implements SubVMTask {
	
	public Object run(String[] args) {
	    try {
		Class.forName("java.rmi.server.RMIClassLoaderSpi");
		return new Boolean(true);
	    } catch (ClassNotFoundException e) {
		return new Boolean(false);
	    }
	}
    }
}
