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

package com.sun.jini.reggie;

import com.sun.jini.start.LifeCycle;
import java.rmi.MarshalledObject;
import java.rmi.activation.ActivationID;

/**
 * Class for starting activatable and non-activatable persistent lookup
 * services.
 *
 * @author Sun Microsystems, Inc.
 */
public class PersistentRegistrarImpl extends RegistrarImpl {

    /**
     * Constructs a non-activatable PersistentRegistrarImpl based on a
     * configuration obtained using the provided arguments.  If lifeCycle is
     * non-null, then its unregister method is invoked during service shutdown.
     */
    protected PersistentRegistrarImpl(String[] configArgs, LifeCycle lifeCycle)
	throws Exception
    {
	super(configArgs, null, true, lifeCycle);
    }

    /**
     * Constructs an activatable PersistentRegistrarImpl assigned
     * the given activation ID, based on a configuration obtained using
     * the provided marshalled string array.
     */
    PersistentRegistrarImpl(ActivationID activationID, MarshalledObject data)
	throws Exception
    {
	super((String[]) data.get(), activationID, true, null);
    }
}
