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

package net.jini.export;

import java.io.IOException;
import java.rmi.Remote;
import net.jini.core.lookup.ServiceID;

/**
 * Allows clients to obtain the ServiceID from a bootstrap service
 * proxy.
 * 
 * Services that wish to support the 
 * {@link net.jini.core.lookup.ServiceRegistrar#lookUp(net.jini.core.lookup.ServiceTemplate, int) } 
 * method must implement this remote interface.
 * 
 */
public interface ServiceIDAccessor extends Remote {
    
    /**
     * A remote method to be implemented by a service, allowing clients to obtain
     * this information from a bootstrap proxy, prior to downloading the
     * services proxy.
     * 
     * @return the ServiceID of the service.
     * @throws IOException 
     */
    ServiceID serviceID() throws IOException;
    
}
