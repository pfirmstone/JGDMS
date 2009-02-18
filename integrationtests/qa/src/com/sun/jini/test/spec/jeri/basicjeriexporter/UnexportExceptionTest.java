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

/**
 * Purpose:  This test verifies that calling <code>unexport</code> on an
 * exporter that has not exported anyting throws a
 * <code>java.lang.IllegalStateException</code>
 * Use Case: Attempting to call <code>unexport</code> on an exporter that has
 * not exported anything.
 * <br>
 * To test the use case:
 * <ol>
 * <li>Construct a <code>BasicJeriExporter</code> instance</li>
 * <li>Call <code>unexport</code> on the exporter instance created in
 *     step 1.
 * <li>Verify that a <code>java.lang.IllegalStateException</code>
 *     is thrown.</li>
 * </ol>
 */
public class UnexportExceptionTest extends BJEAbstractTest{

    /**
     * To test the use case:
     * <ol>
     * <li>Construct a <code>BasicJeriExporter</code> instance</li>
     * <li>Call <code>unexport</code> on the exporter instance created in
     *     step 1.
     * <li>Verify that a <code>java.lang.IllegalStateException</code>
     *     is thrown.</li>
     * </ol>
     */
    public void run() throws Exception {
        //Create an exporter instance
        int listenPort = config.getIntConfigVal("com.sun.jini.test.spec.jeri"
            + ".basicjeriexporter.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(
            TcpServerEndpoint.getInstance(listenPort), new BJETestILFactory());
        try {
            //Call unexport on the exporter instance
            exporter.unexport(true);
            throw new TestException("Unexporting with an unused"
                    + "exporter does not cause an exception to be thrown");
        } catch (IllegalStateException e) {
            //Verify that in IllegalStateException is thrown
        }
    }
}
