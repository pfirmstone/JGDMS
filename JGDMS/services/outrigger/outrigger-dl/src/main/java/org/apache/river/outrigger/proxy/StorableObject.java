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
package org.apache.river.outrigger.proxy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * <p>
 * Interface that must be implemented by objects that must persist their
 * state.
 * </p>
 * @param <T> The type of the object to be restored 
 * <br><code>see LogOps </code>
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public interface StorableObject<T> {

    /**  
     * Store the persistent fields 
     * @param out ObjectOutputStream in which to store the object.
     * @throws IOException if an IO related exception occurs.
     */
    public void store(ObjectOutputStream out) throws IOException;

    /**
     * Restore the persistent fields and return new instance.
     * @param in ObjectInputStream from which to read the object.
     * @return new object instance.
     * @throws IOException if an IO related exception occurs.
     * @throws ClassNotFoundException if class of object is not found.
     */
    public T  restore(ObjectInputStream in) 
	throws IOException, ClassNotFoundException;
}
