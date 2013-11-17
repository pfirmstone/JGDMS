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
import net.jini.core.lookup.*;
import org.apache.river.api.util.ResultStream;

/**
 * Add this to the ResultStream filter chain
 * {@link StreamServiceRegistrar#lookup(ServiceTemplate, Class[], int, int)}
 * to getServiceItem any ServiceItemClasspathSub's in the stream, prior to 
 * proxy verification, or applying constraints.
 * 
 * @author Peter Firmstone.
 * @since 3.0.0
 * @see ServiceItemClasspathSub
 * @see StreamServiceRegistrar
 */
public class ServiceResultStreamUnmarshaller implements ResultStream<ServiceItem> {
    private final ResultStream input;
    
    /** 
     * Note the methods of ServiceResultStreamUnmarshaller, implement the 
     * generic methods of ResultStream<ServiceItem>, but the constructor 
     * doesn't to ensure type safety at the client, where runtime binding 
     * prevents the compiler from checking the type.
     */ 
    public ServiceResultStreamUnmarshaller(ResultStream rs){
        input = rs;
    }

    public ServiceItem get() throws IOException {
	if (input == null) return null;
        for(Object item = input.get(); item != null; item = input.get()) {
            if (item instanceof ServiceItemClasspathSub){
                ServiceItemClasspathSub msi = (ServiceItemClasspathSub) item;
                return msi.getServiceItem();
            } else if (item instanceof ServiceItem) {
		return (ServiceItem) item;
	    }
	    /* If item is not an instanceof ServiceItem or ServiceItemClasspathSub
	     * it is ignored and the next item in the ResultStream is retrieved.
	     */
        }//end item loop
        return null; // Our stream terminated item was null;
    }

    public void close() throws IOException {
	if (input == null) return;
        input.close();
    }

}
