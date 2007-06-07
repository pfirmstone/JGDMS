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

package com.sun.jini.phoenix;

import com.sun.jini.start.LifeCycle;
import com.sun.jini.start.ServiceProxyAccessor;

/**
 * Used to start phoenix from the {@link
 * com.sun.jini.start.ServiceStarter}. 
 **/
class PhoenixStarter implements ServiceProxyAccessor {

    /** reference to recovered Activation instance */
    private final Activation phoenixImpl;
    /** the ActivationSystem proxy */
    private final Object serviceProxy;
    /** the LifeCycle callback object */
    private final LifeCycle lifeCycle;

    /**
     * Constructs a <code>PhoenixStarter</code> instance.  This
     * constructor is defined such that it can be called from the
     * {@link com.sun.jini.start.NonActivatableServiceDescriptor#create
     * NonActivatableServiceDescriptor.create} method which allows
     * phoenix to be started from the {@link
     * com.sun.jini.start.ServiceStarter}.
     **/
    PhoenixStarter(String[] configOptions, LifeCycle lifeCycle)
	throws Exception
    {
	this.lifeCycle = lifeCycle;
	this.phoenixImpl = Activation.main(configOptions, false, this);
	this.serviceProxy = phoenixImpl.getActivationSystemProxy();
    }

    public Object getServiceProxy() {
	return serviceProxy;
    }

    void unregister() {
	lifeCycle.unregister(phoenixImpl);
    }
}

