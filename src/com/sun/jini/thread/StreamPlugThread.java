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
package com.sun.jini.thread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <code>StreamPlugThread</code> is a utility class that "plugs" two streams,
 * one input stream and one output stream, together by creating a thread that
 * repeatedly reads any data available from the input stream and writes it to
 * the output stream.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class StreamPlugThread extends Thread {

    private InputStream in;
    private OutputStream out;

    public StreamPlugThread(InputStream in, OutputStream out) {
	this.in  = in;
	this.out = out;
    }

    public void run() {
	byte[] buf = new byte[2048];
	int count;
	try {
	    while ((count = in.read(buf)) != -1) {
		out.write(buf, 0, count);
		out.flush();
	    }
	} catch (IOException e) {
	}
    }

    public static void plugTogether(InputStream in, OutputStream out) {
	(new StreamPlugThread(in, out)).start();
    }

    public static void plugTogether(OutputStream out, InputStream in) {
	(new StreamPlugThread(in, out)).start();
    }

    public static Process userProg(String cmd)
	throws IOException
    {
	Process proc = Runtime.getRuntime().exec(cmd);
	plugTogether(System.in,  proc.getOutputStream());
	plugTogether(System.out, proc.getInputStream());
	plugTogether(System.err, proc.getErrorStream());
	return proc;
    }

    public static void main(String[] args) {
	if (args.length != 1)
	    error("usage: StreamPlugThread \"command\"");

	try {
	    userProg(args[0]);
	} catch (IOException e) {
	    error("I/O Exception: " + e);
	}
    }

    public static void error(String err) {
	System.err.println("StreamPlugThread: " + err);
	System.exit(1); // non-zero argument means "not good"
    }
}
