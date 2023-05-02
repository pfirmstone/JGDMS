/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Alexey V. Varlamov
* @author Peter Firmstone
* @since 3.0.0
*/

package org.apache.river.api.security;

import org.apache.river.impl.Messages;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.apache.river.api.net.Uri;

/**
 * This class consist of a number of static methods, which provide a common functionality 
 * for various policy and configuration providers. 
 * 
 */
 class PolicyUtils {

    // No reason to instantiate
    private PolicyUtils() {}
    
    /**
     * Auxiliary action for opening InputStream from specified location.
     */
    static class URLLoader implements PrivilegedExceptionAction<InputStream> {

        /** 
         * URL of target location. 
         */
        private final URL location;

        /**
         *  Constructor with target URL parameter. 
         */
        URLLoader(URL location) {
            this.location = location;
        }

        /** 
         * Returns InputStream from the target URL.
         */
        public InputStream run() throws IOException {
            return location.openStream();
        }
    }

    /** 
     * Auxiliary action for accessing specific security property. 
     */
    static class SecurityPropertyAccessor implements PrivilegedAction<String> {

        private String key;
        
        /** 
         * Constructor with a property key parameter. 
         */
        SecurityPropertyAccessor(String key) {
            super();
            this.key = key;
        }

        PrivilegedAction<String> key(String key) {
            this.key = key;
            return this;
        }
        
        /** 
         * Returns specified security property. 
         */
        public String run() {
            return Security.getProperty(key);
        }
    }

    /** 
     * Specific exception to signal that property expansion failed 
     * due to unknown key. 
     */
    static class ExpansionFailedException extends Exception {

        /**
         * @serial
         */
        private static final long serialVersionUID = 2869748055182612000L;

        /** 
         * Constructor with user-friendly message parameter. 
         */
        ExpansionFailedException(String message) {
            super(message);
        }

        /** 
         * Constructor with user-friendly message and causing error. 
         */
        ExpansionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Substitutes all entries like ${some.key}, found in specified string, 
     * for specified values.
     * If some key is unknown, throws ExpansionFailedException. 
     * @param str the string to be expanded
     * @param properties available key-value mappings 
     * @return expanded string
     * @throws ExpansionFailedException
     */
    public static String expand(String str, Properties properties)
            throws ExpansionFailedException {
        final String START_MARK = "${"; //$NON-NLS-1$
        final String END_MARK = "}"; //$NON-NLS-1$
        final int START_OFFSET = START_MARK.length();
        final int END_OFFSET = END_MARK.length();

        StringBuilder result = new StringBuilder(str);
        int start = result.indexOf(START_MARK);
        while (start >= 0) {
            int end = result.indexOf(END_MARK, start);
            if (end >= 0) {
                String key = result.substring(start + START_OFFSET, end);
                String value = properties.getProperty(key);
                if (value != null) {
                    result.replace(start, end + END_OFFSET, value);
                    start += value.length();
                } else {
                    throw new ExpansionFailedException(Messages.getString("security.14F", key)); //$NON-NLS-1$
                }
            }
            start = result.indexOf(START_MARK, start);
        }
        return result.toString();
    }
    
//  Segment has a bug where if the closing bracket is missing it gets caught 
//  endlessly creating new Segments and calling them.
//    public static String expand(String str, Properties properties) throws ExpansionFailedException{
//        Segment s = new Segment(str, null);
//        for (int i = 0; i < 3; i++){ //nested properies 3 deep.
//            s.divideAndReplace("${", "}", null, properties);
//        }
//        s.hasNext(); // There will be at least one result
//        return s.next(); // Don't bother checking for more, not split into array.
//    }

    /**
     * Handy shortcut for 
     * <code>expand(str, properties).replace(File.separatorChar, '/')</code>.
     * @see #expand(String, Properties)
     */
    static String expandURL(String str, Properties properties)
            throws ExpansionFailedException {
        return expand(str, properties).replace(File.separatorChar, '/');
    }
    
    /**
     * Normalizes URLs to standard ones, eliminating pathname symbols.
     * 
     * @param codebase -
     *            the original URL.
     * @return - the normalized URL.
     * @throws URISyntaxException  
     */
    static URI normalizeURL(URL codebase) throws URISyntaxException {
        if (codebase == null) return null;
        if ("file".equals(codebase.getProtocol())) { //$NON-NLS-1$
            try {
                if (codebase.getHost().length() == 0) {
                    String path = codebase.getFile();

                    if (path.length() == 0) {
                        // codebase is "file:"
                        path = "*";
                    }
                    return Uri.uriToURI(Uri.fileToUri(new File(path)));
                } else {
                    // codebase is "file://<smth>"
                    return Uri.uriToURI(Uri.urlToUri(codebase));
                }
            } catch (Exception e) {
                if ( e instanceof SecurityException ) throw (SecurityException) e;
                // Ignore
            }
        }
        return Uri.uriToURI(Uri.urlToUri(codebase));
    }

    /**
     * Converts a file path to URI without accessing file system
     * (like {File#toURI()} does).
     * 
     * @param path -
     *            file path.
     * @return - the resulting URI.
     * @throws URISyntaxException
     */
    static URI filePathToURI(String path) throws URISyntaxException {
        if (File.separatorChar == '\\' && path != null){
            path = path.replace(File.separatorChar, '/');
            path = path.toUpperCase();
        }
        if ( path != null && !path.startsWith("/")) { //$NON-NLS-1$
            return new URI("file", null, //$NON-NLS-1$
                    new StringBuilder(path.length() + 1).append('/')
                            .append(path).toString(), null, null);
        }
        return new URI("file", null, path, null, null); //$NON-NLS-1$
    }

    /**
     * Instances of this interface are intended for resolving  
     * generalized expansion expressions, of the form ${{protocol:data}}. 
     * Such functionality is applicable to security policy files, for example.
     * @see org.apache.river.api.security.PolicyUtils#expandGeneral(String, GeneralExpansionHandler)
     */
    static interface GeneralExpansionHandler {

        /**
         * Resolves general expansion expressions of the form ${{protocol:data}}.
         * @param protocol denotes type of resolution
         * @param data data to be resolved, optional (may be null)
         * @return resolved value, must not be null
         * @throws PolicyUtils.ExpansionFailedException if expansion is impossible
         */
        String resolve(String protocol, String data)
                throws ExpansionFailedException;
    }

    /**
     * Substitutes all entries like ${{protocol:data}}, found in specified string, 
     * for values resolved by passed handler.
     * The data part may be empty, and in this case expression 
     * may have simplified form, as ${{protocol}}.
     * If some entry cannot be resolved, throws ExpansionFailedException;
     * @param str the string to be expanded
     * @param handler the handler to resolve data denoted by protocol  
     * @return expanded string
     * @throws ExpansionFailedException
     */
    static String expandGeneral(String str,
            GeneralExpansionHandler handler) throws ExpansionFailedException {
        final String START_MARK = "${{"; //$NON-NLS-1$
        final String END_MARK = "}}"; //$NON-NLS-1$
        final int START_OFFSET = START_MARK.length();
        final int END_OFFSET = END_MARK.length();

        StringBuilder result = new StringBuilder(str);
        int start = result.indexOf(START_MARK);
        while (start >= 0) {
            int end = result.indexOf(END_MARK, start);
            if (end >= 0) {
                String key = result.substring(start + START_OFFSET, end);
                int separator = key.indexOf(':');
                String protocol = (separator >= 0) ? key
                        .substring(0, separator) : key;
                String data = (separator >= 0) ? key.substring(separator + 1)
                        : null;
                String value = handler.resolve(protocol, data);
                result.replace(start, end + END_OFFSET, value);
                start += value.length();
            }
            start = result.indexOf(START_MARK, start);
        }
        return result.toString();
    }

    /** 
     * A key to security properties, deciding whether usage of 
     * dynamic policy location via system properties is allowed. 
     * @see #getPolicyURLs(Properties, String, String)
     */
    static final String POLICY_ALLOW_DYNAMIC = "policy.allowSystemProperty"; //$NON-NLS-1$

    /** 
     * A key to security properties, deciding whether expansion of 
     * system properties is allowed 
     * (in security properties values, policy files, etc).
     * @see #expand(String, Properties) 
     */
    static final String POLICY_EXPAND = "policy.expandProperties"; //$NON-NLS-1$

    /** 
     * Positive value of switching properties.
     */
    static final String TRUE = "true"; //$NON-NLS-1$

    /** 
     * Negative value of switching properties.
     */
    static final String FALSE = "false"; //$NON-NLS-1$

    /** 
     * Returns false if current security settings disable to perform 
     * properties expansion, true otherwise.
     * @see #expand(String, Properties)  
     */
    static boolean canExpandProperties() {
        return !Util.equalsIgnoreCase(FALSE,AccessController
                .doPrivileged(new SecurityPropertyAccessor(POLICY_EXPAND)));
    }

    /**
     * Obtains a list of locations for a policy or configuration provider.
     * The search algorithm is as follows:
     * <ol>
     * <li> Look in security properties for keys of form <code>prefix + n</code>, 
     * where <i>n</i> is an integer and <i>prefix</i> is a passed parameter. 
     * Sequence starts with <code>n=1</code>, and keeps incrementing <i>n</i> 
     * until next key is not found. <br> 
     * For each obtained key, try to construct an URL instance. On success, 
     * add the URL to the list; otherwise ignore it.
     * <li>
     *         If security settings do not prohibit (through 
     *         {@link #POLICY_ALLOW_DYNAMIC the &quot;policy.allowSystemProperty&quot; property}) 
     *         to use additional policy location, read the system property under the 
     *         passed key parameter. If property exists, it may designate a file or 
     *         an absolute URL. Thus, first check if there is a file with that name, 
     *         and if so, convert the pathname to URL. Otherwise, try to instantiate   
     *         an URL directly. If succeeded, append the URL to the list 
     * <li>
     *         If the additional location from the step above was specified to the 
     *         system via &quot;==&quot; (i.e. starts with '='), discard all URLs above 
     *         and use this only URL.
     * </ol> 
     * <b>Note:</b> all property values (both security and system) related to URLs are
     * subject to {@link #expand(String, Properties) property expansion}, regardless 
     * of the &quot;policy.expandProperties&quot; security setting.  
     * 
     * @param system system properties
     * @param systemUrlKey key to additional policy location
     * @param securityUrlPrefix prefix to numbered locations in security properties
     * @return array of URLs to provider's configuration files, may be empty.
     */
    static URL[] getPolicyURLs(final Properties system,
            final String systemUrlKey, final String securityUrlPrefix) {

        final List<URL> urls = new ArrayList<URL>();
        boolean dynamicOnly = false;
        URL dynamicURL = null;

        //first check if policy is set via system properties
        if (!Util.equalsIgnoreCase(FALSE, Security.getProperty(POLICY_ALLOW_DYNAMIC))) {
            String location = system.getProperty(systemUrlKey);
            if (location != null) {
                if (location.startsWith("=")) { //$NON-NLS-1$
                    //overrides all other urls
                    dynamicOnly = true;
                    location = location.substring(1);
                }
                try {
                    location = expandURL(location, system);
                    // location can be a file, but we need an url...
                    final File f = new File(location);
                    if (f.exists()) {
                        dynamicURL = f.toURI().toURL();
                    } 
                    if (dynamicURL == null) {
                        dynamicURL = new URL(location);
                    }
                } catch (Exception e) {
                    if ( e instanceof SecurityException ) throw (SecurityException) e;
                    // TODO: log error
                    System.err.println("Error detecting system policy location: "+e);
                }
            }
        }
        //next read urls from security.properties 
        if (!dynamicOnly) {
            int i = 1;
            while (true) { 
                String location = Security.getProperty(new StringBuilder(
                                securityUrlPrefix).append(i++).toString());
                if (location == null) {
                    break;
                }
                try {
                    location = expandURL(location, system);
                    URL anURL = new URL(location);
                    if (anURL != null) {
                        urls.add(anURL);
                    }
                }
                catch (Exception e) {
                    if ( e instanceof SecurityException ) throw (SecurityException) e;
                    // TODO: log error
                    System.err.println("Error detecting security policy location: "+e);
                }
            }
        }
        if (dynamicURL != null) {
            urls.add(dynamicURL);
        }
        return urls.toArray(new URL[urls.size()]);
    }

    // Empty set of arguments to default constructor of a Permission.
    private static final Class[] NO_ARGS = {};

    // One-arg set of arguments to default constructor of a Permission.
    private static final Class[] ONE_ARGS = { String.class };

    // Two-args set of arguments to default constructor of a Permission.
    private static final Class[] TWO_ARGS = { String.class, String.class };

    /**
     * Tries to find a suitable constructor and instantiate a new Permission
     * with specified parameters.  
     *
     * @param targetType class of expected Permission instance
     * @param targetName name of expected Permission instance
     * @param targetActions actions of expected Permission instance
     * @return a new Permission instance
     * @throws IllegalArgumentException if no suitable constructor found
     * @throws InstantiationException any exception thrown by Constructor.newInstance()
     */
    static Permission instantiatePermission(Class<?> targetType,
            String targetName, String targetActions)
            throws InstantiationException, IllegalAccessException, 
            IllegalArgumentException, InvocationTargetException 
    {

        // let's guess the best order for trying constructors
        Class[][] argTypes;
        Object[][] args;
        if (targetActions != null) {
            argTypes = new Class[][] { TWO_ARGS, ONE_ARGS, NO_ARGS };
            args = new Object[][] { { targetName, targetActions },
                    { targetName }, {} };
        } else if (targetName != null) {
            argTypes = new Class[][] { ONE_ARGS, TWO_ARGS, NO_ARGS };
            args = new Object[][] { { targetName },
                    { targetName, /*targetActions*/ null }, {} };
        } else {
            argTypes = new Class[][] { NO_ARGS, ONE_ARGS, TWO_ARGS };
            args = new Object[][] { {}, { /*targetName*/ null },
                    { /*targetName*/ null, /*targetActions*/ null } };
        }

        // finally try to instantiate actual permission
        for (int i = 0; i < argTypes.length; i++) {
            try {
                Constructor<?> ctor = targetType.getConstructor(argTypes[i]);
                return (Permission)ctor.newInstance(args[i]);
            }
            catch (NoSuchMethodException ignore) {}
        }
        throw new IllegalArgumentException(
                Messages.getString("security.150", targetType));//$NON-NLS-1$
    }

    /**
     * Checks whether the objects from <code>what</code> array are all
     * presented in <code>where</code> array.
     * 
     * @param what first array, may be <code>null</code> 
     * @param where  second array, may be <code>null</code>
     * @return <code>true</code> if the first array is <code>null</code>
     * or if each and every object (ignoring null values) 
     * from the first array has a twin in the second array; <code>false</code> otherwise
     */
    public static boolean matchSubset(Object[] what, Object[] where) {
        if (what == null) {
            return true;
        }

        for (int i = 0; i < what.length; i++) {
            if (what[i] != null) {
                if (where == null) {
                    return false;
                }
                boolean found = false;
                for (int j = 0; j < where.length; j++) {
                    if (what[i].equals(where[j])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }
}
