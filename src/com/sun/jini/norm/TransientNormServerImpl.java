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
package com.sun.jini.norm;

import com.sun.jini.start.LifeCycle;
import com.sun.jini.start.ServiceStarter;

/**
 * Provides a transient implementation of NormServer.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class TransientNormServerImpl extends NormServerBaseImpl {

    /**
     * Provides a constructor for a transient implementation of NormServer
     * suitable for use with {@link ServiceStarter}.
     *
     * @param configOptions the arguments to use when creating the
     *	      configuration for the server
     * @param lifeCycle object to notify when this service is destroyed, or
     *	      <code>null</code>
     * @throws Exception if there is a problem creating the server
     */
    TransientNormServerImpl(String[] configOptions, LifeCycle lifeCycle)
	throws Exception
    {
	super(false /* persistent */);
	init(configOptions, lifeCycle);
    }
}
