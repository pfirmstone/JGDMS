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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import org.apache.river.api.net.Uri;

/**
 * Somewhat sneaky code to start a ClassServer as a daemon thread,
 * requiring the caller to only have createClassLoader permission. This
 * is to avoid requiring the caller to have permission to read the JSK
 * tools.jar, because its location isn't a constant that we can embed
 * in security policy files, and we don't want to have to grant tests
 * permission to read all files.
 */
public class HTTPD {
    /** default HTTPD port, can be overridden by jsk.port in test.props */
    private static int defaultPort = 8081;

    static {
	String port = TestLibrary.getExtraProperty("jsk.port", null);
	if (port != null) {
	    try {
		defaultPort = Integer.decode(port).intValue();
	    } catch (NumberFormatException e) {
		e.printStackTrace();
	    }
	}
    }

    /** the port being used */
    private int port;
    /** the ClassServer instance */
    private Thread daemon;

    /**
     * Create an instance using the default port, serving up the files
     * in the lib directory of jsk.home (as specified in test.props).
     */
    public HTTPD() throws IOException {
	this(getDefaultPort(), getDefaultDir());
    }

    /**
     * Create an instance on the specified port, serving up the files
     * in the specified directory.
     */
    public HTTPD(int port, String dir) throws IOException {
	this.port = port;
	String libDir =
	    TestLibrary.getExtraProperty("jsk.home", TestLibrary.jskHome) ;
//		+ File.separator + "lib";
	String toolsJar = libDir + "/JGDMS/tools/classserver/target/classserver-3.0-SNAPSHOT.jar";
	String jsklibJar = libDir + File.separator + "jgdms-lib-dl-3.0-SNAPSHOT.jar";
	System.err.println("HTTPD: using " + toolsJar +
			   " on port " + port + " serving " + dir);
        
	URLClassLoader ld = new Loader0(toolsJar, jsklibJar, port, dir);
        StringBuilder sb = new StringBuilder(200);
        URL[] urls = ld.getURLs();
        for (int i = 0, l = urls.length; i < l; i++){
            try {
                sb.append(urls[i].toURI().toString());
            } catch (URISyntaxException ex) {
                System.err.println(HTTPD.class.getName() + ": " + ex.getMessage());
            }
        }
        System.err.println("ClassLoader: " + sb.toString());
	try {
	    Class cl = Class.forName("HTTPD$Daemon", false, ld);
	    Method m = cl.getMethod("create",
				    new Class[]{String.class, int.class,
						String.class});
	    try {
		daemon = (Thread) m.invoke(null,
					   new Object[]{toolsJar,
							new Integer(port),
							dir});
	    } catch (InvocationTargetException e) {
		Throwable cause = e.getCause();
		if (cause instanceof Error) {
		    throw (Error) cause;
		} else {
		    throw (Exception) cause;
		}
	    }
	} catch (RuntimeException e) {
	    throw e;
	} catch (IOException e) {
	    throw e;
	} catch (Exception e) {
	    IOException ee = new IOException("oops"); // hack
	    ee.initCause(e);
	    throw ee;
	}
    }

    /** Returns the default port for serving up davis files */
    public static int getDefaultPort() {
	return defaultPort;
    }

    /** Returns the default directory for serving up davis files */
    public static String getDefaultDir() {
	return (TestLibrary.getExtraProperty("jsk.home", TestLibrary.jskHome) +
		File.separator + "lib-dl");
    }

    /** Shut it down */
    public void stop() throws IOException {
	Socket sock = new Socket(InetAddress.getLocalHost(), port);
	try {
	    DataOutputStream out =
		new DataOutputStream(sock.getOutputStream());
	    out.writeBytes("SHUTDOWN *\r\n\r\n");
	    out.flush();
	} finally {
	    try {
		sock.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * This class is loaded into a Loader0 classloader. This is a
     * bootstrap process so that the caller only has to have
     * createClassLoader permission, and other permissions can be
     * granted to this class by Loader0.
     */
    public static class Daemon {
	/**
	 * Returns a daemon thread running the ClassServer from the
	 * specified toolsJar file, on the specified port, serving
	 * up files from the specified directory.
	 */
	public static Thread create(final String toolsJar,
				    final int port,
				    final String dir)
	    throws Exception
	{
	    Thread t;
	    try {
		t = (Thread) AccessController.doPrivileged(
		    new PrivilegedExceptionAction() {
		    public Object run() throws Exception {
			ClassLoader ld = new Loader(
			    new URL[]{new Uri(new File(toolsJar).toURI().toString()).toURL()},
			    port, dir);
			Class cl =
			    Class.forName("org.apache.river.tool.ClassServer",
					  false, ld);
			Constructor cons =
			    cl.getConstructor(new Class[]{int.class,
							  dir.getClass(),
							  boolean.class,
							  boolean.class});
			try {
			    return cons.newInstance(
					new Object[]{new Integer(port), dir,
						     Boolean.FALSE,
						     Boolean.FALSE});
			} catch (InvocationTargetException e) {
			    Throwable cause = e.getCause();
			    if (cause instanceof Error) {
				throw (Error) cause;
			    } else {
				throw (Exception) cause;
			    }
			}
		    }
		});
		t.setDaemon(true);
		t.start();
		return t;
	    } catch (PrivilegedActionException e) {
		throw e.getException();
	    }
	}
    }

    /** ClassLoader that grants extra permissions to loaded code */
    private static class Loader extends URLClassLoader {
	private int port;
	private String dir;

	Loader(URL[] urls, int port, String dir)
	    throws MalformedURLException
	{
	    super(urls);
	    this.port = port;
	    this.dir = dir;
	}
        
	protected PermissionCollection getPermissions(CodeSource cs) {
	    PermissionCollection perms = super.getPermissions(cs);
	    perms.add(new SocketPermission("localhost:" + port, "listen"));
	    perms.add(new SocketPermission("*", "accept"));
	    perms.add(new FilePermission(dir + File.separator + "-", "read"));
	    return perms;
	}
    }

    /**
     * ClassLoader that prefers all HTTPD nested classes and grants
     * permission to read toolsJar.
     */
    private static class Loader0 extends Loader {
	private final String toolsJar;
	private final String jsklibJar;
        
        /**
         * This was added because the test class path wasn't available for this
         * loader, which meant that Daemon wasn't on the loaders class path,
         * preventing the test from running.  Not sure why this change was
         * necessary, whether something has changed in jtreg or Java 8.
         * 
         * These tests unfortunately are not run often enough by developers.
         * 
         * @return
         * @throws MalformedURLException 
         */
        private static URL[] getPath() throws MalformedURLException{
            String classpath = TestLibrary.getProperty("test.class.path", TestParams.testClasses);
            String [] cp = classpath.split(File.pathSeparator);
            int len = cp.length;
            URL[] path = new URL[len];
            for (int i = 0; i < len; i++){
		try {
		    path[i] = new Uri(new File(cp[i]).toURI().toString()).toURL();
		} catch (URISyntaxException ex){
		    MalformedURLException e = new MalformedURLException("unable to parse test.class.path");
		    e.initCause(e);
		    throw e;
		}
            }
            return path;
        }

        Loader0(String toolsJar, String jsklibJar, int port, String dir)
	    throws MalformedURLException
	{
	    super(getPath(), port, dir);
	    this.toolsJar = toolsJar;
	    this.jsklibJar = jsklibJar;
	}
        
	protected synchronized Class loadClass(String name, boolean resolve)
	    throws ClassNotFoundException
	{
	    if (name.startsWith("HTTPD")) {
		Class c = findLoadedClass(name);
		if (c == null) {
		    c = findClass(name);
		}
		if (resolve) {
		    resolveClass(c);
		}
		return c;
	    } else {
		return super.loadClass(name, resolve);
	    }
	}

	protected PermissionCollection getPermissions(CodeSource cs) {
	    PermissionCollection perms = super.getPermissions(cs);
	    perms.add(new FilePermission(toolsJar, "read"));
	    perms.add(new FilePermission(jsklibJar, "read"));
	    return perms;
	}
    }
}
