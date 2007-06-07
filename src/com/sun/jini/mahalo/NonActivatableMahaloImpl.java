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
package com.sun.jini.mahalo;

import com.sun.jini.start.LifeCycle;

/**
 * Convenience class intended for use with the
 * {@link com.sun.jini.start.ServiceStarter} framework to start
 * an implementation of Mahalo that is not activatable, but which
 * will log its state information to persistent storage.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
class NonActivatableMahaloImpl extends TxnManagerImpl {

    /**
     * Constructs a new instance of <code>TxnManagerImpl</code> that is not
     * activatable, but which will persist its state.
     *
     * @param configArgs <code>String</code> array whose elements are
     *                   the arguments to use when creating the server.
     * @param lifeCycle  instance of <code>LifeCycle</code> that, if 
     *                   non-<code>null</code>, will cause this object's
     *                   <code>unregister</code> method to be invoked during
     *                   shutdown to notify the service starter framework that
     *                   the reference to this service's implementation can be
     *                   'released' for garbage collection. A value of 
     *                   <code>null</code> for this argument is allowed.
     *
     * @throws Exception If there was a problem initializing the service.
     */
    NonActivatableMahaloImpl(String[] configArgs, LifeCycle lifeCycle)
        throws Exception
    {
        super(configArgs, lifeCycle, true);//true ==> persistent
    }//end constructor

}//end class NonActivatableMahaloImpl

