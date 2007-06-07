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
 * Defines an interface that allows a proxy for a resource to express the
 * identity of that resource.  Resources include items like services,
 * leases, service registrations, and lease renewal sets. Any resource that
 * is represented by proxies that implement this interface has a unique
 * identity. That identity is expressed by assigning the resource a
 * universally unique identifier. This universally unique identifier will
 * be represented using the <code>Uuid</code> class. This <code>Uuid</code>
 * must be:
 *
 * <ul>
 * <li> Sufficient to uniquely identify the resource,
 * <li> associated with the resource for the resource's entire lifetime, 
 * <li> never assigned to another resource who's proxies implement 
 *      <code>ReferentUuid</code>, and
 * <li> returned by the <code>getReferentUuid</code> method of
 *      any proxy of the resource, if that proxy implements
 *      <code>ReferentUuid</code>.
 * </ul>
 * <p> 
 * Given two objects, <code>o1</code> and <code>o2</code>, that implement
 * <code>ReferentUuid</code>:
 * <ul>
 * <li> if <code>o1.equals(o2)</code> is <code>true</code>, 
 *      then <code>o1.getReferentUuid</code> and 
 *      <code>o2.getReferentUuid</code> should return equivalent
 *      <code>Uuid</code>s.
 * <li> if <code>o1.getReferentUuid</code> and
 *      <code>o2.getReferentUuid</code> return equivalent
 *      <code>Uuid</code>s, <code>o1.equals(o2)</code> may, but is 
 *      not required to be <code>true</code>.
 * </ul>
 * @author Sun Microsystems, Inc.
 * @see Uuid 
 * @since 2.0
 */
public interface ReferentUuid {
    /**
     * Return the <code>Uuid</code> that has been assigned to the
     * resource this proxy represents.
     * @return the <code>Uuid</code> associated with the
     *         resource this proxy represents. Will not
     *         return <code>null</code>.
     */
    public Uuid getReferentUuid();
}
