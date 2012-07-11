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

package org.apache.river.api.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 *
 * @author Peter Firmstone.
 */
class UriString {
    
    private final static Map<Character,String> escaped = new HashMap<Character,String>();
    
    
        // Allowed
    private static char [] lowalpha = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static char [] upalpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static char [] reserved = {';','/','?',':','@','&','=','+','$',','};
    private static char [] mark = "-_.!~*'()".toCharArray();
    private static char escape = '%';
    private static char fragment = '#';
    // Excluded
    private static Character [] control = {
        '\u0000','\u0001','\u0002','\u0003','\u0004','\u0005','\u0006','\u0007',
        '\u0008','\u0009','\n',    '\u000B','\u000C','\r',    '\u000E','\u000F',
        '\u0010','\u0011','\u0012','\u0013','\u0014','\u0015','\u0016','\u0017',
        '\u0018','\u0019','\u001A','\u001B','\u001C','\u001D','\u001E','\u001F',
        '\u007F'
    };
    private static String [] contEsc = {
        "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07", 
        "%08", "%09", "%0A", "%0B", "%0C", "%0D", "%0E", "%0F", 
        "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17", 
        "%18", "%19", "%1A", "%1B", "%1C", "%1D", "%1E", "%1F",
        "%7F"
    };
    private static Character space = '\u0020';
    private static String spaceEsc = "%20";
    // Excluded because they are often used as delimiters around URI
    private static Character [] delims = {'<','>','"'};
    private static String [] delimEsc = {"%3C", "%3E", "%22"};
    // Excluded because gateways and other transport agents are known
    // to sometimes modify such characters, or they are used as delimiters.
    private static Character [] unwise = {'{','}','|','\\','^','[',']','`'};
    private static String [] unwiseEsc = {"%7B", "%7D", "%7C", "%5C", 
                                   "%53", "%5B", "%5D", "%60"};

    
    static {
        escapes(control, contEsc);
        escaped.put(space, spaceEsc);
        escapes(delims, delimEsc);
        escapes(unwise, unwiseEsc);
    }
    
    static void escapes(Character [] unicode, String[] escape){
        int l = unicode.length;
        if (l != escape.length) throw new IllegalArgumentException("unequal arrays");
        for (int i = 0; i < l; i++){
            escaped.put(unicode[i], escape[i]);
        }
    }
    
    static String escapeIllegalCharacters(String url){
        char [] u = url.toCharArray();
        int l = u.length;
//        for (int i = 0; i < l; i++){
//            // Don't escape if already escaped.
//            if (u[i] == escape) return url;
//        }
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<l; i++){
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

