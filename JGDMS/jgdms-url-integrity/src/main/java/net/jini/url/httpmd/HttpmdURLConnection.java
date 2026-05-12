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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import net.pack200.Pack200;

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
    
    private final boolean pack200;

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
	String filePath = path.substring(0, semiIndex);
	pack200 = filePath.endsWith("pack.gz");
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
    @Override
    public URL getURL() {
	return url;
    }

    /**
     * Returns an input stream that uses MdInputStream to check that the input
     * has the expected message digest.
     */
    @Override
    public InputStream getInputStream() throws IOException {
	try {
	    InputStream result = new MdInputStream(url,
				     delegateConnection.getInputStream(),
				     MessageDigest.getInstance(algorithm),
				     expectedDigest);
	    if (pack200){
		Pack200.Unpacker unpacker = Pack200.newUnpacker();
		ByteArrayOutputStream baos = new ByteArrayOutputStream(102400);
		JarOutputStream jout = new JarOutputStream(new CappedOutputStream(baos, MAX_UNPACKED_JAR_BYTES));
		unpacker.unpack(result, jout);
		result = new JarInputStream(new ByteArrayInputStream(baos.toByteArray()));
	    }
	    return result;
	} catch (NoSuchAlgorithmException e) {
	    throw new IOException("Message digest algorithm not found: " 
		    + algorithm, e);
	}
    }

    private static final int MAX_UNPACKED_JAR_BYTES = 64 * 1024 * 1024; // 64 MB

    private static final class CappedOutputStream extends OutputStream {
	private final OutputStream delegate;
	private long written;
	private final long cap;

	CappedOutputStream(OutputStream delegate, long cap) {
	    this.delegate = delegate;
	    this.cap = cap;
	}

	@Override
	public void write(int b) throws IOException {
	    if (written >= cap)
		throw new IOException("Unpacked JAR exceeds " + cap + " bytes");
	    written++;
	    delegate.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
	    if (written + len > cap)
		throw new IOException("Unpacked JAR exceeds " + cap + " bytes");
	    written += len;
	    delegate.write(b, off, len);
	}
    }
}
