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
import net.jini.export.ServerContext;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.tcp.TcpServerEndpoint;

//utility classes
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJEAbstractTest;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestService;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestServiceImpl;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestILFactory;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETestServerEndpoint;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.BJETransportListener;
import com.sun.jini.test.spec.jeri.basicjeriexporter.util.TransportListener;

//java.lang.reflect
import java.lang.reflect.Method;

//java.rmi
import java.rmi.RemoteException;

//java.rmi.server
import java.rmi.server.ExportException;

//java.security
import java.security.AccessController;
import java.security.AccessControlContext;

//java.util
import java.util.Hashtable;
import java.util.Iterator;

/**
 * Purpose:  This test verifies the correct behavior of the
 * <code>BasicJeriExporter.export()</code> method when using custom
 * invocation layer factories ans server endpoints.
 * Use Case: Exporting a remote object using <code>BasicJeriExporter</code>
 * with custom parameters.
 * <br>
 * For testing Use Case:
 * <ol>
 * <li>Construct a <code>BasicJeriExporter</code>
 *     using an instrumented <code>ServerEndpoint</code> and
 *     <code>InvocationLayerFactory</code></li>
 * <li>Export a remote object using the exporter created in step 1</li>
 * <li>Using the instrumented <code>ServerEndpoint</code> and
 *     <code>InvocationLayerFactory</code> verify the following:
 *     <ol>
 *         <li>The <code>listen()</code> method is called on the
 *             instrumented <code>ServerEndpoint</code></li>
 *         <li>The <code>createInstances()</code> method is called on the
 *             instrumented <code>InvocationLayerFactory</code></li>
 *         <li>The <code>dispatch()</code> method is called on the
 *             instrumented <code>InvocationDispatcher</code> and the access
 *             control context is equal to the access control context in
 *             effect when the object was exported, the context class
 *             loader is equal to the one in effect when the object was
 *             exported, and the server context in effect is an instance
 *             of <code>net.jini.jeri.InboundRequest</code></li>
 *     </ol>
 * </li>
 * </ol>
 */
public class ExportTest_CustomParams extends BJEAbstractTest{

    private static Hashtable methods = new Hashtable();
    private static AccessControlContext acc1, acc2;
    private static ClassLoader cl1, cl2;
    private static Object ir;

    public void run() throws Exception {
        //register a transport listener
        BJETransportListener.registerListener(new Collector());
        //Construct a BasicJeriExporter using an instrumented
        //ServerEndpoint and InvocationLayerFactory
        int listenPort = config.getIntConfigVal("com.sun.jini.test.spec.jeri"
            + ".basicjeriexporter.ConstructorTest.listenPort", 9090);
        BasicJeriExporter exporter = new BasicJeriExporter(new
            BJETestServerEndpoint(TcpServerEndpoint.getInstance(listenPort)),
            new BJETestILFactory(),false,false);
        BJETestServiceImpl service = new BJETestServiceImpl();
        try {
            BJETestService stub = (BJETestService)
                exporter.export(service);
            cl1 = Thread.currentThread().getContextClassLoader();
            stub.doSomething();
            //verify that the listen method was called on the server
            //endpoint class;
            if (methodCalled("listen")){
                //verify that the createInstances method is called
                if (methodCalled("createInvocationDispatcher")){
                    //verify that the dispatch method is called
                    if (methodCalled("dispatch")){
                        //verify the access control context
                        if (acc1.equals(acc2)) {
                            //verify the classloader
                            if (cl1.equals(cl2)){
                                //verify the ServerContext
                                if (ir instanceof InboundRequest) {
                                    //"OK"
                                } else {
                                    throw new TestException(
                                        "The server context is not an"
                                        + "instance of InboundRequest");
                                }
                            } else {
                                log.finer(cl1 + "!=" + cl2);
                                throw new TestException(
                                    "The classloader in effect when the"
                                    + " dispatch method was called is not"
                                    + " equal to the classloader in effect"
                                    + " when the object was exported");
                            }
                        } else {
                            log.finer(acc1 + "!=" + acc2);
                            throw new TestException(
                                "The AccessControl context in effect when"
                                + " the dispatch method was called is not"
                                + " equal to the access control context"
                                + " in effect when the service was exported");
                        }
                    } else {
                        throw new TestException( "The"
                            + " dispatch method was not called on the"
                            + " dispatcher");
                    }
                } else {
                    throw new TestException( "The"
                        + " createDispatcher method was not called on the"
                        + " invocation layer factory");
                }
            } else {
                throw new TestException( "The listen method"
                    + " was not called on the instrumented server endpoint");
            }
        } catch (ExportException e) {
            log.finer("Unexpected exception : " + e.getMessage());
            throw new TestException( "ExportException when"
                + " using exporter with custom parameters");
        } catch (RemoteException e) {
            log.finer("Unexpected Remote exception: " + e.getMessage());
            throw new TestException(
                "Unexpected remote exception when calling method on"
                + " test service: " + e.getMessage());
        }
    }

    private boolean methodCalled(String methodName) {
        Iterator it = methods.keySet().iterator();
        while (it.hasNext()) {
            Method m = (Method) it.next();
            if (m.getName().compareTo(methodName)==0){
                return true;
            }
        }
        return false;
    }

    /**
     * Helper class that server as a transport listener
     */
    public static class Collector implements TransportListener {
        public void called (Method m, Object obj, Object[]args) {
            methods.put(m,m);
            if (m.getName().compareTo("createInvocationDispatcher")==0){
                acc1= (AccessControlContext) args[2];
            }
            if (m.getName().compareTo("dispatch")==0){
                acc2 = (AccessControlContext) args[2];
                cl2 = (ClassLoader) args[3];
                ir = args[1];
            }
        }
    }

}
