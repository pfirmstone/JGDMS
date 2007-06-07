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
package com.sun.jini.tool.envcheck.plugins;

import com.sun.jini.start.NonActivatableServiceDescriptor;
import com.sun.jini.start.ServiceDescriptor;
import com.sun.jini.start.SharedActivationGroupDescriptor;

import com.sun.jini.tool.envcheck.AbstractPlugin;
import com.sun.jini.tool.envcheck.Plugin;
import com.sun.jini.tool.envcheck.EnvCheck;
import com.sun.jini.tool.envcheck.Reporter;
import com.sun.jini.tool.envcheck.Reporter.Message;
import com.sun.jini.tool.envcheck.SubVMTask;

import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;

/**
 * Plugin which performs a variety of checks on codebase components.  If not
 * configured to perform service starter checks, the codebase is expected to be
 * defined by the <code>java.rmi.server.codebase</code> system
 * property. Otherwise, all of the codebases contained in the service
 * descriptors of the service starter <code>Configuration</code> are examined
 * (excepting <code>SharedActivationGroupDescriptors</code>, which do not have a
 * codebase). First, an existence check is performed; the codebase string must
 * be non-null and have length > 0 after white space is trimmed. Non-existence
 * is reported as an error. Then the codebase is decomposed into tokens (URL
 * strings). Each component in a codebase is checked for the following:
 * <ul>
 * <li>check for a valid URL. As a special case, an httpmd URL which
 *     is invalid because the necessary protocol handler was not loaded
 *     will result in the generation of an appropriate error message
 *     and explanation. Further checks are not done for invalid URLs.
 * <li>check that the host name is expressed using a fully qualified domain name
 * <li>check for the use of md5 hashes in httpmd URLs
 * <li>check the ability to resolve the host name to an address
 * <li>check for a host name of 'localhost'
 * <li>check for the ability to access (connect to) the URL
 * </ul>
 * Failure of the first or last checks are displayed as errors. Failure of
 * the other checks are displayed as warnings.
 */
public class CheckCodebase extends AbstractPlugin {

    /** reference to the plugin container */
    EnvCheck envCheck;

    /**
     * Depending on whether service start checks are configured,
     * either check the codebase system property or all of the
     * <code>ServiceDescriptors</code> that are <code>instanceof</code>
     * <code>NonActivatableServiceDescriptor</code>.
     */
    public void run(EnvCheck envCheck) {
	this.envCheck = envCheck;
	String source;
	String codebase;
	if (envCheck.getDescriptors().length == 0) {
	    source = getString("propsource");
	    codebase = envCheck.getProperty("java.rmi.server.codebase");
	    doChecks(null, null, source, codebase);
	} else {
	    ServiceDescriptor[] sd = envCheck.getDescriptors();
	    SharedActivationGroupDescriptor g = envCheck.getGroupDescriptor();
	    for (int i = 0; i < sd.length; i++) {
		if (sd[i] instanceof NonActivatableServiceDescriptor) {
		    NonActivatableServiceDescriptor d = 
			(NonActivatableServiceDescriptor) sd[i];
		    source = getString("desc") + " " + d.getImplClassName();
		    codebase = d.getExportCodebase();
		    doChecks(d, g, source, codebase);
		}
	    }
	}
    }

    /** 
     * Perform all of the checks on <code>codebase</code>. 
     * 
     * @param source a string describing the source of the codebase for
     *               use in report messages
     * @param codebase the codebase to check
     */
    private void doChecks(NonActivatableServiceDescriptor d,
			  SharedActivationGroupDescriptor g,
			  String source, 
			  String codebase) 
    {
	if (checkExistance(source, codebase)) {
	    StringTokenizer tok = new StringTokenizer(codebase);
	    while (tok.hasMoreTokens()) {
		String urlToken = tok.nextToken();
		URL url = checkURL(d, g, source, urlToken);
		if (url != null) {
		    checkForFQDomain(url, source);
		    checkForMD5(url, source);
		    checkForKnownHost(url, source);
		    checkForLocalHost(url, source);
		    checkAccessibility(url, source);
		}
	    }
	}
    }

    /**
     * Check for existence. <code>codebase</code> must be non-null
     * and have length > 0 after trimming whitespace.
     *
     * @param source identifies the source of the codebase
     * @param codebase the codebase to check
     * @return true if existence check is successful
     */
    private boolean checkExistance(String source, final String codebase) {
	Message message;
	boolean gotCodebase;
	if (codebase != null && codebase.trim().length() > 0) {
	    message = new Message(Reporter.INFO,
				  getString("codebaseIs") + " " + codebase,
				  getString("existenceExp"));
	    gotCodebase = true;
	} else {
	    message = new Message(Reporter.INFO,
				  getString("nocodebase"),
				  getString("existenceExp"));
	    gotCodebase = false;
	}
	Reporter.print(message, source);
	return gotCodebase;
    }

    /**
     * Check whether <code>urlToken</code> can be used to construct
     * a <code>URL</code> object. If a <code>MalformedURLException</code>
     * is thrown, check whether the protocol portion of the URL is
     * <code>httpmd:</code>. If so, check whether the protocol handler is
     * installed. If not, output an appropriate message. Otherwise, just
     * complain generally that the URL is malformed.
     *
     * @param source the source of the codebase 
     * @param urlToken the codebase component to check
     * @return the corresponding URL object if successful, <code>null</code>
     *         otherwise
     */
    private URL checkURL(NonActivatableServiceDescriptor d,
			 SharedActivationGroupDescriptor g,
			 String source, 
			 final String urlToken) 
    {
	Message message;
	URL url = null;
	String[] args = new String[]{urlToken};
	Object lobj = envCheck.launch(d, g, taskName("GetURLTask"), args);
	if (lobj instanceof URL) {
	    url = (URL) lobj;
	} else if (lobj instanceof String) {
	    String cause = (String) lobj;
	    if (cause.equals("nohandler")) {
		message = new Message(Reporter.ERROR,
				      getString("nohandler", urlToken),
				      getString("httpmdExp"));
		Reporter.print(message, source);
		try {
		    url = new URL(urlToken);
		} catch (MalformedURLException e) { // should never happen
		    message = new Message(Reporter.ERROR,
					  getString("badURL", urlToken),
					  e, 
					  null);
		    Reporter.print(message, source);
		}
	    } else {
		message = 
		    new Message(Reporter.ERROR,
				getString("badURL", urlToken) + ": " + cause,
				null);
		
		Reporter.print(message, source);
	    }
	} else {
	    handleUnexpectedSubtaskReturn(lobj, source);
	}
	return url;
    }

    /**
     * Check the ability to resolve the host component of <code>url</code>
     * to an <code>InetAddress</code>. If successful, this method is silent.
     *
     * @param url the <code>URL</code> to check
     * @param source the source of the <code>URL</code>
     */
    private void checkForKnownHost(final URL url, String source) {
	try {
	    InetAddress.getByName(url.getHost()).getCanonicalHostName();
	} catch (UnknownHostException e) {
	    Message message = new Message(Reporter.ERROR,
					  getString("noHost", 
						    url.getHost(), 
						    url),
					  null);
	    Reporter.print(message, source);
	}
    }

    /**
     * Check whether the host component of <code>url</code> resolves
     * to a loopback address.
     *
     * @param url the <code>URL</code> to check
     * @param source the source of the <code>URL</code>
     */
    private void checkForLocalHost(final URL url, String source) {
	try {
	    if (InetAddress.getByName(url.getHost()).isLoopbackAddress()) {
		Message message = new Message(Reporter.WARNING,
					      getString("usedLocalhost", url),
					      getString("localhostExp"));
		Reporter.print(message, source);
	    }
	} catch (Exception ignore) { // accessibility check handles this failure
	}
    }

    /**
     * Check the accessibility of the codebase <code>URL</code> by opening a
     * connection to it. This check fails if the <code>openConnection</code>
     * call or subsequent <code>getInputStream </code> call throws an
     * <code>IOException</code>, or if these calls do not complete within 5
     * seconds. These two failure modes result in different error messages
     * being output.
     *
     * @param url the <code>URL</code> to check
     * @param source the source of the <code>URL</code>
     */
    private void checkAccessibility(final URL url, String source) {
	Message message;
	URLAccessor accessor = new URLAccessor(url);
	Thread t = new Thread(accessor);
	t.setDaemon(true);
	t.start();
	try {
	    t.join(5000, 0);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	if (t.isAlive()) {
	    message = new Message(Reporter.ERROR,
				  getString("noresponse", url),
				  null);
	} else if (accessor.getException() == null) {
	    message = new Message(Reporter.INFO,
				  getString("available", url),
				  null);
	} else {
	    message = new Message(Reporter.ERROR,
				  getString("unavailable", url),
				  accessor.getException(),
				  null);
	}
	Reporter.print(message, source);
    }

    /**
     * Check for a fully qualified host name. To be valid, the host
     * name must consist of at least two '.' separated tokens, and the
     * last token must be one of the well-known top level domain names,
     * or the last token must be a two character string which is assumed
     * to be a country code.
     *
     * @param url the <code>URL</code> to check
     * @param source the source of the <code>URL</code>
     */
    private void checkForFQDomain(final URL url, String source) {

	String[] topLevelDomains = {"aero", "biz", "com", "coop","edu", 
				    "gov", "info", "int", "mil","museum", 
				    "name", "net", "org", "pro"};
	String hostName = url.getHost();
	int lastDot = hostName.lastIndexOf('.');
	if (lastDot >= 0 && lastDot < hostName.length() - 1) {
	    String tld = hostName.substring(lastDot + 1); // top level domain
	    if (tld.length() == 2) {
		return;
	    }
	    for (int i = 0; i < topLevelDomains.length; i++) {
		if (tld.equals(topLevelDomains[i])) {
		    return;
		}
	    }
	}
	Message message = new Message(Reporter.WARNING,
				      getString("unqualified", url.getHost()),
				      getString("unqualifiedExp"));
	Reporter.print(message, source);
    }

    /**
     * Check for use of an MD5 httpmd URL. If the protocol of <code>url</code>
     * is httpmd, then the hash function identifier is parsed from the
     * file component of <code>url</code> and a string comparison is done.
     *
     * @param url the <code>URL</code> to check
     * @param source the source of the <code>URL</code>
     */
    private void checkForMD5(final URL url, String source) {
	if(! url.getProtocol().equalsIgnoreCase("httpmd")) {
	    return;
	}
	// can assume hashcode is present, or url construction would have failed
	String target = url.getFile();
	int lastSemi = target.lastIndexOf(';');
	String hash = target.substring(lastSemi + 1);
	if (hash.startsWith("md5")) {
	    Message message = new Message(Reporter.WARNING,
					  getString("usesmd5", url),
					  getString("usesmd5Exp"));
	    Reporter.print(message, source);
	}
    }

    /**
     * A <code>Runnable</code> which attempts to open a <code>URL</code>
     * connection. If the attempt results in an exception being throw,
     * the exception is stored for later access by the invoker of the thread.
     */
    private class URLAccessor implements Runnable {

	URL url;
	Throwable exception = null;
	
	URLAccessor(URL url) {
	    this.url = url;
	}

	public void run() {
	    try {
		URLConnection con = url.openConnection();
		InputStream stream = con.getInputStream();
	    } catch (IOException e) {
		exception = e;
	    }
	}
	
	Throwable getException() {
	    return exception;
	}
    }  

    public static class GetURLTask implements SubVMTask {

	public Object run(String[] args) {
	    String urlToken = args[0];
	    URL url;
	    try {
		url = new URL(urlToken);
	    } catch (MalformedURLException e) {
		int firstColon = urlToken.indexOf(':');
		if (firstColon > 0) {
		    String protocol = urlToken.substring(0, firstColon);
		    if (protocol.equalsIgnoreCase("httpmd")) {
			if (!httpmdHandlerInstalled()) {
			    return "nohandler";
			}
		    }
		}
		return e.getMessage(); 
	    }
	    return url;
	}

	private boolean httpmdHandlerInstalled() {
	    try {
		new URL("httpmd://localhost/foo;sha=0");
	    } catch (MalformedURLException e) {
		return false;
	    }
	    return true;
	}
    }
}
	

