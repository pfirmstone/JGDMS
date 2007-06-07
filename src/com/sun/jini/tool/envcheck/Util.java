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
package com.sun.jini.tool.envcheck;

import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivatableServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.net.URL;
import java.net.URLClassLoader;

import java.lang.reflect.Modifier;

import java.text.MessageFormat;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Miscellaneous utility methods for use by the checking framework
 * and plugins.
 */
public class Util {

    /** table of localization resources */
    private static final HashMap resourceMap = new HashMap();

    /** the resource bundle for this class */
    private static ResourceBundle bundle = getResourceBundle(EnvCheck.class);

    /** the properties which were provided to the local VM */
    private static Properties nonStandardProperties = null;

    /**
     * Validate the accessibility of the non-directory file identified by
     * <code>name</code>. The file must exist, must not be a directory, and must
     * be readable.  If any check fails, an error message is returned. If all
     * checks are successful, <code>null</code> is returned.
     *
     * @param name the file name
     * @param desc a short descriptive string which describes the file, suitable
     *             for inclusion in the error message
     * @return and error message, or <code>null</code> if all checks pass
     */
    public static String checkFileName(String name, String desc) {
	File file = new File(name);
	String canonicalPath = null;
	try {
	    canonicalPath = file.getCanonicalPath();
	} catch (Exception e) {
	    return getString("util.cantresolve", bundle, desc, name);
	}
	if (!file.exists()) {
	    return getString("util.notexist", bundle, desc, canonicalPath);
	}
	if (file.isDirectory()) {
	    return getString("util.isdir", bundle, desc, canonicalPath);
	}
	if (!file.canRead()) {
	    return getString("util.cantread", bundle, desc, canonicalPath);
	}
	return null;
    }

    /**
     * Check the accessibility of the given <code>URL</code>. If the
     * url is a <code>file:</code> url, the usual file access checks are
     * performed. For any other url a stream is opened, and a non-exceptional
     * return is considered a success.
     *
     * @param url the <code>URL</code> to check
     * @param desc a description of the source of the url
     * @return an error message, or <code>null</code> if the access check passes
     */
    public static String checkURL(URL url, String desc) {
        if ("file".equals(url.getProtocol())) {
            String path = url.getFile().replace('/', File.separatorChar);
            return checkFileName(path, desc);
        } else {
	    try {
		url.openStream().close();
		return null;
	    } catch (IOException e) {
		return getString("util.cantreadURL", 
				 bundle, 
				 url.toString(),
				 desc);
	    }
        }
    }

    /**
     * Validate the accessibility of the non-directory file identified by the
     * system property <code>prop</code>. The system property must have a
     * non-<code>null</code> value. The file identified by the value must exist,
     * must not be a directory, and must be readable.  If any check fails, an
     * error message is returned. If all checks are successful,
     * <code>null</code> is returned.
     *
     * @param prop name of a system property whose value must be a file name
     * @param desc a short descriptive string which describes the file, suitable
     *             for inclusion in the error message
     * @return an error message, or <code>null</code> if all checks pass
     */
    public static String checkSystemPropertyFile(String prop, String desc) {
	String name = System.getProperty(prop);
	if (name == null) {
	    return getString("util.undef", bundle, desc);
	}
	return checkFileName(name, desc);
    }
		   
    /**
     * Get the resource bundle associated with class <code>clazz</code>.  The
     * resource bundle name is constructed by the class name to lower case and
     * inserting <code>.resources</code> in front of the name. Thus, if
     * <code>clazz.getName()</code> returned <code>a.b.Foo</code> then the
     * associated resource bundle name would be <code>a.b.resources.foo</code>.
     * If no resource bundle having the associated name is found, a stack trace
     * is printed and <code>null</code> is returned. The resource bundle is
     * loaded using the class loader for the given class, and is cached so that
     * the search is performed only once.
     * 
     * @param clazz the class for which to obtain a resource bundle
     * @return the resource bundle
     */
    public static ResourceBundle getResourceBundle(Class clazz) {
	if (resourceMap.containsKey(clazz)) {
	    return (ResourceBundle) resourceMap.get(clazz);
	}
	String className = clazz.getName();
	int lastDot = className.lastIndexOf(".");
	if (lastDot < 0) {
	    throw new IllegalStateException("Class is in default package");
	}
	String pkgName = className.substring(0, lastDot);
	// includes leading '.'
	String resourceName = className.substring(lastDot).toLowerCase();
	String bundleName = pkgName + ".resources" + resourceName;
	ResourceBundle bundle = null;
	try {
	    bundle = ResourceBundle.getBundle(bundleName, 
					      Locale.getDefault(),
					      clazz.getClassLoader());
	} catch (MissingResourceException e) {
	    e.printStackTrace(); //XXX should failures be silent?
	}
	resourceMap.put(clazz, bundle);
	return bundle;
    }

    /**
     * Get the format string associated with <code>key</code> in the
     * given resource bundle. If <code>key</code> is not present in
     * the bundle, a default format string is returned which will
     * identify the missing key when printed.
     *
     * @param key the key of the format string to retrieve
     * @param bundle the bundle to retrieve the format string from
     * @return the format string.
     */
    private static String getFormat(String key, ResourceBundle bundle) {
	String fmt = "no text found: \"" + key + "\" {0}";
	if (bundle != null) {
	    try {
		fmt = bundle.getString(key);
	    } catch (MissingResourceException e) {
	    }
	}
	return fmt;
    }

    /**
     * Print out string according to resourceBundle format.
     *
     * @param key the key of the format string to retrieve
     * @param bundle the bundle to retrieve the format string from
     */
    public static String getString(String key, ResourceBundle bundle) {
	return MessageFormat.format(getFormat(key, bundle), null);
    }

    /**
     * Print out string according to resourceBundle format.
     *
     * @param key the key of the format string to retrieve
     * @param bundle the bundle to retrieve the format string from
     * @param val the value to substitute into the {0} parameter
     */
    public static String getString(String key, ResourceBundle bundle, Object val)
    {
	return MessageFormat.format(getFormat(key, bundle), new Object[]{val});
    }

    /**
     * Print out string according to resourceBundle format.
     *
     * @param key the key of the format string to retrieve
     * @param bundle the bundle to retrieve the format string from
     * @param val1 the value to substitute into the {0} parameter
     * @param val2 the value to substitute into the {1} parameter
     */
    public static String getString(String key, 
				   ResourceBundle bundle, 
				   Object val1,
				   Object val2) 
    {
	return MessageFormat.format(getFormat(key, bundle),
				    new Object[]{val1, val2});
    }

    /**
     * Print out string according to resourceBundle format.
     *
     * @param key the key of the format string to retrieve
     * @param bundle the bundle to retrieve the format string from
     * @param val1 the value to substitute into the {0} parameter
     * @param val2 the value to substitute into the {1} parameter
     * @param val3 the value to substitute into the {2} parameter
     */
    public static String getString(String key, 
				   ResourceBundle bundle, 
				   Object val1,
				   Object val2,
				   Object val3) 
    {
	return MessageFormat.format(getFormat(key, bundle),
				    new Object[]{val1, val2, val3});
    }
}
