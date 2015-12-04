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

package org.apache.river.api.net;

import org.apache.river.action.GetPropertyAction;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectStreamException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLStreamHandlerFactory;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.river.impl.Messages;

/**
 * This class loader is responsible for loading classes and resources from a
 * list of URLs which can refer to either directories or JAR files. Classes
 * loaded by this {@code URLClassLoader} are granted permission to access the
 * URLs contained in the URL search list.
 * <p>
 * Unlike java.net.URLClassLoader, {@link CodeSource#equals(java.lang.Object) } 
 * and {@link CodeSource#hashCode() } is based on Certificate
 * and RFC3986 {@link Uri#equals(java.lang.Object) } and {@link Uri#hashCode() }, 
 * not {@link URL#equals(java.lang.Object)}. SecureClassLoader
 * uses the overridden CodeSource equality as a key to cache ProtectionDomain's.
 * <p>
 * The following property 
 * <code>-Dnet.jini.loader.codebaseAnnotation=URL</code> 
 * may be set from the command line to revert to {@link URL#equals(java.lang.Object) }
 * and {@link URL#hashCode() }.
 * <p>
 * This allows implementors of {@link java.rmi.Remote} to do two things:
 * <ol>
 * <li>Utilise replication of codebase servers or mirrors.</li>
 * <li>Use different domain names to ensure separation of proxy classes that 
 * otherwise utilise identical jar files</li>
 * </ol>
 * <p>
 * The locking strategy of this ClassLoader is by default, the standard 
 * ClassLoader strategy.  This ClassLoader is also thread safe, so can use
 * a Parallel loading / synchronization strategy if the platform supports it.
 * <p>
 * @since 3.0.0
 */
public class RFC3986URLClassLoader extends java.net.URLClassLoader {
    
    /**
     * value of "net.jini.loader.codebaseAnnotation" property, as cached at class
     * initialization time.  It may contain malformed URLs.
     */
    private final static boolean uri;
    
    private final static Logger logger = Logger.getLogger(RFC3986URLClassLoader.class.getName());
    
    static {
        try {
            registerAsParallelCapable();//Since 1.7
        } catch (NoSuchMethodError e){
	    // Ignore, earlier version of Java.
            logger.log(Level.FINEST, "Platform doesn't support parallel class loading", e);
	}        
        String codebaseAnnotationProperty = null;
	String prop = AccessController.doPrivileged(
           new GetPropertyAction("net.jini.loader.codebaseAnnotation"));
	if (prop != null && prop.trim().length() > 0) codebaseAnnotationProperty = prop;
        uri = codebaseAnnotationProperty == null || 
            !Uri.asciiStringsUpperCaseEqual(codebaseAnnotationProperty, "URL");
    }
    
    private final List<URL> originalUrls; // Copy on Write

    private final List<URL> searchList; // Synchronized
    
    /* synchronize on handlerList for all access to handlerList and handlerMap */
    private final List<URLHandler> handlerList;
    private final Map<Uri, URLHandler> handlerMap = new HashMap<Uri, URLHandler>();

    private final URLStreamHandlerFactory factory;

    private final AccessControlContext creationContext;

    private static class SubURLClassLoader extends RFC3986URLClassLoader {
        // The subclass that overwrites the loadClass() method

        SubURLClassLoader(URL[] urls, AccessControlContext context) {
            super(urls, ClassLoader.getSystemClassLoader(), null, context);
        }

        SubURLClassLoader(URL[] urls, ClassLoader parent, AccessControlContext context) {
            super(urls, parent, null, context);
        }

        /**
         * Overrides the {@code loadClass()} of {@code ClassLoader}. It calls
         * the security manager's {@code checkPackageAccess()} before
         * attempting to load the class.
         *
         * @return the Class object.
         * @param className
         *            String the name of the class to search for.
         * @param resolveClass
         *            boolean indicates if class should be resolved after
         *            loading.
         * @throws ClassNotFoundException
         *             If the class could not be found.
         */
        @Override
        protected Class<?> loadClass(String className, boolean resolveClass) 
                throws ClassNotFoundException 
        {
            /* Synchronization or locking isn't necessary here, ClassLoader
             * has it's own locking scheme, which is likely to change depending
             * on concurrency or multi thread strategies.
             */ 
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                int index = className.lastIndexOf('.');
                if (index != -1) { // skip if class is from a default package
                        sm.checkPackageAccess(className.substring(0, index));
                }
            }
            return super.loadClass(className, resolveClass);
        }
    }

    private static class IndexFile {

        private final HashMap<String, List<URL>> map;
        //private URLClassLoader host;


        static IndexFile readIndexFile(JarFile jf, JarEntry indexEntry, URL url) {
            BufferedReader in = null;
            InputStream is = null;
            try {
                // Add mappings from resource to jar file
                String parentURLString = getParentURL(url).toExternalForm();
                String prefix = "jar:" //$NON-NLS-1$
                        + parentURLString + "/"; //$NON-NLS-1$
                is = jf.getInputStream(indexEntry);
                in = new BufferedReader(new InputStreamReader(is, "UTF8"));
                HashMap<String, List<URL>> pre_map = new HashMap<String, List<URL>>();
                // Ignore the 2 first lines (index version)
                if (in.readLine() == null) return null;
                if (in.readLine() == null) return null;
                TOP_CYCLE:
                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    }
                    URL jar = new URL(prefix + line + "!/"); //$NON-NLS-1$
                    while (true) {
                        line = in.readLine();
                        if (line == null) {
                            break TOP_CYCLE;
                        }
                        if ("".equals(line)) {
                            break;
                        }
                        List<URL> list;
                        if (pre_map.containsKey(line)) {
                            list = pre_map.get(line);
                        } else {
                            list = new LinkedList<URL>();
                            pre_map.put(line, list);
                        }
                        list.add(jar);
                    }
                }
                if (!pre_map.isEmpty()) {
                    return new IndexFile(pre_map);
                }
            } catch (MalformedURLException e) {
                // Ignore this jar's index
            } catch (IOException e) {
                // Ignore this jar's index
            }
            finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
            return null;
        }

        private static URL getParentURL(URL url) throws IOException {
            URL fileURL = ((JarURLConnection) url.openConnection()).getJarFileURL();
            String file = fileURL.getFile();
            String parentFile = new File(file).getParent();
            parentFile = parentFile.replace(File.separatorChar, '/');
            if (parentFile.charAt(0) != '/') {
                parentFile = "/" + parentFile; //$NON-NLS-1$
            }
            URL parentURL = new URL(fileURL.getProtocol(), fileURL
                    .getHost(), fileURL.getPort(), parentFile);
            return parentURL;
        }

        public IndexFile(HashMap<String,List<URL>> map) {
            // Don't need to defensively copy map, it's created for and only
            // used here.
            this.map = map;
        }

        List<URL> get(String name) {
            synchronized (map){
                return map.get(name);
            }
        }
    }

    private static class URLHandler {
        final URL url;
        final URL codeSourceUrl;
        final RFC3986URLClassLoader loader;

        public URLHandler(URL url, RFC3986URLClassLoader loader) {
            this.url = url;
            this.codeSourceUrl = url;
            this.loader = loader;
        }
        
        public URLHandler(URL url, URL codeSourceUrl, RFC3986URLClassLoader loader){
            this.url = url;
            this.codeSourceUrl = codeSourceUrl;
            this.loader = loader;
        }

        void findResources(String name, List<URL> resources) {
            URL res = findResource(name);
            if (res != null && !resources.contains(res)) {
                resources.add(res);
            }
        }

        Class<?> findClass(String packageName, String name, String origName) {
            URL resURL = targetURL(url, name);
            if (resURL != null) {
                try {
                    InputStream is = resURL.openStream();
                    return createClass(is, packageName, origName);
                } catch (IOException e) {
                }
            }
            return null;
        }


        Class<?> createClass(InputStream is, String packageName, String origName) {
            if (is == null) {
                return null;
            }
            byte[] clBuf = null;
            try {
                clBuf = getBytes(is);
            } catch (IOException e) {
                return null;
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (packageName != null) {
                String packageDotName = packageName.replace('/', '.');
                Package packageObj = loader.getPackage(packageDotName);
                if (packageObj == null) {
                    try {
                        loader.definePackage(packageDotName, null, null,
                                            null, null, null, null, null);
                    } catch (IllegalArgumentException e){
                        // Already exists, this is in case of concurrent access
                        // which is very unlikely.
                        packageObj = loader.getPackage(packageDotName);
                        if (packageObj.isSealed()) {
                            throw new SecurityException(Messages
                                    .getString("luni.A1")); //$NON-NLS-1$
                        }
                    }
                } else {
                    if (packageObj.isSealed()) {
                        throw new SecurityException(Messages
                                .getString("luni.A1")); //$NON-NLS-1$
                    }
                }
            }
            // The package is defined and isn't sealed, safe to define class.
            if (uri) return loader.defineClass(
                    origName,
                    clBuf,
                    0, 
                    clBuf != null ? clBuf.length: 0,
                    new UriCodeSource(codeSourceUrl, (Certificate[]) null, null)
            );
            return loader.defineClass(
                    origName, 
                    clBuf,
                    0, 
                    clBuf != null ? clBuf.length: 0,
                    new CodeSource(codeSourceUrl, (Certificate[]) null)
            );
        }

        URL findResource(String name) {
            URL resURL = targetURL(url, name);
            if (resURL != null) {
                try {
                    URLConnection uc = resURL.openConnection();
                    uc.getInputStream().close();
                    // HTTP can return a stream on a non-existent file
                    // So check for the return code;
                    if (!resURL.getProtocol().equals("http")) { //$NON-NLS-1$
                        return resURL;
                    }
                    int code;
                    if ((code = ((HttpURLConnection) uc).getResponseCode()) >= 200
                            && code < 300) {
                        return resURL;
                    }
                } catch (SecurityException e) {
                    return null;
                } catch (IOException e) {
                    return null;
                }
            }
            return null;
        }

        URL targetURL(URL base, String name) {
            try {
                String file = base.getFile() + URIEncoderDecoder.quoteIllegal(name,
                        "/@" + Uri.someLegal);

                return new URL(base.getProtocol(), base.getHost(), base.getPort(),
                        file, null);
            } catch (UnsupportedEncodingException e) {
                return null;
            } catch (MalformedURLException e) {
                return null;
            }
        }
        
        public void close() throws IOException {
            // do nothing.
        }

    }

    private static class URLJarHandler extends URLHandler {
        private final JarFile jf;
        private final String prefixName;
        private final IndexFile index;
        private final Map<Uri, URLHandler> subHandlers = new HashMap<Uri, URLHandler>();

        public URLJarHandler(URL url, URL jarURL, JarFile jf, String prefixName, RFC3986URLClassLoader loader) {
            super(url, jarURL, loader);
            this.jf = jf;
            this.prefixName = prefixName;
            final JarEntry je = jf.getJarEntry("META-INF/INDEX.LIST"); //$NON-NLS-1$
            this.index = (je == null ? null : IndexFile.readIndexFile(jf, je, url));
        }

        public URLJarHandler(URL url, URL jarURL, JarFile jf, String prefixName, IndexFile index, RFC3986URLClassLoader loader) {
            super(url, jarURL, loader);
            this.jf = jf;
            this.prefixName = prefixName;
            this.index = index;
        }

        IndexFile getIndex() {
            return index;
        }

        @Override
        void findResources(String name, List<URL> resources) {
            URL res = findResourceInOwn(name);
            if (res != null && !resources.contains(res)) {
                resources.add(res);
            }
            if (index != null) {
                int pos = name.lastIndexOf("/"); //$NON-NLS-1$
                // only keep the directory part of the resource
                // as index.list only keeps track of directories and root files
                String indexedName = (pos > 0) ? name.substring(0, pos) : name;
                List<URL> urls = index.get(indexedName);
                if (urls != null) {
                    synchronized (urls){
                        urls.remove(url);
                        urls = new ArrayList<URL>(urls); // Defensive copy to avoid sync
                    }
                    for (URL u : urls) {
                        URLHandler h = getSubHandler(u);
                        if (h != null) {
                            h.findResources(name, resources);
                        }
                    }
                }
            }

        }

        @Override
        Class<?> findClass(String packageName, String name, String origName) {
            String entryName = prefixName + name;
            JarEntry entry = jf.getJarEntry(entryName);
            if (entry != null) {
                /**
                 * Avoid recursive load class, especially the class
                 * is an implementation class of security provider
                 * and the jar is signed.
                 */
                try {
                    Manifest manifest = jf.getManifest();
                    return createClass(entry, manifest, packageName, origName);
                } catch (IOException e) {
                }
            }
            if (index != null) {
                List<URL> urls;
                if (packageName == null) {
                    urls = index.get(name);
                } else {
                    urls = index.get(packageName);
                }
                if (urls != null) {
                    synchronized (urls){
                        urls.remove(url);
                        urls = new ArrayList<URL>(urls); // Defensive copy.
                    }
                    for (URL u : urls) {
                        URLHandler h = getSubHandler(u);
                        if (h != null) {
                            Class<?> res = h.findClass(packageName, name, origName);
                            if (res != null) {
                                return res;
                            }
                        }
                    }
                }
            }
            return null;
        }

        private Class<?> createClass(JarEntry entry, Manifest manifest, String packageName, String origName) {
            InputStream is = null;
            byte[] clBuf = null;
            try {
                is = jf.getInputStream(entry);
                clBuf = getBytes(is);
            } catch (IOException e) {
                return null;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
            if (packageName != null) {
                String packageDotName = packageName.replace('/', '.');
                Package packageObj = loader.getPackage(packageDotName);
                if (packageObj == null) {
                    if (manifest != null) {
                        loader.definePackage(packageDotName, manifest,
                                codeSourceUrl);
                    } else {
                        loader.definePackage(packageDotName, null, null,
                                null, null, null, null, null);
                    }
                } else {
                    boolean exception = packageObj.isSealed();
                    if (manifest != null) {
                        if (loader.isSealed(manifest, packageName + "/")) {
                            exception = !packageObj
                                    .isSealed(codeSourceUrl);
                        }
                    }
                    if (exception) {
                        throw new SecurityException(Messages
                                .getString("luni.A1", packageName)); //$NON-NLS-1$
                    }
                }
            }
            CodeSource codeS = uri ? 
                new UriCodeSource(codeSourceUrl, entry.getCertificates(),null) 
                : new CodeSource(codeSourceUrl, entry.getCertificates());
            return loader.defineClass(
                    origName,
                    clBuf,
                    0,
                    clBuf != null ? clBuf.length: 0,
                    codeS
            );
        }

        URL findResourceInOwn(String name) {
            String entryName = prefixName + name;
            if (jf.getEntry(entryName) != null) {
                return targetURL(url, name);
            }
            return null;
        }

        @Override
        URL findResource(String name) {
            URL res = findResourceInOwn(name);
            if (res != null) {
                return res;
            }
            if (index != null) {
                int pos = name.lastIndexOf("/"); //$NON-NLS-1$
                // only keep the directory part of the resource
                // as index.list only keeps track of directories and root files
                String indexedName = (pos > 0) ? name.substring(0, pos) : name;
                List<URL> urls = index.get(indexedName);
                if (urls != null) {
                    synchronized (urls){
                        urls.remove(url);
                        urls = new ArrayList<URL>(urls); // Defensive copy.
                    }
                    for (URL u : urls) {
                        URLHandler h = getSubHandler(u);
                        if (h != null) {
                            res = h.findResource(name);
                            if (res != null) {
                                return res;
                            }
                        }
                    }
                }
            }
            return null;
        }

        private URLHandler getSubHandler(URL url) {
            Uri key = null;
            try {
                key = Uri.urlToUri(url);
            } catch (URISyntaxException ex) {
                logger.log(Level.WARNING, "Unable to create Uri from URL" + url.toString(), ex);
            }
            synchronized (subHandlers){
                URLHandler sub = subHandlers.get(key);
                if (sub != null) {
                    return sub;
                }
                String protocol = url.getProtocol();
                if (protocol.equals("jar")) { //$NON-NLS-1$
                    sub = loader.createURLJarHandler(url);
                } else if (protocol.equals("file")) { //$NON-NLS-1$
                    sub = createURLSubJarHandler(url);
                } else {
                    sub = loader.createURLHandler(url);
                }
                if (sub != null && key != null) {
                    subHandlers.put(key, sub);
                }
                return sub;
            }
        }

        private URLHandler createURLSubJarHandler(URL url) {
            String prfixName;
            String file = url.getFile();
            if (url.getFile().endsWith("!/")) { //$NON-NLS-1$
                prfixName = "";
            } else {
                int sepIdx = file.lastIndexOf("!/"); //$NON-NLS-1$
                if (sepIdx == -1) {
                    // Invalid URL, don't look here again
                    return null;
                }
                sepIdx += 2;
                prfixName = file.substring(sepIdx);
            }
            try {
                URL jarURL = ((JarURLConnection) url
                        .openConnection()).getJarFileURL();
                JarURLConnection juc = (JarURLConnection) new URL(
                        "jar", "", //$NON-NLS-1$ //$NON-NLS-2$
                        jarURL.toExternalForm() + "!/").openConnection(); //$NON-NLS-1$
                JarFile jfile = juc.getJarFile();
                URLJarHandler jarH = new URLJarHandler(url, jarURL, jfile, prfixName, loader);
                // TODO : to think what we should do with indexes & manifest.class file here
                return jarH;
            } catch (IOException e) {
            }
            return null;
        }
        
        public void close() throws IOException {
            IOException first = null;
            try {
                jf.close();
            } catch (IOException e){
                first = e;
            }
            synchronized (subHandlers){
                Iterator<URLHandler> it = subHandlers.values().iterator();
                while (it.hasNext()){
                    try {
                        it.next().close();
                    } catch (IOException e){
                        if (first == null) first = e;
                        else {
                            logger.log(Level.WARNING, "Unable to close URLHandler during URLClassLoader close()", e);
                        }
                    }
                }
                subHandlers.clear();
            }
            if (first != null) throw first;
        }

    }

    private static class URLFileHandler extends URLHandler {
        private final String prefix;

        public URLFileHandler(URL url, RFC3986URLClassLoader loader) {
            super(url, loader);
            String baseFile = url.getFile();
            String host = url.getHost();
            int hostLength = 0;
            if (host != null) {
                hostLength = host.length();
            }
            StringBuilder buf = new StringBuilder(2 + hostLength
                    + baseFile.length());
            if (hostLength > 0) {
                buf.append("//").append(host); //$NON-NLS-1$
            }
            // baseFile always ends with '/'
            buf.append(baseFile);
            prefix = buf.toString();
        }

        @Override
        Class<?> findClass(String packageName, String name, String origName) {
            String filename = prefix + name;
            try {
                filename = URLDecoder.decode(filename, "UTF-8"); //$NON-NLS-1$
            } catch (IllegalArgumentException e) {
                return null;
            } catch (UnsupportedEncodingException e) {
                return null;
            }

            File file = new File(filename);
            if (file.exists()) {
                try {
                    InputStream is = new FileInputStream(file);
                    return createClass(is, packageName, origName);
                } catch (FileNotFoundException e) {
                }
            }
            return null;
        }

        @Override
        URL findResource(String name) {
            int idx = 0;
            String filename;

            // Do not create a UNC path, i.e. \\host
            while (idx < name.length() && 
                   ((name.charAt(idx) == '/') || (name.charAt(idx) == '\\'))) {
                idx++;
            }

            if (idx > 0) {
                name = name.substring(idx);
            }

            try {
                filename = URLDecoder.decode(prefix, "UTF-8") + name; //$NON-NLS-1$

                if (new File(filename).exists()) {
                    return targetURL(url, name);
                }
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            } catch (UnsupportedEncodingException e) {
                // must not happen
                throw new AssertionError(e);
            }
        }

    }
    
    /**
     * To avoid CodeSource equals and hashCode methods in SecureClassLoader keys.
     * 
     * CodeSource uses DNS lookup calls to check location IP addresses are 
     * equal.
     * 
     * This class must not be serialized.
     */
    private static class UriCodeSource extends CodeSource {
        private static final long serialVersionUID = 1L;
        private final Uri uri;
        private final int hashCode;
        
        UriCodeSource(URL url, Certificate [] certs, Collection<Permission> perms){
            super(url, certs);
            Uri uRi = null;
            try {
                uRi = Uri.urlToUri(url);
            } catch (URISyntaxException ex) { }//Ignore
            this.uri = uRi;
            int hash = 7;
            hash = 23 * hash + (this.uri != null ? this.uri.hashCode() : 0);
            hash = 23 * hash + (certs != null ? Arrays.hashCode(certs) : 0);
            hashCode = hash;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public boolean equals(Object o){
            if (!(o instanceof UriCodeSource)) return false;
            if (uri == null) return super.equals(o);
            UriCodeSource that = (UriCodeSource) o;
            if ( !uri.equals(that.uri)) return false;
            Certificate [] mine = getCertificates();
            Certificate [] theirs = that.getCertificates();
            if ( mine == null && theirs == null) return true;
            if ( mine == null && theirs != null) return false;
            if ( mine != null && theirs == null) return false;
            return (Arrays.asList(getCertificates()).equals(
                    Arrays.asList(that.getCertificates())));
        }
        
        Object writeReplace() throws ObjectStreamException {
            return new CodeSource(getLocation(), getCertificates());
        }
       
    }


    /**
     * Constructs a new {@code URLClassLoader} instance. The newly created
     * instance will have the system ClassLoader as its parent. URLs that end
     * with "/" are assumed to be directories, otherwise they are assumed to be
     * JAR files.
     *
     * @param urls
     *            the list of URLs where a specific class or file could be
     *            found.
     * @throws SecurityException
     *             if a security manager exists and its {@code
     *             checkCreateClassLoader()} method doesn't allow creation of
     *             new ClassLoaders.
     */
    public RFC3986URLClassLoader(URL[] urls) {
        this(urls, ClassLoader.getSystemClassLoader(), null);
    }

    /**
     * Constructs a new URLClassLoader instance. The newly created instance will
     * have the system ClassLoader as its parent. URLs that end with "/" are
     * assumed to be directories, otherwise they are assumed to be JAR files.
     * 
     * @param urls
     *            the list of URLs where a specific class or file could be
     *            found.
     * @param parent
     *            the class loader to assign as this loader's parent.
     * @throws SecurityException
     *             if a security manager exists and its {@code
     *             checkCreateClassLoader()} method doesn't allow creation of
     *             new class loaders.
     */
    public RFC3986URLClassLoader(URL[] urls, ClassLoader parent) {
        this(urls, parent, null);
    }

    /**
     * Adds the specified URL to the search list.
     *
     * @param url
     *            the URL which is to add.
     */
    @Override
    protected void addURL(URL url) {
        try {
            originalUrls.add(url);
            searchList.add(createSearchURL(url));
        } catch (MalformedURLException e) {
        }
    }

    /**
     * Returns all known URLs which point to the specified resource.
     *
     * @param name
     *            the name of the requested resource.
     * @return the enumeration of URLs which point to the specified resource.
     * @throws IOException
     *             if an I/O error occurs while attempting to connect.
     */
    @Override
    public Enumeration<URL> findResources(final String name) throws IOException {
        List<URL> result = AccessController.doPrivileged(
                new PrivilegedAction<List<URL>>() {
                    @Override
                    public List<URL> run() {
                        List<URL> results = new LinkedList<URL>();
                        findResourcesImpl(name, results);
                        return results;
                    }
                }, creationContext);
        SecurityManager sm;
        int length = result.size();
        if (length > 0 && (sm = System.getSecurityManager()) != null) {
            ArrayList<URL> reduced = new ArrayList<URL>(length);
            for (int i = 0; i < length; i++) {
                URL url = result.get(i);
                try {
                    sm.checkPermission(url.openConnection().getPermission());
                    reduced.add(url);
                } catch (IOException e) {
                } catch (SecurityException e) {
                }
            }
            result = reduced;
        }
        return Collections.enumeration(result);
    }

    void findResourcesImpl(String name, List<URL> result) {
        if (name == null) {
            return;
        }
        int n = 0;
        while (true) {
            URLHandler handler = getHandler(n++);
            if (handler == null) {
                break;
            }
            handler.findResources(name, result);
        }
    }


    /**
     * Converts an input stream into a byte array.
     *
     * @param is
     *            the input stream
     * @return byte[] the byte array
     */
    private static byte[] getBytes(InputStream is)
            throws IOException {
        byte[] buf = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        int count;
        while ((count = is.read(buf)) > 0) {
            bos.write(buf, 0, count);
        }
        return bos.toByteArray();
    }

    /**
     * Gets all permissions for the specified {@code codesource}. First, this
     * method retrieves the permissions from the system policy. If the protocol
     * is "file:/" then a new permission, {@code FilePermission}, granting the
     * read permission to the file is added to the permission collection.
     * Otherwise, connecting to and accepting connections from the URL is
     * granted.
     *
     * @param codesource
     *            the code source object whose permissions have to be known.
     * @return the list of permissions according to the code source object.
     */
//    @Override
//    protected PermissionCollection getPermissions(final CodeSource codesource) {
//        PermissionCollection pc = super.getPermissions(codesource);
//        URL u = codesource.getLocation();
//        if (u.getProtocol().equals("jar")) { //$NON-NLS-1$
//            try {
//                // Create a URL for the resource the jar refers to
//                u = ((JarURLConnection) u.openConnection()).getJarFileURL();
//            } catch (IOException e) {
//                // This should never occur. If it does continue using the jar
//                // URL
//            }
//        }
//        if (u.getProtocol().equals("file")) { //$NON-NLS-1$
//            String path = u.getFile();
//            String host = u.getHost();
//            if (host != null && host.length() > 0) {
//                path = "//" + host + path; //$NON-NLS-1$
//            }
//
//            if (File.separatorChar != '/') {
//                path = path.replace('/', File.separatorChar);
//            }
//            if (isDirectory(u)) {
//                pc.add(new FilePermission(path + "-", "read")); //$NON-NLS-1$ //$NON-NLS-2$
//            } else {
//                pc.add(new FilePermission(path, "read")); //$NON-NLS-1$
//            }
//        } else {
//            String host = u.getHost();
//            if (host.length() == 0) {
//                host = "localhost"; //$NON-NLS-1$
//            }
//            pc.add(new SocketPermission(host, "connect, accept")); //$NON-NLS-1$
//        }
//        return pc;
//    }

    /**
     * Returns the search list of this {@code URLClassLoader}.
     *
     * @return the list of all known URLs of this instance.
     */
    @Override
    public URL[] getURLs() {
        return originalUrls.toArray(new URL[originalUrls.size()]);
    }

    /**
     * Determines if the URL is pointing to a directory.
     */
    private static boolean isDirectory(URL url) {
        String file = url.getFile();
        return (file.length() > 0 && file.charAt(file.length() - 1) == '/');
    }

    /**
     * Returns a new {@code URLClassLoader} instance for the given URLs and the
     * system {@code ClassLoader} as its parent. The method {@code loadClass()}
     * of the new instance will call {@code
     * SecurityManager.checkPackageAccess()} before loading a class.
     *
     * @param urls
     *            the list of URLs that is passed to the new {@code
     *            URLClassloader}.
     * @return the created {@code URLClassLoader} instance.
     */
    public static URLClassLoader newInstance(final URL[] urls) {
        final AccessControlContext context = AccessController.getContext();
        RFC3986URLClassLoader sub = AccessController
                .doPrivileged(new PrivilegedAction<RFC3986URLClassLoader>() {
                    @Override
                    public RFC3986URLClassLoader run() {
                        return new SubURLClassLoader(urls, context);
                    }
                });
        return sub;
    }

    /**
     * Returns a new {@code URLClassLoader} instance for the given URLs and the
     * specified {@code ClassLoader} as its parent. The method {@code
     * loadClass()} of the new instance will call the SecurityManager's {@code
     * checkPackageAccess()} before loading a class.
     *
     * @param urls
     *            the list of URLs that is passed to the new URLClassloader.
     * @param parentCl
     *            the parent class loader that is passed to the new
     *            URLClassloader.
     * @return the created {@code URLClassLoader} instance.
     */
    public static URLClassLoader newInstance(final URL[] urls,
                                             final ClassLoader parentCl) {
        final AccessControlContext context = AccessController.getContext();
        RFC3986URLClassLoader sub = AccessController
                .doPrivileged(new PrivilegedAction<RFC3986URLClassLoader>() {
                    @Override
                    public RFC3986URLClassLoader run() {
                        return new SubURLClassLoader(urls, parentCl, context);
                    }
                });
        return sub;
    }

    /**
     * Constructs a new {@code URLClassLoader} instance. The newly created
     * instance will have the specified {@code ClassLoader} as its parent and
     * use the specified factory to create stream handlers. URLs that end with
     * "/" are assumed to be directories, otherwise they are assumed to be JAR
     * files.
     * 
     * @param searchUrls
     *            the list of URLs where a specific class or file could be
     *            found.
     * @param parent
     *            the {@code ClassLoader} to assign as this loader's parent.
     * @param factory
     *            the factory that will be used to create protocol-specific
     *            stream handlers.
     * @throws SecurityException
     *             if a security manager exists and its {@code
     *             checkCreateClassLoader()} method doesn't allow creation of
     *             new {@code ClassLoader}s.
     */
    public RFC3986URLClassLoader(URL[] searchUrls, ClassLoader parent,
                          URLStreamHandlerFactory factory) {
        this(searchUrls, parent, factory, AccessController.getContext());
    }
    
    RFC3986URLClassLoader( URL[] searchUrls, 
                            ClassLoader parent, 
                            URLStreamHandlerFactory factory, 
                            AccessControlContext context)
    {
        super(searchUrls, parent, factory);  // ClassLoader protectes against finalizer attack.
        this.factory = factory;
        // capture the context of the thread that creates this URLClassLoader
        creationContext = context;
        int nbUrls = searchUrls.length;
        List<URL> origUrls = new ArrayList<URL>(nbUrls);
        handlerList = new ArrayList<URLHandler>(nbUrls);
        searchList = Collections.synchronizedList(new LinkedList<URL>());
        for (int i = 0; i < nbUrls; i++) {
            origUrls.add(searchUrls[i]);
            try {
                searchList.add(createSearchURL(searchUrls[i]));
            } catch (MalformedURLException e) {
            }
        }
        this.originalUrls = new CopyOnWriteArrayList<URL>(origUrls);
    }

    /**
     * Tries to locate and load the specified class using the known URLs. If the
     * class could be found, a class object representing the loaded class will
     * be returned.
     * 
     * The locking and synchronization strategy of this method is the 
     * responsibility of the caller.
     *
     * @param clsName
     *            the name of the class which has to be found.
     * @return the class that has been loaded.
     * @throws ClassNotFoundException
     *             if the specified class cannot be loaded.
     */
    @Override
    protected Class<?> findClass(final String clsName)
            throws ClassNotFoundException {
        Class<?> cls = AccessController.doPrivileged(
                new PrivilegedAction<Class<?>>() {
                    @Override
                    public Class<?> run() {
                        return findClassImpl(clsName);
                    }
                }, creationContext);
        if (cls != null) {
            return cls;
        }
        throw new ClassNotFoundException(clsName);
    }

    /**
     * Returns an URL that will be checked if it contains the class or resource.
     * If the file component of the URL is not a directory, a Jar URL will be
     * created.
     * 
     * We need to modify this implementation to allow URLStreamHandlerFactory's
     * to use custom caching, such as the per ClassLoader caching used by
     * Apache Geronimo.  This would be very useful as it allows services
     * to upgrade themselves by reloading and replacing their proxy's.
     *
     * @return java.net.URL a test URL
     */
    private URL createSearchURL(URL url) throws MalformedURLException {
        if (url == null) {
            return url;
        }

        String protocol = url.getProtocol();

        if (isDirectory(url) || protocol.equals("jar")) { //$NON-NLS-1$
            return url;
        }
        if (factory == null) {
            return new URL("jar", "", //$NON-NLS-1$ //$NON-NLS-2$
                    -1, url.toString() + "!/"); //$NON-NLS-1$
        }
        // use jar protocol as the stream handler protocol
        return new URL("jar", "", //$NON-NLS-1$ //$NON-NLS-2$
                -1, url.toString() + "!/", //$NON-NLS-1$
                factory.createURLStreamHandler("jar"));//$NON-NLS-1$
    }

    /**
     * Returns an URL referencing the specified resource or {@code null} if the
     * resource could not be found.
     *
     * @param name
     *            the name of the requested resource.
     * @return the URL which points to the given resource.
     */
    @Override
    public URL findResource(final String name) {
        if (name == null) {
            return null;
        }
        URL result = AccessController.doPrivileged(new PrivilegedAction<URL>() {
            @Override
            public URL run() {
                return findResourceImpl(name);
            }
        }, creationContext);
        SecurityManager sm;
        if (result != null && (sm = System.getSecurityManager()) != null) {
            try {
                sm.checkPermission(result.openConnection().getPermission());
            } catch (IOException e) {
                return null;
            } catch (SecurityException e) {
                return null;
            }
        }
        return result;
    }

    /**
     * Returns a URL among the given ones referencing the specified resource or
     * null if no resource could be found.
     *
     * @param resName java.lang.String the name of the requested resource
     * @return URL URL for the resource.
     */
    URL findResourceImpl(String resName) {
        int n = 0;

        while (true) {
            URLHandler handler = getHandler(n++);
            if (handler == null) {
                break;
            }
            URL res = handler.findResource(resName);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    URLHandler getHandler(int num) {
        synchronized (handlerList){
            if (num < handlerList.size()) {
                return handlerList.get(num);
            }

            makeNewHandler();
            if (num < handlerList.size()) {
                return handlerList.get(num);
            }
            return null;
        }
    }

    // synchronize on handlerList.
    private void makeNewHandler() {
        while (!searchList.isEmpty()) {
            URL nextCandidate = searchList.remove(0);
            if (nextCandidate == null) {  // luni.94=One of urls is null
                throw new NullPointerException(Messages.getString("luni.94")); //$NON-NLS-1$
            }
            Uri candidateKey = null;
            try {
                candidateKey = Uri.urlToUri(nextCandidate);
            } catch (URISyntaxException ex) {
                logger.log(Level.WARNING, "Unable to parse URL" + nextCandidate.toString(), ex);
            }
            if (!handlerMap.containsKey(candidateKey)) {
                URLHandler result;
                String protocol = nextCandidate.getProtocol();
                if (protocol.equals("jar")) { //$NON-NLS-1$
                    result = createURLJarHandler(nextCandidate);
                } else if (protocol.equals("file")) { //$NON-NLS-1$
                    result = createURLFileHandler(nextCandidate);
                } else {
                    result = createURLHandler(nextCandidate);
                }
                if (result != null) {
                    handlerMap.put(candidateKey, result);
                    handlerList.add(result);
                    return;
                }
            }
        }
    }

    private URLHandler createURLHandler(URL url) {
        return new URLHandler(url, this);
    }

    private URLHandler createURLFileHandler(URL url) {
        return new URLFileHandler(url, this);
    }

    private URLHandler createURLJarHandler(URL url) {
        String prefixName;
        String file = url.getFile();
        if (url.getFile().endsWith("!/")) { //$NON-NLS-1$
            prefixName = "";
        } else {
            int sepIdx = file.lastIndexOf("!/"); //$NON-NLS-1$
            if (sepIdx == -1) {
                // Invalid URL, don't look here again
                return null;
            }
            sepIdx += 2;
            prefixName = file.substring(sepIdx);
        }
        try {
            URL jarURL = ((JarURLConnection) url
                    .openConnection()).getJarFileURL();
            JarURLConnection juc = (JarURLConnection) new URL(
                    "jar", "", //$NON-NLS-1$ //$NON-NLS-2$
                    jarURL.toExternalForm() + "!/").openConnection(); //$NON-NLS-1$
            JarFile jf = juc.getJarFile();
            URLJarHandler jarH = new URLJarHandler(url, jarURL, jf, prefixName, this);

            if (jarH.getIndex() == null) {
                try {
                    Manifest manifest = jf.getManifest();
                    if (manifest != null) {
                        String classpath = manifest.getMainAttributes().getValue(
                                Attributes.Name.CLASS_PATH);
                        if (classpath != null) {
                            searchList.addAll(0, getInternalURLs(url, classpath));
                        }
                    }
                } catch (IOException e) {
                }
            }
            return jarH;
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * Defines a new package using the information extracted from the specified
     * manifest.
     *
     * @param packageName
     *            the name of the new package.
     * @param manifest
     *            the manifest containing additional information for the new
     *            package.
     * @param url
     *            the URL to the code source for the new package.
     * @return the created package.
     * @throws IllegalArgumentException
     *             if a package with the given name already exists.
     */
    @Override
    protected Package definePackage(String packageName, Manifest manifest,
                                    URL url) throws IllegalArgumentException {
        Attributes mainAttributes = manifest.getMainAttributes();
        String dirName = packageName.replace('.', '/') + "/"; //$NON-NLS-1$
        Attributes packageAttributes = manifest.getAttributes(dirName);
        boolean noEntry = false;
        if (packageAttributes == null) {
            noEntry = true;
            packageAttributes = mainAttributes;
        }
        String specificationTitle = packageAttributes
                .getValue(Attributes.Name.SPECIFICATION_TITLE);
        if (specificationTitle == null && !noEntry) {
            specificationTitle = mainAttributes
                    .getValue(Attributes.Name.SPECIFICATION_TITLE);
        }
        String specificationVersion = packageAttributes
                .getValue(Attributes.Name.SPECIFICATION_VERSION);
        if (specificationVersion == null && !noEntry) {
            specificationVersion = mainAttributes
                    .getValue(Attributes.Name.SPECIFICATION_VERSION);
        }
        String specificationVendor = packageAttributes
                .getValue(Attributes.Name.SPECIFICATION_VENDOR);
        if (specificationVendor == null && !noEntry) {
            specificationVendor = mainAttributes
                    .getValue(Attributes.Name.SPECIFICATION_VENDOR);
        }
        String implementationTitle = packageAttributes
                .getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        if (implementationTitle == null && !noEntry) {
            implementationTitle = mainAttributes
                    .getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        }
        String implementationVersion = packageAttributes
                .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (implementationVersion == null && !noEntry) {
            implementationVersion = mainAttributes
                    .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        }
        String implementationVendor = packageAttributes
                .getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
        if (implementationVendor == null && !noEntry) {
            implementationVendor = mainAttributes
                    .getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
        }

        return definePackage(packageName, specificationTitle,
                specificationVersion, specificationVendor, implementationTitle,
                implementationVersion, implementationVendor, isSealed(manifest,
                dirName) ? url : null);
    }

    private boolean isSealed(Manifest manifest, String dirName) {
        Attributes mainAttributes = manifest.getMainAttributes();
        String value = mainAttributes.getValue(Attributes.Name.SEALED);
        boolean sealed = value != null && value.toLowerCase(Locale.getDefault()).equals("true"); //$NON-NLS-1$
        Attributes attributes = manifest.getAttributes(dirName);
        if (attributes != null) {
            value = attributes.getValue(Attributes.Name.SEALED);
            if (value != null) {
                sealed = value.toLowerCase(Locale.getDefault()).equals("true"); //$NON-NLS-1$
            }
        }
        return sealed;
    }

    /**
     * returns URLs referenced in the string classpath.
     *
     * @param root
     *            the jar URL that classpath is related to
     * @param classpath
     *            the relative URLs separated by spaces
     * @return URL[] the URLs contained in the string classpath.
     */
    private ArrayList<URL> getInternalURLs(URL root, String classpath) {
        // Class-path attribute is composed of space-separated values.
        StringTokenizer tokenizer = new StringTokenizer(classpath);
        ArrayList<URL> addedURLs = new ArrayList<URL>();
        String file = root.getFile();
        int jarIndex = file.lastIndexOf("!/") - 1; //$NON-NLS-1$
        int index = file.lastIndexOf("/", jarIndex) + 1; //$NON-NLS-1$
        if (index == 0) {
            index = file.lastIndexOf(
                    System.getProperty("file.separator"), jarIndex) + 1; //$NON-NLS-1$
        }
        file = file.substring(0, index);
        while (tokenizer.hasMoreElements()) {
            String element = tokenizer.nextToken();
            if (!element.equals("")) { //$NON-NLS-1$
                try {
                    // Take absolute path case into consideration
                    URL url = new URL(new URL(file), element);
                    addedURLs.add(createSearchURL(url));
                } catch (MalformedURLException e) {
                    // Nothing is added
                }
            }
        }
        return addedURLs;
    }

    Class<?> findClassImpl(String className) {
        char dot = '.';
        char slash = '/';
        int len = className.length();
        char[] name = new char [len + 6];
        // Poplulate name
        className.getChars(0,len, name, 0);
        ".class".getChars(0, 6, name, len);
        // Replace dots with slashes up to len and remember the index of the last slash.
        int lastSlash = -1;
        for (int i = 0; i < len; i++){ // len excludes ".class"
            if (name[i] == dot) {
                name[i] = slash;
                lastSlash = i;
            }
        }
        // Create our new classFileName
        String classFileName = new String(name);
        // Share the underlying char[] of classFileName with packageName
        String packageName = null;
        if (lastSlash != -1) {
            packageName = classFileName.substring(0, lastSlash);
        }
        // Query our URLHandlers for the class.
        int n = 0;
        while (true) {
            URLHandler handler = getHandler(n++);
            if (handler == null) {
                break;
            }
            Class<?> res = handler.findClass(packageName, classFileName, className);
            if (res != null) {
                return res;
            }
        }
        return null;

    }
    
    /**
     * Java 6 compatible implementation that overrides Java 7 URLClassLoader.close()
     * 
     * URLClassLoader implements Closeable in Java 7 to allow resources such
     * as open jar files to be released.
     * 
     * Closes any open resources and prevents this ClassLoader loading any
     * additional classes or resources.
     * 
     * TODO: Add support for nested Exceptions when support for Java 6 is dropped. 
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        synchronized (searchList){
            searchList.clear(); // Prevent any new searches.
        }
        IOException first = null;
        synchronized (handlerList){
            Iterator<URLHandler> it = handlerList.iterator();
            while (it.hasNext()){
                try {
                    it.next().close();
                } catch (IOException e){
                    if (first == null) first = e;
                    else {
                        // Log it because it can't be included in returned exceptions.
                        logger.log(Level.WARNING, "Unable to close URLHandler during URLClassLoader close()", e);
                    }
                }
            }
            handlerList.clear();
            handlerMap.clear();
            if (first != null) throw first;
        }
        // This ClassLoader is no longer able to load any new resources.
    }

}
