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

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.jini.io.MarshalledInstance;
import java.rmi.Remote;
import java.rmi.RemoteException;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;
import net.jini.security.proxytrust.TrustEquivalence;
import net.jini.jeri.BasicInvocationHandler;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;


import org.apache.river.test.impl.end2end.jssewrapper.Bridge;

/**
 * An implementation of the smart proxy for the remote service.
 */
@AtomicSerial
final class SmartProxy implements SmartInterface,
                                  RemoteMethodControl,
                                  Serializable,
                                  Constants
{
    /** The methodConstraints which should be assigned to this proxy */
    private static MethodConstraints methodConstraints;

    /** The underlying RMI stub used by this smart proxy */
    private Remote remoteProxy;

    /** The <code>TestCoordinator</code> for this test run */
    private transient TestCoordinator coordinator;

    /**
     * Obtain the method constrains to be used for the export. Clients
     * also call this method to verify that the constraints passed
     * in the proxy are correct.
     *
     * The default constraints are arbitrarily set to
     * reqs={ClientAuthentication.YES, Confidentiality.YES} prefs={}.
     * When constructing the method constraints array, the vAuthConf
     * method is skipped so that it will use the default constraints.<p>
     *
     * Constraints for the method <code>multi</code> are set with the
     * type field wildcarded.<p>
     *
     * Constraints for <code>getProxyVerifier</code> , <code>unexport</code>,
     * and <code>callAfterUnexport</code> are set to
     * InvocationConstraints.EMPTY to avoid constraint conflicts
     *
     * The method constraints are always the same, so the value is cached.
     * The entire body of this method needs to be synchronized, so it's
     * simplest to just synchronize the method.
     *
     * @return the method constraints used to export the service
     */
    synchronized static MethodConstraints getMethodConstraints() {
        if (methodConstraints == null) {
        ArrayList mcArray = new ArrayList();
        TestMethod[] methods =
                    TestMethod.getDeclaredMethods(ConstraintsInterface.class);
        BasicMethodConstraints.MethodDesc desc = null;
        TestMethod multiMethod = null;
        InvocationConstraints constraints;
        for (int i=0; i<methods.length; i++) {
        if (methods[i].getName().equals("vAuthConf")) {
            continue;
        }
        if (methods[i].getName().equals("multi")) {
            multiMethod = methods[i];
            continue;
        }
        constraints = methods[i].parseConstraints();
        Class[] types = methods[i].getParameterTypes();
        desc = new BasicMethodConstraints.MethodDesc(
                            methods[i].getName(),
                            types,
                            constraints);
        mcArray.add(desc);
        }

        /*
             * no constraints for the getProxyVerifier, unexport, or
         * callAfterUnexport methods. This is done to ensure that
         * these calls will be performed regardless of client
         * constraints (assuming the client's constraints don't conflict)
         */
        mcArray.add(
            new BasicMethodConstraints.MethodDesc(
                                      "getProxyVerifier",
                       new Class[]{},
                       InvocationConstraints.EMPTY));
        mcArray.add(
            new BasicMethodConstraints.MethodDesc(
                                      "unexport",
                       new Class[]{},
                       InvocationConstraints.EMPTY));
        mcArray.add(
            new BasicMethodConstraints.MethodDesc(
                                      "callAfterUnexport",
                       new Class[]{},
                       InvocationConstraints.EMPTY));

        /*
         * The multi descriptor must follow all of the more qualified
         * entries. Need to test for null because sometimes when
         * debugging the multi methods are removed from the interface
         */
        if (multiMethod != null) {
        constraints = multiMethod.parseConstraints();
        mcArray.add(
            new BasicMethodConstraints.MethodDesc("multi",
                                   constraints));
        }

        /* default constraints must be dead last */
        InvocationConstraints defaultConstraints = new InvocationConstraints(
                              new InvocationConstraint[]{ClientAuthentication.YES,
                                               Confidentiality.YES},
                              null);
        mcArray.add(
            new BasicMethodConstraints.MethodDesc(defaultConstraints));
        BasicMethodConstraints.MethodDesc[] mArray =
            new BasicMethodConstraints.MethodDesc[mcArray.size()];
        mArray = (BasicMethodConstraints.MethodDesc[])
             mcArray.toArray(mArray);
        methodConstraints = new BasicMethodConstraints(mArray);
    }
    return methodConstraints;
    }

    /**
     * Construct the smart proxy.
     *
     * @param remoteProxy the underlying RMI stub for this proxy
     */
    SmartProxy(Remote remoteProxy, TestCoordinator coordinator) {
    this.remoteProxy = remoteProxy;
    this.coordinator = coordinator;
    }
    
    SmartProxy(GetArg arg) throws IOException, ClassNotFoundException{
        this(arg.get("remoteProxy", null, Remote.class), null);
    }

    /* Inherit javadoc */
    public Object invoke(TestMethod method,Object[] args)
          throws IllegalAccessException,
             IllegalArgumentException,
             InvocationTargetException,
             RemoteException
    {
    return method.invoke(remoteProxy, args);
    }

    /* inherit javadoc */
    public void unexport() throws RemoteException {
    ((CoreInterface)remoteProxy).unexport();
    }

    /* inherit javadoc */
    public void callAfterUnexport() throws RemoteException {
    ((CoreInterface)remoteProxy).callAfterUnexport();
    }

    /* inherit javadoc */
    public SmartInterface newProxy(TestCoordinator coordinator) {
    SmartInterface si = null;
        try {
            MarshalledInstance m = ((CoreInterface)remoteProxy).newProxy();
        si = (SmartInterface)m.get(false);
            si.setCoordinator(coordinator);
    } catch (Exception e) {
        InstanceCarrier ic =
                    (InstanceCarrier) Bridge.writeCallbackLocal.get();
        if (ic == null) {
        ic = coordinator.getDefaultInstanceCarrier();
        }
        Logger logger = ic.getInstance().getLogger();
        logger.log(ALWAYS,"Exception while obtaining new proxy");
        logger.log(ALWAYS,e);
    }
    return si;
    }

    /**
     * Set the <code>TestCoordinator</code> for this run. A setter is
     * needed because the <code>TestCoordinator</code> may not be
     * serializable.
     *
     * @param coordinator the <code>TestCoordinator</code>
     */
    public void setCoordinator(TestCoordinator coordinator) {
    this.coordinator = coordinator;
    }

    /**
     * Test whether this proxy is equal to the given object
     *
     * @param obj the object to compare
     * @return <code>true</code> if the two object are equal
     */
    public boolean equals(Object obj) {
    return (obj instanceof SmartProxy)
               && remoteProxy.equals(((SmartProxy) obj).remoteProxy);
    }

    /**
     * A passthrough call to the underlying <code>RemoteMethodControl</code> stub
     */
    public MethodConstraints getConstraints() {
    return ((RemoteMethodControl) remoteProxy).getConstraints();
    }

    /**
     * A passthrough call to the underlying <code>RemoteMethodControl</code> stub
     */
    public MethodConstraints getServerConstraints() throws RemoteException {
        BasicInvocationHandler suiHandler =
            (BasicInvocationHandler)
            Proxy.getInvocationHandler(remoteProxy);
        return suiHandler.getServerConstraints();
    }

    /**
     * A passthrough call to the underlying <code>RemoteMethodControl</code> stub
    public Subject getServerSubject() throws RemoteException {
    return ((RemoteMethodControl) remoteProxy).getServerSubject();
    }*/;

    /**
     * A passthrough call to the underlying <code>RemoteMethodControl</code> stub
     */
    public RemoteMethodControl setConstraints(MethodConstraints constraints) {
    RemoteMethodControl newStub = ((RemoteMethodControl) remoteProxy).
                                         setConstraints(constraints);
    return new SmartProxy((ConstraintsInterface) newStub, coordinator);
    }

    /**
     * Return the bootstrap proxy for this smart proxy. This method is called
     * when the client calls Security.verifyObjectTrust to verify trust in
     * the smart proxy. The bootstrap returned is the underlying RMI stub,
     * which will be verified as trusted when verifyObjectTrust is called
     * recursively, since RMI stubs can be trusted by inspection. The
     * remoteProxy must implement a getProxyVerifier method as defined by
     * the ProxyTrust interface.
     */
    private ProxyTrustIterator getProxyTrustIterator() {
    return new SingletonProxyTrustIterator(remoteProxy);
    }

    @AtomicSerial
    static class Verifier implements TrustVerifier, Serializable {
        private static final long serialVersionUID = 1L;
    private RemoteMethodControl remoteProxy;

    Verifier(SmartInterface smartProxy) {
        remoteProxy =
        (RemoteMethodControl) ((SmartProxy) smartProxy).remoteProxy;
    }
    
    Verifier(GetArg arg) throws IOException, ClassNotFoundException{
        this(arg.get("remoteProxy", null, RemoteMethodControl.class));
    }
    
    private Verifier(RemoteMethodControl remoteProxy){
        this.remoteProxy = remoteProxy;
    }

    public boolean isTrustedObject(Object obj, TrustVerifier.Context ctx)
        throws RemoteException
    {
        if (!(obj instanceof SmartProxy)) {
        return false;
        }
        RemoteMethodControl oproxy =
        (RemoteMethodControl) ((SmartProxy) obj).remoteProxy;
        MethodConstraints mc = oproxy.getConstraints();
        TrustEquivalence trusted =
        (TrustEquivalence) remoteProxy.setConstraints(mc);
        return trusted.checkTrustEquivalence(oproxy);
    }
    }
}
