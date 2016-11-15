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

import java.io.FilePermission;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.RMIClassLoaderSpi;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.loader.ClassAnnotation;
import net.jini.loader.DownloadPermission;
import net.jini.loader.LoadClass;
import org.apache.river.action.GetPropertyAction;
import org.apache.river.api.net.Uri;
import org.apache.river.concurrent.RC;
import org.apache.river.concurrent.Ref;
import org.apache.river.concurrent.Referrer;
import org.apache.river.logging.Levels;
import org.apache.river.logging.LogUtil;

/**
 * An <code>RMIClassLoader</code> provider that supports preferred
 * classes.
 *
 * <p>See the {@link RMIClassLoader} specification and 
 * {@link net.jini.loader.ClassLoading} for information
 * about how to install and configure the <code>RMIClassLoader</code>
 * service provider.
 *
 * <p><code>PreferredClassProvider</code> uses instances of {@link
 * PreferredClassLoader} to load classes from codebase URI paths
 * supplied to <code>RMIClassLoader.loadClass</code> methods.  In 
 * previous releases only codebase URL paths were permitted.
 *
 * <p><code>PreferredClassProvider</code> does not enforce {@link
 * DownloadPermission} by default, but a subclass can configure it to
 * do so by passing <code>true</code> as the argument to the
 * <code>protected</code> constructor.
 *
 * <p>By overriding the {@link #getClassAnnotation(ClassLoader)
 * getClassAnnotation(ClassLoader)} method, a subclass can also
 * configure the class annotations to be used for classes defined by
 * the system class loader, its ancestor class loaders, and any class
 * loader that is not an instance of {@link ClassAnnotation} or {@link
 * URLClassLoader}.
 *
 * <h3>Common Terms and Behaviours</h3>
 *
 * The following section defines terms and describes behaviours common
 * to how <code>PreferredClassProvider</code> implements the abstract
 * methods of <code>RMIClassLoaderSpi</code>.  Where applicable, these
 * definitions and descriptions are relative to the instance of
 * <code>PreferredClassProvider</code> on which a method is invoked
 * and the context in which it is invoked.
 *
 * <p>The <i>annotation string</i> for a class loader is determined by
 * the following procedure:
 *
 * <ul>
 *
 * <li>If the loader is the system class loader or an ancestor of the
 * system class loader (including the bootstrap class loader), the
 * annotation string is the result of invoking {@link
 * #getClassAnnotation(ClassLoader) getClassAnnotation(ClassLoader)}
 * with the loader.
 *
 * <li>Otherwise, if the loader is an instance of {@link
 * ClassAnnotation}, the annotation string is the result of invoking
 * {@link ClassAnnotation#getClassAnnotation getClassAnnotation} on
 * the loader.
 *
 * <li>Otherwise, if the loader is an instance of {@link
 * URLClassLoader}, the annotation string is a space-separated list of
 * the URLs returned by an invocation of {@link URLClassLoader#getURLs
 * getURLs} on the loader.
 *
 * <li>Otherwise, the annotation string is the result of invoking
 * {@link #getClassAnnotation(ClassLoader)
 * getClassAnnotation(ClassLoader)} with the loader.
 *
 * </ul>
 *
 * The <i>annotation URL path</i> for a class loader is the path of
 * URLs obtained by parsing the annotation string for the loader as a
 * list of URLs separated by spaces, where each URL is parsed as with
 * the {@link URL#URL(String) URL(String)} constructor; if such
 * parsing would result in a {@link MalformedURLException}, then the
 * annotation URL path for the loader is only defined to the extent
 * that it is not equal to any other path of URLs.
 *
 * <p>A <code>PreferredClassProvider</code> maintains an internal
 * table of class loader instances indexed by keys that comprise a
 * path of URIs and a parent class loader.  In previous releases keys utilised 
 * {@link URL}, but now utilise {@link Uri} by default.  The following property 
 * <code>-Dnet.jini.loader.codebaseAnnotation=URL</code> 
 * may be set from the command line to revert to {@link URL}.  The table does not
 * strongly reference the class loader instances, in order to allow
 * them (and the classes they have defined) to be garbage collected
 * when they are not otherwise reachable.
 * 
 * <p>The behavioural difference between {@link Uri} and {@link URL} when used
 * in ClassLoader index keys is subtle, {@link URL} remote links rely on DNS to resolve
 * domain names to IP addresses, for this reason, when using strict {@link URL}
 * codebase annotations, the IP address of each codebase at the time they're resolved
 * is part of the codebase annotations identity.  {@link Uri} identity on the other hand is 
 * determined by RFC3986 normalization and is more flexible the codebase server 
 * to change its IP address or be replicated by other codebase servers 
 * different IP addresses, provided they can be reached by their domain name
 * address.
 *
 * <p>The methods {@link #loadClass loadClass}, {@link #loadProxyClass
 * loadProxyClass}, and {@link #getClassLoader getClassLoader}, which
 * each have a <code>String</code> parameter named
 * <code>codebase</code>, have the following behaviors in common:
 *
 * <ul>
 *
 * <li><code>codebase</code> may be <code>null</code>.  If it is not
 * <code>null</code>, it is interpreted as a path of URLs by parsing
 * it as a list of URLs separated by spaces.  It is recommended that
 * URLs be compliant with RFC3986 Syntax.  Prior to parsing, any file path 
 * separators converted to '/' and any illegal characters are percentage escaped,
 * {@link Uri} is used to parse each URL
 * in compliance with RFC3986, in addition file URL paths are
 * converted to upper case for case insensitive file systems. The array of 
 * RFC3986 normalised Uris along with the current threads context ClassLoader
 * is used to locate the correct ClassLoader.  After normalisation is complete,
 * each URL is parsed with the <code>URL(String)</code> constructor; this could 
 * result in a {@link MalformedURLException}.  This path of URLs is the
 * <i>codebase URL path</i> for the invocation.
 *
 * <li>A class loader known as the <i>codebase loader</i> is chosen
 * based on <code>codebase</code> and the current thread's context
 * class loader as follows.  If <code>codebase</code> is
 * <code>null</code>, then the codebase loader is the current thread's
 * context class loader.  Otherwise, for each non-<code>null</code>
 * loader starting with the current thread's context class loader and
 * continuing with each successive parent class loader, if the
 * codebase Uri RFC3986 normalised path is equal to the loader's annotation
 * Uri RFC3986 normalised path, then the codebase loader is that loader.  
 * If no such matching loader is found, then the codebase loader is the loader 
 * in this <code>PreferredClassProvider</code>'s internal table with the
 * codebase Uri RFC3986 normalised path as the key's path of URLs and the current
 * thread's context class loader as the key's parent class loader.  If
 * no such entry exists in the table, then one is created by invoking
 * {@link #createClassLoader createClassLoader} with the codebase URL
 * path, the current thread's context class loader, and the
 * <code>boolean</code> <code>requireDlPerm</code> value that this
 * <code>PreferredClassProvider</code> was constructed with; the
 * created loader is added to the table, and it is chosen as the
 * codebase loader.
 *
 * <li>The current security context has <i>permission to access the
 * codebase loader</i> if it has the appropriate permission for each
 * of the URLs in the codebase loader's annotation URL path, where the
 * appropriate permission for a URL is defined as follows.  If the
 * result of invoking {@link URL#openConnection
 * openConnection()}.{@link URLConnection#getPermission
 * getPermission()} on the <code>URL</code> object is not a {@link
 * FilePermission} or if it is a <code>FilePermission</code> whose
 * name does not contain a directory separator, then that permission
 * is the appropriate permission.  If it is a
 * <code>FilePermission</code> whose name contains a directory
 * separator, then the appropriate permission is a
 * <code>FilePermission</code> with action <code>"read"</code> and the
 * same name except with the last path segment replaced with
 * <code>"-"</code> (that is, permission to read all files in the same
 * directory and all subdirectories).
 *
 * </ul>
 *
 * <p>When <code>PreferredClassProvider</code> attempts to load a
 * class (or interface) named <code><i>N</i></code> using class loader
 * <code><i>L</i></code>, it does so in a manner equivalent to
 * evaluating the following expression:
 *
 * <pre>
 * 	Class.forName(<code><i>N</i></code>, false, <code><i>L</i></code>)
 * </pre>
 *
 * In particular, the case of <code><i>N</i></code> being the binary
 * name of an array class is supported.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 *
 * @org.apache.river.impl
 *
 * <p>This implementation uses the {@link Logger} named
 * <code>net.jini.loader.pref.PreferredClassProvider</code> to log
 * information at the following levels:
 *
 * <table summary="Describes what is logged by PreferredClassProvider
 * at various logging levels" border=1 cellpadding=5>
 *
 * <tr> <th> Level <th> Description
 *
 * <tr> <td> {@link Levels#FAILED FAILED} <td> class loading failures
 *
 * <tr> <td> {@link Levels#HANDLED HANDLED} <td> exceptions caught
 * during class loading operations
 *
 * <tr> <td> {@link Level#FINE FINE} <td> invocations of {@link
 * #loadClass loadClass} and {@link #loadProxyClass loadProxyClass}
 *
 * <tr> <td> {@link Level#FINEST FINEST} <td> detailed activity of
 * <code>loadClass</code> and <code>loadProxyClass</code>
 * implementations
 *
 * </table>
 **/
public class PreferredClassProvider extends RMIClassLoaderSpi {
    
    /**
     * value of "net.jini.loader.codebaseAnnotation" property, as cached at class
     * initialization time.  It may contain malformed URLs.
     */
    private final static boolean uri;
    static {
        String codebaseAnnotationProperty = null;
	String prop = AccessController.doPrivileged(
           new GetPropertyAction("net.jini.loader.codebaseAnnotation"));
	if (prop != null && prop.trim().length() > 0) {
	    codebaseAnnotationProperty = prop;
	}
        if (codebaseAnnotationProperty == null) uri = true;
        else uri = !"URL".equalsIgnoreCase(codebaseAnnotationProperty);
    }

    /** encodings for primitive array class element types */
    private static final String PRIMITIVE_TYPES = "BCDFIJSZ";

    /** download from codebases with no dl perm allowed? */
    private final boolean requireDlPerm;

    /** true if constructor has completed successfully */
    private final boolean initialized;

    /** provider logger */
    private static final Logger logger =
	Logger.getLogger("net.jini.loader.pref.PreferredClassProvider");

    private static final Permission getClassLoaderPermission =
	new RuntimePermission("getClassLoader");
    
    /**
     * value of "java.rmi.server.codebase" property, as cached at class
     * initialization time.  It may contain malformed URLs.
     */
    private static String codebaseProperty;
    static {
	String prop = AccessController.doPrivileged(
   new GetPropertyAction("java.rmi.server.codebase"));
	if (prop != null && prop.trim().length() > 0) {
	    codebaseProperty = prop;
	}
    }

    /** table of "local" class loaders */
    private static final Set<ClassLoader> localLoaders;
    static {
        Set<Referrer<ClassLoader>> internal = Collections.newSetFromMap(new ConcurrentHashMap<Referrer<ClassLoader>,Boolean>());
        localLoaders = RC.set(internal, Ref.WEAK_IDENTITY, 10000L);
	AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
	    @Override
	    public ClassLoader run() {
		for (ClassLoader loader = ClassLoader.getSystemClassLoader();
		     loader != null;
		     loader = loader.getParent())
		{
		    localLoaders.add(loader);
		}
		return null;
	    }
	});
    }
    /**
     * table mapping codebase Uri path and context class loader pairs
     * to class loader instances.  Entries hold class loaders with weak
     * references, so this table does not prevent loaders from being
     * garbage collected.  This has been changed to a static table, since
     * all PreferredClassProvider instances can share the same cache, all
     * ClassLoaders are unique in the jvm, this prevents accidental duplication.
     */
    private final static ConcurrentMap<LoaderKey,ClassLoader> loaderTable;
    
    /**
     * URL cache is time based, we need this to be as fast as possible,
     * every remote class to be loaded is annotated.  Tuning may be required.
     */
    private final static ConcurrentMap<List<Uri>,URL[]> urlCache;
    private final static ConcurrentMap<String,Uri[]> uriCache;
    
    
    static {
        ConcurrentMap<Referrer<List<Uri>>,Referrer<URL[]>> intern =
                new ConcurrentHashMap<Referrer<List<Uri>>,Referrer<URL[]>>();
        urlCache = RC.concurrentMap(intern, Ref.TIME, Ref.STRONG, 10000L, 10000L);
        ConcurrentMap<Referrer<String>,Referrer<Uri[]>> intern1 =
                new ConcurrentHashMap<Referrer<String>,Referrer<Uri[]>>();
        uriCache = RC.concurrentMap(intern1, Ref.TIME, Ref.STRONG, 10000L, 10000L);
                ConcurrentMap<Referrer<LoaderKey>,Referrer<ClassLoader>> internal =
                new ConcurrentHashMap<Referrer<LoaderKey>,Referrer<ClassLoader>>();
        loaderTable = RC.concurrentMap(internal, Ref.STRONG, Ref.WEAK_IDENTITY, 5000L, 5000L);
    }
    
    /**
     * Creates a new <code>PreferredClassProvider</code>.
     *
     * <p>This constructor is used by the {@link RMIClassLoader}
     * service provider location mechanism when
     * <code>PreferredClassProvider</code> is configured as the
     * <code>RMIClassLoader</code> provider class.
     *
     * <p>If there is a security manager, its {@link
     * SecurityManager#checkCreateClassLoader checkCreateClassLoader}
     * method is invoked; this could result in a
     * <code>SecurityException</code>.
     *
     * <p>{@link DownloadPermission} is not enforced by the created
     * provider.
     *
     * @throws SecurityException if there is a security manager and
     * the invocation of its <code>checkCreateClassLoader</code>
     * method fails
     **/
    public PreferredClassProvider() {
	this(false);
    }

    /**
     * Creates a new <code>PreferredClassProvider</code>.
     *
     * <p>This constructor is used by subclasses to control whether
     * or not {@link DownloadPermission} is enforced.
     *
     * <p>If there is a security manager, its {@link
     * SecurityManager#checkCreateClassLoader checkCreateClassLoader}
     * method is invoked; this could result in a
     * <code>SecurityException</code>.
     *
     * @param requireDlPerm if <code>true</code>, the class loaders
     * created by the provider will only define classes with a {@link
     * CodeSource} that is granted {@link DownloadPermission}
     *
     * @throws SecurityException if there is a security manager and
     * the invocation of its <code>checkCreateClassLoader</code>
     * method fails
     **/
    protected PreferredClassProvider(boolean requireDlPerm) {
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkCreateClassLoader();
	}
	this.requireDlPerm = requireDlPerm;
        ConcurrentMap<Referrer<ClassLoader>,Referrer<Collection<Permission>>> inter =
                new ConcurrentHashMap<Referrer<ClassLoader>,Referrer<Collection<Permission>>>();
        classLoaderPerms = RC.concurrentMap(inter, Ref.WEAK_IDENTITY, Ref.STRONG, 5000L, 5000L);
	initialized = true;
    }

    private void checkInitialized() {
	if (!initialized) {
	    throw new SecurityException("uninitialized instance");
	}
    }

    /**
     * Map to hold permissions needed to check the URLs of
     * URLClassLoader objects.
     */
    private final ConcurrentMap<ClassLoader,Collection<Permission>> classLoaderPerms ;
    
    /*
     * Check permissions to load from the specified loader.  The
     * supplied URLs must be the loader's annotation URLs unless the
     * loader is identical to the "parent" argument-- this method
     * checks for permission to access those URLs.
     *
     * If the specified loader is identical to the "parent" argument,
     * no permissions are checked, because the caller could have used
     * an empty codebase to achieve the same effect anyway.
     */
    private void checkLoader(ClassLoader loader, ClassLoader parent, Uri[] uris,
			     URL[] urls)
    {
	SecurityManager sm = System.getSecurityManager();

	if ((sm != null) && (loader != null) && (loader != parent)) {
	    assert urlsMatchLoaderAnnotation(uris, loader);

	    if (loader.getClass() == PreferredClassLoader.class) {
		((PreferredClassLoader) loader).checkPermissions();
		
	    } else {
		Collection<Permission> perms = classLoaderPerms.get(loader);
                if (perms == null) {
		    PermissionCollection permiss = new Permissions();
                    // long operation so we don't want to synchronize here.
                    PreferredClassLoader.addPermissionsForURLs(
                        urls, permiss, false);
		    perms = new LinkedList<Permission>();
		    Enumeration<Permission> en = permiss.elements();
		    while(en.hasMoreElements()){
			perms.add(en.nextElement());
		    }
                    classLoaderPerms.putIfAbsent(loader, perms);// doesn't matter if they existed.
                }
                Iterator<Permission> it = perms.iterator();
                while (it.hasNext()) {
                    sm.checkPermission(it.next());
                }
	    }
	}
    }

    /**
     * Provides the implementation for {@link
     * RMIClassLoaderSpi#loadClass(String,String,ClassLoader)}.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>If <code>name</code> is the binary name of an array class
     * (of one or more dimensions) with a primitive element type, this
     * method returns the <code>Class</code> for that array class.
     *
     * <p>Otherwise, if <code>defaultLoader</code> is not
     * <code>null</code> and any of the following conditions are true:
     *
     * <ul>
     *
     * <li>There is no security manager.
     *
     * <li>The codebase loader is not the current thread's context
     * class loader and the current security context does not have
     * permission to access the codebase loader.
     *
     * <li><code>codebase</code> is <code>null</code>.
     *
     * <li>The specified codebase URL path is equal to the annotation
     * URL path of <code>defaultLoader</code>.
     *
     * <li>The codebase loader is not an instance of {@link
     * PreferredClassLoader}.
     *
     * <li>The codebase loader is an instance of
     * <code>PreferredClassLoader</code> and an invocation of {@link
     * PreferredClassLoader#isPreferredResource isPreferredResource}
     * on the codebase loader with the class name described below as
     * the first argument and <code>true</code> as the second argument
     * returns <code>false</code>.  If <code>name</code> is the binary
     * name of an array class (of one or more dimensions) with a
     * element type that is a reference type, the class name passed to
     * <code>isPreferredResource</code> is the binary name of that
     * element type; otherwise, the class name passed to
     * <code>isPreferredResource</code> is <code>name</code>.  This
     * invocation is only done if none of the previous conditions are
     * true.  If <code>isPreferredResource</code> throws an
     * <code>IOException</code>, this method throws a
     * <code>ClassNotFoundException</code>.
     *
     * </ul>
     *
     * then this method attempts to load the class with the specified
     * name using <code>defaultLoader</code>.  If this attempt
     * succeeds, this method returns the resulting <code>Class</code>;
     * if it throws a <code>ClassNotFoundException</code>, this method
     * proceeds as follows.
     *
     * <p>Otherwise, this method attempts to load the class with the
     * specified name using the codebase loader, if there is a
     * security manager and the current security context has
     * permission to access the codebase loader, or using the current
     * thread's context class loader otherwise.  If this attempt
     * succeeds, this method returns the resulting <code>Class</code>;
     * if it throws a <code>ClassNotFoundException</code>, this method
     * throws a <code>ClassNotFoundException</code>.
     *
     * @param codebase the codebase URL path as a space-separated list
     * of URLs, or <code>null</code>
     *
     * @param name the binary name of the class to load
     *
     * @param defaultLoader additional contextual class loader
     * to use, or <code>null</code>
     *
     * @return the <code>Class</code> object representing the loaded class
     *
     * @throws MalformedURLException if <code>codebase</code> is
     * non-<code>null</code> and contains an invalid URL
     *
     * @throws ClassNotFoundException if a definition for the class
     * could not be loaded
     **/
    @Override
    public Class loadClass(String codebase,
			   String name,
			   ClassLoader defaultLoader)
	throws MalformedURLException, ClassNotFoundException
    {
	checkInitialized();
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "name=\"{0}\", codebase={1}, defaultLoader={2}",
		       new Object[] {
			   name,
			   codebase != null ? "\"" + codebase + "\"" : null,
			   defaultLoader
		       });
	}
        
        // throws MalformedURLException
    	Uri[] codebaseURIs = pathToURIs(codebase);	// may be null
        URL[] codebaseURLs = asURL(codebaseURIs); // throws MalformedURLException

	/*
	 * Process array class names.
	 */
	String elementTypeName = null;
	int len = name.length();
	if (len > 0 && name.charAt(0) == '[') {
	    int i = 1;
	    char c = 0;
	    while (i < len && (c = name.charAt(i)) == '[') {
		i++;
	    }
	    if (len == i + 1 && PRIMITIVE_TYPES.indexOf(c) != -1) {
		return Class.forName(name);
	    }
	    if (len > i + 2 && c == 'L' && name.charAt(len - 1) == ';') {
		elementTypeName = name.substring(i + 1, len - 1);
	    }
	}

	/*
	 * Try defaultLoader cases that don't require determining the
	 * codebase loader.
	 */
	SecurityManager sm = System.getSecurityManager();
	if (defaultLoader != null &&
	    (sm == null || codebaseURIs == null ||
	     urlsMatchLoaderAnnotation(codebaseURIs, defaultLoader)))
	{
	    try {
		Class c = LoadClass.forName(name, false, defaultLoader);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "class \"{0}\" found " +
			"via defaultLoader, defined by {1}",
			new Object[] { name, getClassLoader(c) });
		}
		return c;
	    } catch (ClassNotFoundException e) {
		defaultLoader = null;	// don't try defaultLoader again
	    }
	}

	/*
	 * Determine the codebase loader.
	 */
	ClassLoader contextLoader = getRMIContextClassLoader();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "(thread context class loader: {0})", contextLoader);
	}
	ClassLoader codebaseLoader;
        codebaseLoader = lookupLoader(codebaseURIs, codebaseURLs, contextLoader);
        

	/*
	 * Try remaining defaultLoader cases that don't require
	 * checking permission to access the codebase loader.
	 */
	if (defaultLoader != null &&
	    !(codebaseLoader instanceof PreferredClassLoader))
	{
	    try {
		Class c = LoadClass.forName(name, false, defaultLoader);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "class \"{0}\" found " +
			"via defaultLoader, defined by {1}",
			new Object[] { name, getClassLoader(c) });
		}
		return c;
	    } catch (ClassNotFoundException e) {
		defaultLoader = null;	// don't try defaultLoader again
	    }
	}

	/*
	 * Check permission to access the codebase loader.
	 */
	SecurityException secEx = null;
	if (sm != null) {
	    try {
		checkLoader(codebaseLoader, contextLoader, codebaseURIs, codebaseURLs);
	    } catch (SecurityException e) {
		secEx = e;
	    }
	}

	/*
	 * Try remaining defaultLoader cases.
	 */
	if (defaultLoader != null) {
	    boolean tryDL = secEx != null;
	    if (!tryDL) {
		try {
		    tryDL = !((PreferredClassLoader) codebaseLoader).
			isPreferredResource(elementTypeName != null ?
					    elementTypeName : name, true);
		} catch (IOException e) {
		    ClassNotFoundException cnfe =
			new ClassNotFoundException(name +
			    " (could not determine preferred setting;" +
			    " original codebase: \"" + codebase + "\")", e);
		    if (logger.isLoggable(Levels.FAILED)) {
			LogUtil.logThrow(logger, Levels.FAILED,
			    PreferredClassProvider.class, "loadClass",
			    "class \"{0}\" not found, " +
			    "could not obtain preferred value",
			    new Object[] { name }, cnfe);
		    }
		    throw cnfe;
		}
	    }
	    if (tryDL) {
		try {
		    Class c = LoadClass.forName(name, false, defaultLoader);
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, "class \"{0}\" found " +
			    "via defaultLoader, defined by {1}",
			    new Object[] { name, getClassLoader(c) });
		    }
		    return c;
		} catch (ClassNotFoundException e) {
		}
	    }
	}

	/*
	 * Done with defaultLoader, try the codebase loader or the
	 * context class loader as appropriate.
	 */
	try {
	    Class c = LoadClass.forName(name, false,
				    (sm != null && secEx == null ?
				     codebaseLoader : contextLoader));
	    if (logger.isLoggable(Level.FINEST)) {
		String message;
		if (sm == null) {
		    message = "class \"{0}\" found " +
			" via thread context class loader " +
			" (no security manager), defined by {1}";
		} else if (secEx != null) {
		    message = "class \"{0}\" found " +
			" via thread context class loader " +
			" (access to codebase loader denied), defined by {1}";
		} else {
		    message = "class \"{0}\" found " +
			"via codebase loader, defined by {1}";
		}
		logger.log(Level.FINEST, message,
			   new Object[] { name, getClassLoader(c) });
	    }
	    return c;
	} catch (ClassNotFoundException e) {
	    if (sm == null) {
		ClassNotFoundException cnfe =
		    new ClassNotFoundException(e.getMessage() + 
			" (no security manager: codebase loader disabled)", e);
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			PreferredClassProvider.class, "loadClass",
			"class \"{0}\" not found " +
			"via thread context class loader " +
			"(no security manager)",
			new Object[] { name }, cnfe);
		}
		throw cnfe;
	    } else if (secEx != null) {
		/*
		 * Presumably the original security exception is
		 * preferable to throw, but log both exceptions.
		 */
		if (logger.isLoggable(Levels.HANDLED)) {
		    LogUtil.logThrow(logger, Levels.HANDLED,
			PreferredClassProvider.class, "loadClass",
			"class \"{0}\" not found " +
			"via thread context class loader " +
			"(access to codebase loader denied)",
			new Object[] { name }, e);
		}
		ClassNotFoundException cnfe =
		    new ClassNotFoundException(e.getMessage() +
			" (access to codebase loader denied)", secEx);
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			PreferredClassProvider.class, "loadClass",
			"class \"{0}\" not found " +
			"via thread context class loader " +
			"(access to codebase loader denied)",
			new Object[] { name }, cnfe);
		}
		throw cnfe;
	    } else {
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			PreferredClassProvider.class, "loadClass",
			"class \"{0}\" not found via codebase loader",
			new Object[] { name }, e);
		}
		throw e;
	    }
	}
    }

    /**
     * Provides the implementation for {@link
     * RMIClassLoaderSpi#getClassAnnotation(Class)}.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>If <code>cl</code> is an array class (of one or more
     * dimensions) with a primitive element type, this method returns
     * <code>null</code>.
     *
     * <p>Otherwise, this method returns the annotation string for the
     * defining class loader of <code>cl</code>, except that if the
     * annotation string would be determined by an invocation of
     * {@link URLClassLoader#getURLs URLClassLoader.getURLs} on that
     * loader and the current security context does not have the
     * permissions necessary to connect to each URL returned by that
     * invocation (where the permission to connect to a URL is
     * determined by invoking {@link URL#openConnection
     * openConnection()}.{@link URLConnection#getPermission
     * getPermission()} on the <code>URL</code> object), this method
     * returns the result of invoking {@link
     * #getClassAnnotation(ClassLoader)
     * getClassAnnotation(ClassLoader)} with the loader instead.
     *
     * @param cl the class to obtain the annotation string for
     *
     * @return the annotation string for the class, or
     * <code>null</code>
     **/
    @Override
    public String getClassAnnotation(Class cl) {
	checkInitialized();
	String name = cl.getName();

	/*
	 * Class objects for arrays of primitive types never need an
	 * annotation, because they never need to be (or can be) downloaded.
	 *
	 * REMIND: should we (not) be annotating classes that are in
	 * "java.*" packages?
	 */
	int nameLength = name.length();
	if (nameLength > 0 && name.charAt(0) == '[') {
	    // skip past all '[' characters (see bugid 4211906)
	    int i = 1;
	    while (nameLength > i && name.charAt(i) == '[') {
		i++;
	    }
	    if (nameLength > i && name.charAt(i) != 'L') {
		return null;
	    }
	}

	return getLoaderAnnotation(getClassLoader(cl), true);
    }

    /**
     * Returns the annotation string for the specified class loader.
     *
     * <p>This method is invoked in order to determine the annotation
     * string for the system class loader, an ancestor of the system
     * class loader, any class loader that is not an instance of
     * {@link ClassAnnotation} or {@link URLClassLoader}, or (for an
     * invocation of {@link #getClassAnnotation(Class)
     * getClassAnnotation(Class)}) a <code>URLClassLoader</code> for
     * which the current security context does not have the
     * permissions necessary to connect to all of its URLs.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>This method returns the value of the system property
     * <code>"java.rmi.server.codebase"</code> (or possibly an earlier
     * cached value).
     *
     * @param loader the class loader to obtain the annotation string
     * for
     *
     * @return the annotation string for the class loader, or
     * <code>null</code>
     **/
    protected String getClassAnnotation(ClassLoader loader) {
	checkInitialized();
	return codebaseProperty;
    }

    /**
     * Returns the annotation string for the specified class loader
     * (possibly null).  If check is true and the annotation would be
     * determined from an invocation of URLClassLoader.getURLs() on
     * the loader, only return the true annotation if the current
     * security context has permission to connect to all of the URLs.
     **/
    private String getLoaderAnnotation(ClassLoader loader, boolean check) {
	
	if (isLocalLoader(loader)) {
	    return getClassAnnotation(loader);
	}

        /*
	 * Get the codebase URL path for the class loader, if it supports
	 * such a notion (i.e., if it is a URLClassLoader or subclass).
	 */
	String annotation = null;
	if (loader instanceof ClassAnnotation) {
	    /*
	     * If the class loader is one of our RMI class loaders, we have
	     * already computed the class annotation string, and no
	     * permissions are required to know the URLs.
	     */
	    annotation = ((ClassAnnotation) loader).getClassAnnotation();

	} else if (loader instanceof java.net.URLClassLoader) {
	    try {
		URL[] urls = ((java.net.URLClassLoader) loader).getURLs();
		if (urls != null) {
		    if (check) {
			SecurityManager sm = System.getSecurityManager();
			if (sm != null) {
			    Permissions perms = new Permissions();
			    for (int i = 0, l = urls.length; i < l; i++) {
				Permission p =
				    urls[i].openConnection().getPermission();
				if (p != null) {
				    if (!perms.implies(p)) {
					sm.checkPermission(p);
					perms.add(p);
				    }
				}
			    }
			}
		    }
		    annotation = PreferredClassLoader.urlsToPath(urls);
		}
	    } catch (SecurityException e) {
		/*
		 * If access was denied to the knowledge of the class
		 * loader's URLs, fall back to the default behavior.
		 */
                logger.log(
                    Level.FINE,
                    "Access denied to knowledge of class loader's URL's",
                    e
                );
	    } catch (IOException e) {
		/*
		 * This shouldn't happen, although it is declared to be
		 * thrown by openConnection() and getPermission().  If it
		 * does happen, forget about this class loader's URLs and
		 * fall back to the default behavior.
		 */
                logger.log(
                    Level.FINE, 
                    "IOException thrown while attempting to access class loader's URL's",
                    e
                );
	    }
	}

	if (annotation != null) {
	    return annotation;
	} else {
	    return getClassAnnotation(loader);
	}
    }

    /**
     * Return true if the given loader is the system class loader or
     * its parent (i.e. the loader for installed extensions) or the null
     * class loader
     */
    private static boolean isLocalLoader(ClassLoader loader) {
	return (loader == null || localLoaders.contains(loader));
    }
    
    /**
     * Provides the implementation for {@link
     * RMIClassLoaderSpi#getClassLoader(String)}.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>If there is a security manager, its
     * <code>checkPermission</code> method is invoked with a
     * <code>RuntimePermission("getClassLoader")</code> permission;
     * this could result in a <code>SecurityException</code>.  Also,
     * if there is a security manager, the codebase loader is not the
     * current thread's context class loader, and the current security
     * context does not have permission to access the codebase loader,
     * this method throws a <code>SecurityException</code>.
     *
     * <p>This method returns the codebase loader if there is a
     * security manager, or the current thread's context class loader
     * otherwise.
     *
     * @param codebase the codebase URL path as a space-separated list
     * of URLs, or <code>null</code>
     *
     * @return a class loader for the specified codebase URL path
     *
     * @throws MalformedURLException if <code>codebase</code> is
     * non-<code>null</code> and contains an invalid URL
     *
     * @throws SecurityException if there is a security manager and
     * the invocation of its <code>checkPermission</code> method
     * fails, or if the current security context does not have the
     * permissions necessary to connect to all of the URLs in the
     * codebase URL path
     **/
    @Override
    public ClassLoader getClassLoader(String codebase)
	throws MalformedURLException
    {
	checkInitialized();
        // throws MalformedURLException
    	Uri[] codebaseURIs = pathToURIs(codebase);	// may be null
        URL[] codebaseURLs = asURL(codebaseURIs); // throws MalformedURLException

	ClassLoader contextLoader = getRMIContextClassLoader();
	SecurityManager sm = System.getSecurityManager();
	if (sm != null) {
	    sm.checkPermission(getClassLoaderPermission);
	} else {
	    return contextLoader;
	}

	ClassLoader codebaseLoader;
        codebaseLoader = lookupLoader(codebaseURIs, codebaseURLs, contextLoader);
	checkLoader(codebaseLoader, contextLoader, codebaseURIs, codebaseURLs);
	return codebaseLoader;
    }

    /**
     * Provides the implementation of {@link
     * RMIClassLoaderSpi#loadProxyClass(String,String[],ClassLoader)}.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>If <code>defaultLoader</code> is not <code>null</code> and
     * any of the following conditions are true:
     *
     * <ul>
     *
     * <li>There is no security manager.
     *
     * <li>The codebase loader is not the current thread's context
     * class loader and the current security context does not have
     * permission to access the codebase loader.
     *
     * <li><code>codebase</code> is <code>null</code>.
     *
     * <li>The specified codebase URL path is equal to the annotation
     * URL path of <code>defaultLoader</code>.
     *
     * <li>The codebase loader is not an instance of {@link
     * PreferredClassLoader}.
     *
     * <li>The codebase loader is an instance of
     * <code>PreferredClassLoader</code> and an invocation of {@link
     * PreferredClassLoader#isPreferredResource isPreferredResource}
     * on the codebase loader for each element of
     * <code>interfaces</code>, with the element as the first argument
     * and <code>true</code> as the second argument, all return
     * <code>false</code>.  These invocations are only done if none of
     * the previous conditions are true.  If any invocation of
     * <code>isPreferredResource</code> throws an
     * <code>IOException</code>, this method throws a
     * <code>ClassNotFoundException</code>.
     *
     * </ul>
     *
     * then this method attempts to load all of the interfaces named
     * by the elements of <code>interfaces</code> using
     * <code>defaultLoader</code>.  If all of the interfaces are
     * loaded successfully, then
     *
     * <ul>
     *
     * <li>If all of the loaded interfaces are <code>public</code>: if
     * there is a security manager, the codebase loader is the current
     * thread's context class loader or the current security context
     * has permission to access the codebase loader, and the
     * annotation URL path for the codebase loader is not equal to the
     * annotation URL path for <code>defaultLoader</code>, this method
     * first attempts to get a dynamic proxy class (using {@link
     * Proxy#getProxyClass Proxy.getProxyClass}) that is defined by
     * the codebase loader and that implements all of the interfaces,
     * and if this attempt succeeds, this method returns the resulting
     * <code>Class</code>.  Otherwise, this method attempts to get a
     * dynamic proxy class that is defined by
     * <code>defaultLoader</code> and that implements all of the
     * interfaces.  If that attempt succeeds, this method returns the
     * resulting <code>Class</code>; if it throws an
     * <code>IllegalArgumentException</code>, this method throws a
     * <code>ClassNotFoundException</code>.
     *
     * <li>If all of the non-<code>public</code> interfaces are
     * defined by the same class loader: this method attempts to get a
     * dynamic proxy class that is defined by that loader and that
     * implements all of the interfaces.  If this attempt succeeds,
     * this method returns the resulting <code>Class</code>; if it
     * throws an <code>IllegalArgumentException</code>, this method
     * throws a <code>ClassNotFoundException</code>.
     *
     * <li>Otherwise (if there are two or more non-<code>public</code>
     * interfaces defined by different class loaders): this method
     * throws a <code>LinkageError</code>.
     *
     * </ul>
     *
     * If any of the attempts to load one of the interfaces throws a
     * <code>ClassNotFoundException</code>, this method proceeds as
     * follows.
     *
     * <p>Otherwise, this method attempts to load all of the
     * interfaces named by the elements of <code>interfaces</code>
     * using the codebase loader, if there is a security manager and
     * the current security context has permission to access the
     * codebase loader, or using the current thread's context class
     * loader otherwise.  If all of the interfaces are loaded
     * successfully, then
     *
     * <ul>
     *
     * <li>If all of the loaded interfaces are <code>public</code>:
     * this method attempts to get a dynamic proxy class that is
     * defined by the loader used to load the interfaces and that
     * implements all of the interfaces.  If this attempt succeeds,
     * this method returns the resulting <code>Class</code>; if it
     * throws an <code>IllegalArgumentException</code>, this method
     * throws a <code>ClassNotFoundException</code>.
     *
     * <li>If all of the non-<code>public</code> interfaces are
     * defined by the same class loader: this method attempts to get a
     * dynamic proxy class that is defined by that loader and that
     * implements all of the interfaces.  If this attempt succeeds,
     * this method returns the resulting <code>Class</code>; if it
     * throws an <code>IllegalArgumentException</code>, this method
     * throws a <code>ClassNotFoundException</code>.
     *
     * <li>Otherwise (if there are two or more non-<code>public</code>
     * interfaces defined by different class loaders): this method
     * throws a <code>LinkageError</code>.
     *
     * </ul>
     *
     * If any of the attempts to load one of the interfaces throws a
     * <code>ClassNotFoundException</code>, this method throws a
     * <code>ClassNotFoundException</code>.
     *
     * @param codebase the codebase URL path as a space-separated list
     * of URLs, or <code>null</code>
     *
     * @param interfaceNames the binary names of the interfaces for
     * the proxy class to implement
     *
     * @return a dynamic proxy class that implements the named
     * interfaces
     *
     * @param defaultLoader additional contextual class loader to use,
     * or <code>null</code>
     *
     * @throws MalformedURLException if <code>codebase</code> is
     * non-<code>null</code> and contains an invalid URL
     *
     * @throws ClassNotFoundException if a definition for one of the
     * named interfaces could not be loaded, or if creation of the
     * dynamic proxy class failed (such as if
     * <code>Proxy.getProxyClass</code> would throw an
     * <code>IllegalArgumentException</code> for the given interface
     * list)
     **/
    @Override
    public Class loadProxyClass(String codebase,
				String[] interfaceNames,
				ClassLoader defaultLoader)
	throws MalformedURLException, ClassNotFoundException
    {
	checkInitialized();
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		"interfaces={0}, codebase={1}, defaultLoader={2}",
		new Object[] {
		    Arrays.asList(interfaceNames),
		    codebase != null ? "\"" + codebase + "\"" : null,
		    defaultLoader
		});
	}
        // throws MalformedURLException containing URISyntaxException message
    	Uri[] codebaseURIs = pathToURIs(codebase);	// may be null
        URL[] codebaseURLs = asURL(codebaseURIs);

	/*
	 * Determine the codebase loader.
	 */
	ClassLoader contextLoader = getRMIContextClassLoader();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "(thread context class loader: {0})", contextLoader);
	}
	ClassLoader codebaseLoader;
        codebaseLoader = lookupLoader(codebaseURIs, codebaseURLs, contextLoader);

	/*
	 * Check permission to access the codebase loader.
	 */
	SecurityManager sm = System.getSecurityManager();
	SecurityException secEx = null;
	if (sm != null) {
	    try {
		checkLoader(codebaseLoader, contextLoader, codebaseURIs, codebaseURLs);
	    } catch (SecurityException e) {
		secEx = e;
	    }
	}

	/*
	 * Try all defaultLoader cases.
	 */
	if (defaultLoader != null) {
	    boolean codebaseMatchesDL = false;
	    boolean tryDL =
		sm == null || secEx != null ||
		codebaseURIs == null;
	    if (!tryDL) {
		codebaseMatchesDL =
		    urlsMatchLoaderAnnotation(codebaseURIs, defaultLoader);
		tryDL = codebaseMatchesDL ||
		    !(codebaseLoader instanceof PreferredClassLoader) ||
		    !interfacePreferred((PreferredClassLoader) codebaseLoader,
					interfaceNames, codebase);
		// throws ClassNotFoundException if IOException occurs
	    }
	    if (tryDL) {
		try {
		    boolean preferCodebaseLoader =
			sm != null && secEx == null && !codebaseMatchesDL;
		    Class c = loadProxyClass(interfaceNames,
					     defaultLoader, "defaultLoader",
					     codebaseLoader,
					     preferCodebaseLoader);
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST,
				   getProxySuccessLogMessage(sm, secEx),
				   getClassLoader(c));
		    }
		    return c;
		} catch (ClassNotFoundException e) {
		} catch (IllegalArgumentException e) {
		    ClassNotFoundException cnfe = new ClassNotFoundException(
			"dynamic proxy class creation failed", e);
		    if (logger.isLoggable(Levels.FAILED)) {
			logger.log(Levels.FAILED,
				   "dynamic proxy class creation failed", e);
		    }
		    throw cnfe;
		}
	    }
	}

	/*
	 * Try the codebase loader or the context class loader as
	 * appropriate.
	 */
	ClassLoader loader;
	String loaderName;
	if (sm != null && secEx == null) {
	    loader = codebaseLoader;
	    loaderName = "codebase loader";
	} else {
	    loader = contextLoader;
	    loaderName = "thread context class loader";
	}
	try {
	    Class c = loadProxyClass(interfaceNames, loader, loaderName,
				     null, false);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   getProxySuccessLogMessage(sm, secEx),
			   getClassLoader(c));
	    }
	    return c;
	} catch (ClassNotFoundException e) {
	    if (sm == null) {
		ClassNotFoundException cnfe =
		    new ClassNotFoundException(e.getMessage() +
			" (no security manager: codebase loader disabled)", e);
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED,
			"proxy class resolution failed (no security manager)",
			cnfe);
		}
		throw cnfe;
	    } else if (secEx != null) {
		/*
		 * Presumably the original security exception is
		 * preferable to throw, but log both exceptions.
		 */
		if (logger.isLoggable(Levels.HANDLED)) {
		    logger.log(Levels.HANDLED,
			"proxy class resolution failed " +
			"(access to codebase loader denied)", e);
		}
		ClassNotFoundException cnfe =
		    new ClassNotFoundException(e.getMessage() +
			" (access to codebase loader denied)", secEx);
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED,
			"proxy class resolution failed " +
			"(access to codebase loader denied)", cnfe);
		}
		throw cnfe;
	    } else {
		if (logger.isLoggable(Levels.FAILED)) {
		    logger.log(Levels.FAILED,
			       "proxy class resolution failed", e);
		}
		throw e;
	    }
	} catch (IllegalArgumentException e) {
	    ClassNotFoundException cnfe = new ClassNotFoundException(
		"dynamic proxy class creation failed", e);
	    if (logger.isLoggable(Levels.FAILED)) {
		logger.log(Levels.FAILED,
			   "dynamic proxy class creation failed", e);
	    }
	    throw cnfe;
	}
    }

    private static String getProxySuccessLogMessage(SecurityManager sm,
						    SecurityException secEx)
    {
	if (sm == null) {
	    return "(no security manager) proxy class defined by {0}";
	} else if (secEx != null) {
	    return
		"(access to codebase loader denied) " +
		"proxy class defined by {0}";
	} else {
	    return "proxy class defined by {0}";
	}
    }

    /**
     * Attempts to load the specified interfaces by name using the
     * specified loader, and if that is successful, attempts to get a
     * dynamic proxy class that implements those interfaces.
     *
     * If tryOtherLoaderFirst is true, attempts to get the proxy class
     * defined by the specified other loader first, and if that fails,
     * falls back to get the proxy class defined by the same loader
     * used to load the interfaces; otherwise, only attempts to get
     * the proxy class defined by the same loader used to load the
     * interfaces.
     *
     * Throws a ClassNotFoundException if attempting to load any of
     * the interfaces throws a ClassNotFoundException.  Throws
     * IllegalArgumentException if the final attempt to get a proxy
     * class throws an IllegalArgumentException.
     **/
    private Class loadProxyClass(String[] interfaceNames,
				 ClassLoader interfaceLoader,
				 String interfaceLoaderName,
				 ClassLoader otherLoader,
				 boolean tryOtherLoaderFirst)
	throws ClassNotFoundException
    {
	Class[] classObjs = new Class[interfaceNames.length];
	boolean[] nonpublic = { false };
	ClassLoader proxyLoader =
	    loadProxyInterfaces(interfaceNames, interfaceLoader,
				classObjs, nonpublic);

	if (logger.isLoggable(Level.FINEST)) {
	    ClassLoader[] definingLoaders = new ClassLoader[classObjs.length];
	    for (int i = 0; i < definingLoaders.length; i++) {
		definingLoaders[i] = getClassLoader(classObjs[i]);
	    }
	    logger.log(Level.FINEST,
		"proxy interfaces loaded via {0}, defined by {1}",
		new Object[] {
		    interfaceLoaderName, Arrays.asList(definingLoaders)
		});
	}

	if (!nonpublic[0]) {
	    if (tryOtherLoaderFirst) {
		try {
		    return Proxy.getProxyClass(otherLoader, classObjs);
		} catch (IllegalArgumentException e) {
		}
	    }
	    proxyLoader = interfaceLoader;
	}

	return Proxy.getProxyClass(proxyLoader, classObjs);
    }

    /**
     * Returns true if at least one of the specified interface names
     * is preferred for the specified class loader; returns false if
     * none of them are preferred.  Throws ClassNotFoundException if
     * PreferredClassLoader.isPreferredResource throws IOException
     * (although isPreferredResource isn't necessarily invoked for all
     * of the specified names, because this method short circuits on
     * the first invocation that returns true).  The codebase argument
     * is the original codebase URL path passed to loadProxyClass,
     * which typically but not necessarily equals the loader's import
     * URL path.
     **/
    private boolean interfacePreferred(PreferredClassLoader codebaseLoader,
				       String[] interfaceNames,
				       String codebase)
	throws ClassNotFoundException
    {
	for (int p = 0, l = interfaceNames.length; p < l; p++) {
	    try {
		if (((PreferredClassLoader) codebaseLoader).
		    isPreferredResource(interfaceNames[p], true))
		{
		    return true;
		}
	    } catch (IOException e) {
		ClassNotFoundException cnfe =
		    new ClassNotFoundException(interfaceNames[p] +
			" (could not determine preferred setting;" +
			" original codebase: \"" + codebase + "\")", e);
		if (logger.isLoggable(Levels.FAILED)) {
		    LogUtil.logThrow(logger, Levels.FAILED,
			PreferredClassProvider.class, "loadProxyClass",
			"class \"{0}\" not found, " +
			"could not obtain preferred value",
			new Object[] { interfaceNames[p] }, cnfe);
		}
		throw cnfe;
	    }
	}
	return false;
    }

    /**
     * Returns an array of URLs corresponding to the annotation string
     * for the specified class loader, or null if the annotation
     * string is null.
     **/
    private Uri[] getLoaderAnnotationURIs(ClassLoader loader)
	throws MalformedURLException
    {
	return pathToURIs(getLoaderAnnotation(loader, false));
    }

    /**
     * Returns true if the specified path of URLs is equal to the
     * annotation URLs of the specified loader, and false otherwise.
     **/
    private boolean urlsMatchLoaderAnnotation(Uri[] urls, ClassLoader loader) {
	try {
	    return Arrays.equals(urls, getLoaderAnnotationURIs(loader));
	} catch (MalformedURLException e) {
	    return false;
	}
    }
    
    /*
     * Load Class objects for the names in the interfaces array from
     * the given class loader.
     *
     * We pass classObjs and useNonpublic array to avoid needing a
     * multi-element return value. useNonpublic is an array to enable
     * the method to take a boolean argument by reference.
     *
     * useNonpublic array is needed to signal when the return value of
     * this method should be used as the proxy class loader.  Because
     * null represents a valid class loader, that value is
     * insufficient to signal that the return value should not be used
     * as the proxy class loader.
     */
    private ClassLoader loadProxyInterfaces(String[] interfaces,
					    ClassLoader loader,
					    Class[] classObjs,
					    boolean[] useNonpublic)

	throws ClassNotFoundException
    {
	/* loader of a non-public interface class */
	ClassLoader nonpublic = null;

	for (int i = 0; i < interfaces.length; i++) {
	    Class cl =
		(classObjs[i] = LoadClass.forName(interfaces[i], false, loader));
		
	    if (!Modifier.isPublic(cl.getModifiers())) {
		ClassLoader current = getClassLoader(cl);
		if (logger.isLoggable(Level.FINEST)) {
		    logger.logp(Level.FINEST,
			PreferredClassProvider.class.getName(),
			"loadProxyClass",
			"non-public interface \"{0}\" defined by {1}",
			new Object[] { interfaces[i], current });
		}
		if (!useNonpublic[0]) {
		    nonpublic = current;
		    useNonpublic[0] = true;
		} else if (current != nonpublic) {
		    throw new IllegalAccessError(
			"non-public interfaces defined in different " +
			"class loaders");
		}
	    }
	}
	return nonpublic;
    }

    /**
     * Convert a string containing a space-separated list of URL Strings into a
     * corresponding array of Uri objects, throwing a MalformedURLException
     * if any of the URLs are invalid.  This method returns null if the
     * specified string is null.
     *
     * @param path the string path to be converted to an array of urls
     * @return the string path converted to an array of URLs, or null
     * @throws MalformedURLException if the string path of urls contains a
     *         mal-formed url which can not be converted into a url object.
     */
    private static Uri[] pathToURIs(String path) throws MalformedURLException {
	if (path == null) {
	    return null;
	}
        Uri[] urls = uriCache.get(path); // Cache of previously converted strings.
        if (urls != null) return urls;
	StringTokenizer st = new StringTokenizer(path);	// divide by spaces
	urls = new Uri[st.countTokens()];
	for (int i = 0; st.hasMoreTokens(); i++) {
            try {
                String ur = st.nextToken();
                ur = Uri.fixWindowsURI(ur);
                urls[i] = Uri.parseAndCreate(ur);
            } catch (URISyntaxException ex) {
                throw new MalformedURLException("URL's must be RFC 3986 Compliant: " 
                        + ex.getMessage());
            }
	}
        Uri [] existed = uriCache.putIfAbsent(path, urls);
        if (existed != null) urls = existed;
	return urls;
    }
    
    /** Converts Uri[] to URL[].
     */
    private URL[] asURL(Uri[] uris) throws MalformedURLException{
        if (uris == null) return null;
        List<Uri> uriList = Arrays.asList(uris);
        URL [] result = urlCache.get(uriList);
        if ( result != null) return result;
        try {
            int l = uris.length;
            URL[] urls = new URL[l];
            for (int i = 0; i < l; i++ ){
                try {
                    urls[i] = uris[i] == null ? null : uris[i].toURL(); // throws MalformedURLException
                } catch (MalformedURLException e){
                    System.err.println("MalformedURLException: " + e + "was thrown ," + uris[i]);
                    throw e;
                }
            }
            URL [] existed = urlCache.putIfAbsent(uriList,urls);
            if (existed != null) urls = existed;
            return urls;
        } catch (IllegalArgumentException ex){
            throw new MalformedURLException(ex.getMessage());
        }
    }

    /**
     * Return the class loader to be used as the parent for an RMI class
     * loader used in the current execution context.
     */
    private static ClassLoader getRMIContextClassLoader() {
	/*
	 * The current implementation simply uses the current thread's
	 * context class loader.
	 */
	return AccessController.doPrivileged(
            new PrivilegedAction<ClassLoader>() {
	      @Override
              public ClassLoader run() {
                  return Thread.currentThread().getContextClassLoader();
              }
            }
        );
    }

    /**
     * Return the origin class loader for the <code>pathURLs</code> or
     * null if the origin loader was not present in the delegation
     * hierarchy.
     *
     * Preferred classes introduces the "class boomerang" problem to
     * RMI class loading.  A class boomerang occurs when a class which
     * is marked preferred is accessible from the codebase of a
     * virtual machine (VM) and is loaded by that VM.  Since the VM
     * has a copy of the class in its own resources and the class is
     * "returning to its origin" the class should not be preferred.
     * If the class is preferred, it will be loaded in a class loader
     * for the local codebase annotation.  As a result, the class'
     * type will not be compatible with types defined from the local
     * definition of the class file in the relevant VM.
     *
     * A boomerang can also occur when a new child loader for a URL
     * path is created but an ancestor of the new class loader has the
     * same URL path as the new class loader.  In such cases the new
     * class loader should not be created.  The incoming class should
     * be loaded from the origin ancestor instead.
     *
     * A simple example of a class boomerang occurs when when a VM
     * makes a remote method call to itself.  Suppose an object whose
     * class was loaded locally in that VM and is preferred in the
     * codebase of the VM is passed in the call.  When the VM receives
     * its own call, an instance of the unmarshalled parameter will
     * not be assignable to instances that were defined by local
     * classes (i.e. never unmarshalled).
     *
     * In order to work around class boomerang problems, the preferred
     * class provider lookupLoader algorithm is different from the
     * analogous algorithm in LoaderHandler.  To avoid boomerangs, the
     * lookupLoader method of this class attempts to locate the
     * "origin" class loader of an incoming class in a remote method
     * call.  Loading classes from their origin loader instead of in a
     * preferred circumvents type compatibility conflicts.
     *
     * To find origin loaders, the lookupLoader method calls
     * findOriginLoader() before locating or creating new
     * PreferredClassLoaders.  An origin loader is found by searching
     * up the delegation hierarchy above the parent (context) class
     * loader for a loader that has an export path which matches the
     * parameter path.
     */
    private ClassLoader findOriginLoader(final Uri[] pathURLs,
					 final ClassLoader parent)
    {
	return AccessController.doPrivileged(
            new PrivilegedAction<ClassLoader>() {
	      @Override
              public ClassLoader run() {
                  return findOriginLoader0(pathURLs, parent);
              }
            }
        );
    }

    private ClassLoader findOriginLoader0(Uri[] pathURLs, ClassLoader parent) {
	for (ClassLoader ancestor = parent;
	     ancestor != null;
	     ancestor = ancestor.getParent())
	{
	    Uri[] ancestorURLs;
	    try {
		ancestorURLs = getLoaderAnnotationURIs(ancestor);
	    } catch (MalformedURLException e) {
		// this ancestor's annotation must not match pathURLs
		continue;
	    }

	    /* check if found a matching ancestor loader */
	    if (Arrays.equals(pathURLs, ancestorURLs)) {
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			"using an existing ancestor class loader " +
			"which serves the requested codebase urls: {0}, " +
			"urls: {1}",
			new Object[] {
			    ancestor,
			    (ancestorURLs != null ?
			     Arrays.asList(ancestorURLs) : null)
			});
		}

		return ancestor;
	    }
	}
	return null;
    }

    /**
     * Look up the class loader for the given codebase URL path
     * and the given parent class loader.  A new class loader instance
     * will be created and returned if no match is found.
     */
    private ClassLoader lookupLoader(final Uri[] uris, URL[] urls,
				     final ClassLoader parent) throws MalformedURLException
    {
	/*
	 * If the requested codebase is null, then PreferredClassProvider
	 * assumes that the class is expected to be a "platform" class
	 * with respect to the parent class loader.
	 */
	if (uris == null) {
            if (logger.isLoggable(Level.FINEST)){
                logger.log(Level.FINEST, 
			"uri string is null, returning parent ClassLoader: {0}", 
			parent);
            }
	    return parent;
	}

	/*
	 * If the requested codebase URL path is empty, the supplied
	 * parent class loader will be sufficient.
	 *
	 * REMIND: To be conservative, this optimization is commented out
	 * for now so that it does not open a security hole in the future
	 * by providing untrusted code with direct access to the public
	 * loadClass() method of a class loader instance that it cannot
	 * get a reference to.  (It's an unlikely optimization anyway.)
	 *
	 * if (urls.length == 0) {
	 *     return parent;
	 * }
	 */
        
        if (logger.isLoggable(Level.FINEST)){
            String uriString;
            String urlString;
            StringBuilder sb = new StringBuilder(120);
            sb.append("Uri[]: ");
            int l = uris.length;
            for (int i = 0; i < l; i++){
                sb.append(uris[i]);
                sb.append(" ");
            }
            uriString = sb.toString();
            sb.delete(0, sb.length()-1);
            sb.append("URL[]: ");
            l = urls.length;
            for (int i = 0; i <l; i++){
                sb.append(urls[i]);
                sb.append(" ");
            }
            urlString = sb.toString();
            logger.log(Level.FINEST, uriString);
            logger.log(Level.FINEST, urlString);
            logger.log(Level.FINEST, "ClassLoader: {0}", parent);
        }
        
        /* Each LoaderKey is unique to a ClassLoader, the LoaderKey contains
         * a weak reference to the parent ClassLoader, the parent ClassLoader
         * will not be collected until all child ClassLoaders have been collected.
         * 
         * When a child ClassLoader is collected, the LoaderKey will be removed
         * within the next 10ms.
         */
	LoaderKey key = uri? new LoaderKey(uris, parent, null) : new LoaderKey(urls, parent, null);
        ClassLoader loader = loaderTable.get(key);

	/*
	 * Four possible cases:
	 *   1) this is our first time creating this classloader
	 *      - loader is null, need to make a new entry and a new loader
	 *   2) we made this classloader before, but it was garbage collected a long while ago
	 *      - identical to case #1
	 *   3) we made this classloader before, and it was garbage collected recently
	 *      - ConcurrentMap.putIfAbsent will replace it, very similar to case #1
	 *   4) we made this classloader before, and it's still alive (CACHE HIT)
	 *      - just return it
	 */
        if (loader == null) {
            /*
             * An existing loader with the given URL path and
             * parent was not found.  Perform the following steps
             * to obtain an appropriate loader:
             * 
             * Search for an ancestor of the parent class loader
             * whose export urls match the parameter URL path
             * 
             * If a matching ancestor could not be found, create a
             * new class loader instance for the requested
             * codebase URL path and parent class loader.  The
             * loader instance is created within an access control
             * context restricted to the permissions necessary to
             * load classes from its codebase URL path.
             */
            loader = findOriginLoader(uris, parent);

            if (loader == null) {
                loader = createClassLoader(urls, parent, requireDlPerm);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "ClassLoader was null creating new PreferredClassLoader{0}", loader);
                }
                /* RIVER-265
                 * The next section of code has been moved inside this
                 * block to avoid caching loaders found using
                 * findOriginLoader
                 */
                ClassLoader existed = loaderTable.putIfAbsent(key, loader);
                if (existed != null) {
                    if (logger.isLoggable(Level.FINEST)){
                        logger.log(Level.FINEST, "ClassLoader existed, replacing {0} with {1}", new Object[]{loader, existed});
                    }
                    loader = existed;
                }
                
            } else if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "ClassLoader: {0}", loader);
            }

        } else if (logger.isLoggable(Level.FINEST)){
            logger.log(Level.FINEST, "ClassLoader found in loader table: {0} with key {1}", new Object[]{loader, key});
        }
        return loader;
    }

    /**
     * Creates the class loader for this
     * <code>PreferredClassProvider</code> to use to load classes from
     * the specified path of URLs with the specified delegation
     * parent.
     *
     * <p><code>PreferredClassProvider</code> implements this method
     * as follows:
     *
     * <p>This method creates a new instance of {@link
     * PreferredClassLoader} that loads classes and resources from
     * <code>urls</code>, delegates to <code>parent</code>, and
     * enforces {@link DownloadPermission} if
     * <code>requireDlPerm</code> is <code>true</code>.  The created
     * loader uses a restricted security context to ensure that the
     * URL retrieval operations undertaken by the loader cannot
     * exercise a permission that is not implied by the permissions
     * necessary to access the loader as a codebase loader for the
     * specified path of URLs.
     *
     * @param urls the path of URLs to load classes and resources from
     *
     * @param parent the parent class loader for delegation
     *
     * @param requireDlPerm if <code>true</code>, the loader must only
     * define classes with a {@link CodeSource} that is granted
     * <code>DownloadPermission</code>
     *
     * @return the created class loader
     *
     * @since 2.1
     **/
    protected ClassLoader createClassLoader(final URL[] urls,
					    final ClassLoader parent,
					    final boolean requireDlPerm)
    {
	checkInitialized();
	return
	    AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
		@Override
		public ClassLoader run() {
		    return new PreferredClassLoader(urls, parent, null,
						    requireDlPerm);
		}
	    }, PreferredClassLoader.getLoaderAccessControlContext(urls));
    }

    /**
     * Loader table key: a codebase annotation and a weak reference to
     * a parent class loader (possibly null).  The weak reference is
     * registered with "refQueue" so that the entry can be removed
     * after the loader has become unreachable.
     * 
     * LoaderKey used to be a combination of URL path and weak reference to a
     * parent class loader.
     * 
     * It was updated to also allow Uri for the following reasons:
     * 
     * 1. Modern environments have dynamically assigned IP addresses, Uri can provide a
     *    level of indirection for Dynamic DNS and Dynamic IP.
     * 2. Virtual hosting is broken with URL.
     * 4. Testing revealed that all Jini specification tests pass with Uri.  
     *    Although this doesn't eliminate the possibility of breakage in user code, 
     *    it does provide a level of confidence that indicates the benefits
     *    outweigh any disadvantages.  Illegal characters are escaped prior
     *    to parsing; to maximise compatibility and minimise deployment issues.
     * 5. Sun bug ID 4434494 states: 
     *      However, to address URL parsing in general, we introduced a new
     *      class called URI in Merlin (jdk1.4). People are encouraged to use 
     *      URI for parsing and Uri comparison, and leave URL class for 
     *      accessing the URL itself, getting at the protocol handler, 
     *      interacting with the protocol etc.
     **/
    private static class LoaderKey extends WeakReference<ClassLoader> {
	private final Object[] uris;
	private final boolean nullParent;
	private final int hashValue;

	public LoaderKey(Object[] urls, ClassLoader parent, ReferenceQueue<ClassLoader> refQueue) {
	    super(parent, refQueue);
	    nullParent = (parent == null);
            uris = urls;
	    int h = nullParent ? 0 : parent.hashCode();
	    for (int i = 0; i < urls.length; i++) {
		h ^= uris[i].hashCode();
	    }
	    hashValue = h;
	}

	@Override
	public int hashCode() {
	    return hashValue;
	}

	@Override
	public boolean equals(Object obj) {
	    if (obj == this) return true;
	    if (!(obj instanceof LoaderKey)) return false;
	    if (hashCode() != obj.hashCode()) return false;
	    LoaderKey other = (LoaderKey) obj;
	    ClassLoader parent;
	    return (nullParent ? other.nullParent
			       : ((parent = get()) != null &&
				  parent == other.get()))
		&& Arrays.equals(uris, other.uris);
	}
        
	@Override
        public String toString(){
            StringBuilder sb = new StringBuilder(120);
            int l = uris.length;
            sb.append(getClass());
            sb.append("\n");
            sb.append("Uri[]: ");
            for (int i = 0; i < l; i++){
                
            }
            sb.append("\n");
            if (!nullParent) {
                sb.append("Parent ClassLoader: ");
                sb.append(get());
            } else {
                sb.append("System parent ClassLoader");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    private static ClassLoader getClassLoader(final Class c) {
	return AccessController.doPrivileged(
	    new PrivilegedAction<ClassLoader>() {
		@Override
		public ClassLoader run() { return c.getClassLoader(); }
	    });
    }
}
