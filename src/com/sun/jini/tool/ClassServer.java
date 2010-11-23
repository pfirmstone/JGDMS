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
package com.sun.jini.tool;

import com.sun.jini.logging.Levels;
import com.sun.jini.start.LifeCycle;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple HTTP server, for serving up JAR and class files.
 * <p>
 * The following items are discussed below:
 * <ul>
 * <li>{@linkplain #main Command line options}
 * <li><a href="#logging">Logging</a>
 * <li><a href="#running">Examples for running ClassServer</a>
 * </ul>
 * <p>
 * <a name="logging"><h3>Logging</h3></a>
 * <p>
 *
 * This implementation uses the {@link Logger} named
 * <code>com.sun.jini.tool.ClassServer</code> to log information at the
 * following logging levels:
 * <p>
 * <table border="1" cellpadding="5"
 *         summary="Describes logging performed by ClassServer at different
 *	   logging levels">
 * <caption halign="center" valign="top"><b><code>
 *    com.sun.jini.tool.ClassServer</code></b></caption>
 *
 * <tr> <th scope="col">Level</th> <th scope="col">Description</th> </tr>
 * <tr>
 *   <td>{@link Level#SEVERE SEVERE}</td>
 *   <td>failure to accept an incoming connection</td>
 * </tr>
 * <tr>
 *   <td>{@link Level#WARNING WARNING}</td>
 *   <td>failure to read the contents of a requested file,
 *       failure to find the message resource bundle, failure while
 *       executing the <code>-stop</code> option
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link Level#INFO INFO}</td>
 *   <td>server startup and termination</td>
 * </tr>
 * <tr>
 *   <td>{@link Level#CONFIG CONFIG}</td>
 *   <td>the JAR files being used for <code>-trees</code></td>
 * </tr>
 * <tr>
 *   <td>{@link Levels#HANDLED HANDLED}</td>
 *   <td>failure reading an HTTP request or writing a response</td>
 * </tr>
 * <tr>
 *   <td>{@link Level#FINE FINE}</td>
 *   <td>bad HTTP requests, HTTP requests for nonexistent files</td>
 * </tr>
 * <tr>
 *   <td>{@link Level#FINER FINER}</td>
 *   <td>good HTTP requests</td>
 * </tr>
 * </table>
 *
 * <p>
 * <a name="running"><h3>Examples for running ClassServer</h3></a>
 * <p>
 *
 * This server can be run directly from the 
 * {@linkplain #main command line}
 * or as a nonactivatable service under the 
 * {@linkplain com.sun.jini.start Service Starter}.
 * <p>
 * An example of running directly from the command line is:
 * <blockquote><pre>
 * % java -jar <var><b>install_dir</b></var>/lib/classserver.jar \
 *        -port 8081 -dir <var><b>install_dir</b></var>/lib-dl -verbose
 * </pre></blockquote>
 * where <var><b>install_dir</b></var>
 * is the directory where the Apache River release is installed.
 * This command places the class server on the (non-default) port 
 * 8081, which  serves out the files under the (non-default) directory  
 * <var><b>install_dir</b></var>/lib-dl. The <code>-verbose</code> option
 * also causes download attempts to be logged.
 * <p>
 * An example of running under the Service Starter is:
 * <blockquote><pre>
 * % java -Djava.security.policy=<var><b>start_policy</b></var> \
 *        -jar <var><b>install_dir</b></var>/lib/start.jar \
 *        <a href="#config">httpd.config</a>
 * </pre></blockquote>
 * <p>
 * where <var><b>start_policy</b></var> is the name of a security
 * policy file (not provided), and <code>httpd.config</code> is the
 * following configuration file:
 * <a name="config"></a>
 * <blockquote><pre>
 * import com.sun.jini.start.NonActivatableServiceDescriptor;
 * import com.sun.jini.start.ServiceDescriptor;
 * 
 * com.sun.jini.start {
 * 
 *   serviceDescriptors = new ServiceDescriptor[]{
 *     new NonActivatableServiceDescriptor(
 *       "",
 *       "<var><b>httpd_policy</b></var>",
 *       "<var><b>install_dir</b></var>/lib/classserver.jar",
 *       "com.sun.jini.tool.ClassServer",
 *       new String[]{"-port", "8081", "-dir", "<var><b>install_dir</b></var>/lib-dl", "-verbose"})
 *     };
 * }
 * </pre></blockquote>
 * where <var><b>httpd_policy</b></var> is the name of a security
 * policy file (not provided).
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class ClassServer extends Thread {
    /** Default HTTP port */
    private static int DEFAULT_PORT = 8080;
    /** Default directory to serve files from on non-Windows OS */
    private static String DEFAULT_DIR = "/vob/jive/lib-dl";
    /** Default directory to serve files from on Windows */
    private static String DEFAULT_WIN_DIR = "J:";
    private static Logger logger =
			    Logger.getLogger("com.sun.jini.tool.ClassServer");

    /** Server socket to accept connections on */
    private ServerSocket server;
    /** Directories to serve from */
    private String[] dirs;
    /** Map from String (JAR root) to JarFile[] (JAR class path) */
    private Map map;
    /** Verbosity flag */
    private boolean verbose;
    /** Stoppable flag */
    private boolean stoppable;
    /** Read permission on dir and all subdirs, for each dir in dirs */
    private FilePermission[] perms;
    /** Life cycle control */
    private LifeCycle lifeCycle;

    /**
     * Construct a server that does not support network shutdown.
     * Use the {@link #start start} method to run it.
     *
     * @param port the port to use
     * @param dirlist the list of directories to serve files from, with entries
     * separated by the {@linkplain File#pathSeparatorChar path-separator
     * character}
     * @param trees <code>true</code> if files within JAR files should be
     * served up
     * @param verbose <code>true</code> if downloads should be logged
     * @throws IOException if the server socket cannot be created
     * @throws NullPointerException if <code>dir</code> is <code>null</code>
     */
    public ClassServer(int port,
		       String dirlist,
		       boolean trees,
		       boolean verbose)
	throws IOException
    {
	init(port, dirlist, trees, verbose, false, null);
    }

    /**
     * Construct a server.  Use the {@link #start start} method to run it.
     *
     * @param port the port to use
     * @param dirlist the list of directories to serve files from, with entries
     * separated by the {@linkplain File#pathSeparatorChar path-separator
     * character}
     * @param trees <code>true</code> if files within JAR files should be
     * served up
     * @param verbose <code>true</code> if downloads should be logged
     * @param stoppable <code>true</code> if network shutdown from the
     * local host should be supported
     * @throws IOException if the server socket cannot be created
     * @throws NullPointerException if <code>dir</code> is <code>null</code>
     */
    public ClassServer(int port,
		       String dirlist,
		       boolean trees,
		       boolean verbose,
		       boolean stoppable)
	throws IOException
    {
	init(port, dirlist, trees, verbose, stoppable, null);
    }

    /**
     * Do the real work of the constructor.
     */
    private void init(int port,
		      String dirlist,
		      boolean trees,
		      boolean verbose,
		      boolean stoppable,
		      LifeCycle lifeCycle)
	throws IOException
    {
	StringTokenizer st = new StringTokenizer(dirlist, File.pathSeparator);
	dirs = new String[st.countTokens()];
	perms = new FilePermission[dirs.length];
	for (int i = 0; st.hasMoreTokens(); i++) {
	    String dir = st.nextToken();
	    if (!dir.endsWith(File.separator))
		dir = dir + File.separatorChar;
	    dirs[i] = dir;
	    perms[i] = new FilePermission(dir + '-', "read");
	}
	this.verbose = verbose;
	this.stoppable = stoppable;
	this.lifeCycle = lifeCycle;
        server = new ServerSocket();
        server.setReuseAddress(true);
        try {
            server.bind(new InetSocketAddress(port));
        } catch( BindException be ) {
            IOException ioe = new IOException( "failure to bind to port: "+port );
            ioe.initCause(be);
            throw ioe ;
        }
	if (!trees)
	    return;
	map = new HashMap();
	Map jfmap = new HashMap();
	for (int i = 0; i < dirs.length; i++) {
	    String[] files = new File(dirs[i]).list();
	    if (files == null)
		continue;
	    for (int j = 0; j < files.length; j++) {
		String jar = files[j];
		if (!jar.endsWith(".jar") && !jar.endsWith(".zip"))
		    continue;
		String name = jar.substring(0, jar.length() - 4);
		if (map.containsKey(name))
		    continue;
		List jflist = new ArrayList(1);
		addJar(jar, jflist, jfmap);
		map.put(name, jflist.toArray(new JarFile[jflist.size()]));
	    }
	}
    }

    /**
     * Construct a running server, accepting the same command line options
     * supported by {@link #main main}, except for the <code>-stop</code>
     * option.
     *
     * @param args command line options
     * @param lifeCycle life cycle control object, or <code>null</code>
     * @throws IOException if the server socket cannot be created
     * @throws IllegalArgumentException if a command line option is not
     * understood
     * @throws NullPointerException if <code>args</code> or any element
     * of <code>args</code> is <code>null</code>
     */
    public ClassServer(String[] args, LifeCycle lifeCycle) throws IOException {
	int port = DEFAULT_PORT;
	String dirlist = DEFAULT_DIR;
	if (File.separatorChar == '\\')
	    dirlist = DEFAULT_WIN_DIR;
	boolean trees = false;
	boolean verbose = false;
	boolean stoppable = false;
	for (int i = 0; i < args.length ; i++ ) {
	    String arg = args[i];
	    if (arg.equals("-port")) {
		i++;
		port = Integer.parseInt(args[i]);
	    } else if (arg.equals("-dir") || arg.equals("-dirs")) {
		i++;
		dirlist = args[i];
	    } else if (arg.equals("-verbose")) {
		verbose = true;
	    } else if (arg.equals("-trees")) {
		trees = true;
	    } else if (arg.equals("-stoppable")) {
		stoppable = true;
	    } else {
		throw new IllegalArgumentException(arg);
	    }
	}
	init(port, dirlist, trees, verbose, stoppable, lifeCycle);
	start();
    }

    /** Add transitive Class-Path JARs to jflist. */
    private void addJar(String jar, List jflist, Map jfmap)
	throws IOException
    {
	JarFile jf = (JarFile) jfmap.get(jar);
	if (jf != null) {
	    if (jflist.contains(jf)) {
		return;
	    }
	} else {
	    for (int i = 0; i < dirs.length; i++) {
		File f = new File(dirs[i] + jar).getCanonicalFile();
		if (f.exists()) {
		    jf = new JarFile(f);
		    jfmap.put(jar, jf);
		    if (verbose)
			print("classserver.jar", f.getPath());
		    logger.config(f.getPath());
		    break;
		}
	    }
	    if (jf == null) {
		if (verbose)
		    print("classserver.notfound", jar);
		logger.log(Level.CONFIG, "{0} not found", jar);
		return;
	    }
	}
	jflist.add(jf);
	Manifest man = jf.getManifest();
	if (man == null)
	    return;
	Attributes attrs = man.getMainAttributes();
	if (attrs == null)
	    return;
	String val = attrs.getValue(Attributes.Name.CLASS_PATH);
	if (val == null)
	    return;
	for (StringTokenizer st = new StringTokenizer(val);
	     st.hasMoreTokens(); )
	{
	    String elt = st.nextToken();
	    String path = decode(elt);
	    if (path == null) {
		if (verbose)
		    print("classserver.notfound", elt);
		logger.log(Level.CONFIG, "{0} not found", elt);
	    }
	    if ('/' != File.separatorChar) {
		path = path.replace('/', File.separatorChar);
	    }
	    addJar(path, jflist, jfmap);
	}
    }

    /** Just keep looping, spawning a new thread for each incoming request. */
    public void run() {
	logger.log(Level.INFO, "ClassServer started [{0}, port {1}]",
		   new Object[]{Arrays.asList(dirs),
				Integer.toString(getPort())});
	try {
	    while (true) {
		new Task(server.accept()).start();
	    }
	} catch (IOException e) {
	    synchronized (this) {
		if (verbose) {
		    e.printStackTrace();
		}
		if (!server.isClosed())
		    logger.log(Level.SEVERE, "accepting connection", e);
		terminate();
	    }
	}
    }

    /** Close the server socket, causing the thread to terminate. */
    public synchronized void terminate() {
	verbose = false;
	try {
	    server.close();
	} catch (IOException e) {
	}
	if (lifeCycle != null)
	    lifeCycle.unregister(this);
	logger.log(Level.INFO, "ClassServer terminated [port {0}]",
		   Integer.toString(getPort()));
    }

    /** Returns the port on which this server is listening. */
    public int getPort() {
	return server.getLocalPort();
    }

    /** Read up to CRLF, return false if EOF */
    private static boolean readLine(InputStream in, StringBuffer buf)
	throws IOException
    {
	while (true) {
	    int c = in.read();
	    if (c < 0)
		return buf.length() > 0;
	    if (c == '\r') {
		in.mark(1);
		c = in.read();
		if (c != '\n')
		    in.reset();
		return true;
	    }
	    if (c == '\n')
		return true;
	    buf.append((char) c);
	}
    }

    /** Parse % HEX HEX from s starting at i */
    private static char decode(String s, int i) {
	return (char) Integer.parseInt(s.substring(i + 1, i + 3), 16);
    }

    /** Decode escape sequences */
    private static String decode(String path) {
	try {
	    for (int i = path.indexOf('%');
		 i >= 0;
		 i = path.indexOf('%', i + 1))
	    {
		char c = decode(path, i);
		int n = 3;
		if ((c & 0x80) != 0) {
		    switch (c >> 4) {
		    case 0xC:
		    case 0xD:
			n = 6;
			c = (char)(((c & 0x1F) << 6) |
				   (decode(path, i + 3) & 0x3F));
			break;
		    case 0xE:
			n = 9;
			c = (char)(((c & 0x0f) << 12) |
				   ((decode(path, i + 3) & 0x3F) << 6) |
				   (decode(path, i + 6) & 0x3F));
			break;
		    default:
			return null;
		    }
		}
		path = path.substring(0, i) + c + path.substring(i + n);
	    }
	} catch (Exception e) {
	    return null;
	}
	return path;
    }

    /** Read the request/response and return the initial line. */
    private static String getInput(Socket sock, boolean isRequest)
	throws IOException
    {
	BufferedInputStream in =
	    new BufferedInputStream(sock.getInputStream(), 256);
	StringBuffer buf = new StringBuffer(80);
	do {
	    if (!readLine(in, buf))
		return null;
	} while (isRequest && buf.length() == 0);
	String initial = buf.toString();
	do {
	    buf.setLength(0);
	} while (readLine(in, buf) && buf.length() > 0);
	return initial;
    }

    /** Simple daemon task thread */
    private class Task extends Thread {
	/** Socket for the incoming request */
	private Socket sock;

	/** Simple constructor */
	public Task(Socket sock) {
	    this.sock = sock;
	    setDaemon(true);
	}

	/** Read specified number of bytes and always close the stream. */
	private byte[] getBytes(InputStream in, long length)
	    throws IOException
	{
	    DataInputStream din = new DataInputStream(in);
	    byte[] bytes = new byte[(int)length];
	    try {
		din.readFully(bytes);
	    } finally {
		din.close();
	    }
	    return bytes;
	}

	/** Canonicalize the path */
	private String canon(String path) {
	    if (path.regionMatches(true, 0, "http://", 0, 7)) {
		int i = path.indexOf('/', 7);
		if (i < 0)
		    path = "/";
		else
		    path = path.substring(i);
	    }
	    path = decode(path);
	    if (path == null || path.length() == 0 || path.charAt(0) != '/')
		return null;
	    return path.substring(1);
	}

	/** Return the bytes of the requested file, or null if not found. */
	private byte[] getBytes(String path) throws IOException {
	    if (map != null) {
		int i = path.indexOf('/');
		if (i > 0) {
		    JarFile[] jfs = (JarFile[])map.get(path.substring(0, i));
		    if (jfs != null) {
			String jpath = path.substring(i + 1);
			for (i = 0; i < jfs.length; i++) {
			    JarEntry je = jfs[i].getJarEntry(jpath);
			    if (je != null)
				return getBytes(jfs[i].getInputStream(je),
						je.getSize());
			}
		    }
		}
	    }
	    if ('/' != File.separatorChar) {
		path = path.replace('/', File.separatorChar);
	    }
	    for (int i = 0; i < dirs.length; i++) {
		File f = new File(dirs[i] + path);
		if (perms[i].implies(new FilePermission(f.getPath(), "read")))
		{
		    try {
			return getBytes(new FileInputStream(f), f.length());
		    } catch (FileNotFoundException e) {
		    }
		}
	    }
	    return null;
	}

	/** Process the request */
	public void run() {
	    try {
		DataOutputStream out =
		    new DataOutputStream(sock.getOutputStream());
		String req;
		try {
		    req = getInput(sock, true);
		} catch (Exception e) {
		    if (verbose) {
			print("classserver.inputerror",
			      new String[]{sock.getInetAddress().getHostName(),
					   Integer.toString(sock.getPort())});
			e.printStackTrace();
		    }
		    logger.log(Levels.HANDLED, "reading request", e);
		    return;
		}
		if (req == null)
		    return;
		if (req.startsWith("SHUTDOWN *")) {
		    if (verbose)
			print("classserver.shutdown",
			      new String[]{sock.getInetAddress().getHostName(),
					   Integer.toString(sock.getPort())});
		    boolean ok = stoppable;
		    try {
			new ServerSocket(0, 1, sock.getInetAddress());
		    } catch (IOException e) {
			ok = false;
		    }
		    if (!ok) {
			out.writeBytes("HTTP/1.0 403 Forbidden\r\n\r\n");
			out.flush();
			return;
		    }
		    try {
			out.writeBytes("HTTP/1.0 200 OK\r\n\r\n");
			out.flush();
		    } catch (Exception e) {
			if (verbose)
			    e.printStackTrace();
			logger.log(Levels.HANDLED, "writing response", e);
		    }
		    terminate();
		    return;
		}
		String[] args = null;
		if (verbose || logger.isLoggable(Level.FINE))
		    args = new String[]{req,
					sock.getInetAddress().getHostName(),
					Integer.toString(sock.getPort())};
		boolean get = req.startsWith("GET ");
		if (!get && !req.startsWith("HEAD ")) {
		    if (verbose)
			print("classserver.badrequest", args);
		    logger.log(Level.FINE,
			       "bad request \"{0}\" from {1}:{2}", args);
		    out.writeBytes("HTTP/1.0 400 Bad Request\r\n\r\n");
		    out.flush();
		    return;
		}
		String path = req.substring(get ? 4 : 5);
		int i = path.indexOf(' ');
		if (i > 0)
		    path = path.substring(0, i);
		path = canon(path);
		if (path == null) {
		    if (verbose)
			print("classserver.badrequest", args);
		    logger.log(Level.FINE,
			       "bad request \"{0}\" from {1}:{2}", args);
		    out.writeBytes("HTTP/1.0 400 Bad Request\r\n\r\n");
		    out.flush();
		    return;
		}
		if (args != null)
		    args[0] = path;
		if (verbose) {
		    print(get ? "classserver.request" : "classserver.probe",
			  args);
		}
		logger.log(Level.FINER,
			   get ?
			   "{0} requested from {1}:{2}" :
			   "{0} probed from {1}:{2}",
			   args);
		byte[] bytes;
		try {
		    bytes = getBytes(path);
		} catch (Exception e) {
		    if (verbose)
			e.printStackTrace();
		    logger.log(Level.WARNING, "getting bytes", e);
		    out.writeBytes("HTTP/1.0 500 Internal Error\r\n\r\n");
		    out.flush();
		    return;
		}
		if (bytes == null) {
		    if (verbose)
			print("classserver.notfound", path);
		    logger.log(Level.FINE, "{0} not found", path);
		    out.writeBytes("HTTP/1.0 404 Not Found\r\n\r\n");
		    out.flush();
		    return;
		}
		out.writeBytes("HTTP/1.0 200 OK\r\n");
		out.writeBytes("Content-Length: " + bytes.length + "\r\n");
		out.writeBytes("Content-Type: application/java\r\n\r\n");
		if (get)
		    out.write(bytes);
		out.flush();
		if (get)
		    fileDownloaded(path, sock.getInetAddress());
	    } catch (Exception e) {
		if (verbose)
		    e.printStackTrace();
		logger.log(Levels.HANDLED, "writing response", e);
	    } finally {
		try {
		    sock.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    private static ResourceBundle resources;
    private static boolean resinit = false;

    private static synchronized String getString(String key) {
	if (!resinit) {
	    resinit = true;
	    try {
		resources = ResourceBundle.getBundle("com.sun.jini.tool.resources.classserver");
	    } catch (MissingResourceException e) {
		logger.log(Level.WARNING, "missing resource bundle {0}",
			   "com.sun.jini.tool.resources.classserver");
	    }
	}
	if (resources != null) {
	    try {
		return resources.getString(key);
	    } catch (MissingResourceException e) {
	    }
	}
	return null;
    }

    private static void print(String key, String val) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0}";
	System.out.println(MessageFormat.format(fmt, new String[]{val}));
    }

    private static void print(String key, String[] vals) {
	String fmt = getString(key);
	if (fmt == null)
	    fmt = "no text found: \"" + key + "\" {0} {1} {2}";
	System.out.println(MessageFormat.format(fmt, vals));
    }

    /**
     * This method provides a way for subclasses to be notified when a
     * file has been completely downloaded.
     * 
     * @param fp The path to the file that was downloaded.
     */
    protected void fileDownloaded(String fp, InetAddress addr) {
    }

    /**
     * Command line interface for creating an HTTP server.
     * The command line options are:
     * <pre>
     * [-port <var>port</var>] [-dir <var>dirlist</var>] [-dirs <var>dirlist</var>] [-stoppable] [-verbose] [-trees]
     * </pre>
     * The default port is 8080; the default can be overridden with
     * the <code>-port</code> option.  The default directory on Windows is
     * <code>J:</code> and the default on other systems is
     * <code>/vob/jive/lib-dl</code>; the default can be overridden with the
     * <code>-dir</code> or <code>-dirs</code> option, providing one or more
     * directories separated by the {@linkplain File#pathSeparatorChar
     * path-separator character}. All files under these directories (including
     * all subdirectories) are served up via HTTP.  If the pathname of a file
     * is <var>path</var> relative to one of the top-level directories, then
     * the file can be downloaded using the URL
     * <pre>
     * http://<var>host</var>:<var>port</var>/<var>path</var>
     * </pre>
     * If a relative <var>path</var> matches a file under more than one
     * top-level directory, the file under the first top-level directory
     * with a match is used. No caching of directory contents or file contents
     * is performed. <p>
     *
     * If the <code>-stoppable</code> option is given, the HTTP server can be
     * shut down with a custom HTTP <code>SHUTDOWN</code> request originating
     * from the local host. The command line options for stopping an existing
     * HTTP server are:
     * <pre>
     * [-port <var>port</var>] -stop
     * </pre>
     * <p>
     *
     * If the <code>-verbose</code> option is given, then all attempts to
     * download files are output. <p>
     * 
     * The <code>-trees</code> option can be used to serve up individual files
     * stored within JAR and zip files in addition to the files that are
     * served up as described above. If the option is used, the server finds
     * all JAR and zip files in the top-level directories (not in
     * subdirectories).  If the name of the JAR or zip file is
     * <var>name</var><code>.jar</code> or <var>name</var><code>.zip</code>,
     * then any individual file named <var>file</var> within it (or within the
     * JAR or zip files referenced transitively in <code>Class-Path</code>
     * manifest attributes), can be downloaded using a URL of the form:
     * <pre>
     * http://<var>host</var>:<var>port</var>/<var>name</var>/<var>file</var>
     * </pre>
     * If multiple top-level directories have JAR or zip files with the same
     * <var>name</var>, the file under the first top-level directory with a
     * match is used. If a <code>Class-Path</code> element matches a file under
     * more than one top-level directory, the file under the first top-level
     * directory with a match is used. When this option is used, an open file
     * descriptor and cached information is held for each JAR or zip file, for
     * the life of the process.
     */
    public static void main(String[] args) {
	int port = DEFAULT_PORT;
	String dirlist = DEFAULT_DIR;
	if (File.separatorChar == '\\')
	    dirlist = DEFAULT_WIN_DIR;
	boolean trees = false;
	boolean verbose = false;
	boolean stoppable = false;
	boolean stop = false;
	for (int i = 0; i < args.length ; i++ ) {
	    String arg = args[i];
	    if (arg.equals("-port")) {
		i++;
		port = Integer.parseInt(args[i]);
	    } else if (arg.equals("-dir") || arg.equals("-dirs")) {
		i++;
		dirlist = args[i];
	    } else if (arg.equals("-verbose")) {
		verbose = true;
	    } else if (arg.equals("-trees")) {
		trees = true;
	    } else if (arg.equals("-stoppable")) {
		stoppable = true;
	    } else if (arg.equals("-stop")) {
		stop = true;
	    } else {
		print("classserver.usage", (String)null);
		return;
	    }
	}
	try {
	    if (stop) {
		Socket sock = new Socket(InetAddress.getLocalHost(), port);
		try {
		    DataOutputStream out =
			new DataOutputStream(sock.getOutputStream());
		    out.writeBytes("SHUTDOWN *\r\n\r\n");
		    out.flush();
		    String status = getInput(sock, false);
		    if (status != null && status.startsWith("HTTP/")) {
			status = status.substring(status.indexOf(' ') + 1);
			if (status.startsWith("403 ")) {
			    print("classserver.forbidden", status);
			} else if (!status.startsWith("200 ") &&
				   status.indexOf(' ') == 3)
			{
			    print("classserver.status",
				  new String[]{status.substring(0, 3),
					       status.substring(4)});
			}
		    }
		} finally {
		    try {
			sock.close();
		    } catch (IOException e) {
		    }
		}
	    } else {
		new ClassServer(port, dirlist, trees, verbose,
				stoppable).start();
	    }
	} catch (IOException e) {
	    logger.log(Level.WARNING, "requesting shutdown", e);
	}
    }
}
