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
package org.apache.river.tool.envcheck.plugins;

import org.apache.river.start.SharedActivationGroupDescriptor;
import org.apache.river.tool.envcheck.AbstractPlugin;
import org.apache.river.tool.envcheck.Plugin;
import org.apache.river.tool.envcheck.EnvCheck;
import org.apache.river.tool.envcheck.Reporter;
import org.apache.river.tool.envcheck.Reporter.Message;
import org.apache.river.tool.envcheck.SubVMTask;
import org.apache.river.tool.envcheck.Util;
import java.io.File;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Checks the existence and readability of the logging configuration file
 * identified by the <code>java.util.logging.config.file</code> system
 * property, if that property is defined. No content verification is done.
 * This check is performed for the tool VM and for the group VM if a
 * <code>SharedActivationGroupDescriptor</code> is provided.
 */
public class CheckLoggingConfig extends AbstractPlugin {

    private EnvCheck envCheck;

    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	checkLoggingConfig(null);
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	if (gd != null) {
	    checkLoggingConfig(gd);
	}
    }

    /**
     * Check the logging configuration for the group or command line.
     *
     * @param gd the group descriptor, or <code>null</code> to check
     *           the command line
     */
    private void checkLoggingConfig(SharedActivationGroupDescriptor gd) {
	Message message;
	String source = gd == null ? getString("cmdline") 
	                           : getString("groupVM");
	Properties p = gd == null ? envCheck.getProperties()
	                          : gd.getServerProperties();
	String task = FileAccessCheckTask.class.getName();
	String name = "java.util.logging.config.file";
	String phrase = getString("loggingconfig");
	String logConfName = p.getProperty(name);
	if (logConfName == null) {
	    message = new Message(Reporter.INFO,
				  getString("noconfig"),
				  getString("loggingconfigExp"));
	} else {
	    String[] args = new String[]{name, phrase};
	    Object lobj = envCheck.launch(null, gd, task, args);
	    if (lobj == null) {
		message = new Message(Reporter.INFO,
				      getString("okconfig"),
				      getString("loggingconfigExp"));
	    } else if (lobj instanceof String) {
		message = new Message(Reporter.ERROR,
				      (String) lobj,
				      getString("loggingconfigExp"));
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("accessexception"),
				      (Throwable) lobj,
				      getString("loggingconfigExp"));
	    }
	}
	Reporter.print(message, source);
    }
}
	

