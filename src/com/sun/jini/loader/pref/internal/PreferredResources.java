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

package com.sun.jini.loader.pref.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Internal data structure which holds preference information for a
 * preferred class loader.  This utility is used only by the preferred
 * class loader provider and is not intended to be a public API.
 *
 * A preferred resources object is created from an input stream which
 * is formatted according to the Preferred List Syntax which is
 * defined in the specification for
 * <code>net.jini.loader.pref.PreferredClassProvider</code>
 *
 * Preferred resources instances hold preferred list expression data
 * and the preferred state for the resources contained in a given
 * preferred class loader.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class PreferredResources {
    /**
     * Constant value that indicates that there is no preference value
     * for a given name.
     */
    public static final int NAME_NO_PREFERENCE = 0;

    /**
     * Constant value that indicates that the resource for a given
     * name is known to be not preferred.  This state is applicable
     * when a preference expression declared that the name is not
     * preferred or when the resource for the name (preferred or not)
     * is known to not exist.
     */
    public static final int NAME_NOT_PREFERRED = 1;

    /**
     * Constant value that indicates that a given name is preferred.
     * This state is applicable when a preference expression declares
     * the name to be preferred but it is uncertain whether the
     * resource for the name the exists.
     */
    public static final int NAME_PREFERRED = 2;

    /**
     * Constant value that indicates that a given resource is
     * preferred.  This state is applicable when a preference
     * expression declares the name to be preferred and the resource
     * for the name is known to exist.
     */
    public static final int NAME_PREFERRED_RESOURCE_EXISTS = 3;
    
    /** string that starts preference specification headers */
    private static final String HEADER_TITLE = "PreferredResources-Version: ";
    private static final String HEADER_MAJOR_VERSION = "1";
    private static final String HEADER_MINOR_VERSION = "0";
    
    /** preference syntax keywords */
    private static final String PREF_PREFIX = "Preferred:";
    private static final String NAME_PREFIX = "Name:";

    /* tables which contain relevant components of preferred names */
    private Map namespacePrefs;
    private Map packagePrefs;
    private Map completeNamePrefs;

    /** flag to signal if this preference object is empty */
    private boolean isEmpty = true;

    /** default preference setting */
    private Boolean defaultPreference = Boolean.FALSE;

    /**
     * Create a preference object from a stream of formatted
     * preference syntax.
     *
     * @see PreferredResources
     */
    public PreferredResources(InputStream in) throws IOException {
	read(in);
    }

    /**
     * Read and parse preference information from the parameter input
     * stream <code>in</code>.  When the method completes, the
     * preference expression maps contain preference settings for
     * preference names contained in the input stream.
     */
    private void read(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader
	    (new InputStreamReader(in, "UTF8"));	
        String line = null;
	String name = null;
	Boolean preference = null;

	/* clear old contents */
	isEmpty = true;
	completeNamePrefs = new HashMap(53);
	packagePrefs = new HashMap(23);
	namespacePrefs = new HashMap(11);
	
	if ((line = readLineTrimComments(br)) != null) {
	    if (!line.startsWith(HEADER_TITLE)) {
		throw new IOException("unsupported preferred list header: " +
				      line);
	    } else {
		String version =
		    line.substring(HEADER_TITLE.length(),
				   line.length()).trim();
		if (!version.startsWith("1.")) {
		    throw new IOException("preferred list major version " +
					  "not supported");
		}
	    }
	    for (line = readLineTrimComments(br); line != null;
		 line = readLineTrimComments(br))
	    {
		if (line.length() == 0) {
		    continue;
		} else if (line.startsWith(NAME_PREFIX)) {
		    if (name != null) {
			throw new IOException("Preferred name without " +
					      "preference value");
		    }
		    name = line.substring(NAME_PREFIX.length()).trim();
		    continue;
		    
		} else if (line.startsWith(PREF_PREFIX)) {
		    String value = line.substring(PREF_PREFIX.length()).trim();
		    if (value.equals("")) {
			throw new IOException("Empty preference value not " +
					      "permitted");
		    }
		    preference = Boolean.valueOf(value);
		} else {
		    throw new IOException("unrecognized preference entry: " +
					  line);
		}
		if (name == null) {
		    if (preference != null) {
			if (!isEmpty) {
			    throw new IOException("default preference must " +
				"be the first expression and can not " +
				"be redefined");
			}
			defaultPreference = preference;
			preference = null;
			isEmpty = false;
		    }
		    
		    /* it is ok for both name and preference to be null */
		    
		} else if (preference != null) {
		    if (name.startsWith("/") ||
			name.startsWith("*") ||
			name.startsWith("-") ||			
			name.startsWith("."))
		    {
			throw new IOException("Invalid character " +
					      "at name beginning: " + name);
		    } else if (name.endsWith("/*")) {
			mapPut(packagePrefs,
			    name.substring(0, name.length() - 2), preference);
		    } else if (name.endsWith("/-")) {
			mapPut(namespacePrefs,
			    name.substring(0, name.length() - 2), preference);
		    } else if (name.endsWith("/")) {
			mapPut(packagePrefs,
			    name.substring(0, name.length() - 1), preference);
		    } else {
			/* no wildcard; must be a full resource name */
			int state = (preference.booleanValue() ?
				     NAME_PREFERRED : NAME_NOT_PREFERRED);
			mapPut(completeNamePrefs, name, new Integer(state));
		    }
		    preference = null;
		    name = null;
		}
	    }
	    if (name != null) {
		throw new IOException("Preferred name without " +
				      "preference value");
	    }
	}
	if (isEmpty) {
	    throw new IOException("Empty preferences list is invalid");
	}
    }

    /**
     * Reads the next line from the specified BufferedReader, removing
     * leading and trailing whitespace and comments.  Null is returned
     * on EOF.
     **/
    private String readLineTrimComments(BufferedReader br) throws IOException {
	String line = br.readLine();
	if (line != null) {
	    line = line.trim();
	    if (line.indexOf('#') == 0) {
		line = "";
	    }
	}
	return line;
    }

    /**
     * Insert a preference expression and value into a given map.
     */
    private void mapPut(Map map, String name, Object preference)
	throws IOException
    {
	isEmpty = false;
	
	if ((name == null) || (name.length() == 0)) {
	    throw new IOException("no name specified in preference" +
				  " expression");
	}
	if (map.put(name, preference) != null) {
	    throw new IOException("duplicate map entry: " + name);
	}
    }

    /**
     * Write the preferences to the specified OutputStream using the
     * preference list syntax. Preference expressions are written in
     * the following order:
     *
     * Complete name expressions
     *
     * Package expressions
     * 
     * Namespace expressions
     *
     * @param out the stream to which formatted preference information
     * will be written
     * @throws IOException if an error occurs while writing to the stream
     */
    public void write(OutputStream out) throws IOException {
        BufferedWriter bw = new BufferedWriter
            (new OutputStreamWriter(out, "UTF8"));

	/* write the specification header */
        bw.write(HEADER_TITLE + "1.0\n");
	bw.write(PREF_PREFIX + " " + defaultPreference + "\n\n");

	/* write out most specific preferences first */
	writeMap(completeNamePrefs, bw, "");
	writeMap(packagePrefs, bw, "/*");
	writeMap(namespacePrefs, bw, "/-");

	bw.flush();
    }

    /**
     * Write the contents of the map into <code>out</code> using the
     * preference syntax.
     */
    private void writeMap(Map prefs, Writer out, String suffix)
	throws IOException
    {
	Iterator i = (new TreeSet(prefs.keySet())).iterator();
	while (i.hasNext()) {
	    Object current = i.next();
	    out.write(NAME_PREFIX + " " + current +
		      suffix + "\n");
	    Object value = prefs.get(current);
	    if (value instanceof Boolean) {
		out.write(PREF_PREFIX + " " + value + "\n\n");
	    } else if (value instanceof Integer) {
		int state = ((Integer) value).intValue();
		if ((state == NAME_PREFERRED) ||
		    (state == NAME_PREFERRED_RESOURCE_EXISTS))
		{
		    out.write(PREF_PREFIX + " " + true + "\n\n");
		} else {
		    out.write(PREF_PREFIX + " " + false + "\n\n");   
		}
	    }
	}
    }

    /**
     * Returns the preference setting that will be applied to names
     * which have no explicit preference setting in contained
     * preference settings.  The default preference is set by the
     * first preference setting with no associated name in a
     * preferences list file.
     *
     * @return default boolean preference value for these preferences
     */
    public Boolean getDefaultPreference() {
	return defaultPreference;
    }
    
    /**
     * Enable MarshalInputStream to optimize preference information:
     * permits complete name expressions to be added for names that
     * only match wild-card expressions.  These added expressions hold
     * data that tells if a resource has been loaded.
     *
     * This method makes this object mutable.  Synchronization must be
     * used to ensure consistent preference state when this method is
     * called.
     *
     * @param name the name for which preferred state will be set
     * @param prefState the preferred state for the given name
     * @throws IOException if the name length is zero length
     */
    public void setNameState(String name, int prefState)
	throws IOException
    {
	isEmpty = false;
	
	if (name.length() == 0) {
	    throw new IOException("no name specified in preference" +
				  " expression");
	}

 	completeNamePrefs.put(name, new Integer(prefState));
    }

    /**
     * Searches the preference maps to determine the preference state
     * of the named resource.  The preference state for the given
     * resource name is returned.  The preference state is an integer
     * that is equal to one of the preference state values defined
     * above.  This state integer tells the preference value of name
     * and whether its resource is known to exist.
     *
     * @param name
     * @param isClass whether the given <code>name</code> refers to a
     *        class resource
     *
     * @return the state for the given name which will be set to one
     *         of the following values: NAME_NO_PREFERENCE,
     *         NAME_NOT_PREFERRED, NAME_PREFERRED,
     *         NAME_PREFERRED_RESOURCE_EXISTS
     * 
     * @throws IOException if an error occurs getting the state for
     *         the supplied name
     */
    public int getNameState(String name, boolean isClass)
	throws IOException
    {
	Integer state = null;

	if (isClass) {
	    state = getClassNameState(name);
	} else {
	    state = (Integer) completeNamePrefs.get(name);
	}
	if (state != null) {
	    return state.intValue();
	}
	return NAME_NO_PREFERENCE;
    }

    /**
     * Returns the preference state for a given name (as is done in
     * getNonclassNameState) but also interprets the notation for
     * inner classes as a wild card so that the preference value for a
     * container class propagates to the classes it contains.  More
     * specific inner classes names override more general container
     * preference.
     */
    private Integer getClassNameState(String name) throws IOException {
	if (!name.endsWith(".class")) {
	    throw new IOException("requested name state on a " +
				  "non-class resource: " + name);
	}
	
	Integer state = null;	
	String container = name;
	int lastDollar = -1;
	do {
	    state = (Integer) completeNamePrefs.get(container);
	    if (state == null) {
		lastDollar = container.lastIndexOf("$");
		if (lastDollar >= 0) {
		    container =
			container.substring(0, lastDollar) + ".class";
		}
	    }
	} while ((lastDollar >= 0) && (state == null));

	return state;
    }
    
    /**
     * Return the boolean value of the most specific wild card
     * (package and namespace) expression which matches
     * <code>name</code>.  Package preferences are always more
     * specific than namespace preferences.
     *
     * @param name the resource name to which the returned boolean
     * value will apply
     *
     * @return <code>Boolean.TRUE/code> if <code>name</code> is
     * preferred. <code>Boolean.FALSE</code> is it is
     * not. <code>null</code> if there is no wildcard preference for
     * the name.
     */
    public Boolean getWildcardPreference(String name) {
	Boolean wildcardPref = null;
	
	int lastSlash = name.lastIndexOf("/");
	if (lastSlash >= 0) {
	    String mostSpecific = name.substring(0, lastSlash);
	    if (!mostSpecific.equals("")) {
		if (!packagePrefs.isEmpty()) {
		    wildcardPref = (Boolean)
			packagePrefs.get(mostSpecific);
		}
		if (wildcardPref == null) {
		    wildcardPref = getNamespacePreference(mostSpecific);
		}
	    }
	}

	return wildcardPref;
    }

    /**
     * Return a Boolean for the most specific namespace expression
     * which matches <code>name</code>. null if the name does not
     * match a namespace preference expression.
     */
    private Boolean getNamespacePreference(String namespace) {
	Boolean namespacePref = null;
	
	if (!namespacePrefs.isEmpty()) {
	    int lastSlash;
	    do {
		namespacePref =
		    (Boolean) namespacePrefs.get(namespace);
		
		lastSlash = namespace.lastIndexOf("/");
		if (lastSlash >= 0) {
		    namespace = namespace.substring(0, lastSlash);
		}

	    } while ((lastSlash >= 0) &&
		     (!namespace.equals("")) &&
		     namespacePref == null);
	}
	return namespacePref;
    }
}
