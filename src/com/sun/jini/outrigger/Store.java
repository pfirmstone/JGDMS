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
package com.sun.jini.outrigger;

/**
 * This interface defines the methods that any OutriggerServerImpl store must
 * implement.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutriggerServerImpl
 * @see LogOps
 * @see Recover
 */
public interface Store {

    /**
     * Set up the store. Recovery (if any) will be completed
     * before this method returns. That is, no calls will be made by
     * <code>setupStore</code> on the <code>Recover</code> object
     * after it returns.
     *
     * @param space object used for recovery of previous state (if any)
     *
     * @return object used to persist state
     */
    public LogOps setupStore(Recover space);

    /**
     * Destroy any persistent state and release
     * any resources associated with this store.
     */
    public void destroy() throws java.io.IOException;

    /**
     * Close the store, release VM resources (stop independent
     * threads, close files, etc.) but do not destroy any persistent
     * state. This method is used when there is a failure
     * in <code>OutriggerServerImpl</code> constructor.
     */
    public void close() throws java.io.IOException;
}
