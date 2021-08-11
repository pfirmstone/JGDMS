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


import java.io.IOException;
import net.jini.activation.ActivationExporter;
import net.jini.activation.arg.ActivationID;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

import org.apache.river.api.util.Startable;


public class NoStubProbeImpl implements Probe, Startable {
    // Stub reference
    private Object ourStub;
    
    public static Object activate(ActivationID activationID, 
	MarshalledInstance data) throws Exception
    {
        NoStubProbeImpl impl = new NoStubProbeImpl(activationID, null);
        impl.start();
	return impl.ourStub;
    }
    private final ActivationExporter exporter;

    public void ping() {
	System.out.println("NoStubProbeImpl::ping()");
    }
    
    public void start() throws Exception {
        ourStub = exporter.export(this);
        throw new IOException("Not implemented yet.");
    }

    // Shared activation constructor
    private NoStubProbeImpl(ActivationID activationID, String[] data)

	throws IOException, ClassNotFoundException
    {
        this.exporter = new ActivationExporter(activationID,
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
            new BasicILFactory(),
            false, true));
    }
}
