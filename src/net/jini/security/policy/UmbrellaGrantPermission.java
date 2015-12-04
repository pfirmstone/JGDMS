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

package net.jini.security.policy;

import java.security.Permission;

/**
 * Permission that is specially intepreted by {@link PolicyFileProvider} as
 * shorthand for a {@link net.jini.security.GrantPermission}
 * covering all permissions authorized to a given protection domain.  For each
 * protection domain authorized with a set of permissions <code>ps</code> that
 * includes an <code>UmbrellaGrantPermission</code>,
 * <code>PolicyFileProvider</code> adds to <code>ps</code> a
 * <code>GrantPermission</code> for all permissions in <code>ps</code>.  All
 * <code>UmbrellaGrantPermission</code> instances are equivalent, and have
 * target names and action strings that are the empty string.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
public final class UmbrellaGrantPermission extends Permission {

    private static final long serialVersionUID = -969939904144051917L;

    /**
     * Creates a new <code>UmbrellaGrantPermission</code>.
     */
    public UmbrellaGrantPermission() {
	super("");
    }

    /** 
     * Behaves as specified by {@link Permission#getActions}.
     */
    public String getActions() {
	return "";
    }

    /**
     * Behaves as specified by {@link Permission#implies}.
     */
    public boolean implies(Permission permission) {
	return permission instanceof UmbrellaGrantPermission;
    }

    /**
     * Behaves as specified by {@link Permission#equals}.
     */
    public boolean equals(Object obj) {
	return obj instanceof UmbrellaGrantPermission;
    }

    /**
     * Behaves as specified by {@link Permission#hashCode}.
     */
    public int hashCode() {
	return UmbrellaGrantPermission.class.hashCode();
    }

    // REMIND: include readObject method verifying target name is empty?
}
