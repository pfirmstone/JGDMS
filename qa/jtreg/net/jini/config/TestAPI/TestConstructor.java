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
/* @test 
 * @summary Tests ConfigurationFile constructors
 * 
 * @library ../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestConstructor
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationNotFoundException;

public class TestConstructor extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getProperty("java.security.properties") == null) {
	    System.setProperty("java.security.properties",
			       src + File.separator + "security.properties");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    static Test[] tests = {
	new TestConstructor(null, ConfigurationFile.class),
	new TestConstructor(new String[0], ConfigurationFile.class),
	new TestConstructor(sa(null), ConfigurationException.class),
	new TestConstructor(sa(""), ConfigurationNotFoundException.class),
	new TestConstructor(sa("-"), ConfigurationFile.class),
	new TestConstructor(sa("foo"), ConfigurationNotFoundException.class),

	/*
	 * These sources have invalid URL syntax, but ConfigurationFile assumes
	 * that they are files and then discovers that the files don't exist.
	 */
	new TestConstructor(sa("http://localhost:jdf/"),
			    ConfigurationNotFoundException.class),
	new TestConstructor(sa("foo://"), ConfigurationNotFoundException.class),
	/* No server on port */
	new Object() {
	    Test test() {
		try {
		    ServerSocket ss = new ServerSocket(0);
		    int port = ss.getLocalPort();
		    ss.close();
		    return new TestConstructor(sa("http://localhost:" + port),
					       ConfigurationException.class);
		} catch (IOException e) {
		    throw unexpectedException(e);
		}
	    }
	}.test(),
	new Object() {
	    HttpServer server = new HttpServer();
	    Test test() {
		return new TestConstructor(
		    sa("http://localhost:" + server.port + "/unknown-file"),
		    ConfigurationNotFoundException.class)
		{
		    public Object run() {
			try {
			    return super.run();
			} finally {
			    server.shutdown();
			}
		    }
		};
	    }
	}.test(),
	new TestConstructor(sa("file:" + File.separator + "unknown-file"),
			    ConfigurationNotFoundException.class),
	new TestConstructor(sa("file:" + File.separator),
			    ConfigurationException.class),
	new TestConstructor(sa(src + "config"), ConfigurationFile.class),
	new TestConstructor(sa("file:" + src.replace(File.separatorChar, '/') +
			       "config"),
			    ConfigurationFile.class),

	new TestConstructor(sa(src + "config"), null, ConfigurationFile.class),
	new TestConstructor(sa(src + "config"),
			    new FileClassLoader(new HashMap()),
			    ConfigurationFile.class),
    };

    private final String[] options;
    private boolean clSupplied;
    private ClassLoader cl;

    public static void main(String[] args) throws Exception {
	test(tests);
    }

    private TestConstructor(String[] options, Class resultType) {
	super(toString(options), resultType);
	this.options = options;
    }

    private TestConstructor(String[] options,
			    ClassLoader cl,
			    Class resultType)
    {
	super(toString(options) + ", " + cl, resultType);
	this.options = options;
	this.clSupplied = true;
	this.cl = cl;
    }

    public Object run() {
	try {
	    if (clSupplied) {
		return new ConfigurationFile(options, cl);
	    } else {
		return new ConfigurationFile(options);
	    }
	} catch (ConfigurationException e) {
	    return e;
	}
    }

    public void check(Object result) {
	if (result.getClass() != getCompareTo()) {
	    throw new FailedException(
		"Should be of type " + getCompareTo());
	}
    }

    static String[] sa(String s) {
	return new String[] { s };
    }

    /**
     * Defines a simple, single-threaded HTTP server, that returns 'Not Found'
     * for all GET requests, and 'Bad Request' for all other requests.
     */
    static class HttpServer extends Thread {
	ServerSocket ss;
	int port;

	HttpServer() {
	    super("HttpServer");
	    try {
		ss = new ServerSocket(0);
		port = ss.getLocalPort();
	    } catch (IOException e) {
		throw unexpectedException(e);
	    }
	    setDaemon(true);
	    start();
	}

	public void run() {
	    try {
		while (true) {
		    Socket s = ss.accept();
		    try {
			BufferedReader in =
			    new BufferedReader(
				new InputStreamReader(
				    s.getInputStream()));
			String req = in.readLine();
			String line;
			do {
			    line = in.readLine();
			} while (line != null
				 && line.length() > 0
				 && line.charAt(0) != '\r'
				 && line.charAt(0) != '\n');
			DataOutputStream out =
			    new DataOutputStream(s.getOutputStream());
			if (req.startsWith("GET")) {
			    out.writeBytes("HTTP/1.0 404 Not Found\r\n\r\n");
			} else {
			    out.writeBytes("HTTP/1.0 400 Bad Request\r\n\r\n");
			}
			out.flush();
		    } finally {
			try {
			    s.close();
			} catch (IOException e) {
			}
		    }
		}
	    } catch (IOException e) {
		debugPrint(30, e.toString());
	    }
	}

	void shutdown() {
	    try {
		ss.close();
	    } catch (IOException e) {
		debugPrint(30, e.toString());
	    }
	}
    }
}
