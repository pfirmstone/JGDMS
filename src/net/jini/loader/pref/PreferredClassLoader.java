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

package net.jini.loader.pref;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLPermission;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.loader.ClassAnnotation;
import net.jini.loader.DownloadPermission;
import net.jini.loader.RemoteClassLoadingPermission;
import org.apache.river.api.net.RFC3986URLClassLoader;
import org.apache.river.api.net.Uri;
import org.apache.river.api.security.AdvisoryDynamicPermissions;
import org.apache.river.api.security.AdvisoryPermissionParser;

/**
 * A class loader that supports preferred classes.
 *
 * <p>A preferred class is a class that is to be loaded by a class
 * loader without the loader delegating to its parent class loader
 * first.  Resources may also be preferred.
 *
 * <p>Like {@link java.net.URLClassLoader},
 * <code>PreferredClassLoader</code> loads classes and resources from
 * a search path of URLs.  If a URL in the path ends with a
 * <code>'/'</code>, it is assumed to refer to a directory; otherwise,
 * the URL is assumed to refer to a JAR file.
 *
 * <p>The location of the first URL in the path can contain a
 * <i>preferred list</i> for the entire path.  A preferred list
 * declares names of certain classes and other resources throughout
 * the path as being <i>preferred</i> or not.  When a
 * <code>PreferredClassLoader</code> is asked to load a class or
 * resource that is preferred (according to the preferred list) and
 * the class or resource exists in the loader's path of URLs, the
 * loader will not delegate first to its parent class loader as it
 * otherwise would do; instead, it will attempt to load the class or
 * resource from its own path of URLs only.
 *
 * <p>The preferred list for a path of URLs, if one exists, is located
 * relative to the first URL in the path.  If the first URL refers to
 * a JAR file, then the preferred list is the contents of the file
 * named <code>"META-INF/PREFERRED.LIST"</code> within that JAR file.
 * If the first URL refers to a directory, then the preferred list is
 * the contents of the file at the location
 * <code>"META-INF/PREFERRED.LIST"</code> relative to that directory
 * URL.  If there is no preferred list at the required location, then
 * no classes or resources are preferred for the path of URLs.  A
 * preferred list at any other location (such as relative to one of
 * the other URLs in the path) is ignored.
 *
 * <p>Note that a class or resource is only considered to be preferred
 * if the preferred list declares the name of the class or resource as
 * being preferred and the class or resource actually exists in the
 * path of URLs.
 *
 * <h3>Preferred List Syntax</h3>
 *
 * A preferred list is a UTF-8 encoded text file, with lines separated
 * by CR&nbsp;LF, LF, or CR (not followed by an LF).  Multiple
 * whitespace characters in a line are equivalent to a single
 * whitespace character, and whitespace characters at the beginning or
 * end of a line are ignored.  If the first non-whitespace character
 * of a line is <code>'#'</code>, the line is a comment and is
 * equivalent to a blank line.
 *
 * <p>The first line of a preferred list must contain a version
 * number in the following format:
 *
 * <pre>
 *     PreferredResources-Version: 1.<i>x</i>
 * </pre>
 *
 * This specification defines only version 1.0, but
 * <code>PreferredClassLoader</code> will parse any version
 * 1.<i>x</i>, <i>x</i>>=0 with the format and semantics specified
 * here.
 *
 * <p>After the version number line, a preferred list comprises an
 * optional default preferred entry followed by zero or more named
 * preferred entries.  A preferred list must contain either a default
 * preferred entry or at least one named preferred entry.  Blank lines
 * are allowed before and after preferred entries, as well as between
 * the lines of a named preferred entry.
 *
 * <p>A default preferred entry is a single line in the following
 * format:
 *
 * <pre>
 *     Preferred: <i>preferred-setting</i>
 * </pre>
 *
 * where <i>preferred-setting</i> is a non-empty sequence of
 * characters.  If <i>preferred-setting</i> equals <code>"true"</code>
 * (case insensitive), then resource names not matched by any of the
 * named preferred entries are by default preferred; otherwise,
 * resource names not matched by any of the named preferred entries
 * are by default not preferred.  If there is no default preferred
 * entry, then resource names are by default not preferred.
 *
 * <p>A named preferred entry is two lines in the following format:
 *
 * <pre>
 *     Name: <i>name-expression</i>
 *     Preferred: <i>preferred-setting</i>
 * </pre>
 *
 * where <i>name-expression</i> and <i>preferred-setting</i> are
 * non-empty sequences of characters.  If <i>preferred-setting</i>
 * equals <code>"true"</code> (case insensitive), then resource names
 * that are matched by <i>name-expression</i> (and not any more
 * specific named preferred entries) are preferred; otherwise,
 * resource names that are matched by <i>name-expression</i> (and not
 * any more specific named preferred entries) are not preferred.
 * 
 * <p>If <i>name-expression</i> ends with <code>".class"</code>, it
 * matches a class whose binary name is <i>name-expression</i> without
 * the <code>".class"</code> suffix and with each <code>'/'</code>
 * character replaced with a <code>'.'</code>.  It also matches any
 * class whose binary name starts with that same value followed by a
 * <code>'$'</code>; this rule is intended to match nested classes
 * that have an enclosing class of that name, so that the preferred
 * settings of a class and all of its nested classes are the same by
 * default.  It is possible, but strongly discouraged, to override the
 * preferred setting of a nested class with a named preferred entry
 * that explicitly matches the nested class's binary name.
 *
 * <p><i>name-expression</i> may match arbitrary resource names as
 * well as class names, with path elements separated by
 * <code>'/'</code> characters.
 *
 * <p>If <i>name-expression</i> ends with <code>"/"</code> or
 * <code>"/*"</code>, then the entry is a directory wildcard entry
 * that matches all resources (including classes) in the named
 * directory.  If <i>name-expression</i> ends with <code>"/-"</code>,
 * then the entry is a namespace wildcard entry that matches all
 * resources (including classes) in the named directory and all of its
 * subdirectories.
 *
 * <p>When more than one named preferred entry matches a class or
 * resource name, then the most specific entry takes precedence.  A
 * non-wildcard entry is more specific than a wildcard entry.  A
 * directory wildcard entry is more specific than a namespace wildcard
 * entry.  A namespace wildcard entry with more path elements is more
 * specific than a namespace wildcard entry with fewer path elements.
 * Given two non-wildcard entries, the entry with the longer
 * <i>name-expression</i> is more specific (this rule is only
 * significant when matching a class).  The order of named preferred
 * entries is insignificant.
 *
 * <h3>Example Preferred List</h3>
 *
 * <p>Following is an example preferred list:
 *
 * <pre>
 *     PreferredResources-Version: 1.0
 *     Preferred: false
 *
 *     Name: com/foo/FooBar.class
 *     Preferred: true
 *
 *     Name: com/foo/*
 *     Preferred: false
 *
 *     Name: com/foo/-
 *     Preferred: true
 *
 *     Name: image-files/*
 *     Preferred: mumble
 * </pre>
 *
 * <p>The class <code>com.foo.FooBar</code> is preferred, as well as
 * any nested classes that have it as an enclosing class.  All other
 * classes in the <code>com.foo</code> package are not preferred
 * because of the directory wildcard entry.  Classes in subpackages of
 * <code>com.foo</code> are preferred because of the namespace
 * wildcard entry.  Resources in the directory <code>"com/foo/"</code>
 * are not preferred, and resources in subdirectories of
 * <code>"com/foo/"</code> are preferred.  Resources in the directory
 * <code>"image-files/"</code> are not preferred because preferred
 * settings other than <code>"true"</code> are interpreted as false.
 * Classes that are in a package named <code>com.bar</code> are not
 * preferred because of the default preferred entry.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public class PreferredClassLoader extends RFC3986URLClassLoader
    implements ClassAnnotation, AdvisoryDynamicPermissions
{
    /**
     * well known name of resource that contains the preferred list in
     * a path of URLs
     **/
    private static final String PREF_NAME = "META-INF/PREFERRED.LIST";

    /** first URL in the path, or null if none */
    private final URL firstURL;

    /** class annotation string for classes defined by this loader */
    private final String exportAnnotation;

    /** permissions required to access loader through public API */
    private final Collection<Permission> permissions;

    /** security context for loading classes and resources */
    private final AccessControlContext acc;

    /** permission required to download code? */
    private final boolean requireDlPerm;

    /** URLStreamHandler to use when creating new "jar:" URLs */
    private final URLStreamHandler jarHandler;

    /** PreferredResources for this loader (null if no preferred list) */
    private final PreferredResources preferredResources;
    
    private final IOException exceptionWhileLoadingPreferred;

    private static final Permission downloadPermission =
	new DownloadPermission();
    private final Permission[] advisoryPermissions;

    /**
     * Creates a new <code>PreferredClassLoader</code> that loads
     * classes and resources from the specified path of URLs and
     * delegates to the specified parent class loader.
     *
     * <p>If <code>exportAnnotation</code> is not <code>null</code>,
     * then it will be used as the return value of the loader's {@link
     * #getClassAnnotation getClassAnnotation} method.  If
     * <code>exportAnnotation</code> is <code>null</code>, the
     * loader's <code>getClassAnnotation</code> method will return a
     * space-separated list of the URLs in the specified path.  The
     * <code>exportAnnotation</code> parameter can be used to specify
     * so-called "export" URLs, from which other parties should load
     * classes defined by the loader and which are different from the
     * "import" URLs that the classes are actually loaded from.
     *
     * <p>If <code>requireDlPerm</code> is <code>true</code>, the
     * loader's {@link #getPermissions getPermissions} method will
     * require that the {@link CodeSource} of any class defined by the
     * loader is granted {@link DownloadPermission}.
     *
     * @param urls the path of URLs to load classes and resources from
     *
     * @param parent the parent class loader for delegation
     *
     * @param exportAnnotation the export class annotation string to
     * use for classes defined by this loader, or <code>null</code>
     *
     * @param requireDlPerm if <code>true</code>, the loader will only
     * define classes with a {@link CodeSource} that is granted {@link
     * DownloadPermission}
     *
     * @throws SecurityException if there is a security manager and an
     * invocation of its {@link SecurityManager#checkCreateClassLoader
     * checkCreateClassLoader} method fails
     **/
    public PreferredClassLoader(URL[] urls,
				ClassLoader parent,
				String exportAnnotation,
				boolean requireDlPerm)
    {
	this(urls, parent, exportAnnotation, requireDlPerm, null);
    }

    /**
     * Creates a new <code>PreferredClassLoader</code> that loads
     * classes and resources from the specified path of URLs,
     * delegates to the specified parent class loader, and uses the
     * specified {@link URLStreamHandlerFactory} when creating new URL
     * objects.  This constructor passes <code>factory</code> to the
     * superclass constructor that has a
     * <code>URLStreamHandlerFactory</code> parameter.
     *
     * <p>If <code>exportAnnotation</code> is not <code>null</code>,
     * then it will be used as the return value of the loader's {@link
     * #getClassAnnotation getClassAnnotation} method.  If
     * <code>exportAnnotation</code> is <code>null</code>, the
     * loader's <code>getClassAnnotation</code> method will return a
     * space-separated list of the URLs in the specified path.  The
     * <code>exportAnnotation</code> parameter can be used to specify
     * so-called "export" URLs, from which other parties should load
     * classes defined by the loader and which are different from the
     * "import" URLs that the classes are actually loaded from.
     *
     * <p>If <code>requireDlPerm</code> is <code>true</code>, the
     * loader's {@link #getPermissions getPermissions} method will
     * require that the {@link CodeSource} of any class defined by the
     * loader is granted {@link DownloadPermission}.
     *
     * @param urls the path of URLs to load classes and resources from
     *
     * @param parent the parent class loader for delegation
     *
     * @param exportAnnotation the export class annotation string to
     * use for classes defined by this loader, or <code>null</code>
     *
     * @param requireDlPerm if <code>true</code>, the loader will only
     * define classes with a {@link CodeSource} that is granted {@link
     * DownloadPermission}
     *
     * @param factory the <code>URLStreamHandlerFactory</code> to use
     * when creating new URL objects, or <code>null</code>
     *
     * @throws SecurityException if there is a security manager and an
     * invocation of its {@link SecurityManager#checkCreateClassLoader
     * checkCreateClassLoader} method fails
     *
     * @since 2.1
     **/
    public PreferredClassLoader(URL[] urls,
				ClassLoader parent,
				String exportAnnotation,
				boolean requireDlPerm,
				URLStreamHandlerFactory factory)
    {
	super(urls, parent, factory);
	firstURL = (urls.length > 0 ? urls[0] : null);
	if (exportAnnotation != null) {
	    this.exportAnnotation = exportAnnotation;
	} else {
	    /*
	     * Caching the value of class annotation string here
	     * assumes that the protected method addURL() is never
	     * called on this class loader.
	     */
	    this.exportAnnotation = urlsToPath(urls);
	}
	this.requireDlPerm = requireDlPerm;
	if (factory != null) {
	    jarHandler = factory.createURLStreamHandler("jar");
	} else {
	    jarHandler = null;
	}

	acc = AccessController.getContext();
	    
	/*
	 * Precompute the permissions required to access the loader.
	 */
	PermissionCollection perm = new Permissions();
	addPermissionsForURLs(urls, perm, false);
        /*
         * If a preferred list exists relative to the first URL of this
         * loader's path, sets this loader's PreferredResources according
         * to that preferred list.  If no preferred list exists relative
         * to the first URL, leaves this loader's PreferredResources null.
         *
         * Throws IOException if an I/O exception occurs from which the
         * existence of a preferred list relative to the first URL cannot
         * be definitely determined.
         *
         * This method must only be invoked while synchronized on this
         * PreferredClassLoader, and it must not be invoked again after it
         * has completed successfully.
         * 
         * This was called from privileged context when it as part of a method.  
         * Note that InputStream is not subject to deserialization attacks 
         * like ObjectInputStream.
         * 
         * Also synchronization is not required as it is called from within
         * the constructor now, this change was made to remove any possiblity
         * of deadlock by minimising locking.
         */
        IOException except = null;
        PreferredResources pref = null;
	Permission [] perms = null;
	if (firstURL != null) {
            try {
                pref = AccessController.doPrivileged(
                    new PreferredResourcesPrivilegedExceptionAction(firstURL, jarHandler)
                );
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException){
                    except = (IOException) e;
                } else {
                    except = new IOException(e);
                }
            }
	    /*
	    * Use parent ClassLoader, if permission is not visible from parent
	    * it will remain unresolved and later be resolved by the policy provider.
	    */
	    perms = AccessController.doPrivileged(
		new PreferredPermissionsPrivilegedAction(
			urls, jarHandler, parent));
	}
        exceptionWhileLoadingPreferred = except;
        preferredResources = pref;
	advisoryPermissions = perms;
        this.permissions = new LinkedList<Permission>();
        Enumeration<Permission> en = perm.elements();
        while(en.hasMoreElements()){
            this.permissions.add(en.nextElement());
        }
    }

    /**
     * Convert an array of URL objects into a corresponding string
     * containing a space-separated list of URLs.
     *
     * Note that if the array has zero elements, the return value is
     * null, not the empty string.
     */
    static String urlsToPath(URL[] urls) {
	if (urls.length == 0) {
	    return null;
	} else if (urls.length == 1) {
	    return urls[0].toExternalForm();
	} else {
	    StringBuilder path = new StringBuilder(urls[0].toExternalForm());
	    for (int i = 1; i < urls.length; i++) {
		path.append(' ');
		path.append(urls[i].toExternalForm());
	    }
	    return path.toString();
	}
    }

    /**
     * Creates a new instance of <code>PreferredClassLoader</code>
     * that loads classes and resources from the specified path of
     * URLs and delegates to the specified parent class loader.
     *
     * <p>The <code>exportAnnotation</code> and
     * <code>requireDlPerm</code> parameters have the same semantics
     * as they do for the constructors.
     *
     * <p>The {@link #loadClass loadClass} method of the returned
     * <code>PreferredClassLoader</code> will, if there is a security
     * manager, invoke its {@link SecurityManager#checkPackageAccess
     * checkPackageAccess} method with the package name of the class
     * to load before attempting to load the class; this could result
     * in a <code>SecurityException</code> being thrown from
     * <code>loadClass</code>.
     *
     * @param urls the path of URLs to load classes and resources from
     *
     * @param parent the parent class loader for delegation
     *
     * @param exportAnnotation the export class annotation string to
     * use for classes defined by this loader, or <code>null</code>
     *
     * @param requireDlPerm if <code>true</code>, the loader will only
     * define classes with a {@link CodeSource} that is granted {@link
     * DownloadPermission}
     *
     * @return the new <code>PreferredClassLoader</code> instance
     *
     * @throws SecurityException if the current security context does
     * not have the permissions necessary to connect to all of the
     * URLs in <code>urls</code>
     **/
    public static PreferredClassLoader
	newInstance(final URL[] urls,
		    final ClassLoader parent,
		    final String exportAnnotation,
		    final boolean requireDlPerm)
    {
	/* ensure caller has permission to access all urls */
	PermissionCollection perms = new Permissions();
	addPermissionsForURLs(urls, perms, false);
	checkPermissions(perms);

	AccessControlContext acc = getLoaderAccessControlContext(urls);
	/* Use privileged status to return a new class loader instance */
	return
	    AccessController.doPrivileged(new PrivilegedAction<PreferredClassLoader>() {
		@Override
		public PreferredClassLoader run() {
		    return new PreferredFactoryClassLoader(urls, parent,
			exportAnnotation, requireDlPerm);
		}
	    }, acc);
    }

    

    /**
     * Returns an InputStream from which the preferred list relative
     * to the specified URL can be read, or null if the there is
     * definitely no preferred list relative to the URL.  If the URL's
     * path ends with "/", then the preferred list is sought at the
     * location "META-INF/PREFERRED.LIST" relative to the URL;
     * otherwise, the URL is assumed to refer to a JAR file, and the
     * preferred list is sought within that JAR file, as the entry
     * named "META-INF/PREFERRED.LIST".
     *
     * Throws IOException if an I/O exception occurs from which the
     * existence of a preferred list relative to the specified URL
     * cannot be definitely determined.
     **/
    private static InputStream getPreferredInputStream(URL firstURL, String resource, URLStreamHandler jarHandler)
	throws IOException
    {
	URL prefListURL;
	try {
	    URL baseURL;	// base URL to load PREF_NAME relative to
	    if (firstURL.getFile().endsWith("/")) { // REMIND: track 4915051
		baseURL = firstURL;
	    } else {
		/*
		 * First try to get a definite answer about the existence of a
		 * PREFERRED.LIST that doesn't by-pass a JAR file cache, if
		 * any. If that fails we determine if the JAR file exists by
		 * attempting to access it directly, without using a "jar:" URL,
		 * because the "jar:" URL handler can mask the distinction
		 * between definite lack of existence and less definitive
		 * errors. Unfortunately, this direct access circumvents the JAR
		 * file caching done by the "jar:" handler, so it ends up
		 * causing duplicate requests of the JAR file on first use when
		 * our first attempt fails. (For HTTP-protocol URLs, the initial
		 * request will use HEAD instead of GET.)
		 *
		 * After determining that the JAR file exists, attempt to
		 * retrieve the preferred list using a "jar:" URL, like
		 * URLClassLoader uses to load resources from a JAR file.
		 */
		if (jarExists(firstURL, jarHandler)) {
		    baseURL = getBaseJarURL(firstURL, jarHandler);
		} else {
		    return null;
		}
	    }
	    prefListURL = new URL(baseURL, resource);
	    URLConnection preferredConnection =
		getPreferredConnection(prefListURL, false);
	    if (preferredConnection != null) {
		return preferredConnection.getInputStream();
	    } else {
		return null;
	    }
	} catch (IOException e) {
	    /*
	     * Assume that any IOException thrown while attempting to
	     * access a "file:" URL and any FileNotFoundException
	     * implies that there is definitely no preferred list
	     * relative to the specified URL.
	     */
	    if (firstURL.getProtocol().equals("file") ||
		e instanceof FileNotFoundException)
	    {
		return null;
	    } else {
		throw e;
	    }
	}
    }

    /* cache existence of jar files referenced by codebase urls */
    private static final Set<String> existSet = new HashSet<String>(11);

    /*
     * Determine if a jar file in a given URL location exists.  If the
     * jar exists record the jar file's URL in a cache of URL strings.
     *
     * Recording the existence of the jar prevents the need to
     * re-determine the jar's existence on subsequent downloads of the
     * jar in potentially different preferred class loaders.
     */
    private static boolean jarExists(URL firstURL, URLStreamHandler jarHandler) throws IOException {
	boolean exists;
        
	synchronized (existSet) {
            // The comment says in a cache of URL strings, URL in Set, bad.
	    exists = existSet.contains(firstURL.toString());
	}

	if (!exists) {
	    /*
	     * first try to get a definite answer of the existence of a JAR
	     * file, if no IOException is thrown when obtaining it through the
	     * "jar:" protocol we can safely assume the JAR file is locally
	     * available upon the attempt (elsewhere) to obtain the preferred
	     * list
	     */
            URL baseURL = null;
	    try {
                baseURL = getBaseJarURL(firstURL, jarHandler);
		((JarURLConnection) baseURL.openConnection()).getManifest();
		exists = true;
	    } catch (IOException e) {
		// we still have no definite answer on whether the JAR file
		// and therefore the PREFERRED.LIST exists
	    } catch (NullPointerException e){
                // Sun Bug ID: 6536522
                // NullPointerException is thrown instead of MalformedURLException
                // Case is the same as above, we have no definite answer on
                // whether the JAR file and therefore the PREFERRED.LIST exists.
                System.err.println("NPE thrown while trying to open connection :" +
                        baseURL);
                e.printStackTrace(System.err);
            }

	    if (!exists) {
		exists = (getPreferredConnection(firstURL, true) != null);
	    }
	    if (exists) {
		synchronized (existSet) {
		    existSet.add(firstURL.toString());
		}
	    }
	}
	return exists;
    }

    /**
     * Returns a "jar:" URL for the root directory of the JAR file at
     * the specified URL.  If this loader was constructed with a
     * URLStreamHandlerFactory, then the returned URL will have a
     * URLStreamHandler that was created by the factory.
     **/
    private static URL getBaseJarURL(final URL url, final URLStreamHandler jarHandler) throws MalformedURLException {
	if (jarHandler == null) {
	    return new URL("jar", "", -1, url + "!/");
	} else {
	    try {
		return AccessController.doPrivileged(
		    new PrivilegedExceptionAction<URL>() {
			@Override
			public URL run() throws MalformedURLException {
			    return new URL("jar", "", -1, url + "!/",
					   jarHandler);
			}
		    });
	    } catch (PrivilegedActionException e) {
		throw (MalformedURLException) e.getCause();
	    }
	}
    }
    
    /**
     * Obtain a url connection from which an input stream that
     * contains a preferred list can be obtained.
     *
     * For http urls, attempts to use http response codes to
     * determine if a preferred list exists or is definitely not
     * found.  Simply attempts to open a connection to other kinds
     * of non-file urls.  If the attempt fails, an IOException is
     * thrown to user code.
     *
     * Returns null if the preferred list definitely does not
     * exist.  Rethrows all indefinite IOExceptions generated
     * while trying to open a connection to the preferred list.
     *
     * The caller has the option to close the connection after the
     * resource has been detected (as will happen when probing for a
     * PREFERRED.LIST).
     */
    private static URLConnection getPreferredConnection(URL url, boolean closeAfter)
	throws IOException
    {
	if (url.getProtocol().equals("file")) {
	    return url.openConnection();
	}
	URLConnection closeConn = null;
	URLConnection conn = null;
	try {
	    closeConn = url.openConnection();
	    conn = closeConn;
						 
	    /* check status of http urls  */
	    if (conn instanceof HttpURLConnection) {
		HttpURLConnection hconn = (HttpURLConnection) conn;
		if (closeAfter) {
		    hconn.setRequestMethod("HEAD");
		}
		int responseCode = hconn.getResponseCode();//NPE
		
		switch (responseCode) {
		case HttpURLConnection.HTTP_OK:
		case HttpURLConnection.HTTP_NOT_AUTHORITATIVE:
		    /* the preferred list exists */
		    break;

		    /* 404, not found appears to be handled by
		     * HttpURLConnection (FileNotFoundException is
		     * thrown), but to be safe do the right thing here as
		     * well.
		     */
		case HttpURLConnection.HTTP_NOT_FOUND:
		case HttpURLConnection.HTTP_FORBIDDEN:
		case HttpURLConnection.HTTP_GONE:
		    /* list definitely does not exist */
		    conn = null;
		    break;
		default:
		    /* indefinite response code */
		    throw new IOException("Indefinite http response for " +
			"preferred list request:" +
			hconn.getResponseMessage());
		}
	    }
        } catch (RuntimeException e){
            if ( e instanceof NullPointerException || e.getCause() instanceof NullPointerException) {
                // Sun Bug ID: 6536522
                throw new IOException(url.toString(), e);
            } else {
                throw e;
            }
	} finally {
	    if (closeAfter && (closeConn != null)) {
		/* clean up after... */
		try {
		    closeConn.getInputStream().close();//RTE NPE
		} catch (IOException e) {
		} catch (RuntimeException e){
                    if ( e instanceof NullPointerException || e.getCause() 
			    instanceof NullPointerException) {
                        // Sun Bug ID: 6536522
                        // swallow
                        e.printStackTrace(System.err);
                    } else {
                        throw e;
                    }
                }
	    }
	}

	return conn;
    }

    /**
     * The codebase annotation for PreferredClassLoader is immutable and 
     * cannot be modified.
     * 
     * @param url 
     * @throws UnsupportedOperationException when invoked.
     */
    @Override
    protected final void addURL(URL url) {
	throw new UnsupportedOperationException(
		"PreferredClassLoader codebase annotation is immutable");
    }

    /**
     * Returns <code>true</code> if a class or resource with the
     * specified name is preferred for this class loader, and
     * <code>false</code> if a class or resource with the specified
     * name is not preferred for this loader.
     *
     * <p>If <code>isClass</code> is <code>true</code>, then
     * <code>name</code> is interpreted as the binary name of a class;
     * otherwise, <code>name</code> is interpreted as the full path of
     * a resource.
     *
     * <p>This method only returns <code>true</code> if a class or
     * resource with the specified name exists in the this loader's
     * path of URLs and the name is preferred in the preferred list.
     * This method returns <code>false</code> if the name is not
     * preferred in the preferred list or if the name is preferred
     * with the default preferred entry or a wildcard preferred entry
     * and the class or resource does not exist in the path of URLs.
     *
     * @param name the name of the class or resource
     *
     * @param isClass <code>true</code> if <code>name</code> is a
     * binary class name, and <code>false</code> if <code>name</code>
     * is the full path of a resource
     *
     * @return <code>true</code> if a class or resource named
     * <code>name</code> is preferred for this loader, and
     * <code>false</code> if a class or resource named
     * <code>name</code> is not preferred for this loader
     *
     * @throws IOException if the preferred list cannot definitely be
     * determined to exist or not exist, or if the preferred list
     * contains a syntax error, or if the name is preferred with the
     * default preferred entry or a wildcard preferred entry and the
     * class or resource cannot definitely be determined to exist or
     * not exist in the path of URLs, or if the name is preferred with
     * a non-wildcard entry and the class or resource does not exist
     * or cannot definitely be determined to exist in the path of URLs
     **/
    protected boolean isPreferredResource(final String name,
					  final boolean isClass)
	throws IOException
    {
        return isPreferredResource0(name, isClass);
    }

    /*
     * Perform the work to determine if a resource name is preferred.
     * 
     * Synchronized removed to avoid possible ClassLoader deadlock.
     */
    private boolean isPreferredResource0(String name,
						      boolean isClass)
	throws IOException
    {
        if (exceptionWhileLoadingPreferred != null) throw exceptionWhileLoadingPreferred;
	if (preferredResources == null) {
	    return false;	// no preferred list: nothing is preferred
	}

	String resourceName = name;
	if (isClass) {
	    /* class name -> resource name */
	    resourceName = name.replace('.', '/') + ".class";
	}

	/*
	 * Determine if the class name is preferred. Making this
	 * distinction is somewhat tricky because we need to cache the
	 * preferred state (i.e. if the name is preferred and its
	 * resource exists) in a way that avoids duplication of
	 * preferred information - state information is stored back
	 * into the preferred resources object for this class loader
	 * and not held in a separate preferred settings cache.
	 */
	boolean resourcePreferred = false;

	int state = preferredResources.getNameState(resourceName, isClass);
	switch (state) {
	case PreferredResources.NAME_NOT_PREFERRED:
	    resourcePreferred = false;
	    break;

	case PreferredResources.NAME_PREFERRED_RESOURCE_EXISTS:
	    resourcePreferred = true;
	    break;

	case PreferredResources.NAME_NO_PREFERENCE:
	    Boolean wildcardPref =
		preferredResources.getWildcardPreference(resourceName);
	    if (wildcardPref == null) {
		/* preferredDefault counts as a wild card */
		wildcardPref = preferredResources.getDefaultPreference();
	    }
	    if (wildcardPref.booleanValue()) {
		resourcePreferred =
		    findResourceUpdateState(name, resourceName);
	    }
	    break;

	case PreferredResources.NAME_PREFERRED:
	    resourcePreferred =
		findResourceUpdateState(name, resourceName);
	    if (!resourcePreferred) {
		throw new IOException("no resource found for " +
				      "complete preferred name");
	    }
	    break;

	default:
	    throw new Error("unknown preference state");
	}
	return resourcePreferred;
    }

    /*
     * Determine if a resource for a given preferred name exists.  If
     * the resource exists record its new state in the
     * preferredResources object.
     */
    private boolean findResourceUpdateState(String name,
					    String resourceName)
	throws IOException
    {
	if (findResource(resourceName) != null) {
	    /* the resource is known to exist */
	    preferredResources.setNameState(resourceName,
	        PreferredResources.NAME_PREFERRED_RESOURCE_EXISTS);
	    return true;
	}

	return false;
    }

    /**
     * Loads a class with the specified name.
     *
     * <p><code>PreferredClassLoader</code> implements this method as
     * follows:
     *
     * <p>This method first invokes {@link #findLoadedClass
     * findLoadedClass} with <code>name</code>; if
     * <code>findLoadedClass</code> returns a non-<code>null</code>
     * <code>Class</code>, then this method returns that
     * <code>Class</code>.
     *
     * <p>Otherwise, this method invokes {@link #isPreferredResource
     * isPreferredResource} with <code>name</code> as the first
     * argument and <code>true</code> as the second argument:
     *
     * <ul>
     *
     * <li>If <code>isPreferredResource</code> throws an
     * <code>IOException</code>, then this method throws a
     * <code>ClassNotFoundException</code> containing the
     * <code>IOException</code> as its cause.
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>true</code>, then this method invokes {@link #findClass
     * findClass} with <code>name</code>.  If <code>findClass</code>
     * throws an exception, then this method throws that exception.
     * Otherwise, this method returns the <code>Class</code> returned
     * by <code>findClass</code>, and if <code>resolve</code> is
     * <code>true</code>, {@link #resolveClass resolveClass} is
     * invoked with the <code>Class</code> before returning.
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>false</code>, then this method invokes the superclass
     * implementation of {@link ClassLoader#loadClass(String,boolean)
     * loadClass} with <code>name</code> and <code>resolve</code> and
     * returns the result.  If the superclass's <code>loadClass</code>
     * throws an exception, then this method throws that exception.
     *
     * </ul>
     *
     * @param name the binary name of the class to load
     *
     * @param resolve if <code>true</code>, then {@link #resolveClass
     * resolveClass} will be invoked with the loaded class before
     * returning
     *
     * @return the loaded class
     *
     * @throws ClassNotFoundException if the class could not be found
     **/
    @Override
    protected Class loadClass(String name, boolean resolve)
	throws ClassNotFoundException
    {
	// First, check if the class has already been loaded
	Class c = findLoadedClass(name);
	if (c == null) {
	    boolean preferred;
	    try {
		preferred = isPreferredResource(name, true);
	    } catch (IOException e) {
		throw new ClassNotFoundException(name +
		    " (could not determine preferred setting; " +
		    (firstURL != null ?
		     "first URL: \"" + firstURL + "\"" : "no URLs") +
		    ")", e);
	    }
	    if (preferred) {
                synchronized (this){
                    // Double check again in case the class has been loaded.
                    c = findLoadedClass(name);
                    if (c == null){
                        c = findClass(name);
                        if (resolve) resolveClass(c);
                    }
                }
	    } else {
		return super.loadClass(name, resolve);
	    }
	}
	return c;
    }
	
    /**
     * Gets a resource with the specified name.
     *
     * <p><code>PreferredClassLoader</code> implements this method as
     * follows:
     *
     * <p>This method invokes {@link #isPreferredResource
     * isPreferredResource} with <code>name</code> as the first
     * argument and <code>false</code> as the second argument:
     *
     * <ul>
     *
     * <li>If <code>isPreferredResource</code> throws an
     * <code>IOException</code>, then this method returns
     * <code>null</code>.
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>true</code>, then this method invokes {@link
     * #findResource findResource} with <code>name</code> and returns
     * the result.
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>false</code>, then this method invokes the superclass
     * implementation of {@link ClassLoader#getResource getResource}
     * with <code>name</code> and returns the result.
     *
     * </ul>
     *
     * @param name the name of the resource to get
     *
     * @return a <code>URL</code> for the resource, or
     * <code>null</code> if the resource could not be found
     **/
    @Override
    public URL getResource(String name) {
	try {
	    return (isPreferredResource(name, false) ?
	        findResource(name) : super.getResource(name));
	} catch (IOException e) {
	}
	return null;
    }
    
    /**
     * Gets an Enumeration of resources with the specified name.
     *
     * <p><code>PreferredClassLoader</code> implements this method as
     * follows:
     *
     * <p>This method invokes {@link #isPreferredResource
     * isPreferredResource} with <code>name</code> as the first
     * argument and <code>false</code> as the second argument:
     *
     * <ul>
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>true</code>, then this method invokes {@link
     * #findResources findResources} with <code>name</code> and returns
     * the results.
     *
     * <li>If <code>isPreferredResource</code> returns
     * <code>false</code>, then this method invokes the superclass
     * implementation of {@link ClassLoader#getResources getResources}
     * with <code>name</code> and returns the result.
     *
     * </ul>
     *
     * @param name the name of the resource to get
     *
     * @return an <code>Enumeration</code> for the resource, the
     * <code>Enumeration</code> is empty if the resource could not be found
     * 
     * @throws IOException if isPreferredResource throws an IOException.
     * 
     * @since 3.0.0
     **/
    @Override
    public Enumeration<URL> getResources(String name) throws IOException{
        return (isPreferredResource(name, false) ?
                findResources(name) : super.getResources(name));
    }

    /*
     * Work around 4841786: wrap ClassLoader.definePackage so that if
     * it throws an IllegalArgumentException because an ancestor
     * loader "defined" the named package since the last time that
     * ClassLoader.getPackage was invoked, then just return the
     * result of invoking ClassLoader.getPackage again here.
     * Fortunately, URLClassLoader.defineClass ignores the value
     * returned by this method.
     */
    @Override
    protected Package definePackage(String name, String specTitle,
				    String specVersion, String specVendor,
				    String implTitle, String implVersion,
				    String implVendor, URL sealBase)
    {
	try {
	    return super.definePackage(name, specTitle,
				       specVersion, specVendor,
				       implTitle, implVersion,
				       implVendor, sealBase);
	} catch (IllegalArgumentException e) {
	    return getPackage(name);
	}
    }
    
    /**
     * {@inheritDoc}
     *
     * <p><code>PreferredClassLoader</code> implements this method as
     * follows:
     *
     * <p>If this <code>PreferredClassLoader</code> was constructed
     * with a non-<code>null</code> export class annotation string,
     * then this method returns that string.  Otherwise, this method
     * returns a space-separated list of this loader's path of URLs.
     **/
    @Override
    public String getClassAnnotation() {
	return exportAnnotation;
    }

    /**
     * Check that the current access control context has all of the
     * permissions necessary to load classes from this loader.
     */
    void checkPermissions() {
	checkPermissions(permissions);
    }

    private void checkPermissions(Collection<Permission> permissions){
        SecurityManager sm = System.getSecurityManager();
	if (sm != null) {		// should never be null?
	    Iterator<Permission> en = permissions.iterator();
	    while (en.hasNext()) {
		sm.checkPermission((Permission) en.next());
	    }
	}
    }

    /**
     * Check that the current access control context has all of the
     * given permissions.
     */
    private static void checkPermissions(PermissionCollection perms) {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {		// should never be null?
	    Enumeration en = perms.elements();
	    while (en.hasMoreElements()) {
		sm.checkPermission((Permission) en.nextElement());
	    }
	}
    }

    /**
     * Returns the static permissions to be automatically granted to
     * classes loaded from the specified {@link CodeSource} and
     * defined by this class loader.
     *
     * <p><code>PreferredClassLoader</code> implements this method as
     * follows:
     *
     * <p>If there is a security manager and this
     * <code>PreferredClassLoader</code> was constructed to enforce
     * {@link DownloadPermission}, then this method checks that the
     * current security policy grants the specified
     * <code>CodeSource</code> the permission
     * <code>DownloadPermission("permit")</code>; if that check fails,
     * then this method throws a <code>SecurityException</code>.
     *
     * <p>Then this method invokes the superclass implementation of
     * {@link #getPermissions getPermissions} and returns the result.
     *
     * @param codeSource the <code>CodeSource</code> to return the
     * permissions to be granted to
     *
     * @return the permissions to be granted to the
     * <code>CodeSource</code>
     *
     * @throws SecurityException if there is a security manager, this
     * <code>PreferredClassLoader</code> was constructed to enforce
     * <code>DownloadPermission</code>, and the current security
     * policy does not grant the specified <code>CodeSource</code> the
     * permission <code>DownloadPermission("permit")</code>
     **/
    @Override
    protected PermissionCollection getPermissions(CodeSource codeSource) {
	if (requireDlPerm) {
	    SecurityManager sm = System.getSecurityManager();
	    if (sm != null) {
		ProtectionDomain pd =
		    new ProtectionDomain(codeSource, null, this, null);

		if (!pd.implies(downloadPermission)) {
		    throw new SecurityException(
			"CodeSource not permitted to define class: " +
			codeSource);
		}
	    }
	}
	
  	return super.getPermissions(codeSource);
    }

    /**
     * Returns a string representation of this class loader.
     **/
    @Override
    public String toString() {
	return super.toString() + "[\"" + exportAnnotation + "\"]";
    }

    /**
     * Return the access control context that a loader for the given
     * codebase URL path should execute with.
     */
    static AccessControlContext getLoaderAccessControlContext(URL[] urls)
    {
	/*
	 * The approach used here is taken from the similar method
	 * getAccessControlContext() in the sun.applet.AppletPanel class.
	 */
	// We don't need to consult the policy here as the ProtectionDomain
	// does so during the permission check, see comment further below.
	PermissionCollection perms = new Permissions();
	// createClassLoader permission needed to create loader in context
	perms.add(new RuntimePermission("createClassLoader"));

	// add permissions to read any "java.*" property
	perms.add(new java.util.PropertyPermission("java.*","read"));

	// add permissions required to load from codebase URL path
	addPermissionsForURLs(urls, perms, true);

	/*
	 * Create an AccessControlContext that consists of a single
	 * protection domain with only the permissions calculated above.
         * Comment added 7th May 2010 by Peter Firmstone:
         * This did call the pre java 1.4 constructor which causes the
         * ProtectionDomain to not consult the Policy, this
         * had the effect of not allowing Dynamic Permission changes to be
         * effected by the Policy.  It doesn't affect the existing
         * DynamicPolicy implementation as it returns the Permissions
         * allowing the ProtectionDomain domain combiner to combine
         * cached permissions with those from the Policy.
         * ProtectionDomain(CodeSource, PermissionCollection)
         * By utilising this earlier constructor it also prevents
         * RevokeableDynamicPolicy, hence the constructor change.  
	 */
	ProtectionDomain pd = new ProtectionDomain(
	    new CodeSource((urls.length > 0 ? urls[0] : null),
			   (Certificate[]) null), perms, null, null);
	return new AccessControlContext(new ProtectionDomain[] { pd });
    }

    /**
     * Adds to the specified permission collection the permissions
     * necessary to load classes from a loader with the specified URL
     * path; if "forLoader" is true, also adds URL-specific
     * permissions necessary for the security context that such a
     * loader operates within, such as permissions necessary for
     * granting automatic permissions to classes defined by the
     * loader.  A given permission is only added to the collection if
     * it is not already implied by the collection.
     **/
    static void addPermissionsForURLs(URL[] urls,
				      PermissionCollection perms,
				      boolean forLoader)
    {
	for (int i = 0; i < urls.length; i++) {
	    URL url = urls[i];
	    try {
		URLConnection urlConnection = url.openConnection();
		Permission p = urlConnection.getPermission();
		if (p != null) {
		    if (p instanceof FilePermission) {
			/*
			 * If the codebase is a file, the permission required
			 * to actually read classes from the codebase URL is
			 * the permission to read all files beneath the last
			 * directory in the file path, either because JAR
			 * files can refer to other JAR files in the same
			 * directory, or because permission to read a
			 * directory is not implied by permission to read the
			 * contents of a directory, which all that might be
			 * granted.
			 */
			String path = p.getName();
			int endIndex = path.lastIndexOf(File.separatorChar);
			if (endIndex != -1) {
			    path = path.substring(0, endIndex+1);
			    if (path.endsWith(File.separator)) {
				path += "-";
			    }
			    Permission p2 = new FilePermission(path, "read");
			    if (!perms.implies(p2)) {
				perms.add(p2);
			    }
			} else {
			    /*
			     * No directory separator: use permission to
			     * read the file.
			     */
			    if (!perms.implies(p)) {
				perms.add(p);
			    }
			}
		    } else {
			if (!perms.implies(p)) {
			    perms.add(p);
			}

	                /*
			 * If the purpose of these permissions is to grant
			 * them to an instance of a URLClassLoader subclass,
			 * we must add permission to connect to and accept
			 * from the host of non-"file:" URLs, otherwise the
			 * getPermissions() method of URLClassLoader will
			 * throw a security exception.
			 */
			if (forLoader) {
                            // URLPermission is required for JDK1.8 and JDK1.9
                            try {
                                perms.add(
                                    new URLPermission(
                                            Uri.urlToUri(url).toString(),
                                            "GET:"
                                    )
                                );
                            } catch (URISyntaxException ex) {
                                Logger.getLogger(PreferredClassLoader.class.getName()).log(Level.FINE, "Unable to grant URLPermission", ex);
                            } catch (IllegalArgumentException ex){
                                Logger.getLogger(PreferredClassLoader.class.getName()).log(Level.FINE, "Unable to grant URLPermission", ex);
                            }
			    // get URL with meaningful host component
			    URL hostURL = url;
			    for (URLConnection conn = urlConnection;
				 conn instanceof JarURLConnection;)
			    {
				hostURL =
				    ((JarURLConnection) conn).getJarFileURL();
				conn = hostURL.openConnection();
			    }
			    String host = hostURL.getHost();
			    if (host != null &&
				p.implies(new SocketPermission(host,
							       "resolve")))
			    {
				Permission p2 =
				    new SocketPermission(host,
							 "connect,accept");
				if (!perms.implies(p2)) {
				    perms.add(p2);
				}
			    }
			}
		    }
		}
	    } catch (IOException e) {
		/*
		 * This shouldn't happen, although it is declared to be
		 * thrown by openConnection() and getPermission().  If it
		 * does, don't bother granting or requiring any permissions
		 * for this URL.
		 */
	    } catch (NullPointerException e){
                // Sun Bug ID: 6536522
            }
	}
    }
    
    @Override
    public Permission[] getPermissions() {
	if (advisoryPermissions == null) {
	    return AdvisoryDynamicPermissions.DEFAULT_PERMISSIONS;
	}
	return advisoryPermissions.clone();
    }

    private static class PreferredPermissionsPrivilegedAction
	    implements PrivilegedAction<Permission[]> {

	private static final String resource = "META-INF/PERMISSIONS.LIST";
	private final URL[] urls;
	private final URLStreamHandler jarHandler;
	private final ClassLoader loader;

	PreferredPermissionsPrivilegedAction(
		URL[] urls,
		URLStreamHandler jarHandler,
		ClassLoader loader) 
	{
	    this.urls = urls;
	    this.jarHandler = jarHandler;
	    this.loader = loader;
	}

	@Override
	public Permission[] run() {
	    Permission[] permissions = null;
	    List<Permission> perms = new LinkedList<Permission>();
	    InputStream in = null;
	    for (int i = 0, l = urls.length; i < l; i++) {
		try {
		    in = getPreferredInputStream(urls[i], resource, jarHandler);
		    if (in != null) {
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(in, "UTF-8"));
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			    String trim = line.trim();
			    if (trim.startsWith("#") || trim.startsWith("//")
				    || (trim.length() == 0)) {
				continue;
			    }
			    perms.add(AdvisoryPermissionParser.parse(line, loader));
			}
			permissions = perms.toArray(new Permission[perms.size()]);
		    }
		} catch (Exception ex) {
		    Logger.getLogger(PreferredClassLoader.class.getName()).log(Level.CONFIG, "No advisory permissions: " + urls[i].toString(), ex);
		} finally {
		    if (in != null) {
			try {
			    in.close();
			} catch (IOException ex) {
			} // Ignore.
		    }
		}
	    }
	    return permissions;
	}

    }
    
    private static class PreferredResourcesPrivilegedExceptionAction 
                implements PrivilegedExceptionAction<PreferredResources>{
        
        private final URL firstURL;
	private final URLStreamHandler jarHandler;
        
        PreferredResourcesPrivilegedExceptionAction(URL first, URLStreamHandler jarHandler){
            firstURL = first;
	    this.jarHandler = jarHandler;
        }

        @Override
        public PreferredResources run() throws Exception {
            PreferredResources pref = null;
            InputStream prefIn = null;
            try {
                prefIn = getPreferredInputStream(firstURL, PREF_NAME, jarHandler);
                if (prefIn != null) pref = new PreferredResources(prefIn);
            } catch (IOException ex) {
                Logger.getLogger(PreferredClassLoader.class.getName()).log(Level.CONFIG, "Unable to access preferred resources", ex);
                throw ex;
            } finally {
                try {
                    if (prefIn != null){
                        prefIn.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(PreferredClassLoader.class.getName()).log(Level.CONFIG, "Problem closing preferred resources input stream", ex);
                } 
            }
            return pref;
        }
    }
}
