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
package net.jini.id;

/**
 * Convenience methods for working with proxies that implement
 * <code>ReferentUuid</code>.
 * @author Sun Microsystems, Inc.
 * @see ReferentUuid
 * @since 2.0
 */
public final class ReferentUuids {
    /** Prevents instantiation. */
    private ReferentUuids() { throw new AssertionError(); }
    
    /**
     * Returns <code>true</code> if the two passed objects are
     * non-<code>null</code>, implement <code>ReferentUuid</code> and
     * their <code>getReferentUuid</code> methods return equivalent
     * <code>Uuid</code>s, or if they are both <code>null</code>.
     * Otherwise returns <code>false</code>.
     * @param o1 The first object to compare.
     * @param o2 The second object to compare.
     * @return <code>true</code> if <code>o1</code> and <code>o2</code>
     *         implement <code>ReferentUuid</code> and their
     *         <code>getReferentUuid</code> methods return equivalent 
     *         <code>Uuid</code>s, or if <code>o1</code> and <code>o2</code>
     *         are both <code>null</code>.
     */
    public static boolean compare(Object o1, Object o2) {
	if (o1 == null || o2 == null)
	    return o1 == o2; // return true if both null

	if (!(o1 instanceof ReferentUuid))
	    return false;

	if (!(o2 instanceof ReferentUuid))
	    return false;

	return ((ReferentUuid)o1).getReferentUuid().equals(
	    ((ReferentUuid)o2).getReferentUuid());
    }
}
