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

package com.sun.jini.jeri.internal.runtime;

/**
 * Wraps collection elements (such as hash table keys) so that two
 * wrapped elements are only considered to be equal if they have the
 * same class (as well as being equal by Object.equals).
 *
 * This class is used to wrap elements in a collection when their
 * classes are not all trusted, with the restriction that only
 * elements of the same class can be considered equal.  This class
 * never invokes Object.equals on an underlying element with an
 * argument that has a different class than the element, to avoid
 * leaking sensitive information to untrusted elements.
 *
 * For this isolation scheme to work, the Object.equals method of any
 * trusted element class should not invoke comparison methods (such as
 * Object.equals) on any pluggable component without first verifying
 * that the component's implementation is at least as trusted as the
 * implementation of the corresponding component in the Object.equals
 * argument (such as by verifying that the corresponding component
 * objects have the same actual class).  If any such verification
 * fails, the Object.equals method should return false without
 * invoking a comparison method on the component.  Furthermore, these
 * guidelines should be recursively obeyed by the comparison methods
 * of each such component for its subcomponents.
 *
 * @author Sun Microsystems, Inc.
 **/
final class SameClassKey {

    private final Object key;

    SameClassKey(Object key) {
	this.key = key;
    }

    public int hashCode() {
	return key.hashCode();
    }

    public boolean equals(Object obj) {
	if (obj == this) {
	    return true;
	} else if (!(obj instanceof SameClassKey)) {
	    return false;
	}
	SameClassKey other = (SameClassKey) obj;
	return Util.sameClassAndEquals(key, other.key);
    }
}
