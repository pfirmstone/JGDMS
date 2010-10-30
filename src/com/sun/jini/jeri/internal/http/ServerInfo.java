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

import com.sun.jini.jeri.internal.runtime.BASE64Encoder;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote HTTP server version/authentication information.
 * 
 * REMIND: need manage/null out password more strictly?
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class ServerInfo implements Cloneable {
    
    /** blank timestamp value */
    static final long NO_TIMESTAMP = -1L;
    
    /** hexadecimal char conversion table */
    private static final char[] hexChars = {
	'0', '1', '2', '3', '4', '5', '6', '7',
	'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /** server host name */
    final String host;
    /** server port */
    final int port;
    /** HTTP major version */
    int major = 1;
    /** HTTP minor version */
    int minor = 0;
    /** authentication scheme, if any */
    String authScheme;
    /** authentication realm */
    String authRealm;
    /** authentication algorithm */
    String authAlgorithm;
    /** authentication cookie */
    String authOpaque;
    /** authentication challenge */
    String authNonce;
    /** authentication username */
    String authUser;
    /** authentication password */
    String authPassword;
    /** time of last update */
    long timestamp = NO_TIMESTAMP;

    /**
     * Creates new ServerInfo for server at given host/port.
     */
    ServerInfo(String host, int port) {
	this.host = host;
	this.port = port;
    }
    
    /**
     * Sets authentication information based on contents of given challenge
     * string (which should be the value of either the "WWW-Authenticate" or
     * "Proxy-Authenticate" header fields).  If given string is null or empty,
     * clears any previous authentication information.
     */
    void setAuthInfo(String str) throws HttpParseException {
	if (str == null || str.length() == 0) {
	    authScheme = null;
	    authRealm = null;
	    authAlgorithm = null;
	    authOpaque = null;
	    authNonce = null;
	    authUser = null;
	    authPassword = null;
	    return;
	}
	
	LineParser lp = new LineParser(str);
	Map entries = lp.getEntries("Digest");
	if (entries != null) {
	    String realm = (String) entries.get("realm");
	    String nonce = (String) entries.get("nonce");
	    if (realm != null && nonce != null) {
		authScheme = "Digest";
		authRealm = realm;
		authNonce = nonce;
		authAlgorithm = (String) entries.get("algorithm");
		authOpaque = (String) entries.get("opaque");
		if (!"true".equalsIgnoreCase((String) entries.get("stale"))) {
		    authUser = null;
		    authPassword = null;
		}
		return;
	    }
	}

	if ((entries = lp.getEntries("Basic")) != null) {
	    String realm = (String) entries.get("realm");
	    if (realm != null) {
		authScheme = "Basic";
		authRealm = realm;
		authAlgorithm = null;
		authOpaque = null;
		authNonce = null;
		authUser = null;
		authPassword = null;
		return;
	    }
	}

	// REMIND: no supported schemes found; clear auth info?
    }

    /**
     * Updates authentication information based on contents of given string
     * (which should be the value of either the "Authorization-Info" or
     * "Proxy-Authorization-Info" header fields).  If given string is null or
     * empty, current authentication settings are left unchanged.
     */
    void updateAuthInfo(String str) throws HttpParseException {
	if (str == null || str.length() == 0) {
	    return;
	}
	if ("Digest".equals(authScheme)) {
	    LineParser lp = new LineParser(str);
	    Map entries = lp.getAllEntries();
	    String nextNonce = (String) entries.get("nextnonce");
	    if (nextNonce != null) {
		authNonce = nextNonce;
	    }
	}
    }
    
    /**
     * Returns (possibly null) authorization string based on current
     * authentication information in conjunction with the given request
     * arguments.
     */
    String getAuthString(String protocol, String method, String uri) {
	if (authScheme == null) {
	    return null;
	}

	if (authUser == null) {
	    PasswordAuthentication pa = getPassword(protocol);
	    if (pa == null) {
		return null;
	    }
	    String user = pa.getUserName();
	    char[] password = pa.getPassword();
	    if (user == null || password == null) {
		return null;
	    }
	    authUser = user;
	    authPassword = new String(password);
	}
	
	if (authScheme.equals("Basic")) {
	    BASE64Encoder enc = new BASE64Encoder();
	    return "Basic " + 
		enc.encode((authUser + ":" + authPassword).getBytes());
	} else if (authScheme.equals("Digest")) {
	    String digest;
	    try {
		digest = computeDigest(method, uri);
	    } catch (NoSuchAlgorithmException ex) {
		return null;
	    }

	    String response = "Digest " +
		"username=\"" + authUser + "\", " +
		"realm=\"" + authRealm + "\", " +
		"nonce=\"" + authNonce + "\", " +
		"uri=\"" + uri + "\", " +
		"response=\"" + digest + "\"";
	    if (authOpaque != null) {
		response += ", opaque=\"" + authOpaque + "\"";
	    }
	    if (authAlgorithm != null) {
		response += ", algorithm=" + authAlgorithm;
	    }
	    return response;
	} else {
	    throw new InternalError();
	}
    }
    
    /**
     * Computes digest authentication response for request using the given
     * method and uri.  Throws NoSuchAlgorithmException if server-specified
     * digest algorithm not supported.
     */
    private String computeDigest(String method, String uri) 
	throws NoSuchAlgorithmException
    {
	// REMIND: cache MessageDigest?
	MessageDigest md = MessageDigest.getInstance(
	    (authAlgorithm != null) ? authAlgorithm : "MD5");
	String hashA1 = 
	    encode(md, authUser + ":" + authRealm + ":" + authPassword);
	String hashA2 = encode(md, method + ":" + uri);
	return encode(md, hashA1 + ":" + authNonce + ":" + hashA2);
    }
    
    /**
     * Returns digest of the given string, represented as string of hexadecimal
     * digits.
     */
    private String encode(MessageDigest md, String str) {
	md.reset();
	byte[] digest = md.digest(str.getBytes());
	StringBuffer sbuf = new StringBuffer(digest.length * 2);
	for (int i = 0; i < digest.length; i++) {
	    sbuf.append(hexChars[(digest[i] >>> 4) & 0xF]);
	    sbuf.append(hexChars[digest[i] & 0xF]);
	}
	return sbuf.toString();
    }

    public Object clone() {
	try {
	    return super.clone();
	} catch (CloneNotSupportedException ex) {
	    throw new InternalError();
	}
    }
    
    /**
     * Class for parsing multi-part HTTP header lines that may appear as the
     * values of the WWW-Authenticate, Proxy-Authenticate, Authorization-Info
     * or Proxy-Authorization-Info header lines.
     */
    private static class LineParser {
	
	/* token types */
	private static final int EOL    = -1;
	private static final int WORD   = 0;
	private static final int QUOTE  = 1;
	private static final int COMMA  = 2;
	private static final int EQUALS = 3;

	private final List entries = new ArrayList();
	private final char[] ca;
	private int pos = 0;
	private String tokenString = null;
	
	/**
	 * Attempts to parse the given line into a series of key/optional value
	 * definitions.  Throws an HttpParseException if the line contains
	 * syntax errors.
	 */
	LineParser(String line) throws HttpParseException {
	    ca = line.toCharArray();
	    int tok = nextToken();

	    while (tok != EOL) {
		String key;

		if (tok == COMMA) {
		    tok = nextToken();
		    continue;
		} else if (tok == WORD) {
		    key = tokenString;
		} else {
		    throw new HttpParseException("illegal key");
		}
		
		tok = nextToken();
		if (tok == COMMA) {
		    entries.add(new String[] { key, null });
		    tok = nextToken();
		    continue;
		} else if (tok != EQUALS) {
		    entries.add(new String[] { key, null });
		    continue;
		}
		
		tok = nextToken();
		if (tok != WORD && tok != QUOTE) {
		    throw new HttpParseException("illegal value");
		}
		entries.add(new String[] { key, tokenString });
		
		tok = nextToken();
		if (tok == COMMA) {
		    tok = nextToken();
		    continue;
		} else if (tok != EOL) {
		    throw new HttpParseException("illegal separator");
		}
	    }
	}
	
	/**
	 * Returns code indicating next token in line.  If token type is WORD,
	 * tokenString is set to the word text; if returned type is QUOTE,
	 * tokenString is set to the quoted string's contents.
	 */
	private int nextToken() throws HttpParseException {
	    int mark;

	    while (pos < ca.length && Character.isWhitespace(ca[pos])) {
		pos++;
	    }
	    if (pos >= ca.length) {
		return EOL;
	    }
	    switch (ca[pos]) {
		case ',':
		    pos++;
		    return COMMA;
		    
		case '=':
		    pos++;
		    return EQUALS;
		    
		case '\"':
		    mark = ++pos;
		    while (pos < ca.length && ca[pos] != '\"') {
			pos++;
		    }
		    if (pos >= ca.length) {
			throw new HttpParseException(
			    "unterminated quote string");
		    }
		    tokenString = new String(ca, mark, pos++ - mark);
		    return QUOTE;
		    
		default:
		    mark = pos;
		    while (pos < ca.length) {
			char c = ca[pos];
			if (c == ',' || c == '=' || c == '\"' ||
			    Character.isWhitespace(c))
			{
			    break;
			}
			pos++;
		    }
		    tokenString = new String(ca, mark, pos - mark);
		    return WORD;
	    }
	}

	/**
	 * Returns all key/value entries associated with the given
	 * authorization scheme in the parsed line, or null if the given scheme
	 * was not described in the parsed line.  Authorization scheme
	 * identifiers are those which appear without a subsequent '=' or ','
	 * character before the next word; key/value entries are associated
	 * with the nearest preceding scheme identifier.  All key strings are
	 * converted to lower case.
	 */
	Map getEntries(String scheme) {
	    Map map = null;
	    String[][] ea = 
		(String[][]) entries.toArray(new String[entries.size()][]);

	    int i;
	    for (i = 0; i < ea.length; i++) {
		if (ea[i][1] == null && scheme.equalsIgnoreCase(ea[i][0])) {
		    map = new HashMap();
		    break;
		}
	    }
	    if (map != null) {
		for (i++; i < ea.length && ea[i][1] != null; i++) {
		    map.put(ea[i][0].toLowerCase(), ea[i][1]);
		}
	    }
	    return map;
	}
	
	/**
	 * Returns the key/value entries encountered in the parsed line.  This
	 * method should be used to obtain the parse results for
	 * Authorization-Info and Proxy-Authorization-Info values, which do not
	 * contain authorization scheme identifiers.  All key strings are
	 * converted to lower case.
	 */
	Map getAllEntries() {
	    Map map = new HashMap();
	    String[][] ea = 
		(String[][]) entries.toArray(new String[entries.size()][]);
	    for (int i = 0; i < ea.length; i++) {
		map.put(ea[i][0].toLowerCase(), ea[i][1]);
	    }
	    return map;
	}
    }
    
    /**
     * Obtains PasswordAuthentication from the currently installed
     * Authenticator.
     */
    private PasswordAuthentication getPassword(final String protocol) {
	return (PasswordAuthentication) AccessController.doPrivileged(
	    new PrivilegedAction() {
		public Object run() {
		    InetAddress addr = null;
		    try {
			addr = InetAddress.getByName(host);
		    } catch (UnknownHostException ex) {
		    }

		    return Authenticator.requestPasswordAuthentication(
			addr, port, protocol, authRealm, authScheme);
		}
	    }
	);
    }
}
