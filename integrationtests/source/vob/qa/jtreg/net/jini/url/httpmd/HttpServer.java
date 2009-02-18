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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/** Defines a simple, single-threaded HTTP server. */
public class HttpServer extends Thread {
    private final String directory;
    private final ServerSocket ss;
    final int port;

    public static void main(String[] args) throws Exception {
	new HttpServer(args[0]).join();
    }

    HttpServer(String directory) throws IOException {
	super("HttpServer");
	this.directory = directory;
	ss = new ServerSocket(0);
	port = ss.getLocalPort();
	setDaemon(true);
	start();
    }

    public void run() {
	debugPrint("Started, directory: " + directory);
	try {
	    while (true) {
		Socket s = ss.accept();
		BufferedReader in = new BufferedReader(
		    new InputStreamReader(s.getInputStream()));
		String req = in.readLine();
		String line;
		do {
		    line = in.readLine();
		} while (line != null
			 && line.length() > 0
			 && line.charAt(0) != '\r'
			 && line.charAt(0) != '\n');
		debugPrint(req);
		DataOutputStream out =
		    new DataOutputStream(s.getOutputStream());
		if (!req.startsWith("GET ")) {
		    debugPrint("Bad Request");
		    out.writeBytes("HTTP/1.0 400 Bad Request\r\n\r\n");
		} else {
		    String path = req.substring(4);
		    int i = path.indexOf(' ');
		    if (i > 0)
			path = path.substring(0, i);
		    try {
			byte[] bytes = getBytes(path);
			debugPrint("OK");
			out.writeBytes(
			    "HTTP/1.0 200 OK\r\n" +
			    "Content-Length: " + bytes.length + "\r\n" +
			    "Content-Type: " + "application/java\r\n\r\n");
			out.write(bytes);
		    } catch (FileNotFoundException e) {
			debugPrint("Not Found");
			out.writeBytes("HTTP/1.0 404 Not Found\r\n\r\n");
		    }
		}
		out.flush();
		s.close();
	    }
	} catch (Throwable e) {
	    debugPrint(e.toString());
	}
    }

    void shutdown() {
	debugPrint("Shutdown"); 
	try {
	    ss.close();
	} catch (IOException e) {
	}
    }

    private void debugPrint(String message) {
	UnitTestUtilities.debugPrint(
	    30, "HTTP server " + port + ": " + message);
    }

    private byte[] getBytes(String path) throws IOException {
	File file = new File(
	    directory + path.replace('/', File.separatorChar));
	int length = (int) file.length();
	DataInputStream in = new DataInputStream(new FileInputStream(file));
	byte[] bytes = new byte[length];
	try {
	    in.readFully(bytes);
	} finally {
	    in.close();
	}
	return bytes;
    }
}
