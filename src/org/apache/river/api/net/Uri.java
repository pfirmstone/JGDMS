/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.net;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.river.impl.Messages;


/**
 * This class represents an immutable instance of a URI as defined by RFC 3986.
 * 
 * This class behaves similarly to java.net.URI and is a drop in replacement, 
 * however all instances are normalised during construction, to comply with
 * RFC 3986.
 * 
 * Normalisation of java.net.URI was limited to the path, the scheme
 * and host are also normalised in accordance with RFC 3986.
 * 
 * It also has some additional useful static methods that deal with common
 * scenario's.
 * 
 * Unlike java.net.URI this class is not Serializable.
 * 
 */
public final class Uri implements Comparable<Uri> {

    private static final long serialVersionUID = -6052424284110960213l;

    static final String unreserved = "_-!.~\'()*"; //$NON-NLS-1$

    static final String punct = ",;:$&+="; //$NON-NLS-1$

    static final String reserved = punct + "?/[]@"; //$NON-NLS-1$

    static final String someLegal = unreserved + punct;

    static final String queryLegal = unreserved + reserved + "\\\""; //$NON-NLS-1$
    
    static final String allLegal = unreserved + reserved;

    private final String string;
    private final String scheme;
    private final String schemespecificpart;
    private final String authority;
    private final String userinfo;
    private final String host;
    private final int port;
    private final String path;
    private final String query;
    private final String fragment;
    private final boolean opaque;
    private final boolean absolute;
    private final boolean serverAuthority;
    private final String hashString;
    private final int hash;
  
    /**
     * 
     * @param string
     * @param scheme
     * @param schemespecificpart
     * @param authority
     * @param userinfo
     * @param host
     * @param port
     * @param path
     * @param query
     * @param fragment
     * @param opaque
     * @param absolute
     * @param serverAuthority
     * @param hash 
     */
    private Uri(String string,
            String scheme,
            String schemespecificpart,
            String authority,
            String userinfo,
            String host,
            int port,
            String path,
            String query,
            String fragment,
            boolean opaque,
            boolean absolute,
            boolean serverAuthority,
            int hash)
    {
        super();
        this.scheme = scheme;
        this.schemespecificpart = schemespecificpart;
        this.authority = authority;
        this.userinfo = userinfo;
        this.host = host;
        this.port = port;
        this.path = path;
        this.query = query;
        this.fragment = fragment;
        this.opaque = opaque;
        this.absolute = absolute;
        this.serverAuthority = serverAuthority;
        if (string == null) {
            StringBuilder result = new StringBuilder();
            if (scheme != null) {
                result.append(scheme);
                result.append(':');
            }
            if (opaque) {
                result.append(schemespecificpart);
            } else {
                if (authority != null) {
                    result.append("//"); //$NON-NLS-1$
                    result.append(authority);
                }

                if (path != null) {
                    result.append(path);
                }

                if (query != null) {
                    result.append('?');
                    result.append(query);
                }
            }

            if (fragment != null) {
                result.append('#');
                result.append(fragment);
            }

            this.string = result.toString();
        } else {
            this.string = string;
        }
        this.hashString = getHashString();
        this.hash = hash == -1 ? hashString.hashCode(): hash;
        
    }
    
    /**
     * Private constructor that doesn't throw URISyntaxException, all public
     * constructors are designed to avoid finalizer attacks by calling static 
     * methods that throw URISyntaxException, just in case we
     * decide to make this class non final at some point in future.
     * @param p 
     */
    private Uri(UriParser p){
        this(p.string,
        p.scheme,
        p.schemespecificpart,
        p.authority,
        p.userinfo,
        p.host,
        p.port,
        p.path,
        p.query,
        p.fragment,
        p.opaque,
        p.absolute,
        p.serverAuthority,
        p.hash);
    }
    
    /**
     * Creates a new URI instance according to the given string {@code uri}.
     *
     * @param uri
     *            the textual URI representation to be parsed into a URI object.
     * @throws URISyntaxException
     *             if the given string {@code uri} doesn't fit to the
     *             specification RFC2396 or could not be parsed correctly.
     */
    public Uri(String uri) throws URISyntaxException {
        this(constructor1(uri));
    }
    
    private static UriParser constructor1(String uri) throws URISyntaxException {
        UriParser p = new UriParser();
        p.parseURI(uri, false);
        return p;
    }

    /**
     * Creates a new URI instance using the given arguments. This constructor
     * first creates a temporary URI string from the given components. This
     * string will be parsed later on to create the URI instance.
     * <p>
     * {@code [scheme:]scheme-specific-part[#fragment]}
     *
     * @param scheme
     *            the scheme part of the URI.
     * @param ssp
     *            the scheme-specific-part of the URI.
     * @param frag
     *            the fragment part of the URI.
     * @throws URISyntaxException
     *             if the temporary created string doesn't fit to the
     *             specification RFC2396 or could not be parsed correctly.
     */
    public Uri(String scheme, String ssp, String frag) throws URISyntaxException {
        this(constructor2(scheme, ssp, frag));
    }
    
    private static UriParser constructor2(String scheme, String ssp, String frag) throws URISyntaxException{
        StringBuilder uri = new StringBuilder();
        if (scheme != null) {
            uri.append(scheme);
            uri.append(':');
        }
        if (ssp != null) {
            // QUOTE ILLEGAL CHARACTERS
            uri.append(quoteComponent(ssp, allLegal));
        }
        if (frag != null) {
            uri.append('#');
            // QUOTE ILLEGAL CHARACTERS
            uri.append(quoteComponent(frag, allLegal));
        }

        UriParser p = new UriParser();
        p.parseURI(uri.toString(), false);
        return p;
    }

    /**
     * Creates a new URI instance using the given arguments. This constructor
     * first creates a temporary URI string from the given components. This
     * string will be parsed later on to create the URI instance.
     * <p>
     * {@code [scheme:][user-info@]host[:port][path][?query][#fragment]}
     *
     * @param scheme
     *            the scheme part of the URI.
     * @param userinfo
     *            the user information of the URI for authentication and
     *            authorization.
     * @param host
     *            the host name of the URI.
     * @param port
     *            the port number of the URI.
     * @param path
     *            the path to the resource on the host.
     * @param query
     *            the query part of the URI to specify parameters for the
     *            resource.
     * @param fragment
     *            the fragment part of the URI.
     * @throws URISyntaxException
     *             if the temporary created string doesn't fit to the
     *             specification RFC2396 or could not be parsed correctly.
     */
    public Uri(String scheme, String userinfo, String host, int port,
            String path, String query, String fragment)
            throws URISyntaxException {
        this(constructor3(scheme, userinfo, host, port, path, query, fragment));
    }
    
    private static UriParser constructor3(String scheme, String userinfo, String host, int port,
            String path, String query, String fragment) throws URISyntaxException {
        if (scheme == null && userinfo == null && host == null && path == null
                && query == null && fragment == null) {
            UriParser p = new UriParser();
            p.path = ""; //$NON-NLS-1$
            return p;
        }

        if (scheme != null && path != null && path.length() > 0
                && path.charAt(0) != '/') {
            throw new URISyntaxException(path, Messages.getString("luni.82")); //$NON-NLS-1$
        }

        StringBuilder uri = new StringBuilder();
        if (scheme != null) {
            uri.append(scheme);
            uri.append(':');
        }

        if (userinfo != null || host != null || port != -1) {
            uri.append("//"); //$NON-NLS-1$
        }

        if (userinfo != null) {
            // QUOTE ILLEGAL CHARACTERS in userinfo
            uri.append(quoteComponent(userinfo, someLegal));
            uri.append('@');
        }

        if (host != null) {
            // check for ipv6 addresses that hasn't been enclosed
            // in square brackets
            if (host.indexOf(':') != -1 && host.indexOf(']') == -1
                    && host.indexOf('[') == -1) {
                host = "[" + host + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            uri.append(host);
        }

        if (port != -1) {
            uri.append(':');
            uri.append(port);
        }

        if (path != null) {
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(path, "/@" + someLegal)); //$NON-NLS-1$
        }

        if (query != null) {
            uri.append('?');
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(query, allLegal));
        }

        if (fragment != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('#');
            uri.append(quoteComponent(fragment, allLegal));
        }

        UriParser p = new UriParser();
        p.parseURI(uri.toString(), true);
        return p;
    }

    /**
     * Creates a new URI instance using the given arguments. This constructor
     * first creates a temporary URI string from the given components. This
     * string will be parsed later on to create the URI instance.
     * <p>
     * {@code [scheme:]host[path][#fragment]}
     *
     * @param scheme
     *            the scheme part of the URI.
     * @param host
     *            the host name of the URI.
     * @param path
     *            the path to the resource on the host.
     * @param fragment
     *            the fragment part of the URI.
     * @throws URISyntaxException
     *             if the temporary created string doesn't fit to the
     *             specification RFC2396 or could not be parsed correctly.
     */
    public Uri(String scheme, String host, String path, String fragment)
            throws URISyntaxException {
        this(scheme, null, host, -1, path, null, fragment);
    }

    /**
     * Creates a new URI instance using the given arguments. This constructor
     * first creates a temporary URI string from the given components. This
     * string will be parsed later on to create the URI instance.
     * <p>
     * {@code [scheme:][//authority][path][?query][#fragment]}
     *
     * @param scheme
     *            the scheme part of the URI.
     * @param authority
     *            the authority part of the URI.
     * @param path
     *            the path to the resource on the host.
     * @param query
     *            the query part of the URI to specify parameters for the
     *            resource.
     * @param fragment
     *            the fragment part of the URI.
     * @throws URISyntaxException
     *             if the temporary created string doesn't fit to the
     *             specification RFC2396 or could not be parsed correctly.
     */
    public Uri(String scheme, String authority, String path, String query,
            String fragment) throws URISyntaxException {
        this(constructor4(scheme, authority, path, query, fragment));
    }

    private static UriParser constructor4(String scheme, String authority, String path, String query,
            String fragment) throws URISyntaxException {
        if (scheme != null && path != null && path.length() > 0
                && path.charAt(0) != '/') {
            throw new URISyntaxException(path, Messages.getString("luni.82")); //$NON-NLS-1$
        }

        StringBuilder uri = new StringBuilder();
        if (scheme != null) {
            uri.append(scheme);
            uri.append(':');
        }
        if (authority != null) {
            uri.append("//"); //$NON-NLS-1$
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(authority, "@[]" + someLegal)); //$NON-NLS-1$
        }

        if (path != null) {
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(path, "/@" + someLegal)); //$NON-NLS-1$
        }
        if (query != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('?');
            uri.append(quoteComponent(query, allLegal));
        }
        if (fragment != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('#');
            uri.append(quoteComponent(fragment, allLegal));
        }

        UriParser p = new UriParser();
        p.parseURI(uri.toString(), false);
        return p;
    }
    
    /*
     * Quote illegal chars for each component, but not the others
     * 
     * @param component java.lang.String the component to be converted @param
     * legalset java.lang.String the legal character set allowed in the
     * component s @return java.lang.String the converted string
     */
    private static String quoteComponent(String component, String legalset) {
        try {
            /*
             * Use a different encoder than URLEncoder since: 1. chars like "/",
             * "#", "@" etc needs to be preserved instead of being encoded, 2.
             * UTF-8 char set needs to be used for encoding instead of default
             * platform one
             */
            return URIEncoderDecoder.quoteIllegal(component, legalset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Compares this URI with the given argument {@code uri}. This method will
     * return a negative value if this URI instance is less than the given
     * argument and a positive value if this URI instance is greater than the
     * given argument. The return value {@code 0} indicates that the two
     * instances represent the same URI. To define the order the single parts of
     * the URI are compared with each other. String components will be orderer
     * in the natural case-sensitive way. A hierarchical URI is less than an
     * opaque URI and if one part is {@code null} the URI with the undefined
     * part is less than the other one.
     *
     * @param uri
     *            the URI this instance has to compare with.
     * @return the value representing the order of the two instances.
     */
    public int compareTo(Uri uri) {
        int ret = 0;

        // compare schemes
        if (scheme == null && uri.scheme != null) {
            return -1;
        } else if (scheme != null && uri.scheme == null) {
            return 1;
        } else if (scheme != null && uri.scheme != null) {
            ret = scheme.compareToIgnoreCase(uri.scheme);
            if (ret != 0) {
                return ret;
            }
        }

        // compare opacities
        if (!opaque && uri.opaque) {
            return -1;
        } else if (opaque && !uri.opaque) {
            return 1;
        } else if (opaque && uri.opaque) {
            ret = schemespecificpart.compareTo(uri.schemespecificpart);
            if (ret != 0) {
                return ret;
            }
        } else {

            // otherwise both must be hierarchical

            // compare authorities
            if (authority != null && uri.authority == null) {
                return 1;
            } else if (authority == null && uri.authority != null) {
                return -1;
            } else if (authority != null && uri.authority != null) {
                if (host != null && uri.host != null) {
                    // both are server based, so compare userinfo, host, port
                    if (userinfo != null && uri.userinfo == null) {
                        return 1;
                    } else if (userinfo == null && uri.userinfo != null) {
                        return -1;
                    } else if (userinfo != null && uri.userinfo != null) {
                        ret = userinfo.compareTo(uri.userinfo);
                        if (ret != 0) {
                            return ret;
                        }
                    }

                    // userinfo's are the same, compare hostname
                    ret = host.compareToIgnoreCase(uri.host);
                    if (ret != 0) {
                        return ret;
                    }

                    // compare port
                    if (port != uri.port) {
                        return port - uri.port;
                    }
                } else { // one or both are registry based, compare the whole
                    // authority
                    ret = authority.compareTo(uri.authority);
                    if (ret != 0) {
                        return ret;
                    }
                }
            }

            // authorities are the same
            // compare paths
            ret = path.compareTo(uri.path);
            if (ret != 0) {
                return ret;
            }

            // compare queries

            if (query != null && uri.query == null) {
                return 1;
            } else if (query == null && uri.query != null) {
                return -1;
            } else if (query != null && uri.query != null) {
                ret = query.compareTo(uri.query);
                if (ret != 0) {
                    return ret;
                }
            }
        }

        // everything else is identical, so compare fragments
        if (fragment != null && uri.fragment == null) {
            return 1;
        } else if (fragment == null && uri.fragment != null) {
            return -1;
        } else if (fragment != null && uri.fragment != null) {
            ret = fragment.compareTo(uri.fragment);
            if (ret != 0) {
                return ret;
            }
        }

        // identical
        return 0;
    }

    /**
     * Parses the given argument {@code rfc3986compliantURI} and creates an appropriate URI
     * instance.
     * 
     * The parameter string is checked for compliance, an IllegalArgumentException
     * is thrown if the string is non compliant.
     *
     * @param rfc3986compliantURI
     *            the string which has to be parsed to create the URI instance.
     * @return the created instance representing the given URI.
     */
    public static Uri create(String rfc3986compliantURI) {
        Uri result = null;
        try {
            result = new Uri(rfc3986compliantURI);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return result;
    }
    
    /**
     * The parameter string doesn't contain any existing escape sequences, any
     * escape character % found is encoded as %25.
     * 
     * The Uri is normalised according to RFC3986.
     * 
     * @param unescapedString
     * @return 
     */
    public static Uri escapeAndCreate(String unescapedString){
        throw new UnsupportedOperationException("not supported");
    }
    
    /**
     * The parameter string may already contain escaped sequences, any illegal
     * characters are escaped and any that shouldn't be escaped are un-escaped.
     * 
     * The escape character % is not re-encoded.
     * @param nonCompliantEscapedString 
     * @return 
     */
    public static Uri parseAndCreate(String nonCompliantEscapedString){
        throw new UnsupportedOperationException("not supported");
    }
    
    // No point cloning an immutable object.
//    public Uri clone() {
//        return new Uri( string,
//                        scheme,
//                        schemespecificpart,
//                        authority,
//                        userinfo,
//                        host,
//                        port,
//                        path,
//                        query,
//                        fragment,
//                        opaque,
//                        absolute,
//                        serverAuthority,
//                        hash);
//    }

    /*
     * Takes a string that may contain hex sequences like %F1 or %2b and
     * converts the hex values following the '%' to lowercase
     */
    private String convertHexToLowerCase(String s) {
        StringBuilder result = new StringBuilder(""); //$NON-NLS-1$
        if (s.indexOf('%') == -1) {
            return s;
        }

        int index = 0, previndex = 0;
        while ((index = s.indexOf('%', previndex)) != -1) {
            result.append(s.substring(previndex, index + 1));
            result.append(s.substring(index + 1, index + 3).toLowerCase());
            index += 3;
            previndex = index;
        }
        return result.toString();
    }

    /*
     * Takes two strings that may contain hex sequences like %F1 or %2b and
     * compares them, ignoring case for the hex values. Hex values must always
     * occur in pairs as above
     */
    private boolean equalsHexCaseInsensitive(String first, String second) {
        if (first.indexOf('%') != second.indexOf('%')) {
            return first.equals(second);
        }

        int index = 0, previndex = 0;
        while ((index = first.indexOf('%', previndex)) != -1
                && second.indexOf('%', previndex) == index) {
            boolean match = first.substring(previndex, index).equals(
                    second.substring(previndex, index));
            if (!match) {
                return false;
            }

            match = first.substring(index + 1, index + 3).equalsIgnoreCase(
                    second.substring(index + 1, index + 3));
            if (!match) {
                return false;
            }

            index += 3;
            previndex = index;
        }
        return first.substring(previndex).equals(second.substring(previndex));
    }

    /**
     * Compares this URI instance with the given argument {@code o} and
     * determines if both are equal. Two URI instances are equal if all single
     * parts are identical in their meaning.
     *
     * @param o
     *            the URI this instance has to be compared with.
     * @return {@code true} if both URI instances point to the same resource,
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Uri)) {
            return false;
        }
        Uri uri = (Uri) o;

        if (uri.fragment == null && fragment != null || uri.fragment != null
                && fragment == null) {
            return false;
        } else if (uri.fragment != null && fragment != null) {
            if (!equalsHexCaseInsensitive(uri.fragment, fragment)) {
                return false;
            }
        }

        if (uri.scheme == null && scheme != null || uri.scheme != null
                && scheme == null) {
            return false;
        } else if (uri.scheme != null && scheme != null) {
            if (!uri.scheme.equalsIgnoreCase(scheme)) {
                return false;
            }
        }

        if (uri.opaque && opaque) {
            return equalsHexCaseInsensitive(uri.schemespecificpart,
                    schemespecificpart);
        } else if (!uri.opaque && !opaque) {
            if (!equalsHexCaseInsensitive(path, uri.path)) {
                return false;
            }

            if (uri.query != null && query == null || uri.query == null
                    && query != null) {
                return false;
            } else if (uri.query != null && query != null) {
                if (!equalsHexCaseInsensitive(uri.query, query)) {
                    return false;
                }
            }

            if (uri.authority != null && authority == null
                    || uri.authority == null && authority != null) {
                return false;
            } else if (uri.authority != null && authority != null) {
                if (uri.host != null && host == null || uri.host == null
                        && host != null) {
                    return false;
                } else if (uri.host == null && host == null) {
                    // both are registry based, so compare the whole authority
                    return equalsHexCaseInsensitive(uri.authority, authority);
                } else { // uri.host != null && host != null, so server-based
                    if (!host.equalsIgnoreCase(uri.host)) {
                        return false;
                    }

                    if (port != uri.port) {
                        return false;
                    }

                    if (uri.userinfo != null && userinfo == null
                            || uri.userinfo == null && userinfo != null) {
                        return false;
                    } else if (uri.userinfo != null && userinfo != null) {
                        return equalsHexCaseInsensitive(userinfo, uri.userinfo);
                    } else {
                        return true;
                    }
                }
            } else {
                // no authority
                return true;
            }

        } else {
            // one is opaque, the other hierarchical
            return false;
        }
    }

    /**
     * Gets the decoded authority part of this URI.
     *
     * @return the decoded authority part or {@code null} if undefined.
     */
    public String getAuthority() {
        return decode(authority);
    }

    /**
     * Gets the decoded fragment part of this URI.
     * 
     * @return the decoded fragment part or {@code null} if undefined.
     */
    public String getFragment() {
        return decode(fragment);
    }

    /**
     * Gets the host part of this URI.
     * 
     * @return the host part or {@code null} if undefined.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the decoded path part of this URI.
     * 
     * @return the decoded path part or {@code null} if undefined.
     */
    public String getPath() {
        return decode(path);
    }

    /**
     * Gets the port number of this URI.
     * 
     * @return the port number or {@code -1} if undefined.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the decoded query part of this URI.
     * 
     * @return the decoded query part or {@code null} if undefined.
     */
    public String getQuery() {
        return decode(query);
    }

    /**
     * Gets the authority part of this URI in raw form.
     * 
     * @return the encoded authority part or {@code null} if undefined.
     */
    public String getRawAuthority() {
        return authority;
    }

    /**
     * Gets the fragment part of this URI in raw form.
     * 
     * @return the encoded fragment part or {@code null} if undefined.
     */
    public String getRawFragment() {
        return fragment;
    }

    /**
     * Gets the path part of this URI in raw form.
     * 
     * @return the encoded path part or {@code null} if undefined.
     */
    public String getRawPath() {
        return path;
    }

    /**
     * Gets the query part of this URI in raw form.
     * 
     * @return the encoded query part or {@code null} if undefined.
     */
    public String getRawQuery() {
        return query;
    }

    /**
     * Gets the scheme-specific part of this URI in raw form.
     * 
     * @return the encoded scheme-specific part or {@code null} if undefined.
     */
    public String getRawSchemeSpecificPart() {
        return schemespecificpart;
    }

    /**
     * Gets the user-info part of this URI in raw form.
     * 
     * @return the encoded user-info part or {@code null} if undefined.
     */
    public String getRawUserInfo() {
        return userinfo;
    }

    /**
     * Gets the scheme part of this URI.
     * 
     * @return the scheme part or {@code null} if undefined.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Gets the decoded scheme-specific part of this URI.
     * 
     * @return the decoded scheme-specific part or {@code null} if undefined.
     */
    public String getSchemeSpecificPart() {
        return decode(schemespecificpart);
    }

    /**
     * Gets the decoded user-info part of this URI.
     * 
     * @return the decoded user-info part or {@code null} if undefined.
     */
    public String getUserInfo() {
        return decode(userinfo);
    }

    /**
     * Gets the hashcode value of this URI instance.
     *
     * @return the appropriate hashcode value.
     */
    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * Indicates whether this URI is absolute, which means that a scheme part is
     * defined in this URI.
     * 
     * @return {@code true} if this URI is absolute, {@code false} otherwise.
     */
    public boolean isAbsolute() {
        return absolute;
    }

    /**
     * Indicates whether this URI is opaque or not. An opaque URI is absolute
     * and has a scheme-specific part which does not start with a slash
     * character. All parts except scheme, scheme-specific and fragment are
     * undefined.
     * 
     * @return {@code true} if the URI is opaque, {@code false} otherwise.
     */
    public boolean isOpaque() {
        return opaque;
    }
    
    /**
     * Normalizes the path part of this URI.
     *
     * @return an URI object which represents this instance with a normalized
     *         path.
     */
    public Uri normalize() {
        if (opaque) {
            return this;
        }
        String normalizedPath = normalize(path);
        // if the path is already normalized, return this
        if (path.equals(normalizedPath)) {
            return this;
        }
        // get an exact copy of the URI re-calculate the scheme specific part
        // since the path of the normalized URI is different from this URI.
        return new Uri( null,
                        scheme,
                        setSchemeSpecificPart(authority, normalizedPath , query),
                        authority,
                        userinfo,
                        host,
                        port,
                        normalizedPath,
                        query,
                        fragment,
                        opaque,
                        absolute,
                        serverAuthority,
                        hash);
    }


    /*
     * normalize path, and return the resulting string
     */
    private String normalize(String path) {
        // count the number of '/'s, to determine number of segments
        int index = -1;
        int pathlen = path.length();
        int size = 0;
        if (pathlen > 0 && path.charAt(0) != '/') {
            size++;
        }
        while ((index = path.indexOf('/', index + 1)) != -1) {
            if (index + 1 < pathlen && path.charAt(index + 1) != '/') {
                size++;
            }
        }

        String[] seglist = new String[size];
        boolean[] include = new boolean[size];

        // break the path into segments and store in the list
        int current = 0;
        int index2 = 0;
        index = (pathlen > 0 && path.charAt(0) == '/') ? 1 : 0;
        while ((index2 = path.indexOf('/', index + 1)) != -1) {
            seglist[current++] = path.substring(index, index2);
            index = index2 + 1;
        }

        // if current==size, then the last character was a slash
        // and there are no more segments
        if (current < size) {
            seglist[current] = path.substring(index);
        }

        // determine which segments get included in the normalized path
        for (int i = 0; i < size; i++) {
            include[i] = true;
            if (seglist[i].equals("..")) { //$NON-NLS-1$
                int remove = i - 1;
                // search back to find a segment to remove, if possible
                while (remove > -1 && !include[remove]) {
                    remove--;
                }
                // if we find a segment to remove, remove it and the ".."
                // segment
                if (remove > -1 && !seglist[remove].equals("..")) { //$NON-NLS-1$
                    include[remove] = false;
                    include[i] = false;
                }
            } else if (seglist[i].equals(".")) { //$NON-NLS-1$
                include[i] = false;
            }
        }

        // put the path back together
        StringBuilder newpath = new StringBuilder();
        if (path.startsWith("/")) { //$NON-NLS-1$
            newpath.append('/');
        }

        for (int i = 0; i < seglist.length; i++) {
            if (include[i]) {
                newpath.append(seglist[i]);
                newpath.append('/');
            }
        }

        // if we used at least one segment and the path previously ended with
        // a slash and the last segment is still used, then delete the extra
        // trailing '/'
        if (!path.endsWith("/") && seglist.length > 0 //$NON-NLS-1$
                && include[seglist.length - 1]) {
            newpath.deleteCharAt(newpath.length() - 1);
        }

        String result = newpath.toString();

        // check for a ':' in the first segment if one exists,
        // prepend "./" to normalize
        index = result.indexOf(':');
        index2 = result.indexOf('/');
        if (index != -1 && (index < index2 || index2 == -1)) {
            newpath.insert(0, "./"); //$NON-NLS-1$
            result = newpath.toString();
        }
        return result;
    }

    /**
     * Tries to parse the authority component of this URI to divide it into the
     * host, port, and user-info. If this URI is already determined as a
     * ServerAuthority this instance will be returned without changes.
     *
     * @return this instance with the components of the parsed server authority.
     * @throws URISyntaxException
     *             if the authority part could not be parsed as a server-based
     *             authority.
     */
    public Uri parseServerAuthority() throws URISyntaxException {
        if (!serverAuthority) {
            UriParser p = new UriParser();
            p.parseURI(this.toString(), false);
            p.parseAuthority(true);
            return new Uri(p);
        }
        return this;
    }

    /**
     * Makes the given URI {@code relative} to a relative URI against the URI
     * represented by this instance.
     *
     * @param relative
     *            the URI which has to be relativized against this URI.
     * @return the relative URI.
     */
    public Uri relativize(Uri relative) {
        if (relative.opaque || opaque) {
            return relative;
        }

        if (scheme == null ? relative.scheme != null : !scheme
                .equals(relative.scheme)) {
            return relative;
        }

        if (authority == null ? relative.authority != null : !authority
                .equals(relative.authority)) {
            return relative;
        }

        // normalize both paths
        String thisPath = normalize(path);
        String relativePath = normalize(relative.path);

        /*
         * if the paths aren't equal, then we need to determine if this URI's
         * path is a parent path (begins with) the relative URI's path
         */
        if (!thisPath.equals(relativePath)) {
            // if this URI's path doesn't end in a '/', add one
            if (!thisPath.endsWith("/")) { //$NON-NLS-1$
                thisPath = thisPath + '/';
            }
            /*
             * if the relative URI's path doesn't start with this URI's path,
             * then just return the relative URI; the URIs have nothing in
             * common
             */
            if (!relativePath.startsWith(thisPath)) {
                return relative;
            }
        }

        String query = relative.query;
        // the result URI is the remainder of the relative URI's path
        String path = relativePath.substring(thisPath.length());
        return new Uri( null,
                        null,
                        setSchemeSpecificPart(null, path, query),
                        null,
                        null,
                        null,
                        -1,
                        path,
                        query,
                        relative.fragment,
                        false,
                        false,
                        false,
                        -1);
    }

    /**
     * Resolves the given URI {@code relative} against the URI represented by
     * this instance.
     *
     * @param relative
     *            the URI which has to be resolved against this URI.
     * @return the resolved URI.
     */
    public Uri resolve(Uri relative) {
        if (relative.absolute || opaque) {
            return relative;
        }

        if (relative.path.equals("") && relative.scheme == null //$NON-NLS-1$
                && relative.authority == null && relative.query == null
                && relative.fragment != null) {
            // if the relative URI only consists of fragment,
            // the resolved URI is very similar to this URI,
            // except that it has the fragement from the relative URI.
            
            return new Uri( null,
                        scheme,
                        schemespecificpart,
                        authority,
                        userinfo,
                        host,
                        port,
                        path,
                        query,
                        relative.fragment,
                        opaque,
                        absolute,
                        serverAuthority,
                        hash);
            // no need to re-calculate the scheme specific part,
            // since fragment is not part of scheme specific part.
           
        }

        if (relative.authority != null) {
            // if the relative URI has authority,
            // the resolved URI is almost the same as the relative URI,
            // except that it has the scheme of this URI.
            return new Uri( null,
                        scheme,
                        relative.schemespecificpart,
                        relative.authority,
                        relative.userinfo,
                        relative.host,
                        relative.port,
                        relative.path,
                        relative.query,
                        relative.fragment,
                        relative.opaque,
                        absolute,
                        relative.serverAuthority,
                        relative.hash);
        } else {
            // since relative URI has no authority,
            // the resolved URI is very similar to this URI,
            // except that it has the query and fragment of the relative URI,
            // and the path is different.
            // re-calculate the scheme specific part since
            // query and path of the resolved URI is different from this URI.
            int endindex = path.lastIndexOf('/') + 1;
            String p = relative.path.startsWith("/")? relative.path: 
                        normalize(path.substring(0, endindex) + relative.path);
            return new Uri( null,
                        scheme,
                        setSchemeSpecificPart(authority, p, relative.query),
                        authority,
                        userinfo,
                        host,
                        port,
                        p,
                        relative.query,
                        relative.fragment,
                        opaque,
                        absolute,
                        serverAuthority,
                        hash);
        }
    }

    /**
     * UriParser method used to re-calculate the scheme specific part of the
     * resolved or normalized URIs
     */
    private String setSchemeSpecificPart(String authority,
                                         String path,
                                         String query) {
        // ssp = [//authority][path][?query]
        StringBuilder ssp = new StringBuilder();
        if (authority != null) {
            ssp.append("//" + authority); //$NON-NLS-1$
        }
        if (path != null) {
            ssp.append(path);
        }
        if (query != null) {
            ssp.append("?" + query); //$NON-NLS-1$
        }
        // reset string, so that it can be re-calculated correctly when asked.
        return ssp.toString();
    }

    /**
     * Creates a new URI instance by parsing the given string {@code relative}
     * and resolves the created URI against the URI represented by this
     * instance.
     *
     * @param relative
     *            the given string to create the new URI instance which has to
     *            be resolved later on.
     * @return the created and resolved URI.
     */
    public Uri resolve(String relative) {
        return resolve(create(relative));
    }

    /*
     * Encode unicode chars that are not part of US-ASCII char set into the
     * escaped form
     * 
     * i.e. The Euro currency symbol is encoded as "%E2%82%AC".
     * 
     * @param component java.lang.String the component to be converted @param
     * legalset java.lang.String the legal character set allowed in the
     * component s @return java.lang.String the converted string
     */
    private String encodeOthers(String s) {
        try {
            /*
             * Use a different encoder than URLEncoder since: 1. chars like "/",
             * "#", "@" etc needs to be preserved instead of being encoded, 2.
             * UTF-8 char set needs to be used for encoding instead of default
             * platform one 3. Only other chars need to be converted
             */
            return URIEncoderDecoder.encodeOthers(s);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString());
        }
    }

    private String decode(String s) {
        if (s == null) {
            return s;
        }

        try {
            return URIEncoderDecoder.decode(s);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Returns the textual string representation of this URI instance using the
     * US-ASCII encoding.
     *
     * @return the US-ASCII string representation of this URI.
     */
    public String toASCIIString() {
        return encodeOthers(toString());
    }

    /**
     * Returns the textual string representation of this URI instance.
     *
     * @return the textual string representation of this URI.
     */
    @Override
    public String toString() {
        return string;
    }

    /*
     * Form a string from the components of this URI, similarly to the
     * toString() method. But this method converts scheme and host to lowercase,
     * and converts escaped octets to lowercase.
     * 
     * Should convert octets to uppercase and follow platform specific 
     * normalization rules for file: uri.
     */
    private String getHashString() {
        StringBuilder result = new StringBuilder();
        if (scheme != null) {
            result.append(scheme.toLowerCase());
            result.append(':');
        }
        if (opaque) {
            result.append(schemespecificpart);
        } else {
            if (authority != null) {
                result.append("//"); //$NON-NLS-1$
                if (host == null) {
                    result.append(authority);
                } else {
                    if (userinfo != null) {
                        result.append(userinfo + "@"); //$NON-NLS-1$
                    }
                    result.append(host.toLowerCase());
                    if (port != -1) {
                        result.append(":" + port); //$NON-NLS-1$
                    }
                }
            }

            if (path != null) {
                result.append(path);
            }

            if (query != null) {
                result.append('?');
                result.append(query);
            }
        }

        if (fragment != null) {
            result.append('#');
            result.append(fragment);
        }

        return convertHexToLowerCase(result.toString());
    }

    /**
     * Converts this URI instance to a URL.
     *
     * @return the created URL representing the same resource as this URI.
     * @throws MalformedURLException
     *             if an error occurs while creating the URL or no protocol
     *             handler could be found.
     */
    public URL toURL() throws MalformedURLException {
        if (!absolute) {
            throw new IllegalArgumentException(Messages.getString("luni.91") + ": " //$NON-NLS-1$//$NON-NLS-2$
                    + toString());
        }
        return new URL(toString());
    }
}
