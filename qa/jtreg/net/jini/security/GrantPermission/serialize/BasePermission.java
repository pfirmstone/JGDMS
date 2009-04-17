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
/* 
 * @summary Test GrantPermission serialization.
 */

import java.security.Permission;

class BasePermission extends Permission {

    private String actions;

    BasePermission(String name, String actions) {
	super(name);
	this.actions = actions;
    }

    public String getActions() {
	return actions;
    }

    public boolean implies(Permission p) {
	if (p == null || p.getClass() != this.getClass()) {
	    return false;
	}
	BasePermission bp = (BasePermission) p;
	return eq(getName(), bp.getName()) && eq(actions, bp.actions);
    }

    public boolean equals(Object obj) {
	return this == obj;
    }

    public int hashCode() {
	return System.identityHashCode(this);
    }

    private static boolean eq(String s1, String s2) {
	return (s1 != null) ? s1.equals(s2) : s2 == null;
    }
}
