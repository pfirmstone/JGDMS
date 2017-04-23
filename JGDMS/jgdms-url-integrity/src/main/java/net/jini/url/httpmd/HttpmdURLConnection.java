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

package net.jini.url.httpmd;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An HTTP URL connection for HTTPMD URLs.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class HttpmdURLConnection extends DelegatingHttpURLConnection {

    /** The message digest algorithm. */ 
    private final String algorithm;

    /** The expected message digest for the URL data. */
    private final byte[] expectedDigest;

    /** The URL specifying the location of the data. */
    private final URL content;

    /** Creates an HTTP URL connection for an HTTPMD URL. */
    HttpmdURLConnection(URL url) throws IOException, MalformedURLException {
	super(url);

	/* Check syntax of message digest parameter */
	String path = url.getPath() == null ? "" : url.getPath();
	int semiIndex = path.lastIndexOf(';');
	if (semiIndex < 0) {
	    throw new MalformedURLException(
		"Message digest parameter is missing");
	}
	int equalsIndex = path.indexOf('=', semiIndex);
	if (equalsIndex < 0) {
	    throw new MalformedURLException(
		"Message digest parameter is missing a '='");
	}
	algorithm = path.substring(semiIndex + 1, equalsIndex);
	try {
	    MessageDigest.getInstance(algorithm);
	} catch (NoSuchAlgorithmException e) {
	    MalformedURLException t = new MalformedURLException(
		"Message digest parameter algorithm is not found: " +
		algorithm);
	    t.initCause(e);
	    throw t;
	}
	String digest = path.substring(equalsIndex + 1);
	int comment = digest.indexOf(',');
	if (comment >= 0) {
	    for (int i = digest.length(); --i > comment; ) {
		char c = digest.charAt(i);
		if (!HttpmdUtil.commentChar(c)) {
		    throw new MalformedURLException(
			"Comment contains illegal character: '" + c + "'");
		}
	    }
	    digest = digest.substring(0, comment);
	}
	int length = digest.length();
	if (length == 0) {
	    throw new MalformedURLException(
		"Message digest parameter digest is empty");
	}
	try {
	    expectedDigest = HttpmdUtil.stringDigest(digest);
	} catch (NumberFormatException e) {
	    MalformedURLException t = new MalformedURLException(
		"Message digest parameter has invalid format for digest: " +
		digest);
	    t.initCause(e);
	    throw t;
	}
	try {
	    content = new URL(
		"http", url.getHost(), url.getPort(),
		path.substring(0, semiIndex) +
		(url.getQuery() == null ? "" : url.getQuery()) +
		(url.getRef() == null ? "" : url.getRef()));
	} catch (MalformedURLException e) {
	    MalformedURLException t = new MalformedURLException(
		"Problem with content location");
	    t.initCause(e);
	    throw t;
	}

	delegateConnection = (HttpURLConnection) content.openConnection();
    }

    /** Returns our URL, not the one for the HTTP connection. */
    public URL getURL() {
	return url;
    }

    /**
     * Returns an input stream that uses MdInputStream to check that the input
     * has the expected message digest.
     */
    public InputStream getInputStream() throws IOException {
	try {
	    return new MdInputStream(url,
				     delegateConnection.getInputStream(),
				     MessageDigest.getInstance(algorithm),
				     expectedDigest);
	} catch (NoSuchAlgorithmException e) {
	    IOException t = new IOException(
		"Message digest algorithm not found: " + algorithm);
	    t.initCause(e);
	    throw t;
	}
    }
}
