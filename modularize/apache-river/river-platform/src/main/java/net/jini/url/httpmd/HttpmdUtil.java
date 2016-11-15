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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.StringTokenizer;
import net.jini.security.Security;

/**
 * Provides utility methods for creating and using HTTPMD URLs.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class HttpmdUtil {

    /**
     * A URL handler to use for HTTPMD URLs so we don't require the protocol
     * handler to be installed.
     */
    private static final Handler handler = new Handler();

    /** Not instantiable. */
    private HttpmdUtil() { }

    /**
     * Computes the message digest of data specified by a URL.
     *
     * @param url the URL of the data
     * @param algorithm the message digest algorithm to use
     * @return the message digest, as a <code>String</code> in hexadecimal
     *	       format
     * @throws IOException if an I/O exception occurs while reading data from
     *	       the URL
     * @throws NoSuchAlgorithmException if no provider is found for the message
     *	       digest algorithm
     * @throws NullPointerException if either argument is <code>null</code>
     */
    public static String computeDigest(URL url, String algorithm)
	throws IOException, NoSuchAlgorithmException
    {
	return computeDigest(url.openStream(), algorithm);
    }

    /** Computes the message digest for an input stream. */
    private static String computeDigest(InputStream in, String algorithm)
	throws IOException, NoSuchAlgorithmException
    {
	try {
	    if (!(in instanceof BufferedInputStream)) {
		in = new BufferedInputStream(in, 2048);
	    }
	    MessageDigest md = MessageDigest.getInstance(algorithm);
	    byte[] buf = new byte[2048];
	    while (true) {
		int n = in.read(buf);
		if (n < 0) {
		    break;
		}
		md.update(buf, 0, n);
	    }
	    return digestString(md.digest());
	} finally {
	    try {
		in.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Computes the message digests for a codebase with HTTPMD URLs.
     * <p>
     * Do not use a directory on a remote filesystem, or a directory URL, if
     * the underlying network access protocol does not provide adequate data
     * integrity or authentication of the remote host.
     *
     * @param sourceDirectory the filename or URL of the directory containing
     *	      the source files corresponding to the URLs in
     *	      <code>codebase</code>
     * @param codebase a space-separated list of HTTPMD URLs. The digest
     *	      values specified in the URLs will be ignored. The path portion of
     *	      the URLs, without the message digest parameters, will be used to
     *	      specify the source files, relative to
     *	      <code>sourceDirectory</code>, to use for computing message
     *	      digests.
     * @return the codebase updated to include computed digests
     * @throws IllegalArgumentException if any of the URLs in
     *	       <code>codebase</code> fail to specify the HTTPMD protocol
     * @throws IOException if an I/O exception occurs while reading data from
     *	       the source files
     * @throws MalformedURLException if any of the URLs in
     *	       <code>codebase</code> have incorrect syntax
     * @throws NullPointerException if either argument is <code>null</code>
     */
    public static String computeDigestCodebase(String sourceDirectory,
					       String codebase)
	throws IOException, MalformedURLException, NullPointerException
    {
	boolean isURL;
	try {
	    new URL(sourceDirectory);
	    isURL = true;
	} catch (MalformedURLException e) {
	    isURL = false;
	}
	if (sourceDirectory.endsWith(isURL ? "/" : File.separator)) {
	    sourceDirectory =
		sourceDirectory.substring(0, sourceDirectory.length() - 1);
	}
	StringTokenizer specs = new StringTokenizer(codebase);
	StringBuffer sb = new StringBuffer();
	boolean first = true;
	while (specs.hasMoreTokens()) {
	    final String spec = specs.nextToken();
	    if (!"httpmd:".regionMatches(true, 0, spec, 0, 7)) {
		throw new IllegalArgumentException(
		    "Codebase URL does not specify HTTPMD protocol: " + spec);
	    }
	    /*
	     * Use doPrivileged so caller doesn't need
	     * java.net.NetPermission("specifyStreamHandler") to specify the
	     * URL stream handler.
	     */
	    URL url;
	    try {
		url = (URL) Security.doPrivileged(
		    new PrivilegedExceptionAction() {
			public Object run() throws MalformedURLException {
			    return new URL(null, spec, handler);
			}
		    });
	    } catch (PrivilegedActionException e) {
		throw (MalformedURLException) e.getCause();
	    }
	    String path = url.getPath();
	    int paramIndex = path.lastIndexOf(';');
	    int equalsIndex = path.indexOf('=', paramIndex);
	    int commentIndex = path.indexOf(',', equalsIndex);
	    String algorithm = path.substring(paramIndex + 1, equalsIndex);
	    URI relSourceURI;
	    try {
		relSourceURI = new URI(
		    "file:" + (path.startsWith("/") ? "" : "/") +
		    path.substring(0, path.indexOf(';')));
	    } catch (URISyntaxException e) {
		throw new MalformedURLException(
		    "Problem with codebase URL " + spec + ": " +
		    e.getMessage());
	    }
	    InputStream in;
	    if (isURL) {
		String relSource = relSourceURI.getRawPath();
		in = new URL(sourceDirectory + relSource).openStream();
	    } else {
		String relSource = relSourceURI.getPath();
		if ('/' != File.separatorChar) {
		    relSource = relSource.replace('/', File.separatorChar);
		}
		in = new FileInputStream(sourceDirectory + relSource);
	    }
	    String digest;
	    try {
		digest = computeDigest(in, algorithm);
	    } catch (NoSuchAlgorithmException e) {
		/* Shouldn't happen -- URL constructor should find this */
		throw new RuntimeException("Shouldn't happen: " + e);
	    }
	    URL result = new URL(
		url,
		path.substring(0, equalsIndex + 1) + digest +
		(commentIndex < 0 ? "" : path.substring(commentIndex)) +
		(url.getQuery() == null ? "" : '?' + url.getQuery()) +
		(url.getRef() == null ? "" : '#' + url.getRef()));
	    if (!first) {
		sb.append(' ');
	    } else {
		first = false;
	    }
	    sb.append(result);
	}
	return sb.toString();
    }

    /** Converts a message digest to a String in hexadecimal format. */
    static String digestString(byte[] digest) {
	StringBuffer sb = new StringBuffer(digest.length * 2);
	for (int i = 0; i < digest.length; i++) {
	    byte b = digest[i];
	    sb.append(Character.forDigit((b >> 4) & 0xf, 16));
	    sb.append(Character.forDigit(b & 0xf, 16));
	}
	return sb.toString();
    }

    /** Converts a String in hexadecimal format to a message digest. */
    static byte[] stringDigest(String s) throws NumberFormatException {
	byte[] result = new byte[(s.length() + 1) / 2];
	int rpos = result.length;
	int last = -1;
	for (int spos = s.length(); --spos >= 0; ) {
	    int digit = Character.digit(s.charAt(spos), 16);
	    if (digit < 0) {
		throw new NumberFormatException(
		    "Illegal hex digit: '" + s.charAt(spos) + "'");
	    }
	    if (last < 0) {
		last = digit;
	    } else {
		result[--rpos] = (byte) (last + (digit << 4));
		last = -1;
	    }
	}
	if (last >= 0) {
	    result[--rpos] = (byte) last;
	}
	return result;
    }

    /**
     * Returns true if the character is permitted in an HTTPMD comment.  Legal
     * comment characters are ASCII letters and numbers, plus '-', '_', '.',
     * '~', '*', ''', '(', ')', ':', '@', '&amp;', '=', '+', '$', and ','.
     */
    static boolean commentChar(char c) {
	return (c >= 'a' && c <= 'z')
	    || (c >= 'A' && c <= 'Z')
	    || (c >= '0' && c <= '9')
	    || ("-_.~*'():@&=+$,".indexOf(c) >= 0);
    }
}
