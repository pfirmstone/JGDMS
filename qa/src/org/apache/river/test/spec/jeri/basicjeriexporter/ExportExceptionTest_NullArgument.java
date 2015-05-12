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
package org.apache.river.test.spec.jeri.basicjeriexporter;

import java.util.logging.Level;

//test harness related imports
import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;

//overture imports
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

//java.rmi.server
import java.rmi.server.ExportException;

/**
 * Purpose: This test verifies that the <code>export</code> method throws
 * the appropriate exceptions
 * Use Case: Attempting to pass a null argument to the export method.
 * <br>
 * For Testing the Use Case:
 * <ol>
 * <li>Create a <code>BasicJeriExporter</code> instance.</li>
 * <li>Call the <code>export</code> method on the exporter created in step 1,
 *     passing null as argument to the method call.</li>
 * <li>Verify that a <code>java.lang.NullPointerException</code>
 *     is thrown.</li>
 * </ol>
 */
public class ExportExceptionTest_NullArgument extends BJEAbstractTest{

    /**
     * For Testing the Use Case:
     * <ol>
     * <li>Create a <code>BasicJeriExporter</code> instance.</li>
     * <li>Call the <code>export</code> method on the exporter created
     *     in step 1, passing null as argument to the method call.</li>
     * <li>Verify that a <code>java.lang.NullPointerException</code>
     *     is thrown.</li>
     * </ol>
     */
    public void run() throws Exception {
        //Create an exporter instance
        int listenPort = config.getIntConfigVal("org.apache.river.test.spec.jeri"
            + ".basicjeriexporter.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(
            TcpServerEndpoint.getInstance(listenPort), new BJETestILFactory());
        try {
            //Call the export method passing null as the argument
            exporter.export(null);
            throw new TestException( "Passing null as argument"
                + " to the export method did not result in an exception being"
                + " thrown");
        } catch (ExportException e) {
            log.finer("Unexpected exception in call to export " +
                e.getMessage());
            throw new TestException( "An export exception"
                + " was thrown when a NullPointerException was expected", e);
        } catch (NullPointerException e) {
            //OK
        } catch (Exception e) {
            log.finer("Unexpected exception in call to export " +
                e.getMessage());
            throw new TestException("An unexpected exception"
                + " was thrown when a NullPointerException was expected", e);
        }
    }

}
