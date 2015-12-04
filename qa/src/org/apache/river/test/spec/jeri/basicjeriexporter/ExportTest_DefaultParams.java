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

//overture imports
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.Endpoint;
import net.jini.jeri.BasicObjectEndpoint;
import net.jini.jeri.tcp.TcpEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

//utility classes
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestService;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestServiceImpl;

//java.lang.reflect
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

//java.rmi.server
import java.rmi.server.ExportException;


/**
 * Purpose:  This test verifies the correct behavior of the
 * <code>BasicJeriExporter.export()<code> method.
 * Use Case: Exporting a remote object using <code>BasicJeriExporter</code>
 * default parameters.
 * <br>
 * For testing the Use Case:
 * <ol>
 * <li>Construct a <code>BasicJeriExporter</code> using default parameters</li>
 * <li>Export a remote object using the exporter created in step 1</li>
 * <li>Examine the returned proxy and verify the following:
 *     <ol>
 *     <li>The proxy is an instance of <code>java.lang.reflect.Proxy</code></li>
 *     <li>The invocation handler for the proxy in an instance of
 *         <code>net.jini.jeri.BasicInvocationHandler<code></li>
 *     <li>The underlying endpoint is an instance of
 *         <code>net.jini.jeri.tcp.TcpEndpoint<code></li>
 *     </ol>
 * </li>
 * </ol>
 */
public class ExportTest_DefaultParams extends BJEAbstractTest {

    /**
     * Tests the default values of <code>BasicJeriExporter.export()</code>.
     * <ol>
     * <li>Construct a <code>BasicJeriExporter</code> using default
     *     parameters</li>
     * <li>Export a remote object using the exporter created in step 1</li>
     * <li>Examine the returned proxy and verify the following:
     *     <ol>
     *     <li>The proxy is an instance of
     *         <code>java.lang.reflect.Proxy</code></li>
     *     <li>The invocation handler for the proxy in an instance of
     *         <code>
     *         net.jini.jeri.BasicInvocationHandler<code></li>
     *     <li>The underlying endpoint is an instance of
     *         <code>net.jini.jeri.tcp.TcpEndpoint<code></li>
     *     </ol>
     * </li>
     * </ol>
     */
    public void run() throws Exception {
        //Construct a BasicJeriExporter using default parameters
        //Create an exporter instance
        int listenPort = config.getIntConfigVal("org.apache.river.test.spec.jeri"
            + ".basicjeriexporter.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(
            TcpServerEndpoint.getInstance(listenPort), new BJETestILFactory());
        BJETestService stub = null;
        BJETestServiceImpl service = new BJETestServiceImpl();
        try {
            //Export a remote object using the exporter constructed in step 1
            stub = (BJETestService)
                exporter.export(service);
        } catch (ExportException e) {
            log.fine("Exception thrown after call to export: "+ e.getMessage());
            throw new TestException(
                "Unexpected Exception after call to export",e);
        }
        //Verify that the stub is an instance of the Proxy class
        if (stub instanceof Proxy) {
            //Veriy that the invocation handler is an instance of
            //BasicInvocationHandler
            InvocationHandler handler = Proxy.getInvocationHandler(stub);
            if (handler instanceof BasicInvocationHandler){
                //Verify that the underlying endpoint is an instance of
                //TcpEnpoint.
                //!!!This code assumes implementation detail: ObjectEnpoint
                //implementation class is BasicObjectEndpoint
                BasicObjectEndpoint oe = (BasicObjectEndpoint)
                    ((BasicInvocationHandler) handler).getObjectEndpoint();
                Endpoint ep = oe.getEndpoint();
                if (ep instanceof TcpEndpoint) {
                    //OK
                } else {
                    throw new TestException( "The Enpoint "
                        + " contained in the stub is not an instance"
                        + " of net.jini.jeri.tcp.TcpEndpoint");
                }
            } else {
                throw new TestException("The invocation handler"
                + " contained in the stub is not an instance of"
                + " net.jini.jeri.BasicInvocationHandler");
            }
        } else {
            throw new TestException("The stub is not an"
                + " instance of java.lang.reflect.Proxy");
        }
    }
}
