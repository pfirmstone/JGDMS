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
package org.apache.river.extra.selfhealing;

import java.lang.reflect.Proxy;

import org.apache.river.extra.discovery.ServiceFinder;

import net.jini.core.lookup.ServiceTemplate;

/**
 * Factory class which supplied an easy way of lookup up River services to use.
 * 
 * When the service starts to misbehave, by throwing exceptions or my timing
 * out, the <code>ServiceWrapper</code> proxy will automatically find a 
 * replacement service and re-route the method call to the new service.  
 * 
 * The exact behaviour can be altered by supplying custom
 * <code>ServiceFinder</code> implementations or implementing a new 
 * <code>lookup</code> method which returns an alternative implementation of
 * <code>ServiceWrapper</code>
 * 
 * @see ServiceFinder
 * @see ServiceWrapper
 */
public class SelfHealingServiceFactory {

	/**
	 * 
	 * @param template
	 * 			A River class which describes the kind of service being requested
	 * @param finder - An extras-specific implementation which the 
	 * 			<code>ServiceWrapper</code> can use to replace its underlying 
	 * 			service
	 * @return a proxy to a <code>ServiceWrapper</code> which masquerades as the
	 * 		   the service type as specified in the template
	 *  
	 * @see ServiceTemplate
	 */
    public static Object lookup(final ServiceTemplate template, final ServiceFinder finder) {
        return Proxy.newProxyInstance(template.serviceTypes[0].getClassLoader(),
                                      template.serviceTypes,
                                      new ServiceWrapper(finder, template));

    }

}