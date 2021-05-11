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
package org.apache.river.test.impl.reliability;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;

import java.net.InetAddress;
import java.rmi.MarshalledObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.qa.harness.SlaveRequest;
import org.apache.river.qa.harness.SlaveTest;

/**
 * A <code>SlaveRequest</code> to start an ApplicationServer.
 */
@AtomicSerial
class StartApplicationServerRequest implements SlaveRequest {
    
    public static SerialForm[] serialForm(){
        return new SerialForm[]{
            new SerialForm("registryHost", String.class)
        };
    }
    
    public static void serialize(PutArg arg, StartApplicationServerRequest s) throws IOException{
        arg.put("registryHost", s.registryHost);
        arg.writeArgs();
    }

    private String registryHost;
    private transient Thread server;
    private static final Logger logger = Logger.getLogger("org.apache.river.qa.harness");

    /**
     * Construct the request.
     *
     * @param registryHost the host on which the rmiregistry is
     *        running. This is needed by the constructor of the
     *        ApplicationServer.
     */
    StartApplicationServerRequest(String registryHost) {
	this.registryHost = registryHost;
    }
    
    StartApplicationServerRequest(GetArg arg) throws IOException, ClassNotFoundException{
	this(arg.get("registryHost", null, String.class));
    }

    /**
     * Called by the <code>SlaveTest</code> after unmarshalling this object.
     * <code>org.apache.river.test.impl.reliability.ApplicationServer</code>
     * is started in a new thread.
     *
     * @param slaveTest a reference to the <code>SlaveTest</code>
     * @return null
     * @throws Exception if an error occurs starting the service
     */
    @Override
    public Object doSlaveRequest(SlaveTest slaveTest) throws Exception {
        server = new Thread(new ApplicationServer(registryHost));
        logger.log(Level.INFO, "Starting application server " +
            "on host " + InetAddress.getLocalHost().getHostName());
        server.start();
	return null;
    }
}
