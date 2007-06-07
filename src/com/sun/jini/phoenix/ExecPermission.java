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
 * Represents permission to execute a command to create an activation group.
 * An instance of this class contains a name (also referred to as a "target
 * name") but no actions list; you either have the named permission or you
 * don't. The target name is any name accepted by {@link FilePermission},
 * with the same matching semantics.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class ExecPermission extends Permission {
    private static final long serialVersionUID = -6208470287358147919L;
    
    /*
     * FilePermission containing the name.
     */
    private transient Permission fp;
    
    /**
     * Constructs an instance with the specified name.
     *
     * @param name the target name
     * @throws NullPointerException if the name is <code>null</code>
     */
    public ExecPermission(String name) {
	super(name);
	fp = new FilePermission(name, "execute");
    }

    /**
     * Returns <code>true</code> if the specified permission is an instance
     * of <code>ExecPermission</code> and a <code>FilePermission</code>
     * constructed with the name of this permission implies a
     * <code>FilePermission</code> constructed with the name of the specified
     * permission; returns <code>false</code> otherwise.
     *
     * @param p the permission to check
     * @return <code>true</code> if the specified permission is an instance
     * of <code>ExecPermission</code> and a <code>FilePermission</code>
     * constructed with the name of this permission implies a
     * <code>FilePermission</code> constructed with the name of the specified
     * permission; returns <code>false</code> otherwise.
     */
    public boolean implies(Permission p) {
	return (p instanceof ExecPermission &&
		fp.implies(((ExecPermission) p).fp));
    }

    /**
     * Two instances of this class are equal if <code>FilePermission</code>
     * instances created with their names are equal.
     */
    public boolean equals(Object obj) {
	return (obj instanceof ExecPermission &&
		fp.equals(((ExecPermission) obj).fp));
    }

    /**
     * Returns a hash code value for this object.
     */
    public int hashCode() {
	return fp.hashCode();
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
	fp = new FilePermission(getName(), "execute");
    }
}
