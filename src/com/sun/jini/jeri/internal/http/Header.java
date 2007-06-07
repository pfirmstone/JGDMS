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

package com.sun.jini.jeri.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 * Class representing HTTP message header.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class Header {
    
    private static final SimpleDateFormat dateFormat;
    static {
	dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
	dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    private Map fields = new HashMap(5);

    /**
     * Creates new header with no field entries.
     */
    Header() {
    }
    
    /**
     * Reads in new header from the given input stream.
     */
    Header(InputStream in) throws IOException {
	String line = MessageReader.readLine(in);
	while (line != null && line.length() > 0) {
	    String next = MessageReader.readLine(in);
	    while (next != null && 
		   next.length() > 0 && 
		   isSpaceOrTab(next.charAt(0)))
	    {
		line += next;
		next = MessageReader.readLine(in);
	    }
	    
	    int sepidx = line.indexOf(':');
	    if (sepidx < 0) {
		throw new HttpParseException("header line missing separator");
	    }
	    String name = line.substring(0, sepidx).trim();
	    String value = line.substring(sepidx + 1).trim();
	    if (name.length() == 0) {
		throw new HttpParseException("invalid header field name");
	    }
	    addField(name, value);
	    
	    line = next;
	}
	if (line == null) {
	    throw new HttpParseException("unexpected EOF in message header");
	}
    }
    
    /**
     * Returns value associated with named field, or null if field not present
     * in this header.
     */
    String getField(String name) {
	return (String) fields.get(new FieldKey(name));
    }
    
    /**
     * If given value is non-null, enters it as value of named field;
     * otherwise, removes field (if present) from this header.
     */
    void setField(String name, String value) {
	FieldKey key = new FieldKey(name);
	if (value != null) {
	    fields.put(key, value);
	} else {
	    fields.remove(key);
	}
    }
    
    /**
     * Returns true if named field's associated value either contains (as an
     * element of a comma-separated list) or is equal to the given value.
     */
    boolean containsValue(String name, String value, boolean ignoreCase) {
	String vlist = getField(name);
	if (vlist != null) {
	    value = value.trim();
	    StringTokenizer tok = new StringTokenizer(vlist, ",");
	    while (tok.hasMoreTokens()) {
		String v = tok.nextToken().trim();
		if (ignoreCase ? value.equalsIgnoreCase(v) : value.equals(v)) {
		    return true;
		}
	    }
	}
	return false;
    }
    
    /**
     * Returns number of field entries in header.
     */
    int size() {
	return fields.size();
    }
    
    /**
     * If given header is non-null, adds its field entries to this header.  Any
     * overlapping field values are appended to the values in this header with
     * a comma in between.
     */
    void merge(Header header) {
	if (header != null) {
	    Iterator ents = header.fields.entrySet().iterator();
	    while (ents.hasNext()) {
		Map.Entry e = (Map.Entry) ents.next();
		addField(((FieldKey) e.getKey()).name, (String) e.getValue());
	    }
	}
    }

    /**
     * Writes header to given output stream.
     */
    void write(OutputStream out) throws IOException {
	Iterator ents = fields.entrySet().iterator();
	while (ents.hasNext()) {
	    Map.Entry e = (Map.Entry) ents.next();
	    MessageWriter.writeLine(out,
		((FieldKey) e.getKey()).name + ": " + (String) e.getValue());
	}
	MessageWriter.writeLine(out, "");
    }
    
    /**
     * Returns formatted date string for given time.
     */
    static String getDateString(long time) {
	return dateFormat.format(new Date(time));
    }

    private static boolean isSpaceOrTab(char c) {
	return c == ' ' || c == '\t';
    }
    
    /**
     * Associates additional value with named field.  If the field is already
     * present in this header, the field's value is set to the given value
     * appended to the old value with a comma in between.
     */
    private void addField(String name, String value) {
	if (value != null) {
	    FieldKey key = new FieldKey(name);
	    String oldv = (String) fields.get(key);
	    String newv = (oldv != null) ? (oldv + ", " + value) : value;
	    fields.put(key, newv);
	}
    }
    
    /**
     * Field lookup key.  Field name comparisons are case-insensitive; however,
     * the original field name string is retained for use when writing the
     * header to a stream.
     */
    private static class FieldKey {
	
	final String name;
	private final int hash;
	
	FieldKey(String name) {
	    this.name = name;
	    hash = name.toLowerCase().hashCode();
	}
	
	public boolean equals(Object obj) {
	    if (obj instanceof FieldKey) {
		return name.equalsIgnoreCase(((FieldKey) obj).name);
	    }
	    return false;
	}
	
	public int hashCode() {
	    return hash;
	}
    }
}
