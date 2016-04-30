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
package net.jini.core.discovery;

import org.apache.river.discovery.Discovery;
import org.apache.river.discovery.DiscoveryConstraints;
import org.apache.river.discovery.DiscoveryProtocolVersion;
import org.apache.river.discovery.UnicastResponse;
import org.apache.river.discovery.UnicastSocketTimeout;
import org.apache.river.discovery.internal.MultiIPDiscovery;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.lookup.ServiceRegistrar;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.net.Uri;

/**
 * LookupLocator supports unicast discovery, using either Discovery V1 or V2 or 
 * https.
 * 
 * Version 1 of the unicast discovery protocol is deprecated.
 * Discovery V2 is used by default, unless otherwise set by Constraints.
 * <p>
 * It's main purpose now is to contain a url used for unicast discovery of 
 * a {@link ServiceRegistrar}, it is now
 * immutable, since River 2.2.1, this may break overriding classes.
 * <p>
 * <code>"jini"</code> Unicast Discovery may be performed using any Discovery 
 * V1 or V2 provider and depending on routing and firewall rules,
 * may be used to contact a {@link ServiceRegistrar}.  
 * The <code>"https"</code> Unicast Discovery provider is neither
 * Discovery V1 or V2 compliant, as firewall rules and proxy servers 
 * that allow https communications are likely to prevent the handshake 
 * required to select a <code>"jini"</code> Discovery provider.
 * <p>
 * LookupLocator is used as a parameter in LookupLocatorDiscovery constructors.  
 * LookupLocatorDiscovery has methods to perform Discovery using either 
 * version 1 or 2 with constraints.
 * ConstrainableLookupLocator is a subclass which uses discovery V1 or V2
 * and enables the use of constraints.
 *
 * @since 1.0
 * @see net.jini.discovery.LookupLocatorDiscovery
 * @see net.jini.discovery.ConstrainableLookupLocator
 */
@AtomicSerial
public class LookupLocator implements Serializable {
    private static final long serialVersionUID = 1448769379829432795L;
    private static final ObjectStreamField[] serialPersistentFields = 
    { 
        /** @serialField The name of the host at which to perform discovery. */
        new ObjectStreamField("host", String.class),
	/** @serialField The port number on the host at which to perform discovery. */
	new ObjectStreamField("port", Integer.TYPE)
    };
    /**
     * The port for both unicast and multicast boot requests.
     */
    private static final short DISCOVERY_PORT = 4160;
    
    private static final short HTTPS_DISCOVERY_PORT = 443;
    
    /**
     * The name of the host at which to perform discovery.
     *
     * @serial
     */
    protected final String host;
    /**
     * The port number on the host at which to perform discovery.
     *
     * @serial
     */
    protected final int port;
    
    /**
     * Either <code>"jini"</code> or <code>"https"</code>.
     * 
     * @serial
     */
    protected final String scheme;
    
    /**
     * The timeout after which we give up waiting for a response from
     * the lookup service.
     */
    private static final int defaultTimeout =
	AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            @Override
	    public Integer run() {
		Integer timeout = Integer.valueOf(60 * 1000);
		try {
		    Integer val = Integer.getInteger(
				    "net.jini.discovery.timeout",
				    timeout);
		    return (val.intValue() < 0 ? timeout : val);
		} catch (SecurityException e) {
		    return timeout;
		}
	    }
	}).intValue();

    /**
     * Construct a new <code>LookupLocator</code> object, set up to perform
     * discovery to the given URL.  The <code>host</code> and <code>port</code>
     * fields will be populated with the <i>host</i> and <i>port</i>
     * components of the input URL.  No host name resolution is attempted.
     * <p>
     * The syntax of the URL must be that of a <i>hierarchical</i> 
     * {@link java.net.URI} with a <i>server-based naming authority</i>.
     * Requirements for the components are as follows:
     * <ul>
     * <li> A <i>scheme</i> of <code>"jini"</code> or <code>"https"</code> must be present.
     * <li> A <i>server-based naming authority</i> must be
     * present; <i>user-info</i> must not be present. The <i>port</i>, if
     * present, must be between 1 and 65535 (both included). If no port is
     * specified, a default value of <code>4160</code> is used for <code>"jini"</code>
     * and a default value of <code>443</code> is used for <code>"https"</code>.
     * <li> A <i>path</i> if present, must be
     * <code>"/"</code>; <i>path segment</i>s must not be present.
     * <li> There must not be any other components present.
     * </ul>
     * <p>
     * The four allowed forms of the URL are thus:
     * <ul>
     * <li> <code>scheme://</code><i>host</i>
     * <li> <code>scheme://</code><i>host</i><code>/</code>
     * <li> <code>scheme://</code><i>host</i><code>:</code><i>port</i>
     * <li>
     * <code>scheme://</code><i>host</i><code>:</code><i>port</i><code>/</code>
     * </ul>
     * @param url the URL to use
     * @throws MalformedURLException <code>url</code> could not be parsed
     * @throws NullPointerException if <code>url</code> is <code>null</code>
     */
    public LookupLocator(String url) throws MalformedURLException {
	this(parseURI(url));
    }
    
    /**
     * Only this constructor doesn't check invariants.
     * @param uri 
     */
    private LookupLocator(URI uri){
	super();
        scheme = uri.getScheme();
        host = uri.getHost();
        port = uri.getPort();
    }

    /**
     * Check invariants before super() is called.
     * @param host
     * @param port
     * @return 
     */
    private static URI parseURI(String scheme, String host, int port){
	if (host == null) {
            throw new NullPointerException("null host");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1) { //URI compliance -1 is converted to discoveryPort.
            sb.append(":").append(port);
        }
        try {
            return parseURI(sb.toString());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("host cannot be parsed", ex);
        }
    }
    
    public LookupLocator(GetArg arg) throws IOException{
	this(parseURI(getScheme(arg), arg.get("host", null, String.class), arg.get("port", 0)));
    }
    
    private static String getScheme(GetArg arg) throws IOException{
        String scheme = "jini";
        try {
            scheme = arg.get("scheme", scheme, String.class);
        } catch (IllegalArgumentException e){
            // Ignore, just means the field doesn't exist in serial form.
        }
        return scheme;
    }

    /**
     * Construct a new <code>LookupLocator</code> object, set to perform unicast
     * discovery to the input <code>host</code> and <code>port</code> with the 
     * default <code>"jini"</code> scheme.  The
     * <code>host</code> and <code>port</code> fields will be populated with the
     * <code>host</code> and <code>port</code> arguments.  No host name
     * resolution is attempted.
     * <p>The <code>host</code>
     * argument must meet any one of the following syntactical requirements:
     * <ul>
     * <li>A host as required by a <i>server-based naming authority</i> in
     * section 3.2.2 of <a href="http://www.ietf.org/rfc/rfc2396.txt">
     * <i>RFC 2396: Uniform Resource Identifiers (URI): Generic Syntax</i></a>
     * <li>A literal IPv6 address as defined by
     * <a href="http://www.ietf.org/rfc/rfc2732.txt">
     * <i>RFC 2732: Format for Literal IPv6 Addresses in URL's</i></a>
     * <li>A literal IPv6 address as defined by
     * <a href="http://www.ietf.org/rfc/rfc3513.txt">
     * <i>RFC 3513: Internet Protocol Version 6 (IPv6) Addressing Architecture
     * </i></a>
     * </ul>
     *
     * @param host the name of the host to contact
     * @param port the number of the port to connect to
     * @throws IllegalArgumentException if <code>port</code> is not between
     * 1 and 65535 (both included) or if <code>host</code> cannot be parsed.
     * @throws NullPointerException if <code>host</code> is <code>null</code>
     */
    public LookupLocator(String host, int port) {
        this(parseURI("jini", host, port));
        }

    /**
     * Check invariants before super() is called.
     */
    private static URI parseURI(String url) throws MalformedURLException {
        if (url == null) {
            throw new NullPointerException("url is null");
        }
        URI uri = null;
        try {
            uri = Uri.uriToURI(Uri.parseAndCreate(url));
        } catch (URISyntaxException e) {
            MalformedURLException mue
                    = new MalformedURLException("URI parsing failure: " + url);
            mue.initCause(e);
            throw mue;
        }
	if (!uri.isAbsolute()) throw new MalformedURLException("no scheme specified: " + url);
	if (uri.isOpaque()) throw new MalformedURLException("not a hierarchical url: " + url);
        String scheme = uri.getScheme().toLowerCase();
	if (!("jini".equals(scheme)|| "https".equals(scheme))) 
            throw new MalformedURLException("Invalid URL scheme: " + scheme);

        String uriPath = uri.getPath();
        if ((uriPath.length() != 0) && (!uriPath.equals("/"))) {
            throw new MalformedURLException(
                    "URL path contains path segments: " + url);
        }
	if (uri.getQuery() != null) throw new MalformedURLException("invalid character, '?', in URL: " + url);
	if (uri.getFragment() != null) throw new MalformedURLException("invalid character, '#', in URL: " + url);
        if (uri.getUserInfo() != null) throw new MalformedURLException("invalid character, '@', in URL host: " + url);
        if ((uri.getHost()) == null) {
            // authority component does not exist - not a hierarchical URL
            throw new MalformedURLException(
                    "Not a hierarchical URL: " + url);
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme)? HTTPS_DISCOVERY_PORT : DISCOVERY_PORT;
            try {
                uri = new URI(
                        uri.getScheme(),
                        uri.getRawUserInfo(),
                        uri.getHost(), 
                        port, 
                        uri.getRawPath(),
                        uri.getRawQuery(),
                        uri.getRawFragment()
                );
            } catch (URISyntaxException e) {
                MalformedURLException mue
                        = new MalformedURLException("recreation of URI with discovery port failed");
                mue.initCause(e);
                throw mue;
            }
        }

        if ((uri.getPort() <= 0) || (uri.getPort() >= 65536)) {
            throw new MalformedURLException("port number out of range: " + url);
        }
        return uri;
    }
    
    /**
     * Returns the scheme used by this LookupLocator, either traditional
     * "jini" or "https".
     * 
     * @return scheme string.
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Returns the name of the host that this instance should contact.
     * <code>LookupLocator</code> implements this method to return
     * the <code>host</code> field.
     *
     * @return a String representing the host value
     */
    public String getHost() {
	return host;
    }

    /**
     * Returns the number of the port to which this instance should connect.
     * <code>LookupLocator</code> implements this method to return the
     * <code>port</code> field.
     *
     * @return an int representing the port value
     */
    public int getPort() {
	return port;
    }

    /**
     * Perform unicast discovery and return the ServiceRegistrar
     * object for the given lookup service.  Unicast discovery is
     * performed anew each time this method is called.
     * <code>LookupLocator</code> implements this method to simply invoke
     * {@link #getRegistrar(int)} with a timeout value, which is determined
     * by the value of the <code>net.jini.discovery.timeout</code> system
     * property.  If the property is set, is not negative, and can be parsed as
     * an <code>Integer</code>, the value of the property is used as the timeout
     * value. Otherwise, a default value of <code>60</code> seconds is assumed.
     *
     * @return the ServiceRegistrar for the lookup service denoted by
     * this LookupLocator object
     * @throws IOException an error occurred during discovery
     * @throws ClassNotFoundException if a class required to unmarshal the
     * <code>ServiceRegistrar</code> proxy cannot be found
     */
    public ServiceRegistrar getRegistrar()
	throws IOException, ClassNotFoundException
    {
	return getRegistrar(defaultTimeout);
    }

    /**
     * Perform unicast discovery and return the ServiceRegistrar
     * object for the given lookup service, with the given discovery timeout.
     * Unicast discovery is performed anew each time this method is called.
     * <code>LookupLocator</code> implements this method to use the values
     * of the <code>host</code> and <code>port</code> field in determining
     * the host and port to connect to.
     *
     * <p>
     * If a connection can be established to start unicast discovery
     * but the remote end fails to respond within the given time
     * limit, an exception is thrown.
     *
     * @param timeout the maximum time to wait for a response, in
     * milliseconds.  A value of <code>0</code> specifies an infinite timeout.
     * @return the ServiceRegistrar for the lookup service denoted by
     * this LookupLocator object
     * @throws IOException an error occurred during discovery
     * @throws ClassNotFoundException if a class required to unmarshal the
     * <code>ServiceRegistrar</code> proxy cannot be found
     * @throws IllegalArgumentException if <code>timeout</code> is negative
     */
    public ServiceRegistrar getRegistrar(int timeout)
	throws IOException, ClassNotFoundException
    {
	return getRegistrar(
                new InvocationConstraints(
                        new UnicastSocketTimeout(timeout), 
                        DiscoveryProtocolVersion.TWO 
                )
        );
    }

    /**
     * Perform unicast discovery and return the ServiceRegistrar
     * object for the given lookup service, with the given constraints.
     * 
     * Unicast discovery is performed anew each time this method is called.
     * <code>LookupLocator</code> implements this method to use the values
     * of the <code>host</code> and <code>port</code> field in determining
     * the host and port to connect to.
     * @param constraints
     * @return lookup service proxy
     * @throws IOException
     * @throws net.jini.io.UnsupportedConstraintException if the
     * discovery-related constraints contain conflicts, or otherwise cannot be
     * processed
     * @throws ClassNotFoundException
     */
    protected final ServiceRegistrar getRegistrar(InvocationConstraints constraints)
            throws IOException, ClassNotFoundException {
        UnicastResponse resp = new MultiIPDiscovery() {
            @Override
            protected UnicastResponse performDiscovery(Discovery disco,
                    DiscoveryConstraints dc,
                    Socket s)
                    throws IOException, ClassNotFoundException {
                return disco.doUnicastDiscovery(
                        s, dc.getUnfulfilledConstraints(), null, null, null);
            }
        }.getResponse(scheme, host, port, constraints);
        return resp.getRegistrar();
    }
    
    /**
     * Return the string form of this LookupLocator, as a URL of scheme
     * <code>"jini"</code> or <code>"https"</code>.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(getHost0(host)).append(":").append(port).append("/");
        return sb.toString();
    }

    /**
     * Two locators are equal if they have the same <code>host</code> and
     * <code>port</code> fields. The case of the <code>host</code> is ignored.
     * Alternative forms of the same IPv6 addresses for the <code>host</code>
     * value are treated as being unequal.
     */
    public boolean equals(Object o) {
	if (o == this) {
	    return true;
	}
	if (o instanceof LookupLocator) {
	    LookupLocator oo = (LookupLocator) o;
	    return port == oo.port && host.equalsIgnoreCase(oo.host);
	}
	return false;
    }

    /**
     * Returns a hash code value calculated from the <code>host</code> and
     * <code>port</code> field values.
     */
    public int hashCode() {
	return host.toLowerCase().hashCode() ^ port;
    }
    
    // Checks if the host is an RFC 3513 IPv6 literal and converts it into
    // RFC 2732 form.
    private static String getHost0(String host) {
	if ((host.indexOf(':') >= 0) && (host.charAt(0) != '[')) {
	    // This is a 3513 form IPv6 literal
	    return '[' + host + ']';
	} else {
	    return host;
	}
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
	out.defaultWriteObject();
    }
    
    /**
     * Added to allow deserialisation of broken serial compatibility in 2.2.0
     * 
     * Invariants are not protected against finalizer and circular reference
     * attacks with standard de-serialization, ensure trust is established
     * prior to obtaining an instance of LookupLocator from a Remote interface
     * or ObjectInputStream.
     * 
     * @serial
     * @param oin
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    private void readObject(ObjectInputStream oin) 
	    throws IOException, ClassNotFoundException{
        oin.defaultReadObject();
	try {
	    parseURI("jini", host, port);
	} catch (NullPointerException ex){
	    InvalidObjectException e = new InvalidObjectException(
		    "Invariants not satisfied during deserialization");
	    e.initCause(ex);
	    throw e;
	} catch (IllegalArgumentException ex){
	    InvalidObjectException e = new InvalidObjectException(
		    "Invariants not satisfied during deserialization");
	    e.initCause(ex);
	    throw e;
    }
}
}
