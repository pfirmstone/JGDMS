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
import org.apache.river.api.util.ResultStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jini.core.lookup.ServiceItem;
import net.jini.lookup.ServiceItemFilter;

/**
 * <p>
 * A Filter utility class designed to filter out unwanted results.  Filters can
 * be daisy chained with pre prepared filters to perform logical AND operations.
 * <p></p>
 * Logical OR operations can be simulated by providing multiple filters in
 * constructors.
 * <p></p>
 * Any references to ServiceResultStreamFilter should be set to null 
 * immediately after filtering.
 * New instances can be created as required.
 * <p>
 * 
 * @since 2.2.1
 */
public class ServiceResultStreamFilter implements ResultStream<ServiceItem> {
    private final List<ServiceItemFilter> filters;
    private final ResultStream inputResultStream;
    
    /**
     * Note the methods of ServiceResultStreamFilter implement 
     * ResultStream<ServiceItem>, but the constructor doesn't, this is to 
     * protect the client against unchecked type casts that would occur
     * if a ResultStream<ServiceItem> was obtained from a service.
     * 
     * All methods in this implementation perform their own type safety
     * checks in order to implement ResultStream<ServiceItem> safely.
     * 
     * @param rs
     * @param sf
     */
    public ServiceResultStreamFilter(ResultStream rs,
            ServiceItemFilter[] sf){
        inputResultStream = rs;
        filters = new ArrayList<ServiceItemFilter>(sf.length);
        filters.addAll(Arrays.asList(sf));
    }

    public ServiceItem get() throws IOException {
        for(Object item = inputResultStream.get(); item != null; 
                item = inputResultStream.get()) {
	    if (item instanceof ServiceItem){
		ServiceItem it = (ServiceItem) item;
		int l = filters.size();
		for ( int i = 0; i < l; i++){
		    ServiceItemFilter filter = filters.get(i);
		    if (filter == null) continue;
		    if (filter.check(it))  return it;
		}// end filter loop
	    }// If it isn't a ServiceItem it is ignored.
        }//end item loop
        return null; // Our stream terminated item was null;
    }

    public void close() throws IOException {
        inputResultStream.close();
    }
}
