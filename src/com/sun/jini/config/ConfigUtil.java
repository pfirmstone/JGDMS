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
package com.sun.jini.config;

import net.jini.core.lookup.ServiceID;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import java.net.UnknownHostException;

/**
 * A set of static convenience methods for use in configuration files.
 * This class cannot be instantiated.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 * @see net.jini.config.ConfigurationFile
 */
public class ConfigUtil {
    /** This class cannot be instantiated. */
    private ConfigUtil() {
	throw new AssertionError(
            "com.sun.jini.config.ConfigUtil cannot be instantiated");
    }
      
    /**
     * Concatenate the strings resulting from calling {@link
     * java.lang.String#valueOf(Object)} on each element of
     * an array of objects. Passing a zero length array will result in
     * the empty string being returned.
     * @param objects the array of objects to be processed.
     * @return the concatenation of the return values from
     *         calling <code>String.valueOf</code> on each element of 
     *         <code>objects</code>.  
     * @throws NullPointerException if <code>objects</code> 
     *         is <code>null</code>.
     */
    public static String concat(Object[] objects) {
	if (objects.length == 0)
	    return "";
	
	final StringBuffer buf = new StringBuffer(String.valueOf(objects[0]));
	for (int i=1; i<objects.length; i++) 
	    buf.append(objects[i]);

	return buf.toString();
    }

    /**
     * Return the local hostname.
     * @return the local hostname.
     * @throws UnknownHostException if no IP address for the local
     *         host could be found.
     */
    public static String getHostName() throws UnknownHostException {
	 return java.net.InetAddress.getLocalHost().getCanonicalHostName();
    }
    
    /**
     * Returns the textual presentation of the local host IP address.
     * @return the textual presentation of the local host IP address.
     * @throws UnknownHostException if no IP address for the local
     *         host could be found.
     */
    public static String getHostAddress() throws UnknownHostException {
         return java.net.InetAddress.getLocalHost().getHostAddress();
    }


    /**
     * Returns a <code>String</code> whose characters, if parsed by a
     * {@link net.jini.config.ConfigurationFile}, would yield a
     * <code>String</code> equivalent to the passed argument.  This is
     * done by replacing CR and LF with their escape codes, quoting
     * '\' and '"' with '\', and enclosing the entire sequence in
     * double quotes. Additionally the tab, form feed, and backspace 
     * characters will be converted to their escape codes and other control
     * characters (besides CR and LF) to octal escapes for better readability
     * if the string is printed for debugging purposes.
     * 
     * @param string the string to turn into a string literal
     * @return a <code>String</code> that if parsed as sequence of 
     *         of characters by <code>ConfigurationFile</code> would 
     *         yield a <code>String</code> equivalent to <code>string</code>
     * @throws NullPointerException if <code>string</code> is 
     * <code>null</code> 
     */
    public static String stringLiteral(String string) {	
	final StringBuffer sb = new StringBuffer(string.length() + 2);
	sb.append('"');

	final char[] ca = string.toCharArray();
	for (int i = 0; i < ca.length; i++) {
	    final char c = ca[i];
	    if (c == '\\' || c == '"')
		sb.append("\\").append(c);
	    else if (c == '\n')
		sb.append("\\n");
	    else if (c == '\r')
		sb.append("\\r");
	    else if (c == '\t')
		sb.append("\\t");
	    else if (c == '\f')
		sb.append("\\f");
	    else if (c == '\b')
		sb.append("\\b");
	    else if (c < 0x20)
		sb.append("\\").append(Integer.toOctalString(c));
	    else 
		sb.append(c);
	}

	return sb.append('"').toString();
    }
    
    /**        
     * Returns a <code>ServiceID</code> constructed from a 128-bit value
     * represented by a string.  The supplied string representation must 
     * be in the format defined by 
     * {@link net.jini.core.lookup.ServiceID#toString ServiceID.toString}, 
     * except that uppercase hexadecimal digits are allowed.
     *
     * @param s the string representation to create the <code>ServiceID</code> 
     * with    
     * @return a <code>ServiceID</code> with the value represented by the
     * given string
     * @throws IllegalArgumentException if the supplied string
     * representation does not conform to the specified format  
     * @throws NullPointerException if <code>s</code> is
     * <code>null</code>
     **/    
    public static ServiceID createServiceID(String s) {
	Uuid uuid = UuidFactory.create(s);
	return new ServiceID(uuid.getMostSignificantBits(), 
			     uuid.getLeastSignificantBits());
    } 
     
 }    
