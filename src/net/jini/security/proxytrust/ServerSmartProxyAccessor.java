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

package net.jini.security.proxytrust;

import java.io.IOException;

/**
 * Defines a local interface to obtain a smart proxy. A service that
 * uses proxies that will not directly be considered trusted by clients
 * can export a remote object that is an instance of this
 * interface to allow clients to authenticate the service principal using 
 * a bootstrap proxy, apply method constraints, prior to obtaining a smart proxy.  
 * The intention is that a remote call to the
 * {@link SmartProxyAccessor#getSmartProxy SmartProxyAccessor.getSmartProxy} 
 * method of a trusted bootstrap proxy will be implemented (on the server side) by
 * delegating to the corresponding method of this local interface.
 * {@link ProxyTrustExporter} is one example of this form of delegation.
 *
 * @author peter
 */
public interface ServerSmartProxyAccessor {
    Object getSmartProxy() throws IOException;
}
