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
package net.jini.lookup;

/**
 * Interface used by a service that does not yet have a service ID, for
 * callback from the JoinManager when a service ID is assigned.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see JoinManager
 */
public interface ServiceIDListener extends java.util.EventListener {
    /** 
     * Called when the JoinManager gets a valid ServiceID from a lookup 
     * service.
     *
     *@param serviceID  the service ID assigned by the lookup service.
     */
    void serviceIDNotify(net.jini.core.lookup.ServiceID serviceID);
}
