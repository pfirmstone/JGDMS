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
import org.apache.river.outrigger.proxy.StorableResource;

/**
 * Interface for a stored resource. Objects implementing this interface
 * are passed into calls to <code>Recover</code> objects. This objects
 * represented by this interface are resources that have expiration
 * times (LeasedResource).
 *
 * @see Recover
 */
public interface StoredResource {

    /**
     * Restore the state of a <code>StorableResource</code>. The resource
     * to be restored will have its expiration set before this method
     * returns.
     * 
     * If this method returned a new StorableResource instead of mutating
     * the passed in StorableResource as a side effect, the implementation 
     * could be thread safe and immutable.
     *
     * @see LogOps#renewOp
     *
     * @param obj resource to restore
     * @throws IOException if an IO related exception occurs.
     * @throws ClassNotFoundException if class not found.
     */
    public void restore(StorableResource obj)
	throws IOException, ClassNotFoundException;
}
