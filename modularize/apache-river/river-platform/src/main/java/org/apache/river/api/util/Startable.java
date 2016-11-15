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

package org.apache.river.api.util;

/**
 * Implemented by an object to enable starting threads, perform remote
 * export or any other activity after construction is complete, required to put
 * the object into an operational state.
 * <p>
 * All services in River now implement Startable to avoid 
 * exporting a service during construction, for JMM compliance.
 * 
 * @see org.apache.river.start.ServiceDescriptor
 * @see org.apache.river.start.ActivateWrapper
 * @see org.apache.river.start.SharedGroupImpl
 * @see org.apache.river.start.NonActivatableServiceDescriptor
 * @see net.jini.export.Exporter
 * @since 3.0.0
 */
public interface Startable {
    /**
     * Called after construction, this method enables objects to delay
     * starting threads or exporting until after construction is complete, 
     * to allow safe publication of the service in accordance with the JMM.
     * <p>
     * In addition to starting threads after construction, it also allows
     * objects to avoid throwing an exception during construction to avoid
     * finalizer attacks.
     * <p>
     * The implementation is required to ensure start() is idempotent 
     * (only executed once, additional invocations must return immediately).
     * 
     * @throws Exception if there's a problem with construction or startup.
     */
    public void start() throws Exception;
}
