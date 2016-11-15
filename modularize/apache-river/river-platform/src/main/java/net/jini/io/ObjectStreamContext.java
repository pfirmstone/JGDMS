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

package net.jini.io;

import java.util.Collection;

/**
 * Provides a collection of context information objects that are
 * associated with an {@link java.io.ObjectOutputStream} or {@link
 * java.io.ObjectInputStream} instance that implements this interface.
 *
 * <p>The class of an object that is being serialized or deserialized
 * can test (in its private
 * <code>writeObject(ObjectOutputStream)</code> or
 * <code>readObject(ObjectInputStream)</code> method) if the object
 * stream being used implements this interface.  If the stream does
 * implement this interface, the class can then retrieve context
 * information relevant to the overall serialization or
 * deserialization operation by invoking the {@link
 * #getObjectStreamContext getObjectStreamContext} method and
 * inspecting the elements of the returned collection.
 *
 * <p>The contents of the collection are determined by the
 * implementation of the object stream.  The context information
 * available from a given element of the collection is determined by
 * that element's type.
 *
 * Examples of types that a context object might implement include
 * {@link net.jini.io.context.ClientHost} and
 * {@link net.jini.io.context.ClientSubject}.
 *
 * @author	Sun Microsystems, Inc.
 * @since 2.0
 */
public interface ObjectStreamContext {

    /**
     * Returns this object stream's collection of context information
     * objects.  
     *
     * <p>The context information available from a given element of
     * the collection is determined by that element's type.  The order
     * of the elements is insignificant.  The collection may be empty.
     *
     * <p>The caller of this method cannot assume that the returned
     * collection is modifiable.
     *
     * @return	a collection of this stream's context objects
     */
    Collection getObjectStreamContext();
}
