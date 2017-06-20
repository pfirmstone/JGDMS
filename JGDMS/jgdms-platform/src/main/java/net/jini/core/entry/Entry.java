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
package net.jini.core.entry;

/**
 * <p>
 * This class is the supertype of all entries that can be stored in a Jini
 * Lookup service.
 * </p><p>
 * Each field of an entry must be a public reference (object) type. You cannot
 * store primitive types in fields of an <code>Entry</code>. An
 * <code>Entry</code> may have any number of methods or constructors. Each field
 * is serialized separately, so references between two fields of an entry will
 * not be reconstituted to be shared references, but instead to separate copies
 * of the original object.
 * </p><p>
 * Implementors notes:
 * <ul>
 * <li> New public non-final fields added to an Entry after deployment must be
 * appended below existing fields to avoid breaking backward compatiblity.
 * <li> If an Entry implementation is extended by a child class, new public,
 * non-final fields cannot be added to the parent class, doing so will break
 * backward compatibility with the child class.
 * <li> Changing the order of fields in an existing Entry implementation will
 * break backward compatibility.
 * <li> Removing fields from an Entry after deployment will break backward
 * compatiblity.
 * </ul>
 * <p>
 * Recommended practice for backward compatible evolution of Entry's:
 * </p>
 * <ol>
 * <li> Do not change or add public non-final fields to an Entry after
 * deployment, subclass instead.
 * <li> Only append public non-final fields to a final Entry class.
 * <li> Use super-types and interfaces for fields where possible, to allow more
 * flexibility.
 * <li> Only use org.apache.river.lookup.util.ConsistentMap or
 * org.apache.river.lookup.util.ConsistentSet Collection types, or use arrays.
 * </ol>
 *
 * @author Sun Microsystems, Inc.
 *
 *
 * @since 1.0
 */
public interface Entry extends java.io.Serializable {
}
