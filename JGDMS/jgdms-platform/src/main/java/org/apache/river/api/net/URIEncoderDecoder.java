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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.impl.Messages;


/**
 * This class is used to encode a string using the format required by {@code
 * application/x-www-form-urlencoded} MIME content type. It contains helper
 * methods used by the Uri class, and performs encoding and decoding in a
 * slightly different way than {@code URLEncoder} and {@code URLDecoder}.
 * @since 3.0.0
 */
class URIEncoderDecoder {

    static final String digits = "0123456789ABCDEF"; //$NON-NLS-1$

    static final String encoding = "UTF8"; //$NON-NLS-1$
    
    // Map of escaped unreserved characters defined in RFC3986
    private static final Map<String,Character> legalEscaped;
    
    static {
        legalEscaped = new HashMap<String,Character>();
        char [] legals = "abcdefghijklmnopqrstuvwyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~".toCharArray();
        StringBuilder buf = new StringBuilder(12);
        int l = legals.length;
        for (int i = 0; i< l; i++){
            char ch = legals[i];
            byte[] bytes;
            try {
                bytes = new String(new char[] { ch }).getBytes(encoding);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(URIEncoderDecoder.class.getName()).log(Level.SEVERE, null, ex);
                continue;
            }
            for (int j = 0; j < bytes.length; j++) {
                buf.append('%');
                buf.append(digits.charAt((bytes[j] & 0xf0) >> 4));
                buf.append(digits.charAt(bytes[j] & 0xf));
            }
            legalEscaped.put(buf.toString(), Character.valueOf(ch));
            buf.delete(0, buf.length());
        }
    }
    
    
    
    /**
     * Validate a string by checking if it contains any characters other than:
     * 1. letters ('a'..'z', 'A'..'Z') 2. numbers ('0'..'9') 3. characters in
     * the legalset parameter 4. others (unicode characters that are not in
     * US-ASCII set, and are not ISO Control or are not ISO Space characters)
     * <p>
     * called from {@code URI.Helper.parseURI()} to validate each component
     *
     * @param s
     *            {@code java.lang.String} the string to be validated
     * @param legal
     *            {@code java.lang.String} the characters allowed in the String
     *            s
     */
    static void validate(String s, String legal) throws URISyntaxException {
        for (int i = 0; i < s.length();) {
            char ch = s.charAt(i);
            if (ch == '%') {
                do {
                    if (i + 2 >= s.length()) {
                        throw new URISyntaxException(s, Messages.getString("luni.7D"), //$NON-NLS-1$
                                i);
                    }
                    int d1 = Character.digit(s.charAt(i + 1), 16);
                    int d2 = Character.digit(s.charAt(i + 2), 16);
                    if (d1 == -1 || d2 == -1) {
                        throw new URISyntaxException(s, Messages.getString("luni.7E", //$NON-NLS-1$
                                s.substring(i, i + 3)), i);
                    }

                    i += 3;
                } while (i < s.length() && s.charAt(i) == '%');

                continue;
            }
            if (    !((ch >= 'a' && ch <= 'z') 
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') 
                    || legal.indexOf(ch) > -1 )
                ) 
            {
                throw new URISyntaxException(s, Messages.getString("luni.7F"), i); //$NON-NLS-1$
            }
            i++;
        }
    }

    static void validateSimple(String s, String legal)
            throws URISyntaxException {
        for (int i = 0; i < s.length();) {
            char ch = s.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || legal.indexOf(ch) > -1)) {
                throw new URISyntaxException(s, Messages.getString("luni.7F"), i); //$NON-NLS-1$
            }
            i++;
        }
    }

    /**
     * All characters except letters ('a'..'z', 'A'..'Z') and numbers ('0'..'9')
     * and legal characters are converted into their hexidecimal value prepended
     * by '%'.
     * <p>
     * For example: '#' -&gt; %23
     * Other characters, which are unicode chars that are not US-ASCII, and are
     * not ISO Control or are not ISO Space chars, are preserved.
     * <p>
     * Called from {@code URI.quoteComponent()} (for multiple argument
     * constructors)
     *
     * @param s
     *            java.lang.String the string to be converted
     * @param legal
     *            java.lang.String the characters allowed to be preserved in the
     *            string s
     * @return java.lang.String the converted string
     */
    static String quoteIllegal(String s, String legal)
            throws UnsupportedEncodingException {
        StringBuilder buf = new StringBuilder(s.length() + 24);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (       
                       (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || legal.indexOf(ch) > -1
                ) 
            {
                buf.append(ch);
            } else {
                byte[] bytes = new String(new char[] { ch }).getBytes(encoding);
                for (int j = 0; j < bytes.length; j++) {
                    buf.append('%');
                    buf.append(digits.charAt((bytes[j] & 0xf0) >> 4));
                    buf.append(digits.charAt(bytes[j] & 0xf));
                }
            }
        }
        return buf.toString();
    }

    /**
     * Other characters, which are Unicode chars that are not US-ASCII, and are
     * not ISO Control or are not ISO Space chars are not preserved. They are
     * converted into their hexidecimal value prepended by '%'.
     * <p>
     * For example: Euro currency symbol -&gt; "%E2%82%AC".
     * <p>
     * Called from URI.toASCIIString()
     *
     * @param s
     *            java.lang.String the string to be converted
     * @return java.lang.String the converted string
     */
    static String encodeOthers(String s) throws UnsupportedEncodingException {
        StringBuilder buf = new StringBuilder(s.length()*9);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch <= 127) {
                buf.append(ch);
            } else {
                byte[] bytes = new String(new char[] { ch }).getBytes(encoding);
                for (int j = 0; j < bytes.length; j++) {
                    buf.append('%');
                    buf.append(digits.charAt((bytes[j] & 0xf0) >> 4));
                    buf.append(digits.charAt(bytes[j] & 0xf));
                }
            }
        }
        return buf.toString();
    }

    /**
     * Decodes the string argument which is assumed to be encoded in the {@code
     * x-www-form-urlencoded} MIME content type using the UTF-8 encoding scheme.
     * <p>
     *'%' and two following hex digit characters are converted to the
     * equivalent byte value. All other characters are passed through
     * unmodified.
     * <p>
     * e.g. "A%20B%20C %24%25" -&gt; "A B C $%"
     * <p>
     * Called from URI.getXYZ() methods
     * 
     * @param s
     *            java.lang.String The encoded string.
     * @return java.lang.String The decoded version.
     */
    static String decode(String s) throws UnsupportedEncodingException {

        StringBuilder result = new StringBuilder(s.length());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int l = s.length();
        for (int i = 0; i < l;) {
            char c = s.charAt(i);
            if (c == '%') {
                out.reset();
                do {
                    if (i + 2 >= l) {
                        throw new IllegalArgumentException(Messages.getString(
                                "luni.80", i)); //$NON-NLS-1$
                    }
                    int d1 = Character.digit(s.charAt(i + 1), 16);
                    int d2 = Character.digit(s.charAt(i + 2), 16);
                    if (d1 == -1 || d2 == -1) {
                        throw new IllegalArgumentException(Messages.getString(
                                "luni.81", s.substring(i, i + 3), //$NON-NLS-1$
                                String.valueOf(i)));
                    }
                    out.write((byte) ((d1 << 4) + d2));
                    i += 3;
                } while (i < l && s.charAt(i) == '%');
                result.append(out.toString(encoding));
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
    
    static String decodeUnreserved(String s) throws URISyntaxException {
        StringBuilder result = new StringBuilder(s.length());
        StringBuilder pct_encoded = new StringBuilder(12);
        int l = s.length();
        for (int i = 0; i < l;) {
            char c = s.charAt(i);
            if (c == '%') {
                    do {
                        if (i + 2 >= l) {
                            throw new URISyntaxException(s, Messages.getString("luni.80", i), i);
                        }
                        char c1 = s.charAt(i+1);
                        char c2 = s.charAt(i+2);
                        if (!isValidHexChar(c1) || !isValidHexChar(c2)) {
                            throw new URISyntaxException(s, Messages.getString(
                                "luni.81", s.substring(i, i + 3), //$NON-NLS-1$
                                String.valueOf(i)), i);
                        }
                        pct_encoded.append('%').append(c1).append(c2);
                        i += 3;
                    } while (i < l && s.charAt(i) == '%');
                    String encoded = pct_encoded.toString().toUpperCase(Locale.ENGLISH);
                    Character ch = legalEscaped.get(encoded);
                    if ( ch != null ){
                        result.append(ch.charValue());
                    } else {
                        // Ensure all pct-encoded strings are uppercase.
                        result.append(encoded);
                    }
                    pct_encoded.delete(0, pct_encoded.length());
                    continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }
    
    private static boolean isValidHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

}
