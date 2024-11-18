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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.river.impl.Messages;


/**
 * This class represents an immutable instance of a URI as defined by RFC 3986.
 * <p>
 * This class replaces java.net.URI functionality.
 * <p>
 * Unlike java.net.URI this class is not Serializable and hashCode and 
 * equality is governed by strict RFC3986 normalisation. In addition "other"
 * characters allowed in java.net.URI as specified by javadoc, not specifically 
 * allowed by RFC3986 are illegal and must be escaped.  This strict adherence
 * is essential to eliminate false negative or positive matches.
 * <p>
 * In addition to RFC3896 normalisation, on OS platforms with a \ file separator
 * the path is converted to UPPER CASE for comparison for file: schema, during
 * equals and hashCode calls.
 * <p>
 * IPv6 and IPvFuture host addresses must be enclosed in square brackets as per 
 * RFC3986.  A zone delimiter %, if present, must be represented in escaped %25
 * form as per RFC6874.
 * <p>
 * In addition to RFC3986 normalization, IPv6 host addresses will be normalized
 * to comply with RFC 5952 A Recommendation for IPv6 Address Text Representation. 
 * This is to ensure consistent equality between identical IPv6 addresses.
 * 
 * @since 3.0.0
 */
public final class Uri implements Comparable<Uri> {

    /* Class Implementation */
    
    /* Legacy java.net.URI RFC 2396 syntax*/
    static final String unreserved = "_-!.~\'()*"; //$NON-NLS-1$
    static final String punct = ",;:$&+="; //$NON-NLS-1$
    static final String reserved = punct + "?/[]@"; //$NON-NLS-1$
    // String someLegal = unreserved + punct;
    // String queryLegal = unreserved + reserved + "\\\""; //$NON-NLS-1$
    // String allLegal = unreserved + reserved;
    
    static final String someLegal = unreserved + punct;
    
    static final String queryLegal = unreserved + reserved + "\\\""; //$NON-NLS-1$
    
//    static final String allLegal = unreserved + reserved;
    
    /* RFC 3986 */
//    private static final char [] latin = new char[256];
//    private static final String [] latinEsc = new String[256];
    
    /* 2.1.  Percent-Encoding
     * 
     * A percent-encoding mechanism is used to represent a data octet in a
     * component when that octet's corresponding character is outside the
     * allowed set or is being used as a delimiter of, or within, the
     * component.  A percent-encoded octet is encoded as a character
     * triplet, consisting of the percent character "%" followed by the two
     * hexadecimal digits representing that octet's numeric value.  For
     * example, "%20" is the percent-encoding for the binary octet
     * "00100000" (ABNF: %x20), which in US-ASCII corresponds to the space
     * character (SP).  Section 2.4 describes when percent-encoding and
     * decoding is applied.
     * 
     *    pct-encoded = "%" HEXDIG HEXDIG
     * 
     * The uppercase hexadecimal digits 'A' through 'F' are equivalent to
     * the lowercase digits 'a' through 'f', respectively.  If two URIs
     * differ only in the case of hexadecimal digits used in percent-encoded
     * octets, they are equivalent.  For consistency, URI producers and
     * normalizers should use uppercase hexadecimal digits for all percent-
     * encodings.
     */
    // Any character that is not part of the reserved and unreserved sets must
    // be encoded.
    // Section 2.1 Percent encoding must be converted to upper case during normalisation.
    private static final char escape = '%';
     /* RFC3986 obsoletes RFC2396 and RFC2732
     * 
     * reserved    = gen-delims / sub-delims
     * 
     * gen-delims  = ":" / "/" / "?" / "#" / "[" / "]" / "@"
     * 
     * sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
     *               / "*" / "+" / "," / ";" / "="
     */
    // Section 2.2 Reserved set is protected from normalisation.
//    private static final char [] gen_delims = {':', '/', '?', '#', '[', ']', '@'};
//    private static final char [] sub_delims = {'!', '$', '&', '\'', '(', ')', '*',
//                                                            '+', ',', ';', '='};
    /*
     * For consistency, percent-encoded octets in the ranges of ALPHA
     * (%41-%5A and %61-%7A), DIGIT (%30-%39), hyphen (%2D), period (%2E),
     * underscore (%5F), or tilde (%7E) should not be created by URI
     * producers and, when found in a URI, should be decoded to their
     * corresponding unreserved characters by URI normalizers.
     */
    // Section 2.3 Unreserved characters (Allowed) must be decoded during normalisation if % encoded.
//    private static final char [] lowalpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();
//    private static final char [] upalpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
//    private static final char [] numeric = "0123456789".toCharArray();
//    private static final char [] unres_punct =  {'-' , '.' , '_' , '~'};
    
    // Section 3.1 Scheme
//    private static final char [] schemeEx = "+-.".toCharArray(); // + ALPHA and numeric.
    
    // To be unescaped during normalisation, unmodifiable and safely published.
//    final static Map<String, Character> unReserved; 
//    final static Map<String, Character> schemeUnreserved;
    
    /* Explicit legal String fields follow, ALPHA and DIGIT are implicitly legal */
    
    /* All characters that are legal URI syntax */
    static final String allLegalUnescaped = ":/?#[]@!$&'()*+,;=-._~";
    static final String allLegal = "%:/?#[]@!$&'()*+,;=-._~";
    /*
     *  Syntax Summary
     * 
     *  URI         = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
     * 
     *  hier-part   = "//" authority path-abempty
     *              / path-absolute
     *              / path-rootless
     *              / path-empty
     *
     *  scheme      = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
     */
    static final String schemeLegal = "+-.";
    /* 
     *  authority   = [ userinfo "@" ] host [ ":" port ]
     *  userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
     */ 
    static final String userinfoLegal = "-._~!$&'()*+,;=:";
    static final String authorityLegal = userinfoLegal + "@[]";
    /*  host        = IP-literal / IPv4address / reg-name
     *  IP-literal = "[" ( IPv6address / IPvFuture  ) "]"
     *  IPvFuture  = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )
     */ 
    static final String iPvFuture = "-._~!$&'()*+,;=:";
    /*  IPv6address =                            6( h16 ":" ) ls32
     *              /                       "::" 5( h16 ":" ) ls32
     *              / [               h16 ] "::" 4( h16 ":" ) ls32
     *              / [ *1( h16 ":" ) h16 ] "::" 3( h16 ":" ) ls32
     *              / [ *2( h16 ":" ) h16 ] "::" 2( h16 ":" ) ls32
     *              / [ *3( h16 ":" ) h16 ] "::"    h16 ":"   ls32
     *              / [ *4( h16 ":" ) h16 ] "::"              ls32
     *              / [ *5( h16 ":" ) h16 ] "::"              h16
     *              / [ *6( h16 ":" ) h16 ] "::"
     * 
     *  ls32        = ( h16 ":" h16 ) / IPv4address
     *              ; least-significant 32 bits of address
     * 
     *  h16         = 1*4HEXDIG
     *              ; 16 bits of address represented in hexadecimal
     * 
     *  IPv4address = dec-octet "." dec-octet "." dec-octet "." dec-octet
     * 
     *  dec-octet   = DIGIT                 ; 0-9
     *              / %x31-39 DIGIT         ; 10-99
     *              / "1" 2DIGIT            ; 100-199
     *              / "2" %x30-34 DIGIT     ; 200-249
     *              / "25" %x30-35          ; 250-255
     *  reg-name    = *( unreserved / pct-encoded / sub-delims )
     */
    static final String hostRegNameLegal = "-._~!$&'()*+,;=";
    /*  port        = *DIGIT
     * 
     *  path        = path-abempty    ; begins with "/" or is empty
     *              / path-absolute   ; begins with "/" but not "//"
     *              / path-noscheme   ; begins with a non-colon segment
     *              / path-rootless   ; begins with a segment
     *              / path-empty      ; zero characters
     * 
     *  path-abempty  = *( "/" segment )
     *  path-absolute = "/" [ segment-nz *( "/" segment ) ]
     *  path-noscheme = segment-nz-nc *( "/" segment )
     *  path-rootless = segment-nz *( "/" segment )
     *  path-empty    = 0<pchar>
     *  
     *  segment       = *pchar
     *  segment-nz    = 1*pchar
     *  segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" ) ; non-zero-length segment without any colon ":"
     * 
     *  pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
     */ 
    static final String pcharLegal = "-._~!$&'()*+,;=:@";
    static final String segmentNzNcLegal = "-._~!$&'()*+,;=@";
    static final String segmentLegal = pcharLegal;
    static final String pathLegal = segmentLegal + "/";
            
    /*  query       = *( pchar / "/" / "?" )
     * 
     *  fragment    = *( pchar / "/" / "?" )
     */
    static final String queryFragLegal = pcharLegal + "/?";
  
    private final static char a = 'a';
    private final static char z = 'z';
    private final static char A = 'A';
    private final static char Z = 'Z';
    private final static char upperCaseBitwiseMask = 0xdf;
    private final static char lowerCaseBitwiseMask = 0x20;
    
    static String toAsciiUpperCase(String s){
        return new String(toAsciiUpperCase(s.toCharArray()));
    }
    
    static char [] toAsciiUpperCase(char [] array){
        int length = array.length;
        for (int i = 0; i < length ; i++){
            if (array[i] >= a && array[i] <= z) {
                array[i] = toAsciiUpperCase(array[i]);
            } 
        }
        return array;
    }
    
    static char toAsciiUpperCase(char c){
        return (char) (c & upperCaseBitwiseMask);
    }
    
    static String toAsciiLowerCase(String s){
        return new String(toAsciiLowerCase(s.toCharArray()));
    }
    
    static char[] toAsciiLowerCase(char [] array){
        int length = array.length;
        for (int i = 0; i < length ; i++){
            if (array[i] >= A && array[i] <= Z) {
                array[i] = toAsciiLowerCase(array[i]);
            }
        }
        return array;
    }
    
    static char toAsciiLowerCase(char c){
        return (char) (c | lowerCaseBitwiseMask);
    }
    
    static boolean charArraysEqual( char [] a , char [] b){
        int alen = a.length;
        int blen = b.length;
        if (alen != blen) return false;
        for (int i = 0; i < alen; i++){
            if (a[i] !=  b[i]) return false;
        }
        return true;
    }
    
    static boolean asciiStringsUpperCaseEqual(String a, String b){
        char [] ac = a.toCharArray();
        toAsciiUpperCase(ac);
        char [] bc = b.toCharArray();
        toAsciiUpperCase(bc);
        return charArraysEqual(ac, bc);
    }
    
    static boolean asciiStringsLowerCaseEqual(String a, String b){
        char [] ac = a.toCharArray();
        toAsciiLowerCase(ac);
        char [] bc = b.toCharArray();
        toAsciiLowerCase(bc);
        return charArraysEqual(ac, bc);
    }
    /** Fixes windows file URI string by converting back slashes to forward
     * slashes and inserting a forward slash before the drive letter if it is
     * missing.  No normalisation or modification of case is performed.
     * @param uri String representation of URI
     * @return fixed URI String
     */
    public static String fixWindowsURI(String uri) {
        if (uri == null) return null;
        if (File.separatorChar != '\\') return uri;
        if ( uri.startsWith("file:") || uri.startsWith("FILE:")){
            char [] u = uri.toCharArray();
            int l = u.length; 
            StringBuilder sb = new StringBuilder(uri.length()+1);
            for (int i=0; i<l; i++){
                // Ensure we use forward slashes
                if (u[i] == File.separatorChar) {
                    sb.append('/');
                    continue;
                }
                if (i == 5 && uri.startsWith(":", 6 )) {
                    // Windows drive letter without leading slashes doesn't comply
                    // with URI spec, fix it here
                    sb.append("/");
                }
                sb.append(u[i]);
            }
            return sb.toString();
        }
        return uri;
    }
    
    public static URI uriToURI(Uri uri){
        return URI.create(uri.toString());
    }
    
    public static Uri urlToUri(URL url) throws URISyntaxException{
        return Uri.parseAndCreate(fixWindowsURI(url.toString()));
    }
    
    public static File uriToFile(Uri uri){
        return new File(uriToURI(uri));
    }
   
    public static Uri fileToUri(File file) throws URISyntaxException{
        String path = file.getAbsolutePath();
        if (File.separatorChar == '\\') {
            path = path.replace(File.separatorChar, '/');
        }
        path = fixWindowsURI("file:" + path);
        return Uri.escapeAndCreate(path); //$NON-NLS-1$
    }
    
    public static Uri filePathToUri(String path) throws URISyntaxException{
        String forwardSlash = "/";
        if (path == null || path.length() == 0) {
            // codebase is "file:"
            path = "*";
        }
        // Ensure compatibility with URLClassLoader, when directory
        // character is dropped by File.
        boolean directory = false;
        if (path.endsWith(forwardSlash)) directory = true;
        path = new File(path).getAbsolutePath();
        if (directory) {
            if (!(path.endsWith(File.separator))){
                path = path + File.separator;
            }
        }
        if (File.separatorChar == '\\') {
            path = path.replace(File.separatorChar, '/');
        }
        path = fixWindowsURI("file:" + path);
        return Uri.escapeAndCreate(path); //$NON-NLS-1$
    }
    
    /* Begin Object Implementation */

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
    private final boolean fileSchemeCaseInsensitiveOS;
  
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
            int hash, 
            boolean fileSchemeCaseInsensitiveOS)
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
        this.fileSchemeCaseInsensitiveOS = fileSchemeCaseInsensitiveOS;
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
        p.hash, 
        p.fileSchemeCaseInsensitiveOS);
    }
    
    /**
     * Creates a new URI instance according to the given string {@code uri}.
     *
     * The URI must strictly conform to RFC3986, it doesn't support extended
     * characters sets like java.net.URI, instead all non ASCII characters
     * must be escaped.
     * 
     * Any encoded unreserved characters are decoded.
     * 
     * @param uri
     *            the textual URI representation to be parsed into a URI object.
     * @throws URISyntaxException
     *             if the given string {@code uri} doesn't fit to the
     *             specification RF3986 or could not be parsed correctly.
     */
    public Uri(String uri) throws URISyntaxException {
        this(constructor1(uri));
    }
    
    private static UriParser constructor1(String uri) throws URISyntaxException {
        uri = URIEncoderDecoder.decodeUnreserved(uri);
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
            uri.append(quoteComponent(ssp, allLegalUnescaped));
        }
        if (frag != null) {
            uri.append('#');
            // QUOTE ILLEGAL CHARACTERS
            uri.append(quoteComponent(frag, Uri.queryFragLegal));
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
            uri.append(quoteComponent(userinfo, Uri.userinfoLegal));
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
            uri.append(quoteComponent(path, "/@" + Uri.pathLegal)); //$NON-NLS-1$
        }

        if (query != null) {
            uri.append('?');
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(query, Uri.queryFragLegal));
        }

        if (fragment != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('#');
            uri.append(quoteComponent(fragment, Uri.queryFragLegal));
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
            uri.append(quoteComponent(authority, "@[]" + Uri.authorityLegal)); //$NON-NLS-1$
        }

        if (path != null) {
            // QUOTE ILLEGAL CHARS
            uri.append(quoteComponent(path, "/@" + Uri.pathLegal)); //$NON-NLS-1$
        }
        if (query != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('?');
            uri.append(quoteComponent(query, Uri.queryFragLegal));
        }
        if (fragment != null) {
            // QUOTE ILLEGAL CHARS
            uri.append('#');
            uri.append(quoteComponent(fragment, Uri.queryFragLegal));
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
    @Override
    public int compareTo(Uri uri) {
        int ret;

        // compare schemes
        if (scheme == null && uri.scheme != null) {
            return -1;
        } else if (scheme != null && uri.scheme == null) {
            return 1;
        } else if (scheme != null && uri.scheme != null) {
            ret = scheme.compareToIgnoreCase(uri.scheme);
            if (ret != 0) return ret;
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
            
            if (fileSchemeCaseInsensitiveOS){
                ret = toAsciiUpperCase(path).compareTo(toAsciiUpperCase(uri.path));
//                ret = path.toUpperCase(Locale.ENGLISH).compareTo(uri.path.toUpperCase(Locale.ENGLISH));
            } else {
                ret = path.compareTo(uri.path);
            }
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
     * escape character % found is encoded as %25. Illegal characters are 
     * escaped if possible.
     * 
     * The Uri is normalised according to RFC3986.
     * 
     * @param unescapedString URI in un-escaped string form
     * @return an RFC3986 compliant Uri.
     * @throws java.net.URISyntaxException if string cannot be escaped.
     */
    public static Uri escapeAndCreate(String unescapedString) throws URISyntaxException{
        return new Uri(quoteComponent(unescapedString, allLegalUnescaped));
    }
    
    /**
     * The parameter string may already contain escaped sequences, any illegal
     * characters are escaped and any that shouldn't be escaped are un-escaped.
     * 
     * The escape character % is not re-encoded.
     * @param nonCompliantEscapedString URI in string from.
     * @return an RFC3986 compliant Uri.
     * @throws java.net.URISyntaxException if string cannot be escaped.
     */
    public static Uri parseAndCreate(String nonCompliantEscapedString) throws URISyntaxException{
        return new Uri(quoteComponent(nonCompliantEscapedString, allLegal));
    }
    

    /*
     * Takes a string that may contain hex sequences like %F1 or %2b and
     * converts the hex values following the '%' to uppercase
     */
    private String convertHexToUpperCase(String s) {
        StringBuilder result = new StringBuilder(""); //$NON-NLS-1$
        if (s.indexOf('%') == -1) {
            return s;
        }

        int index, previndex = 0;
        while ((index = s.indexOf('%', previndex)) != -1) {
            result.append(s.substring(previndex, index + 1));
            // Convert to upper case ascii
            result.append(toAsciiUpperCase(s.substring(index + 1, index + 3).toCharArray()));
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
        //Hex will always be upper case.
        if (first != null) return first.equals(second); 
        return second == null;
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
        if (hash != o.hashCode()) return false;
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
            if ( !(path != null && (path.equals(uri.path) 
                    || fileSchemeCaseInsensitiveOS
                    // Upper case comparison required for Windows & VMS.
                    && asciiStringsUpperCaseEqual(path, uri.path)
                    ))) 
            {
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
     * <p>
     * Indicates whether the specified Uri is implied by this {@link
     * Uri}. Returns {@code true} if all of the following conditions are
     * {@code true}, otherwise {@code false}:
     * </p>
     * <ul>
     * <li>this scheme is not {@code null}
     * <li>this scheme is equal to {@code implied}'s scheme.
     * <li>if this host is not {@code null}, the
     * following conditions are checked
     * <ul>
     * <li>{@code cs}'s host is not {@code null}
     * <li>the wildcard or partial wildcard of this {@code Uri}'s
     * host matches {@code implied}'s host.
     * </ul>
     * <li>if this {@code Uri}'s port != -1 the port of {@code
     * implied}'s location is equal to this {@code Uri}'s port
     * <li>this {@code Uri}'s path matches {@code implied}'s path
     * whereas special wildcard matching applies as described below.
     * </ul>
     * Matching rules for path:
     * <ul>
     * <li>if this {@code Uri}'s path ends with {@code "/-"},
     * then {@code implied}'s path must start with {@code Uri}'s path
     * (exclusive the trailing '-')
     * <li>if this {@code Uri}'s path ends with {@code ";-"},
     * then {@code implied}'s path must start with {@code Uri}'s path
     * (exclusive the trailing '-') this is a custom addition for httmd.
     * <li>if this {@code Uri}'s path ends with {@code "/*"},
     * then {@code implied}'s path must start with {@code Uri}'s path
     * (exclusive the trailing '*') and must not have any further '/'
     * <li>if this {@code Uri}'s path ends with {@code "/"},
     * then {@code implied}'s path must start with {@code Uri}'s path
     * <li>if this {@code Uri}'s path does not end with {@code
     * "/"}, then {@code implied}'s path must start with {@code Uri}'s
     * path with the '/' appended to it.
     * </ul>
     * Examples for Uri that imply the Uri
     * "http://harmony.apache.org/milestones/M9/apache-harmony.jar":
     *
     * <pre>
     * http:
     * http://&#42;/milestones/M9/*
     * http://*.apache.org/milestones/M9/*
     * http://harmony.apache.org/milestones/-
     * http://harmony.apache.org/milestones/M9/apache-harmony.jar
     * </pre>
     *
     * @param implied
     *            the Uri to check.
     * @return {@code true} if the argument is implied by this
     *         {@code Uri}, otherwise {@code false}.
     */
    public boolean implies(Uri implied) { // package private for junit
        //.This section of code was copied from Apache Harmony's CodeSource
        // SVN Revision 929252
        //
        // Here, javadoc:N refers to the appropriate item in the API spec for 
        // the CodeSource.implies()
        // The info was taken from the 1.5 final API spec

        // javadoc:1
//        if (cs == null) {
//            return false;
//        }

        
        // javadoc:2
        // with a comment: the javadoc says only about certificates and does 
        // not explicitly mention CodeSigners' certs.
        // It seems more convenient to use getCerts() to get the real 
        // certificates - with a certificates got form the signers
//        Certificate[] thizCerts = getCertificatesNoClone();
//        if (thizCerts != null) {
//            Certificate[] thatCerts = cs.getCertificatesNoClone();
//            if (thatCerts == null
//                    || !PolicyUtils.matchSubset(thizCerts, thatCerts)) {
//                return false;
//            }
//        }

        // javadoc:3
        
            
            //javadoc:3.1
//            URL otherURL = cs.getLocation();
//            if ( otherURL == null) {
//                return false;
//            }
//            URI otherURI;
//            try {
//                otherURI = otherURL.toURI();
//            } catch (URISyntaxException ex) {
//                return false;
//            }
            //javadoc:3.2
            if (hash == implied.hash){
                if (this.equals(implied)) {
                    return true;
                }
            }
            //javadoc:3.3
            if ( scheme != null){
                if (!scheme.equalsIgnoreCase(implied.scheme)) {
                    return false;
                }
            }
            //javadoc:3.4
            if (host != null) {
                if (implied.host == null) {
                    return false;
                }

                // 1. According to the spec, an empty string will be considered 
                // as "localhost" in the SocketPermission
                // 2. 'file://' URLs will have an empty getHost()
                // so, let's make a special processing of localhost-s, I do 
                // believe this'll improve performance of file:// code sources 

                //
                // Don't have to evaluate both the boolean-s each time.
                // It's better to evaluate them directly under if() statement.
                // 
                // boolean thisIsLocalHost = thisHost.length() == 0 || "localhost".equals(thisHost);
                // boolean thatIsLocalHost = thatHost.length() == 0 || "localhost".equals(thatHost);
                // 
                // if( !(thisIsLocalHost && thatIsLocalHost) &&
                // !thisHost.equals(thatHost)) {

                if (!((host.length() == 0 || "localhost".equals(host)) && (implied.host //$NON-NLS-1$
                        .length() == 0 || "localhost".equals(implied.host))) //$NON-NLS-1$
                        && !host.equals(implied.host)) {
                    
                    // Do wildcard matching here to replace SocketPermission functionality.
                    // This section was copied from Apache Harmony SocketPermission
                    boolean hostNameMatches = false;
                    boolean isPartialWild = (host.charAt(0) == '*');
                    if (isPartialWild) {
                        boolean isWild = (host.length() == 1);
                        if (isWild) {
                            hostNameMatches = true;
                        } else {
                            // Check if thisHost matches the end of thatHost after the wildcard
                            int length = host.length() - 1;
                            hostNameMatches = implied.host.regionMatches(implied.host.length() - length,
                                    host, 1, length);
                        }
                    }
                    if (!hostNameMatches) return false; // else continue.
                    
                    /* Don't want to try resolving URI with DNS, it either has a
                     * matching host or it doesn't.
                     * 
                     * The following section is for resolving hosts, it is
                     * not relevant here, but has been preserved for information
                     * purposes only.
                     * 
                     * Not only is it expensive to perform DNS resolution, hence
                     * the creation of Uri, but a CodeSource.implies
                     * may also require another SocketPermission which may 
                     * cause the policy to get stuck in an endless loop, since it
                     * doesn't perform the implies in priviledged mode, it might
                     * also allow an attacker to substitute one codebase for
                     * another using a dns cache poisioning attack.  In any case
                     * the DNS cannot be assumed trustworthy enough to supply
                     * the policy with information at this level. The implications
                     * are greater than the threat posed by SocketPermission
                     * which simply allows a network connection, as this may
                     * apply to any Permission, even AllPermission.
                     * 
                     * Typically the URI of the codebase will be a match for
                     * the codebase annotation string that is stored as a URL
                     * in CodeSource, then converted to a URI for comparison.
                     */

                    // Obvious, but very slow way....
                    // 
                    // SocketPermission thisPerm = new SocketPermission(
                    //          this.location.getHost(), "resolve");
                    // SocketPermission thatPerm = new SocketPermission(
                    //          cs.location.getHost(), "resolve");
                    // if (!thisPerm.implies(thatPerm)) { 
                    //      return false;
                    // }
                    //
                    // let's cache it: 

//                    if (this.sp == null) {
//                        this.sp = new SocketPermission(thisHost, "resolve"); //$NON-NLS-1$
//                    }
//
//                    if (cs.sp == null) {
//                        cs.sp = new SocketPermission(thatHost, "resolve"); //$NON-NLS-1$
//                    } 
//
//                    if (!this.sp.implies(cs.sp)) {
//                        return false;
//                    }
                    
                } // if( ! this.location.getHost().equals(cs.location.getHost())
            } // if (this.location.getHost() != null)

            //javadoc:3.5
            if (port != -1) {
                if (port != implied.port) {
                    return false;
                }
            }

            //javadoc:3.6
            // compatbility with URL.getFile
            String thisFile;
            String thatFile;
            if (fileSchemeCaseInsensitiveOS){
                thisFile = path == null ? null: toAsciiUpperCase(path);
                thatFile = implied.path == null ? null: toAsciiUpperCase(implied.path);
            } else {
                thisFile = path;
                thatFile = implied.path;
            }
            if (thatFile == null || thisFile == null) return false;
            if (thisFile.endsWith("/-")) { //javadoc:3.6."/-" //$NON-NLS-1$
                if (!thatFile.startsWith(thisFile.substring(0, thisFile
                        .length() - 2))) {
                    return false;
                }
            } else if (thisFile.endsWith(";-")) { //httpmd
                if (!thatFile.startsWith(thisFile.substring(0, thisFile
                        .length() - 2))) {
                    return false;
                }
            } else if (thisFile.endsWith("/*")) { //javadoc:3.6."/*" //$NON-NLS-1$
                if (!thatFile.startsWith(thisFile.substring(0, thisFile
                        .length() - 2))) {
                    return false;
                }
                // no further separators(s) allowed
                if (thatFile.indexOf("/", thisFile.length() - 1) != -1) { //$NON-NLS-1$
                    return false;
                }
            } else {
                // javadoc:3.6."/"
                if (!thisFile.equals(thatFile)) {
                    if (!thisFile.endsWith("/")) { //$NON-NLS-1$
                        if (!thatFile.equals(thisFile + "/")) { //$NON-NLS-1$
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            
            // Fragment and path are ignored.
            //javadoc:3.7
            // A URL Anchor is a URI Fragment.
//            if (thiss.getFragment() != null) {
//                if (!thiss.getFragment().equals(implied.getFragment())) {
//                    return false;
//                }
//            }
            // ok, every check was made, and they all were successful. 
            // it's ok to return true.
        

        // javadoc: a note about CodeSource with null location and null Certs 
        // is applicable here 
        return true;
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
                        hash, 
                        fileSchemeCaseInsensitiveOS);
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
        int index2;
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

        String qry = relative.query;
        // the result URI is the remainder of the relative URI's path
        String pth = relativePath.substring(thisPath.length());
        return new Uri( null,
                        null,
                        setSchemeSpecificPart(null, pth, qry),
                        null,
                        null,
                        null,
                        -1,
                        pth,
                        qry,
                        relative.fragment,
                        false,
                        false,
                        false,
                        -1, 
                fileSchemeCaseInsensitiveOS);
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
                        hash,
                    fileSchemeCaseInsensitiveOS);
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
                        relative.hash, 
                    fileSchemeCaseInsensitiveOS);
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
                        hash, 
                    fileSchemeCaseInsensitiveOS);
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
            ssp.append("//"); //$NON-NLS-1$
            ssp.append(authority);
        }
        if (path != null) {
            ssp.append(path);
        }
        if (query != null) {
            ssp.append("?"); //$NON-NLS-1$
            ssp.append(query);
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
     * and converts escaped octets to uppercase.
     * 
     * Should convert octets to uppercase and follow platform specific 
     * normalization rules for file: uri.
     */
    private String getHashString() {
        StringBuilder result = new StringBuilder();
        if (scheme != null) {
            result.append(toAsciiLowerCase(scheme));
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
                        result.append(userinfo); //$NON-NLS-1$
                        result.append("@");
                    }
                    result.append(toAsciiLowerCase(host));
                    if (port != -1) {
                        result.append(":"); //$NON-NLS-1$
                        result.append(port);
                    }
                }
            }

            if (path != null) {
                if (fileSchemeCaseInsensitiveOS){
                    result.append(toAsciiUpperCase(path));
                } else {
                    result.append(path);
                }
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

        return convertHexToUpperCase(result.toString());
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
        if (opaque) return new URL(toString()); // Let the Handler parse it.
        String hst = host;
        StringBuilder sb = new StringBuilder();
        //userinfo will be rare, utilise sb, then clear it.
        if (userinfo != null){
            sb.append(userinfo).append('@').append(hst);
            hst = sb.toString();
            sb.delete(0, sb.length()-1);
        }
        // now lets create the file section of the URL.
        sb.append(path);
        if (query != null) sb.append('?').append(query);
        if (fragment != null) sb.append('#').append(fragment);
        String file = sb.toString(); //for code readability
        // deprecated to provide a warning against misuse, not for removal.
        @SuppressWarnings("deprecation") 
        URL url = new URL(scheme, hst, port, file, null);
        return url;
    }
}
