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

import org.apache.river.qa.harness.TestException;

//utility classes
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestServerEndpoint;
import org.apache.river.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;

//overture imports
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InvocationLayerFactory;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * The purpose of this test is to verify the constructors for
 * <code>BasicJeriExporter</code>
 * Use Cases:
 * <ol>
 * <li>Constructing a <code>BasicJeriExporter</code> with default values</li>
 * <li>Constructing a <code>BasicJeriExporter</code> with a
 *     <code>SeverEndpoint</code></li>
 * <li>Constructing a <code>BasicJeriExporter</code> with a
 *     <code>SeverEndpoint</code>, <code>InvocationLayerFactory</code>,
 *     distibuted garbage collection flag, and server virtual machine
 *     keep-alive flag</li>
 * </ol>
 * <br>
 * For testing Use Case 1:
 * <ol>
 * <li>Invoke <code>BasicJeriExporter()</code></li>
 * <li>Verify that the <code>ServerEnpoint</code> used by the exporter is
 *     <code>TcpServerEnpoint</code></li>
 * <li>Verify that the <code>InvocationLaterFactory</code> used by
 *     the exporter is <code>BasicILFactory</code></li>
 * <li>Verify that the distibuted garbage collection flag for the
 *     exporter instance is set to true</li>
 * <li>Verify that the server virtual machine keep-alive flag for the
 *     exporter instance is set to true</li>
 * </ol>
 * <br>
 * For Testing Use Case 2:
 * <ol>
 * <li>Invoke <code>BasicJeriExporter(ServerEndpoint se)</code> with a
 *     dummy <code>ServerEndpoint</code> implementation</li>
 * <li>Verify that the <code>ServerEnpoint</code> used by the exporter
 *     is the dummy implementation</li>
 * <li>Verify 2, 3, and 4 from Use Case 1</li>
 * </ol>
 * <br>
 * For Testing Use Case 3:
 * <ol>
 * <li>Invoke <code>BasicJeriExporter(ServerEndpoint se,
 *     InvocationLayerFactory ilf, boolean enableDGC, boolean keepAlive)</code>
 *     with dummy <code>ServerEnpoint</code> and
 *     <code>InvocationLaterFactory</code> implementations and
 *     garbage collection and keep-alive flags both set to false</li>
 * <li>Verify that the <code>ServerEnpoint</code> used by the exporter is the
 *     dummy <code>ServerEnpoint</code> implementation</li>
 * <li>Verify that the <code>InvocationLayerFactory</code> used by the
 *     exporter is the dummy <code>InvocationLayerFactory</code></li>
 * <li>Verify that the distibuted garbage collection flag for the
 *     exporter instance is set to false</li>
 * <li>Verify that the server virtual machine keep-alive flag for
 *     the exporter instance is set to false</li>
 * </ol>
 */

public class ConstructorTest extends BJEAbstractTest{

    /**
     * Tests the following use cases:
     * <ol>
     * <li>Constructing a <code>BasicJeriExporter</code>
     *     with default values</li>
     * <li>Constructing a <code>BasicJeriExporter</code> with a
     *     <code>SeverEndpoint</code></li>
     * <li>Constructing a <code>BasicJeriExporter</code> with a
     *     <code>SeverEndpoint</code>, <code>InvocationLayerFactory</code>,
     *     distibuted garbage collection flag, and server virtual machine
     *     keep-alive flag</li>
     * </ol>
     */
    public void run() throws Exception {
        //Verify constructing a BasicJeriExporter with ServerEndpoint
        //argument
        ServerEndpointConstructorTest();
        //Verify constructing a BasicJeriExporter with ServerEnpoint,
        //InvocationLayerFactory, DGC flag, and keep alive flag
        //arguments
        CustomParametersConstructorTest();
    }
    /**
     * Tests the noarg constructor for <code>BasicJeriExporter</code>:
     * <ol>
     * <li>Invoke <code>BasicJeriExporter()</code></li>
     * <li>Verify that the <code>ServerEnpoint</code> used by the exporter is
     *     <code>TcpServerEnpoint</code></li>
     * <li>Verify that the <code>InvocationLaterFactory</code> used by
     *     the exporter is <code>BasicILFactory</code></li>
     * <li>Verify that the distibuted garbage collection flag for the
     *     exporter instance is set to true</li>
     * <li>Verify that the server virtual machine keep-alive flag for the
     *     exporter instance is set to true</li>
     * </ol>
     *
     private void NoArgConstructorTest() throws Exception {
        //Invoke BasicJeriExporter()
        BasicJeriExporter exporter = new BasicJeriExporter();
        //Verify that the ServerEnpoint used by the exporter is TcpServerEnpoint
        if (exporter.getServerEndpoint() instanceof TcpServerEndpoint) {
            //Verify that the InvocationLayerFactory is an instance of
            //BasicILFactory
            if (exporter.getInvocationLayerFactory() instanceof BasicILFactory){
                if (exporter.getEnableDGC()){
                    if (exporter.getKeepAlive()) {
                        result = Status.passed("NoArg constructor defaults to"
                            + " TcpServerEnpoint, BasicILFactory, and true"
                            + " values of enable DGC flag and keep alive flag");
                    } else {
                        throw new TestException("Keep alive value"
                        + " is set to false");
                    }
                } else {
                    throw new TestException("Default DGC value"
                        + " is set to false");
                }
            } else {
                throw new TestException("Default"
                    + " InvocationLayerFactory is not an instance of"
                    + " BasicILFactory");
            }
        } else {
            throw new TestException("Default server endpoint is"
             + " not an instance of TcpServerEndpoint");
        }

    }*/

    /**
     * Tests the <code>BasicJeriExporter(ServerEndpoint se)</code>
     * constructor.
     * <ol>
     * <li>Invoke <code>BasicJeriExporter(ServerEndpoint se)</code> with a
     *     dummy <code>ServerEndpoint</code> implementation</li>
     * <li>Verify that the <code>ServerEnpoint</code> used by the exporter
     *     is the dummy implementation</li>
     * <li>Verify 2, 3, and 4 from Use Case 1</li>
     * </ol>
     */
    private void ServerEndpointConstructorTest() throws Exception {
        //Invoke BasicJeriExporter(ServerEndpoint se)
        int listenPort = config.getIntConfigVal("org.apache.river.test.spec.jeri."
            + "basicjeriexporter.ConstructorTest.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(
            new BJETestServerEndpoint(TcpServerEndpoint.getInstance(
            listenPort)), new BJETestILFactory());
        //Verify that the ServerEnpoint used by the exporter is a custom
        //ServerEnpoint
        if (exporter.getServerEndpoint() instanceof BJETestServerEndpoint) {
            //Verify that the InvocationLayerFactory is an instance of
            //BasicILFactory
            if (exporter.getInvocationLayerFactory() instanceof BasicILFactory){
                if (!exporter.getEnableDGC()){
                    if (exporter.getKeepAlive()) {
                            //"ServerEnpoint constructor"
                            //+ " works properly");
                    } else {
                        throw new TestException("Default keep alive"
                        + " valie is set to false");
                    }
                } else {
                    throw new TestException("Default DGC value"
                        + " is not set to false");
                }
            } else {
                throw new TestException("Default"
                    + " InvocationLayerFactory is not an instance of"
                    + " BasicILFactory");
            }
        } else {
            throw new TestException("Default server endpoint is"
             + " not an instance of custom server endpoint");
        }
    }

    /**
     * For Testing Use Case 3:
     * <ol>
     * <li>Invoke <code>BasicJeriExporter(ServerEndpoint se,
     *     InvocationLayerFactory ilf, boolean enableDGC, boolean keepAlive)
     *     </code> with dummy <code>ServerEnpoint</code> and
     *     <code>InvocationLaterFactory</code> implementations and
     *     garbage collection and keep-alive flags both set to false</li>
     * <li>Verify that the <code>ServerEnpoint</code> used by the exporter is
     *     the dummy <code>ServerEnpoint</code> implementation</li>
     * <li>Verify that the <code>InvocationLayerFactory</code> used by the
     *     exporter is the dummy <code>InvocationLayerFactory</code></li>
     * <li>Verify that the distibuted garbage collection flag for the
     *     exporter instance is set to false</li>
     * <li>Verify that the server virtual machine keep-alive flag for
     *     the exporter instance is set to false</li>
     * </ol>
     */
    private void CustomParametersConstructorTest() throws Exception {
        //BasicJeriExporter(ServerEndpoint se,InvocationLayerFactory ilf,
        //boolean enableDGC, boolean keepAlive)
        int listenPort = config.getIntConfigVal("org.apache.river.test.spec.jeri"
            + ".basicjeriexporter.ConstructorTest.listenPort", 9090);
        ServerEndpoint se = new BJETestServerEndpoint(
            TcpServerEndpoint.getInstance(listenPort));
        InvocationLayerFactory ilf = new BJETestILFactory();
        BasicJeriExporter exporter = new BasicJeriExporter(se,ilf,false,false);
        //Verify that the ServerEnpoint used by the exporter is a custom
        //ServerEnpoint
        if (exporter.getServerEndpoint() instanceof BJETestServerEndpoint) {
            //Verify that the InvocationLayerFactory is an instance of
            //BasicILFactory
            if (exporter.getInvocationLayerFactory() instanceof
                BJETestILFactory){
                if (!exporter.getEnableDGC()){
                    if (!exporter.getKeepAlive()) {
                            //"Custom parameters constructor"
                            //+ " works as expected");
                    } else {
                        throw new TestException("Keep alive value"
                        + " is not set to false");
                    }
                } else {
                    throw new TestException("DGC value"
                        + " is not set to false");
                }
            } else {
                throw new TestException("InvocationLayerFactory is"
                    + " not an instance of the custom factory");
            }
        } else {
            throw new TestException("Default server endpoint is"
             + " not an instance of custom server endpoint");
        }
    }


}
