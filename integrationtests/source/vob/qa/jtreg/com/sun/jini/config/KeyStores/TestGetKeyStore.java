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
 * @summary Tests KeyStores.getKeyStore
 * @author Tim Blackman
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @run main/othervm/policy=policy TestGetKeyStore
 */

import com.sun.jini.config.KeyStores;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;

public class TestGetKeyStore extends BasicTest {

    static final String src =
	System.getProperty("test.src", ".") + File.separator;

    static {
	if (System.getProperty("java.security.policy") == null) {
	    System.setProperty("java.security.policy", src + "policy");
	}
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
    }

    static Test[] tests = {
	new TestGetKeyStore(null, null, NullPointerException.class),
	new TestGetKeyStore("not-found", null, FileNotFoundException.class),
	new TestGetKeyStore(src + "keystore", null, KeyStore.class),
	new TestGetKeyStore("file:" + src + "keystore", null, KeyStore.class),
	new TestGetKeyStore("file:" + src + "policy", null, IOException.class),
	new TestGetKeyStore(
	    "badprot://foo", null, FileNotFoundException.class),
	new TestGetKeyStore(
	    "http://unknwnxx/foo", null, UnknownHostException.class),
	testWithHttpServer("unknown-file", null, FileNotFoundException.class),
	testWithHttpServer("keystore", null, KeyStore.class),
	testWithHttpServer("policy", null, IOException.class),
	new TestGetKeyStore(src + "keystore", "bad", KeyStoreException.class),
	new TestGetKeyStore(
	    src + "keystore", KeyStore.getDefaultType(), KeyStore.class)
    };

    private final String location;
    private final String type;

    public static void main(String[] args) throws Exception {
	test(tests);
    }

    private TestGetKeyStore(String location, String type, Class resultType) {
	super(location + ", " + type, resultType);
	this.location = location;
	this.type = type;
    }

    public Object run() {
	try {
	    return KeyStores.getKeyStore(location, type);
	} catch (Exception e) {
	    return e;
	}
    }

    public void check(Object result) {
	if (result.getClass() != getCompareTo()) {
	    throw new FailedException(
		"Should be of type " + getCompareTo());
	}
    }

    static Test testWithHttpServer(String file, String type, Class resultType)
    {
	final HttpServer server = new HttpServer();
	return new TestGetKeyStore(
	    "http://localhost:" + server.port + "/" + file, type, resultType)
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

    /**
     * Defines a simple, single-threaded HTTP server, that returns the contents
     * of files in this directory for GET requests and 'Bad Request' for all
     * other requests.
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
			handleRequest(req, s);
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

	void handleRequest(String req, Socket s) throws IOException {
	    DataOutputStream out =
		new DataOutputStream(s.getOutputStream());
	    if (!req.startsWith("GET")) {
		out.writeBytes("HTTP/1.0 400 Bad Request\r\n\r\n");
	    } else {
		String path = req.substring(4);
		if (path.startsWith("/")) {
		    path = path.substring(1);
		}
		int i = path.indexOf(' ');
		if (i > 0) {
		    path = path.substring(0, i);
		}
		try {
		    byte[] bytes = getBytes(path);
		    out.writeBytes("HTTP/1.0 200 OK\r\n");
		    out.writeBytes("Content-Length: " + bytes.length + "\r\n");
		    out.writeBytes("Content-Type: text\r\n\r\n");
		    out.write(bytes);
		} catch (FileNotFoundException e) {
		    debugPrint(30, e.toString());
		    out.writeBytes("HTTP/1.0 404 Not Found\r\n\r\n");
		}
	    }
	    out.flush();
	}

	byte[] getBytes(String path) throws IOException {
	   File file = new File(src + path);
	   DataInputStream in =
	       new DataInputStream(
		   new BufferedInputStream(
		       new FileInputStream(file)));
	    byte[] bytes = new byte[(int) file.length()];
	    try {
		in.readFully(bytes);
	    } finally {
		in.close();
	    }
	    return bytes;
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
