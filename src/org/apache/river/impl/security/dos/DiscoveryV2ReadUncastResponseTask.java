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

package org.apache.river.impl.security.dos;

import com.sun.jini.discovery.DiscoveryProtocolException;
import com.sun.jini.discovery.UnicastResponse;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.concurrent.Callable;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.io.MarshalledInstance;

class DiscoveryV2ReadUncastResponseTask implements Callable<UnicastResponse> {

    private final InputStream in;
    private final ClassLoader defaultLoader;
    private final boolean verifyCodebaseIntegrity;
    private final ClassLoader verifierLoader;
    private final Collection context;

    DiscoveryV2ReadUncastResponseTask(InputStream in,
            ClassLoader defaultLoader,
            boolean verifyCodebaseIntegrity,
            ClassLoader verifierLoader, 
            Collection context) 
    {
        this.in = in;
        this.defaultLoader = defaultLoader;
        this.verifyCodebaseIntegrity = verifyCodebaseIntegrity;
        this.verifierLoader = verifierLoader;
        this.context = context;
    }

    public UnicastResponse call() throws Exception {
        try {
            DataInput din = new DataInputStream(in);
            String host = din.readUTF();

            // read LUS port
            int port = din.readUnsignedShort();
            String[] groups = new String[din.readInt()];
            for (int i = 0; i < groups.length; i++) {
                groups[i] = din.readUTF();
            }
            MarshalledInstance mi = (MarshalledInstance) new ObjectInputStream(in).readObject();
            ServiceRegistrar reg = 
                    (ServiceRegistrar) mi.get(defaultLoader, 
                    verifyCodebaseIntegrity, verifierLoader, context);
            return new UnicastResponse(host, port, groups, reg);
        } catch (RuntimeException e) {
            throw new DiscoveryProtocolException(null, e);
        }
    }
}
