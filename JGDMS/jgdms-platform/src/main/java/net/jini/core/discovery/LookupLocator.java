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
import org.apache.river.discovery.MultiIPDiscovery;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.lookup.ServiceRegistrar;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
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
 * The <code>"https"</code> scheme based Unicast Discovery is neither
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
 * <br><code>see net.jini.discovery.LookupLocatorDiscovery</code>
 * @see net.jini.discovery.ConstrainableLookupLocator
 */
@AtomicSerial
public class LookupLocator implements Serializable {
    private static final long serialVersionUID = 1448769379829432795L;
    private static final ObjectStreamField[] serialPersistentFields = 
            serialForm();
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            /** @serialField The name of the host at which to perform discovery. */
            new SerialForm("host", String.class),
            /** @serialField The port number on the host at which to perform discovery. */
            new SerialForm("port", Integer.TYPE),
            /** @serialField the URL scheme used to perform discovery. */
            new SerialForm("scheme", String.class)
        };
    }
    
    public static void serialize(PutArg arg, LookupLocator l) throws IOException{
        arg.put("host", l.host);
        arg.put("port", l.port);
        arg.put("scheme", l.scheme);
        arg.writeArgs();
    }
    
    /**
     * The port for both unicast and multicast boot requests.
     */
    private static final short DISCOVERY_PORT = 4160;
    
    private static final short HTTPS_DISCOVERY_PORT = 443;
    /*  IPv6 Regex by Dartware, LLC is licensed under a 
        Creative Commons Attribution-ShareAlike 3.0 Unported License.
        http://creativecommons.org/licenses/by-sa/3.0/
        
        IPv6 regular expression courtesy of Dartware, LLC (http://intermapper.com)
        For full details see http://intermapper.com/ipv6regex
     */
    private static final Pattern IPV6 = Pattern.compile(
       //     123                 3   4                  42 56                 6   7                  89                                   910  11                                 1110  8  75 1213              13  151617              17    16 1819                                 1920 21                                 2120 18 1512 2223              23  242526              26    25 2728
        "^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*$"
    );
    
    /**
     * The name of the host at which to perform discovery.
     * @since 1.0
     * @serial
     */
    protected final String host;
    /**
     * The port number on the host at which to perform discovery.
     * @since 1.0
     * @serial
     */
    protected final int port;
    
    /**
     * Either <code>"jini"</code> or <code>"https"</code>.
     * @since 3.1
     * @serial
     */
    private final String scheme;
    
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
     * @since 1.0
     */
    public LookupLocator(String url) throws MalformedURLException {
	this(parseURI(url));
    }
    
    /**
     * Only this constructor doesn't check invariants.
     * @param uri 
     */
    private LookupLocator(Uri uri){
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
    private static Uri parseURI(String scheme, String host, int port){
	if (host == null) {
            throw new NullPointerException("null host");
        }
        Matcher isIPv6 = IPV6.matcher(host); //  Try to match and encapsulate
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://");
        if (isIPv6.matches()){
            sb.append('[');
            int zone = host.indexOf('%');
            if (zone > 0) {
                // Strip zone component, it has no relevance outside of this host.
                sb.append(host.substring(0, zone));
            } else sb.append(host);
            sb.append(']');
        } else if (host.startsWith("[")){ // Properly escaped IPv6 or IP Future.
            int zone = host.indexOf('%');
            if (zone > 0) {
                // Strip zone component, it has no relevance outside of this host.
                sb.append(host.substring(0, zone));
                sb.append(']'); // Replace closing square bracket.
            } else sb.append(host);
        } else { 
            sb.append(host);
        }
        if (port != -1) { //URI compliance -1 is converted to discoveryPort.
            sb.append(":").append(port);
        }
        try {
            return parseURI(sb.toString());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("host cannot be parsed", ex);
        }
    }
    
    /**
     * Deserialization constructor.
     * 
     * @param arg
     * @throws IOException
     * @throws ClassNotFoundException 
     * @since 3.1
     * @see AtomicSerial
     */
    public LookupLocator(GetArg arg) throws IOException, ClassNotFoundException{
	this(parseURI(getScheme(arg), arg.get("host", null, String.class), arg.get("port", 0)));
    }
    
    private static String getScheme(GetArg arg) throws IOException, ClassNotFoundException{
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
     * section 3.2.2 of <a href="http://www.ietf.org/rfc/rfc3986.txt">
     * <i>RFC 3986: Uniform Resource Identifiers (URI): Generic Syntax</i></a>
     * <li>A literal IPv6 address as defined by
     * <a href="http://www.ietf.org/rfc/rfc4291.txt">
     * <i>RFC 4291: IP Version 6 Addressing Architecture</i></a>
     * <a href="http://www.ietf.org/rfc/rfc4007.txt">
     * <i>RFC 4007: IPv6 Scoped Address Architecture</i></a>
     * <a href="http://www.ietf.org/rfc/rfc6874.txt">
     * <i>RFC 6874: Representing IPv6 Zone Identifiers in Address Literals and 
     * Uniform Resource Identifiers</i></a>
     * </ul>
     * 
     * This constructor attempts to identify an unenclosed IPv6
     * host address and if recognized, encloses the IPv6 address within square brackets.
     * 
     * If an IPv6 zone is present as delimited by '%' or '%25' when correctly escaped,
     * this constructor will strip the zone from the IPv6 address, 
     * as it is only of meaning on the local node and would also make the Object 
     * equals contract indeterminate as the zone is an optional component without
     * rules for normalization.
     *
     * @param host the name of the host to contact
     * @param port the number of the port to connect to
     * @throws IllegalArgumentException if <code>port</code> is not between
     * 1 and 65535 (both included) or if <code>host</code> cannot be parsed.
     * @throws NullPointerException if <code>host</code> is <code>null</code>
     * @since 1.0
     */
    public LookupLocator(String host, int port) {
        this(parseURI("jini", host, port));
        }

    /**
     * Check invariants before super() is called.
     */
    private static Uri parseURI(String url) throws MalformedURLException {
        if (url == null) {
            throw new NullPointerException("url is null");
        }
        Uri uri = null;
        try {
            uri = Uri.parseAndCreate(url);
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
                uri = new Uri(
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
     * @since 3.1
     */
    public final String scheme() {
        return scheme == null ? "jini" : scheme;
    }

    /**
     * Returns the name of the host that this instance should contact.
     * <code>LookupLocator</code> implements this method to return
     * the <code>host</code> field.
     *
     * @return a String representing the host value
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @since 1.0
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
     * @param constraints discovery constraints
     * @return lookup service proxy
     * @throws IOException an error occurred during discovery
     * @throws net.jini.io.UnsupportedConstraintException if the
     * discovery-related constraints contain conflicts, or otherwise cannot be
     * processed
     * @throws ClassNotFoundException if a class required to unmarshal the
     * <code>ServiceRegistrar</code> proxy cannot be found
     * @since 3.0
     */
    protected final ServiceRegistrar getRegistrar(InvocationConstraints constraints)
            throws IOException, ClassNotFoundException {
        return getRegistrar(constraints, null);
    }
    
    /**
     * Perform unicast discovery and return the ServiceRegistrar
     * object for the given lookup service, with the given constraints
     * and context.
     * 
     * Note the context may include {@link net.jini.core.constraint.MethodConstraints}
     * for {@link net.jini.loader.ProxyCodebaseSpi}.
     * 
     * Unicast discovery is performed anew each time this method is called.
     * <code>LookupLocator</code> implements this method to use the values
     * of the <code>host</code> and <code>port</code> field in determining
     * the host and port to connect to.
     * @param constraints discovery constraints
     * @param context the stream context {@link net.jini.io.ObjectStreamContext#getObjectStreamContext() }
     * @return lookup service proxy
     * @throws IOException an error occurred during discovery
     * @throws net.jini.io.UnsupportedConstraintException if the
     * discovery-related constraints contain conflicts, or otherwise cannot be
     * processed
     * @throws ClassNotFoundException if a class required to unmarshal the
     * <code>ServiceRegistrar</code> proxy cannot be found
     * @since 3.1
     */
    protected final ServiceRegistrar getRegistrar(
					    InvocationConstraints constraints,
					    final Collection context)
	throws IOException, ClassNotFoundException 
    {
	UnicastResponse resp = new MultiIPDiscovery() {
            @Override
            protected UnicastResponse performDiscovery(Discovery disco,
                    DiscoveryConstraints dc,
                    Socket s)
                    throws IOException, ClassNotFoundException {
                return disco.doUnicastDiscovery(
                        s, dc.getUnfulfilledConstraints(), null, null, context);
            }
        }.getResponse(scheme(), host, port, constraints);
        return resp.getRegistrar();
    }
    
    /**
     * Return the string form of this LookupLocator, as a URL of scheme
     * <code>"jini"</code> or <code>"https"</code>.
     * @since 1.0
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme()).append("://").append(getHost0(host)).append(":").append(port).append("/");
        return sb.toString();
    }

    /**
     * Two locators are equal if they have the same <code>scheme</code>,
     * <code>host</code> and <code>port</code> fields. The case of 
     * <code>scheme</code> and <code>host</code> are ignored.
     * <p>
     * Alternative forms of the same IPv6 addresses for the <code>host</code>
     * component are normalized and treated as equal if they normalize to the same
     * form when following the recommendations set out in RFC5952:
     * <a href="https://www.ietf.org/rfc/rfc5952.txt">
     * <i>A Recommendation for IPv6 Address Text Representation</i></a>
     * @since 1.0
     */
    @Override
    public boolean equals(Object o) {
	if (o == this) {
	    return true;
	}
	if (o instanceof LookupLocator) {
	    LookupLocator oo = (LookupLocator) o;
	    return port == oo.port && scheme().equalsIgnoreCase(oo.scheme()) 
                    && host.equalsIgnoreCase(oo.host);
	}
	return false;
    }

    /**
     * Returns a hash code value calculated from the <code>scheme</code>,
     * <code>host</code> and <code>port</code> field values.
     * @since 1.0
     */
    @Override
    public int hashCode() {
        int hash = host.toLowerCase().hashCode() ^ port;
        hash = 17 * hash + (this.scheme().hashCode());
        return hash;
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
	    parseURI(scheme(), host, port);
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
