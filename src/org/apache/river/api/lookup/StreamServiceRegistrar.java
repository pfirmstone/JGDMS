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
package org.apache.river.api.lookup;

import java.io.IOException;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.ServiceItem;
import org.apache.river.api.util.ResultStream;

/**
 * <p>
 * Defines an extension interface to the lookup service, for use on large or 
 * global networks such as the internet or low bandwidth networks.  
 * The interface is not a remote interface; each implementation of the 
 * lookup service exports proxy objects that implement the 
 * StreamServiceRegistrar interface local to the client, using an
 * implementation-specific protocol to communicate with the actual remote 
 * server.  All of the proxy methods obey normal RMI remote interface 
 * semantics except where explicitly noted.  Two proxy objects are equal if 
 * they are proxies for the same lookup service.  Every method invocation 
 * (on both StreamServiceRegistrar and ServiceRegistration) is atomic with 
 * respect to other invocations.
 * <p></p>
 * StreamServiceRegistrar is intended to perform the same function
 * as ServiceRegistrar, but with the ability to return results as a 
 * stream, so memory consumption is minimised at the client and network
 * communication is minimised between the client and lookup service server. 
 * <p>
 * @see ServiceRegistrar
 * @see ServiceRegistration
 * @author Peter Firmstone
 * @since 2.2.1
 */
public interface StreamServiceRegistrar extends ServiceRegistrar{

    /**
     * Returns a ResultStream that provides access to ServiceClasspathSubItem 
     * instances.  The ResultStream terminates with a null value.  The result
     * stream may be infinite, or limited by an integer limit value.
     * 
     * A ServiceClasspathSubItem implementation instance is a ServiceItem that
     * contains only Objects that are resolvable on the local classpath, 
     * this is useful for clients to perform filtering before requiring a 
     * download of the actual ServiceItem.
     * 
     * The ResultStream should be closed once the desired service has been
     * found, or services have been processed.
     *
     * @param tmpl template to match
     * specified template
     * 
     * @param maxBatchSize held locally, larger batch sizes reduce network 
     * traffic, but may delay processing locally depending on implementation.
     * @param limit - Zero for infinite, otherwise limits the number of matching
     * results.
     * @return ResultStream containing ServiceItem's
     * @throws java.io.IOException 
     * @see ServiceItem
     * @see ServiceClasspathSubItem
     * @see ResultStream
     * @see ServiceResultStreamFilter
     * @see ResultStreamUnmarshaller
     * @since 2.3.0
     */
    ResultStream lookup(ServiceTemplate tmpl, Class[] entryClasses,
            int maxBatchSize, int limit)  throws IOException;
}
