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

package com.sun.jini.phoenix;

import java.security.Permission;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;

/**
 * Represents permission to use a specific option or options in the command
 * for creating an activation group. An instance of this class contains a
 * name (also referred to as a "target name") but no actions list; you either
 * have the named permission or you don't. The target name can be any of the
 * following forms:
 * <pre>
 * "<i>literal</i>"
 * <i>literal</i>*
 * <i>literal</i>{<i>fpname</i>}
 * <i>string</i>
 * </pre>
 * A target name is parsed according to the first form (in the order given
 * above) that it matches. A <i>literal</i> is zero or more arbitrary
 * characters. An <i>fpname</i> is zero or more characters, none of which
 * are '{'. A <i>string</i> is zero or more arbitrary characters that do
 * not match any of the previous forms.
 * <p>
 * An option <i>option</i> matches a target name of the form
 * "<i>literal</i>" if <i>option</i> is equal to <i>literal</i>.
 * <p>
 * An option <i>option</i> matches a target name of the form
 * <i>literal</i>* if <i>option</i> starts with <i>literal</i>.
 * <p>
 * An option <i>option</i> matches a target name of the form
 * <i>literal</i>{<i>fpname</i>} if <i>option</i> starts with
 * <i>literal</i> and the remainder of <i>option</i> matches <i>fpname</i>
 * according to the matching semantics defined by {@link FilePermission};
 * that is, if a <code>FilePermission</code> created with target name
 * <i>fpname</i> (and some actions) implies a <code>FilePermission</code>
 * created with the remainder of <i>option</i> (and the same actions).
 * <p>
 * An option <i>option</i> matches a target name of the form
 * <i>string</i> if <i>option</i> is equal to <i>string</i>.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class ExecOptionPermission extends Permission {
    private static final long serialVersionUID = 5842294756823092756L;
    
    /**
     * the name without any wildcard or fpname on the end
     */
    private transient String prefix;
    /**
     * true if the name ended with a wildcard or fpname, false otherwise
     */
    private transient boolean plain;
    /*
     * FilePermission containing fpname, if any
     */
    private transient Permission fp;

    /**
     * Constructs an instance with the specified name.
     *
     * @param name the target name
     * @throws NullPointerException if the name is <code>null</code>
     */
    public ExecOptionPermission(String name) {
	super(name);
	if (name == null) {
	    throw new NullPointerException("name cannot be null");
	}
	init(name);
    }

    /**
     * Parses the target name and initializes the transient fields.
     */
    private void init(String name) {
	prefix = name;
	plain = true;
	int last = name.length() - 1;
	if (last >= 0) {
	    char c = name.charAt(last);
	    if (c == '"' && last > 0 && name.charAt(0) == '"') {
		prefix = name.substring(1, last);
	    } else if (c == '*') {
		prefix = name.substring(0, last);
		plain = false;
	    } else if (c == '}') {
		int mid = name.lastIndexOf('{');
		if (mid >= 0) {
		    prefix = name.substring(0, mid);
		    String fpname = name.substring(mid + 1, last);
		    if (!fpname.equals("<<ALL FILES>>")) {
			fp = new FilePermission(fpname, "read");
		    }
		    plain = false;
		}
	    }
	}
    }

    /**
     * Returns <code>true</code> if the specified permission is an instance
     * of <code>ExecOptionPermission</code> and every option that matches
     * the name of specified permission also matches the name of this
     * permission; returns <code>false</code> otherwise.
     *
     * @param p the permission to check
     * @return <code>true</code> if the specified permission is an instance
     * of <code>ExecOptionPermission</code> and every option that matches
     * the name of specified permission also matches the name of this
     * permission; <code>false</code> otherwise
     */
    public boolean implies(Permission p) {
	if (!(p instanceof ExecOptionPermission)) {
	    return false;
	}
	ExecOptionPermission ep = (ExecOptionPermission) p;
	if (plain) {
	    return ep.plain && prefix.equals(ep.prefix);
	} else if (fp == null) {
	    return ep.prefix.startsWith(prefix);
	} else if (ep.plain) {
	    return (ep.prefix.startsWith(prefix) &&
		    fp.implies(new FilePermission(
				      ep.prefix.substring(prefix.length()),
				      "read")));
	} else {
	    return prefix.equals(ep.prefix) && fp.implies(ep.fp);
	}
    }

    /**
     * Two instances of this class are equal if each implies the other;
     * that is, every option that matches the name of one instance matches
     * the name of the other instance.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof ExecOptionPermission)) {
	    return false;
	}
	ExecOptionPermission p = (ExecOptionPermission) obj;
	return (prefix.equals(p.prefix) &&
		plain == p.plain &&
		(fp == null ? p.fp == null : fp.equals(p.fp)));
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	int h = prefix.hashCode();
	if (!plain) {
	    if (fp != null) {
		h += fp.hashCode();
	    } else {
		h++;
	    }
	}
	return h;
    }

    /**
     * Returns the empty string.
     */
    public String getActions() {
	return "";
    }
    
    /**
     * Recreates any transient state.
     *
     * @throws InvalidObjectException if the target name is <code>null</code>
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException
    {
	s.defaultReadObject();
	if (getName() == null) {
	    throw new InvalidObjectException("name cannot be null");
	}
	init(getName());
    }
}
