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

/**
 * 
 * @author peter
 * @since 3.0.0
 */
class Util {

    static String toUpperCase(String s) {
        return toASCIIUpperCase(s);
    }

    static boolean equalsIgnoreCase(String s1, String s2) {
        s1 = toASCIIUpperCase(s1);
        s2 = toASCIIUpperCase(s2);
        return s1.equals(s2);
    }
    static String toASCIIUpperCase(String s) {
        int len = s.length();
        StringBuilder buffer = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
                    char c = s.charAt(i);
            if ('a' <= c && c <= 'z') {
                    buffer.append((char) (c - ('a' - 'A')));
            } else {
                            buffer.append(c);
                    }
            }
            return buffer.toString();
    }
    
}
