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
package com.sun.jini.mahalo;

import com.sun.jini.landlord.LeasedResource;

/**
 * <code>LeaseManager</code> provides an interface for tracking lease
 * status.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public interface LeaseManager {
    /**
     * Notifies the manager of a new lease being created.
     * @param resource The resource associated with the new Lease.
     */
    public void register(LeasedResource resource);

    /**
     * Notifies the manager of a lease being renewed.
     * @param resource The resource associated with the new Lease.
     */
    public void renewed(LeasedResource resource);
}
