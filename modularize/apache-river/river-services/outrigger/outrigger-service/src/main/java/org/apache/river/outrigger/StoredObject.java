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
package org.apache.river.outrigger;

import java.io.IOException;

/**
 * Interface for a stored resource. Objects implementing this interface 
 * are passed into calls to <code>Recover</code> objects.
 *
 * @param <T> the type of object stored.
 * @see Recover
 */
public interface StoredObject<T extends StorableObject<T>> {

    /**  
     * Restore the state of a <code>StorableObject</code> object.
     * 
     * There are two use cases for this method:<br>
     * 
     * 1. The object passed in is restored with the stored state and the same object returned.<br>
     * 2. The object passed in is immutable and a copy with the restored state is returned.<br>
     *   
     * @param object to restore
     * @return a restored instance of T
     * @throws IOException if an IO related exception occurs.
     * @throws ClassNotFoundException if class not found.
     */  
    public T restore(T object)
	throws IOException, ClassNotFoundException;
}
