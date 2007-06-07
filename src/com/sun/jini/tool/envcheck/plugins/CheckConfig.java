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
import com.sun.jini.tool.envcheck.EnvCheck;
import com.sun.jini.tool.envcheck.Reporter;
import com.sun.jini.tool.envcheck.Reporter.Message;
import com.sun.jini.tool.envcheck.Plugin;
import com.sun.jini.tool.envcheck.SubVMTask;
import com.sun.jini.tool.envcheck.Util;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Set;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;

import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import com.sun.jini.start.ServiceDescriptor;
import com.sun.jini.start.ClassLoaderUtil;

/**
 * Check the configuration files for services identified by service descriptors
 * in the service starter configuration. For each
 * <code>SharedActivatableServiceDescriptor</code> or
 * <code>NonActivatableServiceDescriptor</code>, a check is made that the
 * command line arguments are not <code>null</code> or empty and that a
 * configuration can be instantiated from those arguments. The configuration is
 * searched for all occurrences of <code>initialLookupGroups</code> entries and
 * generates warnings for those set to <code>ALL_GROUPS</code>.
 */
public class CheckConfig extends AbstractPlugin {

    /** reference to the plugin container */
    private EnvCheck envCheck;

    /**
     * If configured to perform JSK checks, perform checks for each descriptor.
     * Any service which conforms to the same command line argument convention
     * may be included in a JSK check.
     *
     * @param envCheck the plugin container
     */
    public void run(EnvCheck envCheck) {
        this.envCheck = envCheck;
	ServiceDescriptor[] d = envCheck.getDescriptors();
	for (int i = 0; i < d.length; i++) {
	    if (d[i] instanceof NonActivatableServiceDescriptor) {
		NonActivatableServiceDescriptor serviceDesc = 
		    (NonActivatableServiceDescriptor) d[i];
		String source = getString("descfor") 
		              + " " 
		              + serviceDesc.getImplClassName();
		if (checkArgs(serviceDesc, source)) {
		    checkServiceConfig(serviceDesc, source);
		}
	    }
	}
    }

    /**
     * Check that the arguments array obtained by calling
     * <code>getServerConfigArgs</code> on the given service descriptor is
     * non-<code>null</code> and has length > 0. This method is silent if the
     * check is successful.
     *
     * @param d the <code>NonActivatableServiceDescriptor</code> to check
     * @return true if the arguments are 'well formed'
     */
    private boolean checkArgs(NonActivatableServiceDescriptor d, 
			      String source) 
    {
        String[] args = d.getServerConfigArgs();
        if (args == null || args.length == 0) {
	    Message message = new Message(Reporter.ERROR,
					  getString("emptydesclist"),
					  getString("emptydesclistExp"));
	    Reporter.print(message, source);
            return false;
        }
	return true;
    }

    /**
     * Check the service configuration file by instantiating the configuration
     * in a subtask created using the same properties and arguments that would
     * be used to run the actual service. If instantiation is successful, the
     * configuration file and overrides are presumed to be well-formed. If the
     * load is successful, another subtask is run in the same way which checks
     * for an <code>initialLookupGroups</code> entry of <code>ALL_GROUPS.</code>
     * A warning is output if such an entry is found.
     *
     * @param d the <code>NonActivatableServiceDescriptor</code> to check
     */
    private void checkServiceConfig(NonActivatableServiceDescriptor d,
				    String source)
    {
	if (checkConfigLoad(d, source)) {
	    checkForAllGroups(d, source);
	}
    }

    /**
     * Execute the subtask which loads the configuration file.
     *
     * @param d the service descriptor
     * @param source the source of the arguments
     * @return true if the load was successful
     */
    private boolean checkConfigLoad(NonActivatableServiceDescriptor d, 
				    String source) 
    {
	Message message;
	String task = taskName("ConfigTask");
	boolean ret = false;
	Object o = envCheck.launch(d, envCheck.getGroupDescriptor(), task);
	if (o instanceof Boolean) {
	    if (((Boolean) o).booleanValue()) {
		message = new Message(Reporter.INFO,
				      getString("ssconfigOK"),
				      getString("ssconfigExp"));
		ret = true;
	    } else {
		message = new Message(Reporter.ERROR,
				      getString("loadfailed"),
				      getString("ssconfigExp"));
	    }
	    Reporter.print(message, source);
	} else if (o instanceof ConfigurationException) {
	    message = new Message(Reporter.ERROR,
				  getString("loadfailed"),
				  (Throwable) o,
				  getString("ssconfigExp"));
	    Reporter.print(message, source);
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}
	return ret;
    }

    /**
     * Execute the subtask which checks for ALL_GROUPS.
     *
     * @param d the service descriptor
     * @param source the source of the arguments
     */
    private void checkForAllGroups(NonActivatableServiceDescriptor d, 
				   String source) 
    {
	String task = taskName("GetGroupsTask");
	Object o = envCheck.launch(d, envCheck.getGroupDescriptor(), task);
	if (o instanceof GroupInfo[]) {
	    Message message;
	    GroupInfo[] info = (GroupInfo[]) o;
	    if (info.length == 0) {
		message = new Message(Reporter.INFO,
				      getString("notallgroup"),
				      getString("allgroupExp"));
		Reporter.print(message, source);
	    } else {
		for (int i = 0; i < info.length; i++) {
		    GroupInfo gi = info[i];
		    if (gi.groups == null) {
			message = new Message(Reporter.WARNING,
					      getString("allgroup"),
					      getString("allgroupExp"));
		    } else {
			StringBuffer groupList = new StringBuffer();
			for (int j = 0; j < gi.groups.length; j++) {
			    String group = gi.groups[j];
			    if (group.equals("")) {
				group = "public";
			    }
			    if (j > 0) {
				groupList.append(",");
			    }
			    groupList.append(group);
			}
			message = new Message(Reporter.INFO,
					      getString("groups", 
							groupList.toString()),
					      getString("allgroupExp"));
		    }
		    Reporter.print(message, source + ": " + gi.entryName);
		}
	    }
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}			
   }

    /** Struct to hold entryName/Group pairs */
    private static class GroupInfo implements Serializable {

	String entryName;
	String[] groups;

	GroupInfo(String entryName, String[] groups) {
	    this.entryName = entryName;
	    this.groups = groups;
	}
    }

    /**
     * Subtask which obtains all <code>initialLookupGroups</code> entries and
     * returns them in an array of <code>GroupInfo</code> objects.
     */
    public static class GetGroupsTask implements SubVMTask {
	
	/**
	 * Instantiate the configuration. <code>args</code> is stripped of its
	 * first value, which is the name of this task. The remaining args are
	 * used to instantiate the configuration. This operation should never
	 * fail since the configuration will have been instantiated previous in
	 * an identical way. Once instantiated, search for an ALL_GROUPS
	 * definition.
	 *
	 * @param args the args used to start this VM
	 * @return a <code>Boolean(false)</code> if no entries named
	 *         <code>initialLookupGroups,</code> or the value of
	 *         <code>initialLookupGroups</code> (a <code>String[]</code>) if
	 *         the entry is found.
	 */
	public Object run(String[] args) {
	    try {
		Configuration config = 
		    ConfigurationProvider.getInstance(args);
		return getGroups(config);
	    } catch (Exception e) {
		return e;
	    }
	}
	
	/**
	 * Search for all entries named <code>initialLookupGroups</code>
	 * in the configuration and return the array of <code>GroupInfo</code>
	 * objects containing the full entry name and associated groups
	 *
	 * @param conf the configuration to examine
	 * @return the <code>GroupInfo</code> array
	 */
	private Object getGroups(Configuration conf) {
	    ConfigurationFile cf = (ConfigurationFile) conf;
	    ArrayList list = new ArrayList();
	    Set names = cf.getEntryNames();
	    Iterator it = names.iterator();
	    while (it.hasNext()) {
		String name = (String) it.next();
		int lastDot = name.lastIndexOf(".initialLookupGroups");
		if (lastDot > 0) {
		    String component = name.substring(0, lastDot);
		    try {
			String[] groups = 
			    (String[]) (conf.getEntry(component, 
						      "initialLookupGroups",
						      String[].class));
			list.add(new GroupInfo(name, groups));
		    } catch (ConfigurationException e) {
			return e;
		    }
		}
	    }
	    return list.toArray(new GroupInfo[list.size()]);
	}
    }

    /**
     * Subtask to load the configuration
     */
    public static class ConfigTask implements SubVMTask {
	
	/**
	 * Instantiate the configuration using the given <code>args</code>.
	 *
	 * @return a <code>Boolean(true)</code> if successful, or the 
	 *         exception thrown if unsuccessful
	 */
	public Object run(String[] args) {
	    try {
		ConfigurationProvider.getInstance(args);
		return new Boolean(true);
	    } catch (Exception e) {
		return e;
	    }
	}
    }
}
