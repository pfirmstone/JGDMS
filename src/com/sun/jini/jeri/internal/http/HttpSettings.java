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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Pattern;


/**
 * Utility class for querying HTTP/HTTPS-related system properties.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public class HttpSettings {
    
    private static final Object lastNonProxyLock = new Object();
    private static String lastNonProxyHosts = null;
    private static SoftReference lastNonProxyPatterns = null;

    private final boolean ssl;
    private final Properties props;

    /**
     * Returns an HttpSettings instance which can be used to query values of
     * HTTP-related (if ssl is false) or HTTPS-related (if ssl is true) system
     * properties. Calling this method will cause the checkPropertiesAccess
     * method of the current security manager (if any) to be invoked. The
     * returned HttpSettings instance is not guaranteed to reflect changes to
     * the system properties that occur after the invocation of this method.
     */
    public static HttpSettings getHttpSettings(boolean ssl) {
	return new HttpSettings(ssl, System.getProperties());
    }
    
    /**
     * Creates new HttpSettings instance which returns values from the given
     * system properties list.
     */
    private HttpSettings(boolean ssl, Properties props) {
	this.ssl = ssl;
	this.props = props;
    }

    /**
     * Returns proxy host if given host should be proxied through it,
     * else empty string. Proxy host is http[s].proxyHost system property
     * value if set; else proxyHost system property value if set and not ssl,
     * else none. Non-proxied hosts given by http.nonProxyHosts system
     * property value if set.
     */
    public String getProxyHost(String host) {
	String str = props.getProperty(ssl ?
				       "https.proxyHost" : "http.proxyHost");
	if (str == null && !ssl) {
	    str = props.getProperty("proxyHost");
	}
	if (str == null || (str.length() != 0 && nonProxied(host))) {
	    str = "";
	}
	return str;
    }
    
    /**
     * Returns http[s].proxyPort system property value if set; else if not
     * ssl returns proxyPort system property value if set; else returns
     * 443 (if ssl) or 80 (if not ssl).
     */
    public int getProxyPort() {
	String str = props.getProperty(ssl ?
				       "https.proxyPort" : "http.proxyPort");
	int port = -1;
	if (str != null) {
	    try { port = Integer.parseInt(str); } catch (Exception ex) {}
	} else if (!ssl) {
	    str = props.getProperty("proxyPort");
	    if (str != null) {
		try { port = Integer.parseInt(str); } catch (Exception ex) {}
	    }
	}
	if (port <= 0 || port > 0xFFFF) {
	    port = ssl ? 443 : 80;
	}
	return port;
    }

    /**
     * Returns com.sun.jini.jeri.http[s].responseAckTimeout system
     * property value if set; otherwise returns 15000.
     */
    public long getResponseAckTimeout() {
	String str = props.getProperty(ssl ?
	    "com.sun.jini.jeri.https.responseAckTimeout" :
	    "com.sun.jini.jeri.http.responseAckTimeout");
	if (str != null) {
	    try { return Long.parseLong(str); } catch (Exception ex) {}
	}
	return 15000;
    }
    
    /**
     * Returns com.sun.jini.jeri.http[s].idleConnectionTimeout
     * system property value if set; otherwise returns 15000.
     */
    public long getConnectionTimeout() {
	String str = props.getProperty(ssl ?
	    "com.sun.jini.jeri.https.idleConnectionTimeout" :
	    "com.sun.jini.jeri.http.idleConnectionTimeout");
	if (str != null) {
	    try { return Long.parseLong(str); } catch (Exception ex) {}
	}
	return 15000;
    }

    /**
     * Returns com.sun.jini.jeri.http[s].idleServerConnectionTimeout
     * system property value if set; otherwise returns getConnectionTimeout()
     * plus 30000 (if ssl) or 10000 (if not ssl).
     */
    public long getServerConnectionTimeout() {
	String str = props.getProperty(ssl ?
	    "com.sun.jini.jeri.https.idleServerConnectionTimeout" :
	    "com.sun.jini.jeri.http.idleServerConnectionTimeout");
	if (str != null) {
	    try { return Long.parseLong(str); } catch (Exception ex) {}
	}
	return getConnectionTimeout() + (ssl ? 30000 : 10000);
    }

    /**
     * Returns com.sun.jini.jeri.http.disableProxyPersistentConnections
     * system property as boolean value if set and not ssl; otherwise returns
     * false.
     */
    public boolean getDisableProxyPersistentConnections() {
	return ssl ?
	    false :
	    Boolean.valueOf(props.getProperty(
		"com.sun.jini.jeri.http.disableProxyPersistentConnections")).
								booleanValue();
    }

    /**
     * Returns com.sun.jini.jeri.http.pingProxyConnections system
     * property as boolean value if set; otherwise returns false.
     */
    public boolean getPingProxyConnections() {
	String key = ssl ? "com.sun.jini.jeri.https.pingProxyConnections"
	                 : "com.sun.jini.jeri.http.pingProxyConnections";
	String prop = props.getProperty(key);
	boolean ping = Boolean.valueOf(prop).booleanValue();
	return ping;
    }

    /**
     * Returns com.sun.jini.jeri.http.pingProxyConnectionTimeout
     * system property as long value if set; otherwise returns
     * Long.MAX_VALUE (essentially, never timeout).
     */
    public long getPingProxyConnectionTimeout() {
	String key = ssl? "com.sun.jini.jeri.https.pingProxyConnectionTimeout"
	                : "com.sun.jini.jeri.http.pingProxyConnectionTimeout";
	String prop = props.getProperty(key);
	try {
	    long timeout = Long.valueOf(prop).longValue();
	    return timeout;
	} catch (Exception ex) {}
	return Long.MAX_VALUE;
    }

    /**
     * If http.nonProxyHosts system property value is set, returns true iff
     * given host matches any regular expressions contained in value; if
     * http.nonProxyHosts is unset, returns false.
     */
    private boolean nonProxied(String host) {
	String str = props.getProperty("http.nonProxyHosts");
	if (str == null) {
	    return false;
	}
	Pattern[] patterns;
	synchronized (lastNonProxyLock) {
	    if (!str.equalsIgnoreCase(lastNonProxyHosts) ||
		(patterns = (Pattern[]) lastNonProxyPatterns.get()) == null)
	    {
		StringTokenizer tok = new StringTokenizer(str, "|");
		List plist = new ArrayList();
		while (tok.hasMoreTokens()) {
		    try {
			plist.add(Pattern.compile(
				      convertToRegex(tok.nextToken()), 
				      Pattern.CASE_INSENSITIVE));
		    } catch (IllegalArgumentException ex) {
			// ignore pattern
		    }
		}
		patterns = (Pattern[]) 
		    plist.toArray(new Pattern[plist.size()]);
		lastNonProxyPatterns = new SoftReference(patterns);
		lastNonProxyHosts = str;
	    }
	}
	for (int i = 0; i < patterns.length; i++) {
	    if (patterns[i].matcher(host).matches()) {
		return true;
	    }
	}
	return false;
    }
    
    /**
     * Converts host pattern obtained from http.nonProxyHosts property to
     * java.util.regex-style regular expression.  Throws
     * IllegalArgumentException if given host pattern is invalid.
     */
    private static String convertToRegex(String hostPattern) {
	int len = hostPattern.length();
	StringBuffer sbuf = new StringBuffer(len);
	for (int i = 0; i < len; i++) {
	    char c = hostPattern.charAt(i);
	    if (Character.isLetter(c) || Character.isDigit(c)) {
		sbuf.append(c);
	    } else if (c == '.') {
		sbuf.append("\\.");
	    } else if (c == '*') {
		sbuf.append(".*");
	    } else {
		throw new IllegalArgumentException("illegal char: " + c);
	    }
	}
	return sbuf.toString();
    }
}
