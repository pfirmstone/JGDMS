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

package com.sun.jini.discovery.internal;

import java.security.BasicPermission;

/**
 * Permission required in order to set the EndpointInternals instances stored
 * by the SslEndpointInternals and KerberosEndpointInternals holder classes.
 */
public final class EndpointInternalsPermission extends BasicPermission {

    private static final long serialVersionUID = 2L;
    static final String SET = "set";
    static final String GET = "get";

    /**
     * Creates new instance.
     */
    public EndpointInternalsPermission(String name) {
	super(name);
	if (!name.equals(GET) && !name.equals(SET)) {
	    throw new IllegalArgumentException(name);
	}
    }
    
    // REMIND: include readObject method?
}
