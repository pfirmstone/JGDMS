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

package org.apache.river.impl.net;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility that escapes illegal characters in URI strings according to RFC2396
 * as well as converting MS Windows file absolute path strings to URI compliant 
 * syntax.
 * 
 * @author Peter Firmstone.
 */
public class UriString {
    
    // prevents instantiation.
    private UriString(){};
    
    private final static Map<Character,String> escaped = new HashMap<Character,String>();
    private final static Collection<Character> alpha;
    
        // Allowed
    private static final char [] lowalpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char [] upalpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char [] numeric = "0123456789".toCharArray();
    private static final char [] reserved = {';','/','?',':','@','&','=','+','$',','};
    private static final char [] mark = "-_.!~*'()".toCharArray();
    private static final char escape = '%';
    private static final char fragment = '#';
    // Excluded
    private static final Character [] control = {
        '\u0000','\u0001','\u0002','\u0003','\u0004','\u0005','\u0006','\u0007',
        '\u0008','\u0009','\n',    '\u000B','\u000C','\r',    '\u000E','\u000F',
        '\u0010','\u0011','\u0012','\u0013','\u0014','\u0015','\u0016','\u0017',
        '\u0018','\u0019','\u001A','\u001B','\u001C','\u001D','\u001E','\u001F',
        '\u007F'
    };
    private static final String [] contEsc = {
        "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07", 
        "%08", "%09", "%0A", "%0B", "%0C", "%0D", "%0E", "%0F", 
        "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17", 
        "%18", "%19", "%1A", "%1B", "%1C", "%1D", "%1E", "%1F",
        "%7F"
    };
    private static final Character space = '\u0020';
    private static final String spaceEsc = "%20";
    // Excluded because they are often used as delimiters around URI
    private static final Character [] delims = {'<','>','"'};
    private static final String [] delimEsc = {"%3C", "%3E", "%22"};
    // Excluded because gateways and other transport agents are known
    // to sometimes modify such characters, or they are used as delimiters.
    private static final Character [] unwise = {'{','}','|','\\','^','[',']','`'};
    private static final String [] unwiseEsc = {"%7B", "%7D", "%7C", "%5C", 
                                   "%53", "%5B", "%5D", "%60"};

    
    static {
        escapes(control, contEsc);
        escaped.put(space, spaceEsc);
        escapes(delims, delimEsc);
        escapes(unwise, unwiseEsc);
        alpha = new ArrayList<Character>(lowalpha.length + upalpha.length);
        addArrayToCollection(alpha, lowalpha);
        addArrayToCollection(alpha, upalpha);
    }
    
    private static void escapes(Character [] unicode, String[] escape){
        int l = unicode.length;
        if (l != escape.length) throw new IllegalArgumentException("unequal arrays");
        for (int i = 0; i < l; i++){
            escaped.put(unicode[i], escape[i]);
        }
    }
    
    private static void addArrayToCollection(Collection<Character> col, char [] chars){
        int l = chars.length;
        for ( int i = 0; i < l; i++){
            col.add(chars[i]);
        }
    }
    
    /**
     * Finds a character in an array and returns the index at which it exists,
     * or returns -1 if it doesn't
     * 
     * @param array
     * @param character
     * @return 
     */
    public static int index(char [] array, char character){
        int l = array.length;
        for (int i = 0; i < l; i++){
            if (array[i] == character) return i;
        }
        return -1;
    }
    
    public static String parse(String url) {
        boolean isFile = url.startsWith("file:") || url.startsWith("FILE:");
        char slash = reserved[1];
        char [] u = url.toCharArray();
        int l = u.length; 
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<l; i++){
            if (isFile){
                // Ensure we use forward slashes
                if (u[i] == File.separatorChar) {
                    u[i] = '/';
                    sb.append(u[i]);
                    continue;
                }
                if (i == 5 && url.startsWith(":", 6 )) {
                    // Windows drive letter without leading slashes doesn't comply
                    // with URI spec, fix it here
                    // Ensure that drive letter is upper case only.
                    int upcase = index(upalpha, u[i]);
                    int lowcase = index(lowalpha, u[i]);
                    if ( upcase > 0){
                        sb.append("///");
                        sb.append(u[i]);
                        continue;
                    } else if ( lowcase > 0){
                        sb.append("///");
                        sb.append(upalpha[lowcase]);
                        continue;
                    }
                }
                if (i == 6 && u[5] == '/' && url.startsWith(":", 7) ){
                    // Ensure drive letter is upper case only.
                    int upcase = index(upalpha, u[i]);
                    int lowcase = index(lowalpha, u[i]);
                    if ( upcase > 0){
                        sb.append("//");
                        sb.append(u[i]);
                        continue;
                    } else if ( lowcase > 0){
                        sb.append("//");
                        sb.append(upalpha[lowcase]);
                        continue;
                    }
                }
                if (i == 8 && u[5] == slash && u[6] == slash 
                        && u[7] == slash && url.startsWith(":", 9)){
                    // Ensure drive letter is upper case only.
                    int upcase = index(upalpha, u[i]);
                    int lowcase = index(lowalpha, u[i]);
                    if ( upcase > 0){
                        sb.append(u[i]);
                        continue;
                    } else if ( lowcase > 0){
                        sb.append(upalpha[lowcase]);
                        continue;
                    }
                }
                    
            }
            Character c = Character.valueOf(u[i]);
            if (escaped.keySet().contains(c)){
                sb.append(escaped.get(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
}

