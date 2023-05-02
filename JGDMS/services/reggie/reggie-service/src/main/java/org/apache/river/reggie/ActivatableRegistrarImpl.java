/*
 * Copyright 2018 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.reggie;

import net.jini.activation.arg.ActivationID;
import net.jini.export.DynamicProxyCodebaseAccessor;

/**
 *
 * @author peter
 */
public class ActivatableRegistrarImpl extends PersistentRegistrarImpl  
				      implements DynamicProxyCodebaseAccessor {
     /**
     * Constructs an ActivatableRegistrarImpl assigned
     * the given activation ID, based on a configuration obtained using
     * the provided marshalled string array.
     */
    public ActivatableRegistrarImpl(ActivationID activationID, 
				    String[] data) throws Exception
    {
	super(activationID, data);
    }
    
}
