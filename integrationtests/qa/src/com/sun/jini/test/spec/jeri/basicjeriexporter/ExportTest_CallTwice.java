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
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

//utility classes
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestService;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestServiceImpl;

//java.rmi.server
import java.rmi.server.ExportException;


/**
 * Purpose:  This test verifies the correct behavior of the
 * <code>BasicJeriExporter.export()</code> method.
 * Use Case: Attempting to use the same exporter twice.
 * <br>
 * For testing the Use Case:
 * <ol>
 * <li>Construct a <code>BasicJeriExporter</code> using default parameters.</li>
 * <li>Export a remote object using the exporter constructed
 *     in step 1.</li>
 * <li>Export another remote object using the same exporter that was used
 *     in step 2</li>
 * <li>Verify that an <code>IllegalStateException</code> is thrown.</li>
 * </ol>
 */
public class ExportTest_CallTwice extends BJEAbstractTest {

    /**
     * Tests that a <code>BasicJeriExporter</code> can only be used once.
     * <ol>
     * <li>Construct a <code>BasicJeriExporter</code> using default parameters.
     * </li>
     * <li>Export a remote object using the exporter constructed
     *     in step 1.</li>
     * <li>Export another remote object using the same exporter that was used
     *     in step 2</li>
     * <li>Verify that an <code>IllegalStateException</code> is thrown.</li>
     * </ol>
     */
    public void run() throws Exception {
        //Create an exporter instance
        int listenPort = config.getIntConfigVal("com.sun.jini.test.spec.jeri"
            + ".basicjeriexporter.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(
            TcpServerEndpoint.getInstance(listenPort), new BJETestILFactory());
        BJETestServiceImpl service = new BJETestServiceImpl();
        try {
            //Export a remote object using the exporter constructed in step 1
            BJETestService stub = (BJETestService)
                exporter.export(service);
        } catch (ExportException e) {
            log.fine("Exception thrown after first call to export: "
                + e.getMessage());
            throw new TestException(
                "Unexpected Exception after first call to export",e);
        }
        BJETestServiceImpl service2 = new BJETestServiceImpl();
        try {
            //Export another remote object using the same exporter that
            //was used in step 2
            BJETestService stub =
                (BJETestService) exporter.export(service2);
        } catch (IllegalStateException e) {
            //OK
        } catch (ExportException e) {
            log.fine("Unexpected exception thrown after second call to export: "
                + e.getMessage());
            throw new TestException(
                "Unexpected Exception after second call to export",e);
        }
    }
}
