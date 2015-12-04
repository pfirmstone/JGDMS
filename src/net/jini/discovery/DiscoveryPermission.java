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
package net.jini.discovery;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Permission for using LookupDiscovery.  The permission contains a name
 * but no actions list.  The name is a discovery group name.  The empty
 * string represents the "public" group (as usual).  The name "*" represents
 * all groups.  The name can start with "*." to signify a prefix wildcard
 * match; in this case, group names are assumed to be in dotted domain name
 * style (e.g., "*.sun.com").
 * <p>
 * Note that, due to limitations of the Java(TM) platform security model,  
 * permission must be granted not only to the code that calls LookupDiscovery, 
 * but also to LookupDiscovery itself.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see LookupDiscovery
 */
public final class DiscoveryPermission extends Permission
				       implements java.io.Serializable 
{
    private static final long serialVersionUID = -3036978025008149170L;

    /**
     * Simple constructor.
     *
     * @param group the group name (wildcard permitted)
     */
    public DiscoveryPermission(String group) {
	super(group == null ? "" : group);
	if (group == null)
	    group = "";
	else if (group.startsWith("*") &&
		 !(group.length() == 1 ||
		   (group.length() > 2 && group.startsWith("*."))))
	    throw new IllegalArgumentException(
				       "invalid group wildcard specification");
    }

    /**
     * Simple constructor.
     *
     * @param group the group name (wildcard permitted)
     * @param action ignored
     */
    public DiscoveryPermission(String group, String action) {
	this(group);
    }

    /**
     * Returns true if the name of this permission (the one on which
     * the method is invoked) is the same as the name of the
     * permission parameter, or if the name of this permission starts
     * with '*' and the remainder (after the '*') is a suffix of the
     * name of the permission parameter.
     */
    public boolean implies(Permission p) {
	if (!(p instanceof DiscoveryPermission))
	    return false;
	String grp = p.getName();
	String group = getName();
	if (group.startsWith("*")) {
	    if (group.length() == 1)
		return true;
	    if (grp.length() == 1)
		return false;
	    return (grp.length() >= group.length() &&
		    group.regionMatches(false, 2, grp,
					grp.length() - group.length() + 2,
					group.length() - 2));
	}
	if (grp.startsWith("*"))
	    return false;
	return group.equals(grp);
    }

    /** Two instances are equal if they have the same name. */
    public boolean equals(Object obj) {
	return (this == obj ||
		(obj instanceof DiscoveryPermission &&
		 getName().equals(((DiscoveryPermission)obj).getName())));
    }

    public int hashCode() {
	return getName().hashCode();
    }

    /**
     * Always returns the empty string; this permission type has no actions
     * list.
     */
    public String getActions() {
	return "";
    }

    // inherits javadoc
    public PermissionCollection newPermissionCollection() {
	/* default permission collection is inadequate (bug 4158302) */
	return new Collection();
    }

    /** Simple permission collection */
    private static class Collection extends PermissionCollection {
	private static final long serialVersionUID = -6656227831159479611L;

	/**
	 * Permissions
	 *
	 * @serial
	 **/
	private final ArrayList perms = new ArrayList(3);

	public synchronized void add(Permission p) {
	    if (isReadOnly())
		throw new SecurityException("collection is read-only");
	    if (perms.indexOf(p) < 0)
		perms.add(p);
	}

	public synchronized boolean implies(Permission p) {
	    for (int i = perms.size(); --i >= 0; ) {
		if (((Permission)perms.get(i)).implies(p))
		    return true;
	    }
	    return false;
	}

	public Enumeration elements() {
	    return Collections.enumeration(perms);
	}
    }
}
