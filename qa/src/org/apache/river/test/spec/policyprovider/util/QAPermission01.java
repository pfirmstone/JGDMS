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
package org.apache.river.test.spec.policyprovider.util;

// java.security
import java.security.Permission;

/**
 *  Helper class to verify proper behavior of GrantPermission class.
 */
public class QAPermission01 extends Permission {

    /** Constructor */
    public QAPermission01(String name) {
	super(name);
    }

    /** return true for QAPermission01 with the same name */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	}
	if (obj instanceof QAPermission01) {
	    return sameName((QAPermission01) obj);
	}
	return false;
    }

    /** return true for QAPermission01 with the same name */
    public boolean implies(Permission p) {
	if (p instanceof QAPermission01) {
	    return sameName((QAPermission01) p);
	}
	return false;
    }

    /** return 0 for all  QAPermission01 objects */
    public int hashCode() {
	return 0;
    }

    /** return empty string for all  QAPermission01 objects */
    public String getActions() {
	return "";
    }

     /*
     * return true for QAPermission01 with the same name
     */
    private boolean sameName(QAPermission01 p) {
        return getName().equals(p.getName());
    }
}
