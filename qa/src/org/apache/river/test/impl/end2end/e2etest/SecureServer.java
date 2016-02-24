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

package org.apache.river.test.impl.end2end.e2etest;

/* JAAS imports */
import org.apache.river.test.impl.end2end.jssewrapper.Bridge;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.lang.reflect.Proxy;
import java.rmi.MarshalledObject;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.security.proxytrust.ProxyTrust;

/**
 * A secure RMI server. This class does not provide the server implementation.
 * Rather, the server is created from the Proxy class, and the exported
 * proxy class is embedded in a smart proxy.
 */

public class SecureServer implements Constants {

    /** cached method constraints for the service */
    private MethodConstraints methodConstraints = null;

    /** the <code>TestCoordinator</code> for this test run */
    private TestCoordinator coordinator;

    /** The service, to prevent it from being GC'ed */
    private Remote service;

    /** The stub, for use in serviceProxy */
    private Remote stub;

    /**
     * Construct an instance of a SecureServer
     */
    SecureServer(TestCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Obtain the initial service proxy in the context of the server subject.
     * The proxy is (arbitrarily) created with the flags set to AS_IS.
     *
     * @return a serialized copy of service proxy
     */

    MarshalledInstance getProxy() {
        MarshalledInstance iface = null;
        try {
            iface =
            Subject.doAsPrivileged(
                ProviderManager.getSubjectProvider().getServerSubject(),
                new PrivilegedExceptionAction<MarshalledInstance>() {
                    public MarshalledInstance run() throws RemoteException,
                        NotBoundException {
                            return serverProxy();
                        }
                },
                null);

        /*
         * when this method is invoked as the result of a remote call,
         * the client should have set up the bridge, and the clients
         * logger is used to report problems. Otherwise, it is assumed
         * that the failure occured during initial setup and a TestException
         * is thrown which is expected to terminate the test
         */
        } catch (PrivilegedActionException e) {
            InstanceCarrier ic =
                (InstanceCarrier) Bridge.readCallbackLocal.get();
            if (ic != null) {
                Logger logger = ic.getInstance().getLogger();
                logger.log(ALWAYS,"Unexpected exception "
                    + "getting server proxy");
                logger.log(ALWAYS,e);
            } else {
                throw new TestException("Unexpected exception "
                    + "getting server proxy",e.getException());
            }
        }
        return iface;
    }

    /**
     * Returns a serialized Remote stub for an exported
     * <code>SecureServer</code> object.
     *
     * @return a MarshalledInstance containing the serialized stub
     */
    MarshalledInstance serverProxy() throws RemoteException, NotBoundException
    {
        MarshalledInstance obj = null;
        ServiceHandler handler = new ServiceHandler(this, coordinator);
        BasicJeriExporter exporter = getExporter();
        handler.setExporter(exporter); // needed so it can do the unexport
        if (exporter != null) {
	    if (service == null) {
		service = (Remote) createProxy(handler);
		stub = exporter.export(service);
	    }
            SmartProxy sp = new SmartProxy(stub, coordinator);
            try {
                obj = new MarshalledInstance(sp);
            } catch (IOException e) {
                InstanceCarrier ic =
                    (InstanceCarrier) Bridge.readCallbackLocal.get();
                if (ic != null) {
                    Logger logger = ic.getInstance().getLogger();
                    logger.log(ALWAYS, "IOException while "
                        + "marshalling proxy");
                    logger.log(ALWAYS, e);
                } else {
                    throw new TestException("IOException while "
                        + "marshalling proxy",e);
                }
            }
        }
        return obj;
    }

    /**
     * create a proxy that implements the server side of the remote interface.
     *
     * @return an instance of a proxy which implements ConstraintsInterface,
     *         CoreInterface, and ProxyTrust
     */
    private Object createProxy(InvocationHandler handler) {
        return Proxy.newProxyInstance(SecureServer.class.getClassLoader(),
            new Class[]{ConstraintsInterface.class, CoreInterface.class,
                ProxyTrust.class},handler);
    }

    /**
     * build an export descriptor for the remote interface.<p>
     *
     * The default constraints are arbitrarily set to
     * reqs={ClientAuthentication.YES, Confidentiality.YES} prefs={}.
     * When constructing the method constraints array, the vAuthConf
     * method is skipped so that it will use the default constraints.<p>
     *
     * Constraints for the method <code>multi</code> are set with the
     * type field wildcarded.<p>
     *
     * Constraints for <code>getProxyVerifier</code> are set to
     * InvocationConstraints.EMPTY to avoid constraint conflicts when
     * obtaining the trust verifier.<p>
     */
    private BasicJeriExporter getExporter() {
        BasicILFactory factory =
            new BasicILFactory(SmartProxy.getMethodConstraints(),
                     SecureServerPermission.class);
        BasicJeriExporter exporter = null;
        try {
            exporter = new BasicJeriExporter(
        ProviderManager.getEndpoint(),
                factory,
                false, // DGC disabled
                true,  // keepalive
                null); // objID
            /*
             * when this method is invoked as the result of a remote call,
             * the client should have set up the bridge, and the clients
             * logger is used to report problems. Otherwise, it is assumed
             * that the failure occured during initial setup and a TestException
             * is thrown which is expected to terminate the test
             */
        } catch (Exception e) {
            InstanceCarrier ic;
            ic = (InstanceCarrier) Bridge.readCallbackLocal.get();
            if (ic != null) {
                Logger logger = ic.getInstance().getLogger();
                logger.log(ALWAYS, "Unexpected exception thrown while "
                    + "generating the exporter");
                logger.log(ALWAYS,e);
            } else {
                e.printStackTrace();
                throw new TestException("Unexpected exception thrown "
                    + "while generating the exporter " + e,e);
            }
        }
        return exporter;
    }
}
