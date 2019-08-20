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
import java.net.URISyntaxException;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.river.impl.Messages;

/**
 * UriParser for parsing RFC3986 compliant URI strings.
 * @since 3.0.0
 */
final class UriParser {
    
    private static final boolean isFileCaseInsensitiveOS = File.separatorChar == '\\';
    
    String string;
    String scheme;
    String schemespecificpart;
    String authority;
    String userinfo;
    String host;
    int port = -1;
    String path;
    String query;
    String fragment;
    boolean opaque;
    boolean absolute;
    boolean serverAuthority = false;
    int hash = -1;
    boolean fileSchemeCaseInsensitiveOS;

    void parseURI(String uri, boolean forceServer) throws URISyntaxException {
        char fSlash = '/';
        boolean fileURI;
        StringBuilder temp = new StringBuilder(uri);
        // assign uri string to the input value per spec
        string = uri;
        int index;
        int index1;
        int index2;
        int index3;
        // parse into Fragment, Scheme, and SchemeSpecificPart
        // then parse SchemeSpecificPart if necessary
        // Fragment
        index = temp.indexOf("#");
        if (index != -1) {
            // remove the fragment from the end
            fragment = temp.substring(index + 1);
            validateFragment(uri, fragment, index + 1);
            //                temp = temp.substring(0, index);
            temp.delete(index, temp.length());
        }
        // Scheme and SchemeSpecificPart
        index = index1 = temp.indexOf(":");
        index2 = temp.indexOf("/");
        index3 = temp.indexOf("?");
        // if a '/' or '?' occurs before the first ':' the uri has no
        // specified scheme, and is therefore not absolute
        if (index != -1 && (index2 >= index || index2 == -1) && (index3 >= index || index3 == -1)) {
            // the characters up to the first ':' comprise the scheme
            absolute = true;
            scheme = Uri.toAsciiLowerCase(temp.substring(0, index));
            if (scheme.length() == 0) {
                throw new URISyntaxException(uri, Messages.getString("luni.83"), index);
            }
            validateScheme(uri, scheme, 0);
            fileURI = (scheme.equalsIgnoreCase("file"));
            fileSchemeCaseInsensitiveOS = (fileURI && isFileCaseInsensitiveOS);
            schemespecificpart = temp.substring(index + 1);
            if (schemespecificpart.length() == 0) {
                throw new URISyntaxException(uri, Messages.getString("luni.84"), index + 1);
            }
        } else {
            absolute = false;
            schemespecificpart = temp.toString();
        }
        if (scheme == null || schemespecificpart.length() > 0 && schemespecificpart.charAt(0) == fSlash) {
            opaque = false;
            // the URI is hierarchical
            // Query
            temp.delete(0, temp.length());
            temp.append(schemespecificpart);
            index = temp.indexOf("?");
            if (index != -1) {
                query = temp.substring(index + 1);
                temp.delete(index, temp.length());
                validateQuery(uri, query, index2 + 1 + index);
                /**
                 * The following line of code was incorrect and caused 6 test failures.
                 * According to RFC 3986, Pages 40 and 41:
                 * 
                 * For example,
                 * because the "http" scheme makes use of an authority component, has a
                 * default port of "80", and defines an empty path to be equivalent to
                 * "/", the following four URIs are equivalent:
                 * 
                 *    http://example.com
                 *    http://example.com/
                 *    http://example.com:/
                 *    http://example.com:80/
                 * 
                 * Normalization should not remove delimiters when their associated
                 * component is empty unless licensed to do so by the scheme 
                 * specification.
                 * 
                 * For example, the URI "http://example.com/?" cannot be
                 * assumed to be equivalent to any of the examples above.
                 */
//                if ("".equals(query)) query = null; //This line causes ? to be removed.
            }
            // Authority and Path
            if (temp.length() >= 2 && temp.charAt(0) == fSlash && temp.charAt(1) == fSlash) {
                //$NON-NLS-1$
                index = temp.indexOf("/", 2);
                if (index != -1) {
                    authority = temp.substring(2, index);
                    path = temp.substring(index);// begins with "/"
                    //  path        = path-abempty    ; begins with "/" or is empty
                    String [] segment = path.split("/");
                    int l = segment.length;
                    int index4 = 1;
                    for (int i = 0; i < l ; i++){
                        validateSegment(uri, segment[i], index1 + index + index4, Uri.segmentLegal);
                        index4 += (segment[i].length() + 1);
                    }
                } else {
                    authority = temp.substring(2);
                    if (authority.length() == 0 && query == null && fragment == null) {
                        throw new URISyntaxException(uri, Messages.getString("luni.9F"), uri.length()); //$NON-NLS-1$
                    }
                    path = ""; //$NON-NLS-1$
                    // nothing left, so path is empty (not null, path should
                    // never be null)
                }
                if (authority.length() == 0) {
                    authority = null;
                } 
                // Authority validated by userinfo, host and port, later.
            } else {
                // no authority specified
                String legal;
                int index4 = 0;
                if (scheme == null){
                    // path-noscheme   ; begins with a non-colon segment
                    // path-noscheme = segment-nz-nc *( "/" segment )
                    legal = Uri.segmentNzNcLegal;
                } else {
                    legal = Uri.segmentLegal;
                    index4 = index;
                    // increment index4 if starts with slash.
                    //if (temp.charAt(0) == fSlash) index4 ++;
                }
                path = temp.toString();
                String [] segment = path.split("/");
                int l = segment.length;
                for (int i = 0; i < l ; i++){
                    // in case scheme == null only first segment is segment-nz-nc
                    if (i == 1) legal = Uri.segmentLegal;
                    validateSegment(uri, segment[i], index4, legal);
                    index4 += (segment[i].length() + 1);
                }
            }
        } else {
            // if not hierarchical, URI is opaque
            opaque = true;
            validateSsp(uri, schemespecificpart, index2 + 2 + index);
        }
        parseAuthority(forceServer);
        // Normalise path and replace string.
        if (!opaque){
            StringBuilder result = new StringBuilder();
            String normalizedPath = normalize(path);
            if (scheme != null) {
                result.append(scheme);
                result.append(':');
            }
            
            schemespecificpart = setSchemeSpecificPart(authority, normalizedPath , query);
            
            if (authority != null) {
                result.append("//"); //$NON-NLS-1$
                result.append(authority);
            }

            if (path != null) {
                result.append(normalizedPath);
            }

            if (query != null) {
                result.append('?');
                result.append(query);
            }

            if (fragment != null) {
                result.append('#');
                result.append(fragment);
            }

            this.string = result.toString();
        }      
    }

    private void validateScheme(String uri, String scheme, int index) throws URISyntaxException {
        // first char needs to be an alpha char
        char ch = scheme.charAt(0);
        if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
            throw new URISyntaxException(uri, Messages.getString("luni.85"), 0); //$NON-NLS-1$
        }
        try {
            URIEncoderDecoder.validateSimple(scheme, "+-."); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.85"), index + e.getIndex());
        }
    }

    private void validateSsp(String uri, String ssp, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(ssp, Uri.allLegalUnescaped);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.86", e.getReason()), index + e.getIndex());
        }
    }

    private void validateSegment(String uri, String segment, int index, String legal) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(segment, legal); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.88", e.getReason()), index + e.getIndex());
        }
    }
    private void validateQuery(String uri, String query, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(query, Uri.queryFragLegal);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.89", e.getReason()), index + e.getIndex());
        }
    }

    private void validateFragment(String uri, String fragment, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(fragment, Uri.queryFragLegal);
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.8A", e.getReason()), index + e.getIndex());
        }
    }

    /**
     * determine the host, port and userinfo if the authority parses
     * successfully to a server based authority
     *
     * behaviour in error cases: if forceServer is true, throw
     * URISyntaxException with the proper diagnostic messages. if
     * forceServer is false assume this is a registry based uri, and just
     * return leaving the host, port and userinfo fields undefined.
     *
     * and there are some error cases where URISyntaxException is thrown
     * regardless of the forceServer parameter e.g. malformed ipv6 address
     */
    void parseAuthority(boolean forceServer) throws URISyntaxException {
        if (authority == null) {
            return;
        }
        String temp;
        String tempUserinfo = null;
        String tempHost;
        int index;
        int hostindex = 0;
        int tempPort = -1;
        temp = authority;
        index = temp.indexOf('@');
        if (index != -1) {
            // remove user info
            tempUserinfo = temp.substring(0, index);
            validateUserinfo(authority, tempUserinfo, 0);
            temp = temp.substring(index + 1); // host[:port] is left
            hostindex = index + 1;
        }
        index = temp.lastIndexOf(':');
        int endindex = temp.indexOf(']');
        if (index != -1 && endindex < index) {
            // determine port and host
            tempHost = temp.substring(0, index);
            if (index < (temp.length() - 1)) {
                // port part is not empty
                try {
                    tempPort = Integer.parseInt(temp.substring(index + 1));
                    if (tempPort < 0) {
                        if (forceServer) {
                            throw new URISyntaxException(authority, Messages.getString("luni.8B"), hostindex + index + 1); //$NON-NLS-1$
                        }
                        return;
                    }
                } catch (NumberFormatException e) {
                    if (forceServer) {
                        throw new URISyntaxException(authority, Messages.getString("luni.8B"), hostindex + index + 1); //$NON-NLS-1$
                    }
                    return;
                }
            }
        } else {
            tempHost = temp;
        }
        if (tempHost.equals("")) {
            //$NON-NLS-1$
            if (forceServer) {
                throw new URISyntaxException(authority, Messages.getString("luni.A0"), hostindex); //$NON-NLS-1$
            }
            return;
        }    
        if (!isValidHost(forceServer, tempHost)) {
            return;
        }
        // this is a server based uri,
        // fill in the userinfo, host and port fields
        // If IPv6, we need to normalize representation to comply with RFC 5952
        userinfo = tempUserinfo;
        host = rfc5952Normalize(tempHost);
        port = tempPort;
        serverAuthority = true;
    }
    
    private static final Pattern OCTET_BEGIN_ZERO = Pattern.compile(
            // ipv6 octet | ipv4 address
            "^([0]{1,3})([0-9a-f]{1,3})$"
    );
    
    private static final Pattern IPv4 = Pattern.compile(
            "^([1-2]{0,1}[0-5]{0,1}[0-5]{1}\\.[1-2]{0,1}[0-5]{0,1}[0-5]{1}\\.[1-2]{0,1}[0-5]{0,1}[0-5]{1}\\.[1-2]{0,1}[0-5]{0,1}[0-5]{1})$"
    );
    
    /**
     * Normalize IPv6 to preferred representation as per RFC 5952.
     * @param host
     * @return 
     */
    private String rfc5952Normalize(String host) throws URISyntaxException{
        if (host.charAt(0) == '[' && host.charAt(1) != 'v'){ //IPv6
            // RFC 4007 <address>%<zone_id>
            // RFC 6874 [<address>%25<zone_id>]
            int delimiter = host.indexOf("%25");
            String address, zone_id;
            if (delimiter > 0) {
                address = Uri.toAsciiLowerCase(host.substring(1, delimiter));
                zone_id = host.substring(delimiter+3, host.length()-1);
            } else {
                address = Uri.toAsciiLowerCase(host.substring(1,host.length()-1));
                zone_id = null;
            }
            String [] hostAddressOctets;
            // Expand all shortened forms.
            int shortened = address.indexOf("::");
            if (shortened >= 0){ // Shortened form.
                String beginning = address.substring(0, shortened);
                String end = address.substring(shortened + 2, address.length());
                // Split into octets.
                String [] beg = beginning.split(":");
                String [] en = end.split(":");
                // Is it an IPv4 mapped address ?
                Matcher isIPv4mapped = IPv4.matcher(en[en.length-1]);
                if (isIPv4mapped.matches()){
                    hostAddressOctets = new String [7];
                } else {
                    hostAddressOctets = new String [8];
                }
                for (int i = 0, len = hostAddressOctets.length; i < len; i++){
                    hostAddressOctets[i] = "0";  // Populate with zero's.
                }
                // populate from beginning
                for (int i = 0, len = beg.length, lenO = hostAddressOctets.length; i < len && i < lenO; i++){
                    hostAddressOctets[i] = beg[i];
                }
                // populate from end backwards.
                for (int i = hostAddressOctets.length - 1, j = en.length - 1; i >= 0 && j >= 0; i--, j--){
                    hostAddressOctets[i] = en[j];
                }
            } else {
                hostAddressOctets = address.split(":");
            }
            
            // Strip leading zero's
            for (int i = 0, l=hostAddressOctets.length; i < l; i++){
                Matcher match = OCTET_BEGIN_ZERO.matcher(hostAddressOctets[i]);
                if (match.matches()) hostAddressOctets[i] = match.group(2);
            }
            
            // Identify maximum length of zero's and abbreviate.
            int zeroCount = 0;
            int maxZeroCount = 0;
            int maxBeginIndex = -1;
            int maxLastIndex = -1;
            int beginIndex = -1;
            int lastIndex = -1;
            for (int i = 0, l = hostAddressOctets.length; i < l; i++){
                if ("0".equals(hostAddressOctets[i])) {
                    if (zeroCount == 0) beginIndex = i;
                    lastIndex = i;
                    zeroCount ++;
                } else {  // Reset
                    if (zeroCount > maxZeroCount){
                        maxZeroCount = zeroCount;
                        maxBeginIndex = beginIndex;
                        maxLastIndex = lastIndex; // Exclusive of current index.
                    }
                    zeroCount = 0;
                }
            }
            if (zeroCount > maxZeroCount){ // In case the last octet is :0.
                maxZeroCount = zeroCount;
                maxBeginIndex = beginIndex;
                maxLastIndex = lastIndex; // Exclusive of current index.
            }
            StringBuilder sb = new StringBuilder(64);
            sb.append('[');
            if (maxZeroCount > 1){ // Abbreviate.
                for (int i = 0, l = hostAddressOctets.length; i < l; i ++){
                    if (i == 0 && maxBeginIndex == 0) sb.append(":");  // Special case with leading zero.
                    if (i == maxBeginIndex) sb.append(":");
                    if (maxBeginIndex <= i && i <= maxLastIndex) continue;
                    sb.append(hostAddressOctets[i]);
                    if (i < l - 1) sb.append(":");
                }
            } else { // No abbreviation
                for (int i = 0, l = hostAddressOctets.length; i < l; i++){
                    sb.append(hostAddressOctets[i]);
                    if (i < l - 1) sb.append(":");
                }
            }
            if (zone_id != null) sb.append("%25").append(zone_id);
            sb.append(']');
            return sb.toString();
        }
        return host;
    }

    private void validateUserinfo(String uri, String userinfo, int index) throws URISyntaxException {
        try {
            URIEncoderDecoder.validate(userinfo, Uri.userinfoLegal); //$NON-NLS-1$
        } catch (URISyntaxException e) {
            throw new URISyntaxException(uri, Messages.getString("luni.8C", e.getReason()), index + e.getIndex());
        }
    }

    /**
     * distinguish between IPv4, IPv6, domain name and validate it based on
     * its type
     */
    private boolean isValidHost(boolean forceServer, String host) throws URISyntaxException {
        if (host.charAt(0) == '[') {
            // ipv6 address or IPvFuture
            if (host.charAt(host.length() - 1) != ']') {
                throw new URISyntaxException(host, Messages.getString("luni.8D"), 0); //$NON-NLS-1$
            }
            // check for valid IPvFuture syntax.
            if (isValidIPvFutureAddress(host)) return true;
            if (!isValidIP6Address(host)) {
                throw new URISyntaxException(host, Messages.getString("luni.8E")); //$NON-NLS-1$
            }
            return true;
        }
        // '[' and ']' can only be the first char and last char
        // of the host name
        if (host.indexOf('[') != -1 || host.indexOf(']') != -1) {
            throw new URISyntaxException(host, Messages.getString("luni.8F"), 0); //$NON-NLS-1$
        }
        int index = host.lastIndexOf('.');
        if (index < 0 || index == host.length() - 1 || !Character.isDigit(host.charAt(index + 1))) {
            // domain name
            if (isValidDomainName(host)) {
                return true;
            }
            if (forceServer) {
                throw new URISyntaxException(host, Messages.getString("luni.8F"), 0); //$NON-NLS-1$
            }
            return false;
        }
        // IPv4 address
        if (isValidIPv4Address(host)) {
            return true;
        }
        if (forceServer) {
            throw new URISyntaxException(host, Messages.getString("luni.90"), 0); //$NON-NLS-1$
        }
        return false;
    }

    private boolean isValidDomainName(String host) throws URISyntaxException {
        URIEncoderDecoder.validate(host, Uri.hostRegNameLegal); //$NON-NLS-1$
        String label = null;
        StringTokenizer st = new StringTokenizer(host, "."); //$NON-NLS-1$
        while (st.hasMoreTokens()) {
            label = st.nextToken();
            if (label.startsWith("-") || label.endsWith("-")) {
                //$NON-NLS-1$ //$NON-NLS-2$
                return false;
            }
        }
        if ( label != null && !label.equals(host)) {
            char ch = label.charAt(0);
            if (ch >= '0' && ch <= '9') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIPv4Address(String host) {
        int index;
        int index2;
        try {
            int num;
            index = host.indexOf('.');
            num = Integer.parseInt(host.substring(0, index));
            if (num < 0 || num > 255) {
                return false;
            }
            index2 = host.indexOf('.', index + 1);
            num = Integer.parseInt(host.substring(index + 1, index2));
            if (num < 0 || num > 255) {
                return false;
            }
            index = host.indexOf('.', index2 + 1);
            num = Integer.parseInt(host.substring(index2 + 1, index));
            if (num < 0 || num > 255) {
                return false;
            }
            num = Integer.parseInt(host.substring(index + 1));
            if (num < 0 || num > 255) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    private boolean isValidIP6Address(String ipAddress) {
        // Section 2.3 Unreserved characters (Allowed in zone).
        char [] lowalpha = new char[0];
        char [] upalpha = lowalpha;
        char [] numeric= lowalpha;
        char [] unres_punct= lowalpha;
        
        int length = ipAddress.length();
        boolean doubleColon = false;
        int numberOfColons = 0;
        int numberOfPeriods = 0;
        boolean zone = false;
        String word = ""; //$NON-NLS-1$
        char c = 0;
        char prevChar;
        int offset = 0; // offset for [] ip addresses
        if (length < 2) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            prevChar = c;
            c = ipAddress.charAt(i);
            switch (c) {
            // case for an open bracket [x:x:x:...x]
                case '[':
                    if (i != 0) {
                        return false; // must be first character
                    }
                    if (ipAddress.charAt(length - 1) != ']') {
                        return false; // must have a close ]
                    }
                    if ((ipAddress.charAt(1) == ':') && (ipAddress.charAt(2) != ':')) {
                        return false;
                    }
                    offset = 1;
                    if (length < 4) {
                        return false;
                    }
                    break;
            // case for a closed bracket at end of IP [x:x:x:...x]
                case ']':
                    if (i != length - 1) {
                        return false; // must be last character
                    }
                    if (ipAddress.charAt(0) != '[') {
                        return false; // must have a open [
                    }
                    break;
            // case for the last 32-bits represented as IPv4
            // x:x:x:x:x:x:d.d.d.d
                case '.':
                    if (zone) break; // Allowed
                    numberOfPeriods++;
                    if (numberOfPeriods > 3) {
                        return false;
                    }
                    if (!isValidIP4Word(word)) {
                        return false;
                    }
                    if (numberOfColons != 6 && !doubleColon) {
                        return false;
                    }
                    // a special case ::1:2:3:4:5:d.d.d.d allows 7 colons
                    // with
                    // an IPv4 ending, otherwise 7 :'s is bad
                    if (numberOfColons == 7 && ipAddress.charAt(0 + offset) != ':' && ipAddress.charAt(1 + offset) != ':') {
                        return false;
                    }
                    word = ""; //$NON-NLS-1$
                    break;
                case ':':
                    if (zone) return false;
                    numberOfColons++;
                    if (numberOfColons > 7) {
                        return false;
                    }
                    if (numberOfPeriods > 0) {
                        return false;
                    }
                    if (prevChar == ':') {
                        if (doubleColon) {
                            return false;
                        }
                        doubleColon = true;
                    }
                    word = ""; //$NON-NLS-1$
                    break;
                case '%':
                    if (zone) return false; // Can only be one % sign to avoid complicated parsing, See RFC 6874
                    String zoneId = ipAddress.substring(i, i+3);
                    if ("%25".equals(zoneId)){
                        zone = true;
                    }
                    // Initialize unreserved allowable characters
                    lowalpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();
                    upalpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
                    numeric = "0123456789".toCharArray();
                    unres_punct = new char[] {'-' , '.' , '_' , '~'};
                    word = ""; //$NON-NLS-1$
                    break;
                default:
                    if (zone){ // The following is allowed in zone.
                        for (int j = 0, len = lowalpha.length; j < len; j++){
                            if (c == lowalpha[j]) break;
                        }
                        for (int j = 0, len = upalpha.length; j < len; j++){
                            if (c == upalpha[j]) break;
                        }
                        for (int j = 0, len = numeric.length; j < len; j++){
                            if (c == numeric[j]) break;
                        }
                        for (int j = 0, len = unres_punct.length; j < len; j++){
                            if (c == unres_punct[j]) break;
                        }
                    }
                    if (word.length() > 3) {
                        return false;
                    }
                    if (!isValidHexChar(c)) {
                        return false;
                    }
                    word += c;
            }
        }
        // Check if we have an IPv4 ending
        if (numberOfPeriods > 0) {
            if (numberOfPeriods != 3 || !isValidIP4Word(word)) {
                return false;
            }
        } else {
            // If we're at then end and we haven't had 7 colons then there
            // is a problem unless we encountered a doubleColon
            if (numberOfColons != 7 && !doubleColon) {
                return false;
            }
            // If we have an empty word at the end, it means we ended in
            // either a : or a .
            // If we did not end in :: then this is invalid
            if (word.equals("") && ipAddress.charAt(length - 1 - offset) != ':' && ipAddress.charAt(length - 2 - offset) != ':') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIP4Word(String word) {
        char c;
        if (word.length() < 1 || word.length() > 3) return false;
        for (int i = 0; i < word.length(); i++) {
            c = word.charAt(i);
            if (!(c >= '0' && c <= '9')) return false;
        }
        return (Integer.parseInt(word) <= 255);
    }

    private boolean isValidHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private boolean isValidIPvFutureAddress(String ipvFuture) throws URISyntaxException {
        // [ at index 0 has been checked.
        if (ipvFuture.charAt(1) != 'v') return false;
        if (!isValidHexChar(ipvFuture.charAt(2))) return false;
        if (ipvFuture.charAt(3) != '.') return false;
        String sub = ipvFuture.substring(4, ipvFuture.length()-1);
        URIEncoderDecoder.validate(sub, Uri.iPvFuture);
        return true;
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
}
