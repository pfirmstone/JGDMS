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

package com.sun.jini.test.impl.end2end.e2etest;

/* Java imports */
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.jeri.BasicJeriExporter;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;

import java.rmi.server.ServerNotActiveException;

import net.jini.io.context.ClientSubject;
import net.jini.export.ServerContext;

import com.sun.jini.test.impl.end2end.jssewrapper.Bridge;

/**
 * The invocation handler for the interface. This provides the
 * server side implementation of the remote service.
 */
class ServiceHandler implements InvocationHandler, Constants {

    /** The <code>TestCoordinator</code> for this test run */
    private TestCoordinator coordinator;

    /** The service associated with this invocation handler */
    private SecureServer service;

    /** the secure exporter associated with this invocation handler */
    private BasicJeriExporter exporter;

    /**
     * Creates an InvocationHandler for the Remote service.
     *
     * @param service the SecureServer instance associated with this handler
     */
    ServiceHandler(SecureServer service, TestCoordinator coordinator) {
        this.service = service;
        this.coordinator = coordinator;
    }

    /**
     * The method called to execute the method defined by the remote
     * interfaces implemented by this object.
     *
     * If the method name is <code>unexport</code>, the proxy is unexported.<p>
     *
     * If the method name is <code>newProxy</code>, the servers
     * <code>newProxy()</code> method is called. The MarshalledObject returned
     * by <code>newProxy</code> is returned to the invoker. This proxy is
     * always obtained in the context of the server subject.<p>
     *
     * if the method name is getProxyVerifier, the invocation handler
     * returns the trust verifier for the SmartProxy, which is
     * an instance of <code>CheckedTrustVerifier</code> containing a
     * reference to the SmartProxy. The SmartProxy is obtained
     * from the map maintained in SecureService, since the Remote
     * stub is generated code which contains no reference to its container.
     * Since the returned SmartProxy does not implement ProxyTrust.Verifier,
     * the equalsIgnoreConstraints path will be taken in Security.
     * Because the returned proxy is a copy of the service side
     * object, no constraints will have been set on this copy.<p>
     *
     * Otherwise, the handler inspects the first character of the method
     * name and returns the appropriate object based on the method
     * naming convention used for methods defined in ConstraintsInterface.
     * Methods in this interface begin with the following initial characters:
     *
     *  o, w, p, v, m
     *
     * Methods defined by other interfaces implemented by this object
     * should be tested for before the ConstraintsInterface methods
     * are handled in case they begin with one of these letters.
     */
    public Object invoke(Object proxy, Method m, Object[] args)
        throws Throwable
    {
        TestMethod method = new TestMethod(m);
        /* get references from the calling client instance */
        InstanceCarrier ic = (InstanceCarrier) Bridge.readCallbackLocal.get();
        if (ic == null) {
            ic = coordinator.getDefaultInstanceCarrier();
        }
        TestClient client = ic.getInstance();
        Logger logger = client.getLogger();
        SmartInterface smartProxy = client.getUnconstrainedProxy();
        if (client instanceof SecureClient) {
            try{
                ClientSubject clientSubject = (ClientSubject) ServerContext
                    .getServerContextElement(ClientSubject.class);
                if (clientSubject!=null) {
                    Subject subject = clientSubject.getClientSubject();
                   ((SecureClient)client).setTestClientSubject(subject);
                }

            } catch (ServerNotActiveException e) {
                e.printStackTrace();
            }
        }

        /* use an extended set of if..else blocks to ensure that
         * a match to methods from CoreInterface cannot also fall
         * through to match the ConstraintsInterface naming convention
         */
        if (method.getName().equals("unexport")) {
            unexport();
        } else if (method.getName().equals("newProxy")) {
            try {
                return Subject.doAsPrivileged(
                    ProviderManager.getSubjectProvider().getServerSubject(),
                    new PrivilegedExceptionAction() {
                        public Object run() throws RemoteException,
                            NotBoundException {
                            return service.serverProxy();
                        }
                    }, null);
            } catch (PrivilegedActionException e) {
                logger.log(ALWAYS,"Unexpected exception building proxy" + e) ;
                return null;
            }
        } else if (method.getName().equals("getProxyVerifier")) {
            return new SmartProxy.Verifier(smartProxy);
        } else if (method.getName().startsWith("p")) {
            return new Integer(0);
        } else if (method.getName().startsWith("w")) {
            return new WriterObject(coordinator.getDefaultInstanceCarrier());
        } else if (method.getName().startsWith("o")) {
            return new PlainObject();
        } else if (method.getName().equals("equals")) {
            if (!(args[0] instanceof Proxy)) return new Boolean(false);
            InvocationHandler h = Proxy.getInvocationHandler(args[0]);
            if (!(h instanceof ServiceHandler)) return new Boolean(false);
            ServiceHandler sh = (ServiceHandler) h;
            return new Boolean(sh.service.equals(service)
                && sh.exporter.equals(exporter));
        } else if (method.getName().equals("hashCode")) {
            return new Integer(service.hashCode() ^ exporter.hashCode());
        } else if (method.getName().equals("toString")) {
            return service.toString() + "#" + exporter.toString();
        }
        return null;
    }

    void setExporter(BasicJeriExporter exporter) {
        this.exporter = exporter;
    }

    /**
     * unexports this service. This unexport is done forcibly since
     * it implements the action of the unexport Remote method call.
     * The exporter to use is derived from the key obtained from the
     * given proxy.
     */
    private void unexport() throws RemoteException {
        if (exporter == null) {
            throw new TestException("exporter missing in invocation handler",
                null);
        }
        if (!exporter.unexport(true)) {
            InstanceCarrier ic;
            ic = (InstanceCarrier) Bridge.readCallbackLocal.get();
            if (ic == null) {
                ic = coordinator.getDefaultInstanceCarrier();
            }
            TestClient client = ic.getInstance();
            client.logFailure("Attempt to unexport returned false");
        }
    }
}
