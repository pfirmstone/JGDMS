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

import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;
import com.sun.jini.start.ServiceDescriptor;

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
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.RMISecurityManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationProvider;
import net.jini.config.NoSuchEntryException;

/**
 * Check that the persistence directory supplied by any
 * <code>SharedActivatableServiceDescriptor</code>s are either non-existant or
 * empty. Check is performed in a subtask VM started identically to how the
 * activation system would have started it. The first entry in the service
 * configuration named <code>persistenceDirectory</code> is checked. Doesn't
 * work correctly if multiple services share a configuration. Don't know how to
 * handle this.
 */
public class CheckPersistence extends AbstractPlugin {

    EnvCheck envCheck;

    /**
     * Check the persistence directory for every
     * <code>SharedActivatableServiceDescriptor</code> in the starter
     * configuration.
     *
     * @param envCheck the plugin container
     */
    public void run(EnvCheck envCheck) {
        this.envCheck = envCheck;
	ServiceDescriptor[] d = envCheck.getDescriptors();
	for (int i = 0; i < d.length; i++) {
	    if (d[i] instanceof SharedActivatableServiceDescriptor) {
		SharedActivatableServiceDescriptor sd = 
		    (SharedActivatableServiceDescriptor) d[i];
		checkDirectory(sd);
	    }
	}
    }

    /** 
     * Launch a subtask for the given descriptor to obtain all the
     * <code>persistenceDirectory</code> entries. Check each
     * entry found for validity.
     *
     * @param d the descriptor to check, which must be a
     *        <code>SharedActivatableServiceDescriptor</code
     */
    private void checkDirectory(SharedActivatableServiceDescriptor d) {
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	String source = getString("descfor", d.getImplClassName());
	Object o = envCheck.launch(d, gd, taskName("GetEntriesTask"));
	if (o instanceof String[]) {
	    checkEntries((String[]) o, d, source);
	} else if (o instanceof String) {
	    Message message = new Message(Reporter.WARNING,
					  (String) o,
					  getString("dirExp"));
	    Reporter.print(message, source);
	} else {
	    handleUnexpectedSubtaskReturn(o, source);
	}
    }

    /** 
     * Check <code>entries</code> for validity. <code>entries</code> 
     * contains a collection of pairs, the first being the fully
     * qualified name of the <code>persistenceDirectory</code> entry,
     * and the second being its value.
     *
     * @param entries the array of entry/value pairs
     * @param d the descriptor
     * @param source the source descriptive text
     */
    private void checkEntries(String[] entries, 
			      SharedActivatableServiceDescriptor d,
			      String source) 
    {
	if (entries.length == 0) {
	    Message message = new Message(Reporter.WARNING,
					  getString("noentry"),
					  getString("dirExp"));
	    Reporter.print(message, source);
	}
	for (int i = 0; i < entries.length; i += 2) {
	    String name = entries[i];
	    String dir = entries[i + 1];
	    String loopSource = source + ": " + name + "=" + dir;
	    Object lobj = checkDir(dir, d);
	    Message message;
	    if (lobj == null) {
		message = new Message(Reporter.INFO,
				      getString("dirOK"),
				      getString("dirExp"));
		Reporter.print(message, loopSource);
	    } else if (lobj instanceof String) {
		message = new Message(Reporter.ERROR,
				      (String) lobj,
				      getString("dirExp"));
		Reporter.print(message, loopSource);
	    } else {
		handleUnexpectedSubtaskReturn(lobj, loopSource);
	    }
	}
    }

    /**
     * Perform a check on the given persistence directory. 
     *
     * @param dir the name of the directory to check
     * @param d the service descriptor
     * @return <code>null</code> if the specified directory is empty
     *         or non-existant (i.e. OK). Otherwise returns an error message
     *         or <code>Throwable</code> returned by the subtask.
     */
    private Object checkDir(String dir, SharedActivatableServiceDescriptor d) {
	if (dir == null) {
	    return getString("nulldir");
	}
	String taskName = taskName("CheckDirTask");
	String[] args = new String[]{dir};
	SharedActivationGroupDescriptor g =  envCheck.getGroupDescriptor();
	return envCheck.launch(d, g, taskName, args);
    }

    /**
     * Perform directory check with an active security policy in place.
     */
    public static class CheckDirTask implements SubVMTask {

	private ResourceBundle bundle = 
	    Util.getResourceBundle(CheckPersistence.class);

	public Object run(String[] args) {
	    System.setSecurityManager(new RMISecurityManager());
	    String dir = args[0];
	    File dirFile = new File(dir);
	    if (!dirFile.exists()) {
		return null; // the OK value
	    }
	    if (!dirFile.isDirectory()) {
		return Util.getString("notadir", bundle, dir);
	    }
	    File[] contents = dirFile.listFiles();
	    if (contents == null) { // should never happen
		return Util.getString("emptylist", bundle, dir);
	    }
	    if (contents.length > 0) {
		return Util.getString("dirnotempty", bundle, dir);
	    }
	    return null; // directory exists but is empty
	}
    }

    /**
     * The subtask which obtains the list of persistence directory entries. The
     * arg list is cleaned up, the configuration in obtained, and a String array
     * of pairs of all entries named  <code>persistenceDirectory</code> and
     * their associated value is returned.
     */
    public static class GetEntriesTask implements SubVMTask {

	private ResourceBundle bundle = 
	    Util.getResourceBundle(CheckPersistence.class);

	public Object run(String[] args) {
	    try {
		Configuration config = 
		    ConfigurationProvider.getInstance(args);
		return getEntries(config);
	    } catch (ConfigurationException e) {
		return Util.getString("configproblem", bundle, e.getMessage());
	    } catch (Exception e) {
		return e;
	    }
	}

	/**
	 * Obtain all of the <code>persistenceDirectory</code> entries in the
	 * configuration and return them as pairs in a <code>String</code>
	 * array.
	 *
	 * @param conf the configuration to examine
	 * @return the array of entry/value pairs
	 */
	private Object getEntries(Configuration conf) {
	    ConfigurationFile cf = (ConfigurationFile) conf;
	    ArrayList list = new ArrayList();
	    Set names = cf.getEntryNames();
	    Iterator it = names.iterator();
	    String s = "";
	    while (it.hasNext()) {
		String name = (String) it.next();
		s += name + "\n";
		int lastDot = name.lastIndexOf(".persistenceDirectory");
		if (lastDot > 0) {
		    String component = name.substring(0, lastDot);
		    try {
			String dir =
			    (String) conf.getEntry(component, 
						   "persistenceDirectory",
						   String.class,
						   null);
			list.add(name);
			list.add(dir);
		    } catch (ConfigurationException e) {
			return e;
		    }
		}
	    }
	    return list.toArray(new String[list.size()]);
	}
    }
}
