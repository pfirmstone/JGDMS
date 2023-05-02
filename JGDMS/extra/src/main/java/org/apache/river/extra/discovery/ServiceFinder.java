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
package org.apache.river.extra.discovery;

import java.rmi.RemoteException;

import net.jini.core.lookup.ServiceTemplate;

/**
 * This interface provides a convenience wrapper around service discovery simply
 * for the purposes of demonstrating the self-healing proxy.
 * 
 * This implementation has at least one obvious potential failing.  The
 * <code>ServiceFinder</code> implementation does not check that the service
 * it replaces the proxy with is different to the service which has previous
 * failed.  For example, <code>invoke</code> might fail against service with
 * ID 0000-0000-0000-AAAA, if (for whatever reason) the ServiceFinder again
 * finds the service with ID 0000-0000-0000-AAAA, then we would expect this call
 * to fail also.
 * 
 * It would be wise to offer alternative implementations of <code>ServiceFinder</code>
 * which guard against these kinds of behaviours.
 * 
 */
public interface ServiceFinder {

    Object findNewService(ServiceTemplate template) throws RemoteException;

    void terminate();

}