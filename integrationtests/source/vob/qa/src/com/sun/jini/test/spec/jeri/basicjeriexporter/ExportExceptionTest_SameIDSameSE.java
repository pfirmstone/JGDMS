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
package com.sun.jini.test.spec.jeri.basicjeriexporter;

import java.util.logging.Level;

//test harness related imports
import com.sun.jini.qa.harness.TestException;

//overture imports
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

//utility classes
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestService;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestServiceImpl;

//java.rmi
import java.rmi.Remote;

//java.rmi.server
import java.rmi.server.ExportException;

/**
 * Purpose: This test verifies that the <code>export</code> method throws
 * the appropriate exceptions
 * Use Case: Attempting to export an object with the same object
 * identifier and server endpoint twice.
 * <br>
 * For testing the Use Case:
 * <ol>
 * <li>Create <code>Remote</code>, <code>ServerEnpoint</code>
 *     and <code>Uuid</code> instances
 *     (instances of classes implementing these interfaces).</li>
 * <li>Create a <code>BasicJeriExporter</code> instance using the
 *     <code>ServerEnpoint</code> and <code>Uuid</code> instances created
 *     in step 1</li>
 * <li>Export the remote object created in step 1 using the exporter
 *     created in step 2.</li>
 * <li>Create a second BasicJeriExporter instance using the
 *     <code>ServerEndpoint</code> and <code>Uuid</code> instances created
 *     in step 1.</li>
 * <li>Export the remote object created in step 1 using the exporter
 *      created in step 4.</li>
 * <li>Verify that a <code>java.rmi.server.ExportException<code> is thrown.</li>
 * </ol>
 */

public class ExportExceptionTest_SameIDSameSE extends BJEAbstractTest {
    /**
     * For testing the Use Case:
     * <ol>
     * <li>Create <code>Remote</code>, <code>ServerEnpoint</code>
     *     and <code>Uuid</code> instances
     *     (instances of classes implementing these interfaces).</li>
     * <li>Create a <code>BasicJeriExporter</code> instance using the
     *     <code>ServerEnpoint</code> and <code>Uuid</code> instances created
     *     in step 1</li>
     * <li>Export the remote object created in step 1 using the exporter
     *     created in step 2.</li>
     * <li>Create a second BasicJeriExporter instance using the
     *     <code>ServerEndpoint</code> and <code>Uuid</code> instances created
     *     in step 1.</li>
     * <li>Export the remote object created in step 1 using the exporter
     *      created in step 4.</li>
     * <li>Verify that a <code>java.rmi.server.ExportException<code>
     *     is thrown.</li>
     * </ol>
     */
    public void run() throws Exception {
        //Create Remote, ServerEnpoint, and Uuid instances
        Remote exportee = new BJETestServiceImpl();
        int listenPort =
            config.getIntConfigVal("com.sun.jini.test.spec.jeri.basicjeriexporter"
            + ".ExportTest_SameIDSameSE.listenPort", 9090);
        log.finest("Test creating a ServerEnpoint on port: " + listenPort);
        ServerEndpoint ep = TcpServerEndpoint.getInstance(listenPort);
        Uuid id = UuidFactory.generate();
        //Create a BasicJeriExport instance using the ServerEndoint and Uuid
        //instances created above
        log.finest("Creating BasicJeriExporter with " + ep + ", EnableDGC=true"
            + " , keepAlive=true, " + id);
        BasicJeriExporter exporter =
            new BasicJeriExporter(ep,new BJETestILFactory(),true,true,id);
        //Export the remote object
        try {
            log.finest("Exporting " + exportee);
            exporter.export(exportee);
        } catch (ExportException e) {
            log.finer("Unexpected exception thrown while exporting service"
                + " " + e.getMessage());
            throw new TestException("Unsexpected ExportException"
                + " when exporting test service", e);
        }
        //Create a second BasicJeriExporter with the same Uuid and
        //ServerEnpoint
        log.finest("Creating second BasicJeriExporter with " + ep +
            ", EnableDGC=true , keepAlive=true, " + id);
        BasicJeriExporter secondExporter =
            new BasicJeriExporter(ep, new BJETestILFactory() ,true,true,id);
        //Export the remote object using the second exporter
        try {
            log.finest("Attempting second export using the same object, Uuid,"
                + " and ServerEnpoint");
            secondExporter.export(exportee);
            throw new TestException("An export exception was"
                + " not thrown when attempting to export the same object"
                + " twice with the same ServerEnpoint and Uuid");
        } catch (ExportException e) {
            //Verify that an export exception is thrown
        }
    }

}
