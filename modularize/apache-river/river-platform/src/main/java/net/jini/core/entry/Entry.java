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
 * This class is the supertype of all entries that can be stored in a
 * Jini Lookup service.
 * <p>
 * Each field of an entry must be a public reference (object) type.
 * You cannot store primitive types in fields of an
 * <code>Entry</code>.  An <code>Entry</code> may have any number of
 * methods or constructors.  Each field is serialized separately, so
 * references between two fields of an entry will not be reconstituted
 * to be shared references, but instead to separate copies of the
 * original object.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see net.jini.core.lookup.ServiceRegistrar
 *
 * @since 1.0
 */
public interface Entry extends java.io.Serializable {
}
