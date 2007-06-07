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

package com.sun.jini.jeri.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;

/**
 * Class representing first line of an HTTP message.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class StartLine {
    
    /** major version number */
    final int major;
    /** minor version number */
    final int minor;
    /** request or response line? */
    final boolean isRequest;
    /** request method */
    final String method;
    /** request URI */
    final String uri;
    /** response status code */
    final int status;
    /** response status reason */
    final String reason;
    
    /**
     * Creates start line for HTTP request message.
     */
    StartLine(int major, int minor, String method, String uri) {
	this.major = major;
	this.minor = minor;
	this.method = method;
	this.uri = uri;
	status = -1;
	reason = null;
	isRequest = true;
    }
    
    /**
     * Creates start line for HTTP response message.
     */
    StartLine(int major, int minor, int status, String reason) {
	this.major = major;
	this.minor = minor;
	this.status = status;
	this.reason = reason;
	method = null;
	uri = null;
	isRequest = false;
    }
    
    /**
     * Reads start line from given input stream.
     */
    StartLine(InputStream in) throws IOException {
	String line = MessageReader.readLine(in);
	if (line == null) {
	    throw new HttpParseException("unexpected EOF in start line");
	}

	try {
	    StringTokenizer tok = new StringTokenizer(line, "", true);
	    if (line.startsWith("HTTP")) {
		if (!tok.nextToken("/").equals("HTTP")) {
		    throw new HttpParseException();
		}
		tok.nextToken();
		major = Integer.parseInt(tok.nextToken("."));
		tok.nextToken();
		minor = Integer.parseInt(tok.nextToken(" "));
		tok.nextToken();
		status = Integer.parseInt(tok.nextToken());
		tok.nextToken();
		reason = tok.nextToken("\n");

		method = null;
		uri = null;
		isRequest = false;
	    } else {
		method = tok.nextToken(" ");
		tok.nextToken();
		uri = tok.nextToken();
		tok.nextToken();
		if (!tok.nextToken("/").equals("HTTP")) {
		    throw new HttpParseException();
		}
		tok.nextToken();
		major = Integer.parseInt(tok.nextToken("."));
		tok.nextToken();
		minor = Integer.parseInt(tok.nextToken("\n"));
		
		status = -1;
		reason = null;
		isRequest = true;
	    }
	} catch (Exception ex) {
	    throw (HttpParseException) 
		new HttpParseException("invalid start line").initCause(ex);
	}
    }
    
    /**
     * Writes start line to given output stream.
     */
    void write(OutputStream out) throws IOException {
	String version = "HTTP/" + major + "." + minor;
	MessageWriter.writeLine(out, isRequest ?
				     method + " " + uri + " " + version :
				     version + " " + status + " " + reason);
    }
    
    /**
     * Compares two sets of major/minor version numbers.  Returns -1 if
     * major1/minor1 is less than major2/minor2, 1 if major1/minor1 is more
     * than major2/minor2, and 0 if the two pairs are equal.
     */
    static int compareVersions(int major1, int minor1, int major2, int minor2)
    {
	if (major1 != major2) {
	    return (major1 > major2) ? 1 : -1;
	} else if (minor1 != minor2) {
	    return (minor1 > minor2) ? 1 : -1;
	} else {
	    return 0;
	}
    }
}
