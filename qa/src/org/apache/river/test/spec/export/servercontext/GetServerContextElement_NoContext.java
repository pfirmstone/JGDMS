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
package org.apache.river.test.spec.export.servercontext;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;

// java.util
import java.util.logging.Level;

// davis packages
import net.jini.export.ServerContext;

// java.rmi
import java.rmi.server.ServerNotActiveException;

// Server Context Element
import org.apache.river.test.spec.export.util.FakeType;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the
 *   {@link net.jini.export.ServerContext#getServerContextElement(Class)}
 *   method.
 *   getServerContextElement(Class) returns the first element in the current
 *   server context collection (obtained by calling
 *   {@link net.jini.export.ServerContext#getServerContext()}) that is an
 *   instance of the given type.
 *   Parameters:
 *     type - the type of the element
 *   Throws:
 *     {@link java.rmi.server.ServerNotActiveException} - if no server context
 *     is set for the current thread
 *
 * Infrastructure:
 *     - {@link GetServerContextElement_NoContext}
 *         performs actions
 *     - src/manifest/qa1-export-servercontext-tests/null-context/
 *       META-INF/services/net.jini.export.ServerContext$Spi
 *         - is visible to the system class loader as a .jar file named
 *           qa1-export-servercontext-tests-null.jar,
 *         - contains 1 line:
 *             org.apache.river.test.spec.export.util.NullServerContext
 *     - {@link org.apache.river.test.spec.export.util.NullServerContext}
 *         server context provider that implements
 *         {@link net.jini.export.ServerContext.Spi} interface; 
 *         {@link org.apache.river.test.spec.export.util.NullServerContext#getServerContext()}
 *         method returns null.
 *
 * Actions:
 *   Test performs the following steps:
 *     - checking that there is no server context provider implementing
 *       {@link net.jini.export.ServerContext.Spi} interface whose
 *       getServerContext() method returns non-null value;
 *     - invoking getServerContextElement(FakeType.class) method when:
 *       - no context is set for the current thread,
 *       - there are no providers implementing ServerContext.Spi interface
 *         whose getServerContext() method returns non-null value;
 *     - verifying that ServerNotActiveException is thrown.
 *
 * </pre>
 */
public class GetServerContextElement_NoContext
        extends GetServerContext_NoContext {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {

        /* Try to get server context element */
        try {
            logger.log(Level.FINE,
                    "+++++ invoking ServerContext."
                    + "getServerContextElement(FakeType.class)");
            Object retCnxtElement =
                    ServerContext.getServerContextElement(FakeType.class);
            logger.log(Level.FINE,
                    "returned context element: " + retCnxtElement.toString());
            throw new TestException(
                    "" + " test failed:: "
                    + "No exception has been thrown");
        } catch (ServerNotActiveException e) {
            logger.log(Level.FINE,
                    "ServerContext.getServerContextElement(FakeType.class) has"
                    + " thrown: " + e);
        } catch (Exception e) {
            throw new TestException(
                    "" + " test failed:: "
                    + "ServerContext.getServerContextElement(FakeType.class) has"
                    + " thrown: " + e);
        }
        return;
    }
}
