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

import java.security.Permission;
import java.security.UnresolvedPermission;



/**
 * Utility class to make working with PERMISSIONS.LIST files easier.
 *
 */
public final class AdvisoryPermissionParser {

    /**
     * Parses the encoded permission and instantiates it using the provided
     * ClassLoader.
     * 
     * @param encodedPermission
     * @param loader
     * @return 
     */
    public static Permission parse(String encodedPermission, ClassLoader loader) {
	if (encodedPermission == null) {
	    throw new NullPointerException("missing encoded permission");
	}
	if (encodedPermission.length() == 0) {
	    throw new IllegalArgumentException("empty encoded permission");
	}
	String parsedType = null;
	String parsedName = null;
	String parsedActions = null;
	try {
	    char[] encoded = encodedPermission.toCharArray();
	    int length = encoded.length;
	    int pos = 0;

	    /* skip whitespace */
	    while (Character.isWhitespace(encoded[pos])) {
		pos++;
	    }

	    /* the first character must be '(' */
	    if (encoded[pos] != '(') {
		throw new IllegalArgumentException("expecting open parenthesis");
	    }
	    pos++;

	    /* skip whitespace */
	    while (Character.isWhitespace(encoded[pos])) {
		pos++;
	    }

	    /* type is not quoted or encoded */
	    int begin = pos;
	    while (!Character.isWhitespace(encoded[pos])
		    && (encoded[pos] != ')')) {
		pos++;
	    }
	    if (pos == begin || encoded[begin] == '"') {
		throw new IllegalArgumentException("expecting type");
	    }
	    parsedType = new String(encoded, begin, pos - begin);

	    /* skip whitespace */
	    while (Character.isWhitespace(encoded[pos])) {
		pos++;
	    }

	    /* type may be followed by name which is quoted and encoded */
	    if (encoded[pos] == '"') {
		pos++;
		begin = pos;
		while (encoded[pos] != '"') {
		    if (encoded[pos] == '\\') {
			pos++;
		    }
		    pos++;
		}
		parsedName = unescapeString(encoded, begin, pos);
		pos++;
		
		if (Character.isWhitespace(encoded[pos])) {
		    /* skip whitespace */
		    while (Character.isWhitespace(encoded[pos])) {
			pos++;
		    }

		    /*
		     * name may be followed by actions which is quoted and
		     * encoded
		     */
		    if (encoded[pos] == '"') {
			pos++;
			begin = pos;
			while (encoded[pos] != '"') {
			    if (encoded[pos] == '\\') {
				pos++;
			    }
			    pos++;
			}
			parsedActions = unescapeString(encoded, begin, pos);
			pos++;

			/* skip whitespace */
			while (Character.isWhitespace(encoded[pos])) {
			    pos++;
			}
		    }
		}
	    }

	    /* the final character must be ')' */
	    char c = encoded[pos];
	    pos++;
	    while ((pos < length) && Character.isWhitespace(encoded[pos])) {
		pos++;
	    }
	    if ((c != ')') || (pos != length)) {
		throw new IllegalArgumentException(
			"expecting close parenthesis");
	    }
	} catch (ArrayIndexOutOfBoundsException e) {
	    throw new IllegalArgumentException("parsing terminated abruptly");
	}
	try {
	    // Can't call LoadClass.forName from ext loader,
	    // ext loader used to enable package access to PolicyUtils
	    Class<?> klass = Class.forName(parsedType, true, loader);
	    return PolicyUtils.instantiatePermission(klass, parsedName, parsedActions);
	} catch (Exception e) {
	    return new UnresolvedPermission(parsedType, parsedName, parsedActions, null);
	}
    }
    
    public static String getEncoded(Permission perm){
	return getEncoded(perm.getClass().getCanonicalName(), perm.getName(), perm.getActions());
    }

    /**
     * Returns the string encoding of this <code>Permission</code> in a form
     * suitable for restoring this <code>Permission</code>.
     *
     * <p>
     * The encoded format is:
     *
     * <pre>
     * (type)
     * </pre>
     *
     * or
     *
     * <pre>
     * (type &quot;name&quot;)
     * </pre>
     *
     * or
     *
     * <pre>
     * (type &quot;name&quot; &quot;actions&quot;)
     * </pre>
     *
     * where <i>name</i> and <i>actions</i> are strings that must be encoded for
     * proper parsing. Specifically, the <code>&quot;</code>,<code>\</code>,
     * carriage return, and line feed characters must be escaped using
     * <code>\&quot;</code>, <code>\\</code>,<code>\r</code>, and
     * <code>\n</code>, respectively.
     *
     * <p>
     * The encoded string contains no leading or trailing whitespace characters.
     * A single space character is used between <i>type</i> and
     * &quot;<i>name</i>&quot; and between &quot;<i>name</i>&quot; and
     * &quot;<i>actions</i>&quot;.
     *
     * @return The string encoding of this <code>PermissionInfo</code>.
     */
    public static String getEncoded(String type, String name, String actions) {
	StringBuilder output = new StringBuilder(
		8
		+ type.length()
		+ ((((name == null) ? 0 : name.length()) + ((actions == null) ? 0
		: actions.length())) << 1));
	output.append('(');
	output.append(type);
	if (name != null) {
	    output.append(" \"");
	    escapeString(name, output);
	    if (actions != null) {
		output.append("\" \"");
		escapeString(actions, output);
	    }
	    output.append('\"');
	}
	output.append(')');
	return output.toString();
    }

    /**
     * This escapes the quotes, backslashes, \n, and \r in the string using a
     * backslash and appends the newly escaped string to a StringBuilder.
     */
    private static void escapeString(String str, StringBuilder output) {
	int len = str.length();
	for (int i = 0; i < len; i++) {
	    char c = str.charAt(i);
	    switch (c) {
		case '"':
		case '\\':
		    output.append('\\');
		    output.append(c);
		    break;
		case '\r':
		    output.append("\\r");
		    break;
		case '\n':
		    output.append("\\n");
		    break;
		default:
		    output.append(c);
		    break;
	    }
	}
    }

    /**
     * Takes an encoded character array and decodes it into a new String.
     */
    private static String unescapeString(char[] str, int begin, int end) {
	StringBuilder output = new StringBuilder(end - begin);
	for (int i = begin; i < end; i++) {
	    char c = str[i];
	    if (c == '\\') {
		i++;
		if (i < end) {
		    c = str[i];
		    switch (c) {
			case '"':
			case '\\':
			    break;
			case 'r':
			    c = '\r';
			    break;
			case 'n':
			    c = '\n';
			    break;
			default:
			    c = '\\';
			    i--;
			    break;
		    }
		}
	    }
	    output.append(c);
	}
	
	return output.toString();
    }
    
}
