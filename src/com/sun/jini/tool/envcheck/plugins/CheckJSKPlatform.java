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

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.StringTokenizer;

import com.sun.jini.start.SharedActivationGroupDescriptor;

/**
 * Check whether <code>jsk-platform.jar</code> is in the classpath
 * of the command-line being analyzed, and if the activation group
 * if there is one.
 */
public class CheckJSKPlatform extends AbstractPlugin {

    /** the plugin container */
    private EnvCheck envCheck;

    /** classpath components already seen, to avoid circular reference loops */
    private HashSet seen = new HashSet();

    /**
     * Check the command line vm and the group, if there is one,
     * for a classpath containing <code>jsk-platform.jar</code>.
     *
     * @param envCheck the plugin container
     */
    public void run(EnvCheck envCheck) {
        this.envCheck = envCheck;
	String classpath = envCheck.getJarToRun();
	if (classpath != null) {
	    checkPlatform(classpath, getString("jarfile"));
	} else {
	    classpath = envCheck.getClasspath();
	    checkPlatform(classpath, getString("cmdline"));
	}
	SharedActivationGroupDescriptor gd = envCheck.getGroupDescriptor();
	if (gd != null) {
	    classpath = gd.getClasspath();
	    checkPlatform(classpath, getString("grouppath"));
	}
    }

    /**
     * Check <code>classpath</code> for the existence of
     * <code>jsk-platform.jar</code>.
     * 
     * @param classpath the classpath to check
     * @param source the source description
     */
    private void checkPlatform(String classpath, String source) {
	Message message;
	String[] paths = parseClasspath(classpath, source);
	for (int i = 0; i < paths.length; i++) {
	    if (paths[i].endsWith("jsk-platform.jar")) {
		message = new Message(Reporter.INFO,
				      getString("hasplatform"),
				      getString("platformExp"));
		Reporter.print(message, source);
		return;
	    }
	}
	message = new Message(Reporter.WARNING,
			      getString("noplatform"),
			      getString("platformExp"));
	Reporter.print(message, source);
    }

    /**
     * Separate each of the components making up the classpath into
     * separate tokens. For tokens which resolve to jar files, recursively
     * include their Class-Path manifest entries if defined. Verify
     * each component for existence and accessibility.
     */
    private String[] parseClasspath(String path, String source) {
	if (path == null || path.trim().length() == 0) {
	    return new String[0];
	}
	ArrayList list = new ArrayList();
	StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);
	while (tok.hasMoreTokens()) {
	    String item = tok.nextToken();
	    list.addAll(checkItem(item, source));
	}
	return (String[]) list.toArray(new String[list.size()]);
    }

    /**
     * Checks a component on the classpath for existence and accessibility.
     * If the item meets these criteria, it is placed in a list which
     * is returned to the caller. If the item is a JAR file whose manifest
     * which contains a <code>Class-Path</code> manifest entry, then each
     * of those items are checked and conditionally added to the list
     * as well (recursively). If an <code>item</code> has been seen
     * previously, and empty list is returned immediately.
     *
     * @param item the classpath component to verify 
     * @param source the source descriptive text
     * @return the list containing this item and all items referred to by
     *         this item.
     */
    private ArrayList checkItem(String item, String source) {
	Message message;
	ArrayList list = new ArrayList();
	if (seen.contains(item)) {
	    return list;
	}
	seen.add(item);
	File itemFile = new File(item);
	if (!itemFile.exists()) {
	    message = new Message(Reporter.WARNING,
				  getString("nosuchitem", item),
				  null);
	    Reporter.print(message, source);
	    return list;
	}
	if (!itemFile.canRead()) {
	    message = new Message(Reporter.WARNING,
				  getString("unreadableitem", item),
				  null);
	    Reporter.print(message, source);
	    return list;
	}
	list.add(item);
	JarFile jar;
	try {
	    jar = new JarFile(item);
	} catch (IOException e) {  // probably not a zip/jar file
	    return list;
	}
	Manifest manifest;
	try {
	    manifest = jar.getManifest();
	    if (manifest == null) {
		return list;
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	    return list;
	}
	String classPath = manifest.getMainAttributes().getValue("Class-Path");
	if (classPath != null) {
	    StringTokenizer tok = new StringTokenizer(classPath);
	    while (tok.hasMoreTokens()) {
		String fileName = tok.nextToken();
		File nextJar = new File(itemFile.getParentFile(), fileName);
		source = source + ": " + item + " Manifest Class-Path";
		list.addAll(checkItem(nextJar.toString(), source));
	    }
	}
        return list;
    }
}
