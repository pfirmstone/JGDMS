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
import net.jini.core.entry.Entry;

/**
 * Provides a means to obtain attributes from a bootstrap proxy returned from
 * {@link net.jini.core.lookup.ServiceRegistrar#lookUp(net.jini.core.lookup.ServiceTemplate, int) }
 * 
 * Services should implement this interface.
 * 
 * It's recommended that this is implemented using 
 * {@link net.jini.lookup.JoinManager#getAttributes() }
 * or equivalent, to manage attribute state, to ensure that lookup services
 * and services registered therein maintain equivalent attributes.
 * 
 */
public interface ServiceAttributesAccessor extends Remote {
    /**
     * Allows clients to retrieve a services attributes prior to the service
     * itself, this allows the clients to perform additional filtering, before
     * a service code-base download is required.
     * 
     * @return
     * @throws IOException 
     */
    public Entry [] getServiceAttributes() throws IOException;
}
