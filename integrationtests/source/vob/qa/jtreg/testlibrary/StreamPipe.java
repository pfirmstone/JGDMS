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
/**
 * 
 */

import java.io.*;

/**
 * Pipe output of one stream into input of another.
 */
public class StreamPipe extends Thread {
    
    private InputStream in;
    private OutputStream out;
    private String preamble;
    private static Object lock = new Object();
    private static int count = 0;

    public StreamPipe(InputStream in, OutputStream out, String name) {
	this(in, out, name, "# ");
    }

    public StreamPipe(InputStream in, OutputStream out, String name, String preamble) {
	super(name);
	this.in  = in;
	this.out = out;
	this.preamble = ((preamble == null) ? "# " : preamble);
    }
    
    public void run() {
	BufferedReader r = new BufferedReader(new InputStreamReader(in), 1);
	BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out));
	byte[] buf = new byte[256];
	boolean bol = true;	// beginning-of-line
	int count;
	
	try {
	    String line;
	    while ((line = r.readLine()) != null) {
		w.write(preamble);
		w.write(line);
		w.newLine();
		w.flush();
	    }
	} catch (IOException e) {
	    System.err.println("*** IOException in StreamPipe.run:");
	    e.printStackTrace();
	}
    }
    
    public static void plugTogether(InputStream in, OutputStream out, String preamble) {
	String name = null;
	
	synchronized (lock) {
	    name = "TestLibrary: StreamPipe-" + (count ++ );
	}

	Thread pipe = new StreamPipe(in, out, name, preamble);
	pipe.setDaemon(true);
	pipe.start();
    }
}
