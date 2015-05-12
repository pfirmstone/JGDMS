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
package org.apache.river.test.impl.start;

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;

import org.apache.river.start.*;


public class NoStubProbeImpl implements Probe {
    // Stub reference
    private final Object ourStub;
    
    public static Object activate(ActivationID activationID, 
	MarshalledObject data) throws Exception
    {
        NoStubProbeImpl impl = new NoStubProbeImpl(activationID, data);
	return impl.ourStub;
    }

    public void ping() {
	System.out.println("NoStubProbeImpl::ping()");
    }

    // Shared activation constructor
    private NoStubProbeImpl(ActivationID activationID, MarshalledObject data)

	throws IOException, ClassNotFoundException
    {
	ourStub = Activatable.exportObject(this, activationID, 0);
    }
}
