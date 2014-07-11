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

/* JAAS imports */
import javax.security.auth.Subject;

/* Java imports */
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.MarshalledObject;
import java.rmi.MarshalException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import net.jini.io.UnsupportedConstraintException;

import net.jini.jeri.ssl.ConfidentialityStrength;

import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.security.Security;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.jeri.connection.OutboundRequestHandle;
import net.jini.jeri.connection.Connection;
import net.jini.jeri.connection.ConnectionEndpoint;
import net.jini.jeri.connection.ServerConnection;

import java.security.AccessControlException;
import java.security.NoSuchProviderException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/* JSSE imports */
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/* Wrapper imports */
import com.sun.jini.test.impl.end2end.jssewrapper.Bridge;
import com.sun.jini.test.impl.end2end.jssewrapper.ReadCallback;
import com.sun.jini.test.impl.end2end.jssewrapper.WriteCallback;
import com.sun.jini.test.impl.end2end.jssewrapper.EndpointWrapper;

/*
 * A client that uses a proxy exported by <code>SecureServer</code>
 * to execute the methods defined by <code>ConstraintsInterface</code>
 * and <code>CoreInterface</code>, and verifies the approriate security
 * handling of these method calls.
 */
class SecureClient implements Constants, TestClient, Runnable {

    /** the counter for generating SecureClient map keys */
    private static int instanceCounter = 0;

    /** the map for locating SecureClient instances */
    private static Map instanceMap = new HashMap();

    /**
     * counter for number of live threads.
     */
    private static int liveThreadCount = 0;

    /** lock to synchronize mods to liveThreadCount */
    private static Object liveThreadLock = new Object();

    /**
     * the seed for the random number generator used when the coverage
     * options is active. It is static so that every client initializes
     * its random number generator with the same value, necessary if
     * the test is to be reproducable
     */
    private static long seed = 0;

    /** the test coordinator */
    private TestCoordinator coordinator;

    /** the test display object */
    private UserInterface ui;

    /** synchronization object for GUI start function */
    private Object goMonitor = new Object();

    /** if true, abort test on first failure */
    private boolean abortOnFail =
        System.getProperty("end2end.abortOnFail") != null;

    /**
     * the coverage factor (e.g 50 => perform one test in 50 * 50,
     * approximately)
     */
    private int coverage;

    /** the currently executing method. */
    private TestMethod currentMethod;

    /** the total number of tests to be run (plus unexport and call after) */
    private int totalTests;

    /**
     * the constraints the client has imposed on the proxy. This reference
     * should be valid at all times (it is set at top of loop).
     */
    private InvocationConstraints clientConstraints;

    /**
     * the combined constraints. This reference is only
     * valid after the servers readCallData method has been invoked.
     */
    private InvocationConstraints combinedConstraints;

    /**
     * the authenticated client subject constraints. This reference is only
     * valid after the servers readCallData method has been invoked.
     */
    private Subject clientSubject;

    /**
     * the currently active cipher suite. This reference is only
     * valid after the writeCallback method has been invoked.
     */
    private CipherSuite cipherSuite;

    /** the instance key of this instance */
    private Integer myKey;

    /** the first picked proxy obtained from SecureServer */
    private MarshalledObject pickledStub;

    /** the proxy obtained by unmarshalling pickled server stubs */
    private SmartInterface origIface;

    /** the proxy obtained by calls to origIface.newProxy() */
    private SmartInterface unconstrainedIface;

    /** the proxy used for making remote calls after applying constraints */
    private SmartInterface iface;

    /** the random number generator for selecting tests when coverage > 1 */
    private Random random;

    private static SubjectProvider subjectProvider =
        ProviderManager.getSubjectProvider();

    /**
     * The set of constraints to be applied by the client. Note that
     * if any constraints are added to this list, the call validation
     * methods may need to be modified. The <code>sminp</code> is a
     * ConstraintAlternatives containing ServerMinPrincipals constraints
     * for all of the server principals. ServerMinPrincipal cannot be
     * used as a constraint directly because the JSSE provider has a
     * limitation that the client cannot select which subjects the
     * server authenticates as.
     */
    private static InvocationConstraint[] constraintsArray = null;

    public static void initialize() {
        if (ProviderManager.isKerberosProvider()) {
            constraintsArray = new InvocationConstraint[] {
                ServerAuthentication.YES,
                ClientAuthentication.YES,
                Confidentiality.YES,
                Confidentiality.NO,
                Integrity.YES,
                Integrity.YES,
                Delegation.YES,
                Delegation.NO,
                subjectProvider.getServerMainPrincipal()
            };
        } else {
            constraintsArray = new InvocationConstraint[] {
                ServerAuthentication.YES,
                ClientAuthentication.NO,
                Confidentiality.YES,
                Confidentiality.NO,
                Integrity.YES,
                Integrity.NO,
                Delegation.YES,
                Delegation.NO,
                subjectProvider.getServerMinPrincipal()
            };
        }
    }

    /** position in array of <code>ServerAuthentication.YES</code> */
    private static final int SERVERAUTH   = 1 << 0;

    /** position in array of <code>ClientAuthentication.NO</code> */
    private static final int CLIENTAUTH = 1 << 1;

    /** position in array of <code>Conficentiality.YES</code> */
    private static final int CONFYES      = 1 << 2;

    /** position in array of <code>Conficentiality.YES</code> */
    private static final int CONFNO       = 1 << 3;

    /** position in array of <code>Integrity.YES</code> */
    private static final int INTEGYES     = 1 << 4;

    /** position in array of <code>Integrity.YES</code> */
    private static final int INTEGNO      = 1 << 5;

    /** position in array of <code>Delegation.YES</code> */
    private static final int DELEGYES      = 1 << 6;

    /** position in array of <code>Delegation.YES</code> */
    private static final int DELEGNO      = 1 << 7;

    /** position in array of <code>ServerMinPrincipal(serverDSA)</code> */
    private static final int SMINP	  = 1 << 8;

    /** bit mask representing conflicting Confidentiality constraints */
    private final int BADCONF = CONFYES | CONFNO;

    /** bit mask representing conflicting Integrity constraints */
    private final int BADINTEG = INTEGYES | INTEGNO;

    /** bit mask representing conflicting Delegation constraints */
    private final int BADDELEG = DELEGYES | DELEGNO;

    private InvocationConstraint[] preferencesArray = {
        ConfidentialityStrength.STRONG,
        ConfidentialityStrength.WEAK,
        subjectProvider.getClientMaxPrincipal()
    };

    /** bit mask representing ConfidentialityStrength.STRONG */
    private final int STRONG = 1 << 0 ;

    /** bit mask representing ConfidentialityStrength.WEAK */
    private final int WEAK = 1 << 1;

    /** bit mask representing conflicting strength constraints */
    private final int BADSTRENGTH = STRONG | WEAK;

    /** the number of tests executed */
    private int testCount;

    /** the index of the test being executed */
    private int testNumber;

    /** the thread number */
    private int threadNumber;

    /** the failure count */
    private int failureCount = 0;

    /** the array of methods defined in <code>ConstraintsInterface</code> */
    private final TestMethod[] methods =
        TestMethod.getDeclaredMethods(ConstraintsInterface.class);

    /** the test logger for this instance */
    private Logger logger;

    /**
     * the start of the range of tests to execute - causes expensive setup
     * to be skipped if individual tests are to be run
     */
    private int startCount = 0;

    /**
     * the end of the range of tests to execute - causes the run to
     * terminate after the last test when individual tests are run
     */
    private int endCount = -1;

    /**
     * Get the TestClient instance associated with the given key.
     * This method is called from the server to obtain the client
     * instance which performed a remote call. This makes it possible,
     * among other things, for the server to append to the log instance
     * owned by the calling client. The serialized key is provided
     * to the server as part of the wrapper/bridge callback implementation.
     *
     * @param key the key associated with the TestClient instance.
     * @return the associated TestClient instance
     */
    static TestClient getInstance(Integer key) {
        synchronized (instanceMap) {
            return (TestClient) instanceMap.get(key);
        }
    }

    /**
     * Creates a <code>SecureClient</code>.
     *
     * @param coordinator the test coordinator
     * @param pickledStub the serialized proxy exported by the server
     */
    SecureClient(TestCoordinator coordinator, MarshalledObject pickledStub) {
        this.coordinator = coordinator;
        this.pickledStub = pickledStub;
        totalTests = computeTotalTestCount();
        logger = new Logger();
        synchronized (instanceMap) {
            myKey = new Integer(instanceCounter++);
            instanceMap.put(myKey,this);
        }
        Set testSet = coordinator.getTests();
        Iterator it = testSet.iterator();
        while (it.hasNext()) {
            String testName = (String) it.next();
            try {
                int testNum = Integer.parseInt(testName);
                if (startCount == 0) {
                    startCount = testNum;
                    endCount = testNum;
                } else {
                    startCount = Math.min(startCount, testNum);
                    endCount = Math.max(endCount, testNum);
                }
            } catch (NumberFormatException e) {
            }
        }
        coverage = Integer.getInteger("end2end.coverage", 1).intValue();
        if (coverage > 1) {
            synchronized (this.getClass()) {
                if (seed == 0) {
                    seed = Long.getLong("end2end.seed", 0).longValue();
                    if (seed == 0) {
                        seed = new Date().getTime();
                    }
                    logger.log(ALWAYS, "Partial coverage factor: " + coverage);
                    logger.log(ALWAYS, "Random number seed: " + seed);
                    logger.writeLog();
                }
            }
            random = new Random(seed);
        }
    }

    /**
     * Method called to set the client subject to verify when the
     * server is executing a remote call.
     */
    public void setTestClientSubject(Subject subject) {
        clientSubject = subject;
    }

    /**
     * deserialize an object. Any exceptions normally generated by
     * the MarshalledObject.get method are caught and logged.
     *
     * @params mobj the serialized object
     * @return the deserialized object, or null if the object could not be
     *         deserialized.
     */
    private Object unpickle(MarshalledObject mobj) {
        Object obj = null;
        try {
            obj = mobj.get();
        } catch (IOException e) {
            logger.log(ALWAYS, "I/O error unmarshalling stub");
            logger.log(ALWAYS, e);
        } catch (ClassNotFoundException e) {
            logger.log(ALWAYS, "class not found while unmarshalling stub");
            logger.log(ALWAYS, e);
        }
        return obj;
    }

    /**
     * Start the client side of the test, running in the context of
     * the client subject. An InstancePasser is installed
     * in the bridge for this thread, allowing access to the logger
     * by objects which don't have direct references to this SecureClient
     * object. If a different object is temporarily installed in the bridge,
     * it is the responsibility of the installer to restore the InstancePasser.
     *
     * This method increments a thread counter. This is needed at the
     * end of the test; the last thread to finish has the responsibility
     * of unexporting the service.
     */
    public void run() {
        synchronized (liveThreadLock) {
            liveThreadCount++;
            threadNumber = liveThreadCount;
        }
        InstancePasser ip = new InstancePasser(this);
        Bridge.writeCallbackLocal.set(ip);
        try {
            Subject.doAsPrivileged( subjectProvider.getClientSubject(),
                new PrivilegedExceptionAction() {
                    public Object run() throws RemoteException,
                        NotBoundException {
                        logger.log(CLIENTSUBJECT, "Running SecureClient "
                            + "with subject " + subjectProvider.getSubject()) ;
                        origIface = (SmartInterface) unpickle(pickledStub);
                        if (origIface != null) {
                            runTest();
                        }
                        logger.writeLog(); // in case of lingering output
                        return null;
                    }
                }, null);
        } catch (PrivilegedActionException e) {
            logger.log(ALWAYS, "Unexpected exception running client") ;
            logger.log(ALWAYS, e) ;
            logger.writeLog();
            return;
        }
    }

    /**
     * Run the body of the test. For each possible value of the marshal
     * control value, request a new proxy from the service, verify
     * that the method constraints imposed by the server are retained
     * properly in the proxy, perform all method calls using all legal
     * combinations of client constraints. If this is the last thead
     * alive, unexport the original proxy.
     */
    void runTest() {
        testCount = 0;
        testNumber = 0;
        /*
         * get a new proxy.
         */
        unconstrainedIface = origIface.newProxy(coordinator);
        if (unconstrainedIface == null) {
            logger.writeLog(); // just in case the buffer is not empty
            throw new TestException("new proxy obtained "
                + "from server is null", null);
        }
        checkServerConstraints(unconstrainedIface);

        boolean lastThread = false;
        try {
            /* perform all remote calls for this proxy */
            doAllConstraints(unconstrainedIface);
            synchronized (liveThreadLock) {
                if (--liveThreadCount == 0) lastThread = true;
            }
            if (lastThread) {
                /* if this is the last thread alive, unexport
                the original proxy */
                cleanup(origIface, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        /* write per client statistics, and per test statistics if last */
        Date dateStamp = new Date();
        System.out.println("************************************" +
            "************************************");
        System.out.println(dateStamp + " Summary for thread "
            + threadNumber + " ");
        System.out.println(dateStamp + " Total tests run for thread "
            + threadNumber + ": " + testCount);
        System.out.println(dateStamp + " Number of failures: " + failureCount);
        System.out.println("************************************" +
            "************************************");
        if (lastThread) {
            System.out.println("\nTesting Complete");
        }
        logger.endBoundary();
        logger.writeLog();
        ui.setCallStatus("Client test complete " + new Date());
    }

    /**
     * Clean the client after a proxy is no longer needed. Performs
     * remote calls to the server to request an unexport, and verifies
     * that the unexported proxy can no longer perform remote calls.<p>
     * The behavior of the unexport remote call depends on whether it
     * is the last unexport to be performed. Non 'final' exports
     * complete successfully, but the 'final' export to be done
     * results in a RemoteException being thrown.
     *
     * @param proxy the proxy for the Remote object
     * @para finalUnexport if true, this should be the last unexport
     */
    private void cleanup(SmartInterface proxy, boolean finalUnexport) {
        /* call unexport, and call again to see if it happened */
        testNumber++;
        testCount++;
        writeBoundaryHeader();
        try {
            currentMethod = new TestMethod(
                CoreInterface.class.getMethod("unexport",new Class[]{}));
        } catch (NoSuchMethodException e) {     // should never happen
            logFailure("method unexport() "
                + "not found in CoreInterface");
        }
        CallHandler handler = new UnexportCallHandler(proxy, finalUnexport);
        handler.handleCall(currentMethod);
        logger.endBoundary();
        logger.writeLog();
        checkForPause();
        testNumber++;
        testCount++;
        writeBoundaryHeader();
        try {
            currentMethod = new TestMethod(CoreInterface.class.getMethod(
                "callAfterUnexport", new Class[]{}));
        } catch (NoSuchMethodException e) {  // should never happen
            logFailure("method unexport() not found in CoreInterface");
        }
        handler = new CallAfterUnexportCallHandler(proxy, finalUnexport);
        handler.handleCall(currentMethod);
        logger.endBoundary();
        logger.writeLog();
        checkForPause();
    }

    /**
     * Ask the <code>UserInterface</code> if a pause is requested and
     * suspend this thread if so.
     */
    private void checkForPause() {
        if (ui.stopAfterCall()) {
            synchronized (goMonitor) {
                try {
                    goMonitor.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * The <code>CallHandler</code> for performing the
     * remote <code>unexport</code> call
     */
    private class UnexportCallHandler extends CallHandler {
        private boolean finalUnexport;
        private SmartInterface proxy;

        /**
         * Construct the <code>CallHandler</code>
         *
         * @param proxy the remote proxy
         * @param finalExport if true, this is the last export call
         */
        UnexportCallHandler(SmartInterface proxy, boolean finalExport) {
            super(SecureClient.this, ui, coordinator);
            this.finalUnexport = finalExport;
            this.proxy = proxy;
        }

        /* inherit javadoc */
        protected void doCall(TestMethod method) throws Exception {
            proxy.unexport();
        }

        /**
         * Validate the success of the call. The
         * call should succeed if this is not the final
         * unexport. The last unexport should throw an EOFException.
         *
         * @param method the unexport <code>Method</code> (unused)
         */
        protected void validateSuccess(TestMethod method) {
            if (finalUnexport) {
                logFailure("Final call to unexport succeeded");
            }
        }

        /**
         * Validate the failure of the remote call.
         *
         * @param method the unexport <code>Method</code> (unused)
         * @param e the exception thrown by the <code>unexport</code> call
         */
        protected void validateException(TestMethod method, Exception e) {
        }
    }

    /**
     * The <code>CallHandler</code> for the <code>callAfterUnexport</code>
     * method call.
     */
    private class CallAfterUnexportCallHandler extends CallHandler {

    private boolean finalUnexport;
        private SmartInterface proxy;

        /**
         * Construct the <code>CallHandler</code>
         *
         * @param proxy the remote proxy
         * @param finalUnexport if true, this was the last unexport performed
         */
        CallAfterUnexportCallHandler(SmartInterface proxy,
            boolean finalUnexport) {
            super(SecureClient.this, ui, coordinator);
            this.finalUnexport = finalUnexport;
            this.proxy = proxy;
        }

        /* inherit javadoc
         * If this is the final unexport case, then it is possible that
         * cached connections will cause one or more calls to throw
         * an UnmarshalException with a nested IOException. It is
         * also possible that a ConnectIOException with a nested
         * IOException is thrown. Therefore, in
         * this case, a loop is performed to make the call a number
         * of times, until the call is successful (should never happen),
         * or some other exception pattern occurs. Note that I don't
         * check whether the nested exception is an IOException.
         */
        protected void doCall(TestMethod method) throws Exception {
            final int LOOPCOUNT = 5;
            RemoteException caughtEx = null;
            if (finalUnexport) {
                for (int i = 0; i < LOOPCOUNT; i++) {
                    try {
                        proxy.callAfterUnexport();
                        return; // should never happen
                    }
                    catch (Exception e) {
                        if (!(e instanceof UnmarshalException)||
                            !(e instanceof ConnectIOException)||
                            !(e instanceof ConnectException)) {
                            throw e;
                        }
                        caughtEx = (RemoteException) e;
                    }
                }
                throw caughtEx;
            } else {
                proxy.callAfterUnexport();
            }
        }

        /**
         * Validate the success of the call. This
         * call should always throw an exception,
         * so reaching this method is a test failure
         *
         * @param method the remote <code>Method</code> (unused)
         */
        protected void validateSuccess(TestMethod method) {
            String finalString = finalUnexport ? "After final unexport, "
                : "After non-final unexport, ";
            logFailure(finalString + "call to callAfterUnexport succeeded");
        }

        /**
         * Validate the failure of the remote call. This
         * call should have thrown a Remote exception
         * so log failure if any other exception was thrown.
         *
         * @param method the remote <code>Method</code> (unused)
         * @param e the exception thrown as a result of the remote call
         */
        protected void validateException(TestMethod method, Exception e) {
            String finalString = finalUnexport ? "After final unexport, "
                : "After non-final unexport, ";
            if (!(e instanceof RemoteException)) {
                logFailure(finalString + "call to callAfterUnexport "
                    + "threw non-Remote exception", e);
            }
        }
    }

    /**
     * Call all methods in ConstraintsInterface, applying all combinations
     * of client constraints and preferences. Client constraints are
     * applied as a mixture of proxy constraints and contextual constraints.
     *
     * @param proxy the proxy of the exported Remote service
     */
    private void doAllConstraints(SmartInterface proxy) {
        logger.writeLog();
        /* loop for all combinations of non-conflicting requirements */
        int l = constraintsArray.length;
        for (int req = 0; req < (1 << l); req++) {
            if (((req & BADCONF) == BADCONF)||((req & BADINTEG) == BADINTEG)
                || ((req & BADDELEG) == BADDELEG)){
                continue;
            }
            /* split constraints between proxy and context */
            int flags = req;
            ArrayList<InvocationConstraint> reqList = new ArrayList<InvocationConstraint>(l);
            for (int i = 0; i < l; i++) {
                if ((flags & 1) != 0) {
                    reqList.add(constraintsArray[i]);
                }
                flags >>= 1;
            }
            /* loop for all combinations of non-conflicting preferences */
            int len = preferencesArray.length;
            for (int pref = 0; pref < (1 << len); pref++) {
                logger.startBoundary("Proxy setup");
                if ((pref & BADSTRENGTH) == BADSTRENGTH) {
                    continue;
                }
                if ((testNumber + methods.length) < startCount) {
                    testNumber += methods.length;
                    continue;
                }
                if (endCount >= 0 && testNumber > endCount) {
                    System.exit(1);
                }
                if (startCount == 0 && (coverage > 1 && random
                    .nextInt(coverage) != 0)) {
                    testNumber++;
                    continue;
                }
                /* split preferences between proxy and context */
                flags = pref;
                ArrayList<InvocationConstraint> prefList = new ArrayList<InvocationConstraint>(len);
                for (int i = 0; i < len; i++) {
                    if ((flags & 1) != 0) {
                        prefList.add(preferencesArray[i]);
                    }
                    flags >>= 1;
                }
                clientConstraints = new InvocationConstraints(reqList, prefList);
                ui.setClientProxyConstraints(clientConstraints);
                BasicMethodConstraints mc =
                    new BasicMethodConstraints(clientConstraints);
                try {
                    currentMethod =
                    new TestMethod(Security.class.getMethod("verifyObjectTrust",
                        new Class[]{ Object.class,
                        ClassLoader.class, Collection.class}));
                } catch (NoSuchMethodException e) {     // should never happen
                    logFailure("method "
                        + "verifyObjectTrust(Object, ClassLoader, "
                        + "Collection) "
                        + "not found in Security");
                }
                try {
                    logger.log(DEBUG,"calling verifyObjectTrust for " + proxy);
                    proxy = (SmartInterface) proxy.setConstraints(mc);
                    Security.verifyObjectTrust(proxy,
                        proxy.getClass().getClassLoader(),
                        Collections.singleton(mc));
                    iface = proxy;
                    if (!validVerifierCall(clientConstraints)) {
                        logFailure("verifyObjectTrust call succeeded "
                            + "inappropriately");
                    }
                } catch (RemoteException e) {
                    iface = null;
                    if (e instanceof ConnectIOException) {
                        if (validVerifierCall(clientConstraints)) {
                            logFailure("verifyObjectTrust call failed "
                                + "inappropriately",e);
                        }
                    } else {
                        logFailure("verifyObjectTrust threw an "
                            + "unexpected Remote Exception", e);
                    }
                } catch (SecurityException e) {
                    if (validVerifierCall(clientConstraints)) {
                        logFailure("verifyObjectTrust threw an "
                            + "unexpected SecurityException", e);
                    }
                }
                logger.endBoundary();
                logger.writeLog();
                /*
                 * verifyObjectTrust may fail differently depending on whether
                 * the proxy is an RMI stub or a smart proxy. This is
                 * because constraints are applied when validating trust
                 * for a non-RMI proxy, which requires contact with a
                 * trusted remote server. However, RMI stubs are verified
                 * by inspection.
                 */
                Object ic = Bridge.writeCallbackLocal.get();
                Bridge.writeCallbackLocal.set(
                    new ClientSideAction(SecureClient.this));
                callAllMethods();
                Bridge.writeCallbackLocal.set(ic);
            }
        }
    }

    /**
     * execute all methods defined in <code>ConstraintsInterface</code>
     * unless restricted by command line input.
     */
    private void callAllMethods() {
        CallHandler callHandler = new ConstraintsCallHandler();
        /*
         * if non-empty, tests contains the set of methods to be
         * called, or the integer test numbers to be executed,
         * obtained from the command line by the <code>TestCoordinator</code>
         */
        Set tests = coordinator.getTests();
        for (int i=0; i<methods.length; i++) {
            checkForPause();
            testNumber++;
            if (endCount >= 0 && testNumber > endCount) {
                System.exit(1);
            }
            ui.setTestCount(testNumber, totalTests) ;
            if (coverage > 1) {
                if (random.nextInt(coverage) != 0) {
                    continue;
                }
            }
            testCount++;
            cipherSuite = null;         // undefine from previous loop
            combinedConstraints = null; // undefine from previous loop
            clientSubject = null;       // undefine from previous loop
            ui.setTestSuite(null);
            ui.setClientSubject(null);
            ui.setCombinedConstraints(null);
            if (iface == null) continue;
            String intString = Integer.toString(testNumber) ;
            if (tests.contains(methods[i].getName())
                || tests.contains(intString)
                || tests.isEmpty()) {
                writeBoundaryHeader();
                logger.log(PROXYCONSTRAINTS, "Client Proxy constraints: "
                    + clientConstraints);
                currentMethod = methods[i];
                checkClientConstraints(currentMethod);
                callHandler.handleCall(currentMethod);
                logger.endBoundary();
                logger.writeLog();
            }
        }
    }

    /**
     * The <code>CallHandler</code> for making calls specified by
     * the <code>ConstraintsInterface</code> interface.
     */
    private class ConstraintsCallHandler extends CallHandler {

        ConstraintsCallHandler() {
            super(SecureClient.this, ui, coordinator);
        }

        /**
         * Create method arguments and call the given method.
         *
         * @param testMethod the <code>Method</code> to call
         */
        protected void doCall(TestMethod testMethod) throws Exception {
            Class[] ptypes = testMethod.getParameterTypes();
            Object[] args = new Object[ptypes.length];
            for (int i=0; i<args.length; i++) {
                if (ptypes[i] == PlainObject.class) {
                    args[i] = new PlainObject();
                }
                /*
                 * Note that the combined constraints passed to ReaderObject
                 * must be computed since the combinedConstraints class
                 * attribute is not valid at the time this method is called.
                 */
                if (ptypes[i] == ReaderObject.class) {
                    InstanceCarrier ic = coordinator
                        .getDefaultInstanceCarrier();
                    args[i] = new ReaderObject(getConstraints(testMethod), ic);
                }
                if (ptypes[i] == int.class) {
                    args[i] = new Integer(0);
                }
            }
            iface.invoke(testMethod, args);
        }

        /**
         * A method call failed. Determine whether failure was expected.
         * If unexpected, log as a failure.
         *
         * @param method the <code>Method</code> called
         * @param e the exception thrown by the method
         */
        protected void validateException(TestMethod method, Exception e) {
            Throwable t = e;
            if (e instanceof InvocationTargetException ) {
                t = ((InvocationTargetException) e).getTargetException();
            }
            if (!((t instanceof ConnectIOException)
                || (t instanceof SecurityException))) {
                logFailure("Unexpected exception calling " + method.getName(),
                    t);
                return;
            }
            if (clientSubject==null){
                boolean ok = true;
                Iterator it = method.parseConstraints().requirements()
                    .iterator();
                while (it.hasNext()) {
                    InvocationConstraint sc = (InvocationConstraint)
                    it.next();
                    if (sc == ClientAuthentication.YES) {
                        ok = false;
                    }
                }
                if (ok) {
                    return;
                }
            }
            if (ProviderManager.isKerberosProvider()) {
                boolean ok = false;
                Iterator it = method.parseConstraints().requirements()
                    .iterator();
                while (it.hasNext()) {
                    InvocationConstraint sc = (InvocationConstraint)
                    it.next();
                    if (sc == ClientAuthentication.NO) {
                        ok = true;
                    }
                }
                if (ok) {
                    return;
                }
            }

            if (validCall(method) == null) {
                if (!conflictingConstraints(
                    InvocationConstraints.combine(
                        clientConstraints,
                        method.parseConstraints()
                    ))){
                    logFailure("Method " + method.getName()
                        + " failed inappropriately",t);
                }
            }
        }

        /**
         * Method to determine if constraints conflict
         */
        private boolean conflictingConstraints(InvocationConstraints ics) {
            //Conflicting constraints were previously identified through
            //reduceBy.  Rather than anticipate future conflicting constraints
            //this method assumes UnsupportedConstraintsException was accurately
            //thrown.
            return true;
        }

        /**
         * A method call succeeded. Determine whether success was expected.
         * If unexpected, log as a failure
         *
         * @param method the successfully called <code>Method</code>
         */
        protected void validateSuccess(TestMethod method) {
            String failureMessage = validCall(method);
            if (failureMessage != null) {
                logFailure("Method " + method.getName() + " "
                    + "succeeded inappropriately\n" +  failureMessage);
            } else {
                checkClientSubject(clientSubject);
            }
        }

        /**
         * Determine whether a method call is valid, i.e. should return
         * without throwing an exception.
         * <p>
         * Note: it is assumed here that conflicting client
         * constraints are never imposed. Also, conflicting server
         * constraints cannot be imposed or the server export would fail.<p>
         *
         *
         * If a method call is found to be invalid, the returned string
         * contains a brief description of the validity check that failed.
         *
         * @returns a descriptive string for the following invalid cases:
         * <ul>
         * <li>if any server constraints conflict with any client constraints.
         * <li>if any constraint is Integrity.NO, since the JSSE provider
         *     always imposes Integrity.YES implicitly.
         * <li>if the method name is vBogus and ClientAuthentication.YES
         *     is asserted by the server, since this method should fail
         *     the access control check in the  call controller when client
         *     authentication is asserted by the server.
         * <li>if Confidentiality.NO and ServerAuthentication.NO are both
         *     imposed, since the JSSE provider does not provide a ciphersuite
         *     which can satisfy both of these constraints simultaneously.
         * <li>if Delegation.YES and ClientAuthentication.YES are both
         *     imposed, since the JSSE provider does not support delegation.
         * </ul>
         * or returns null if the call is valid.
         */
        private String validCall(TestMethod method) {
            Set serverRequirements = method.parseConstraints().requirements();
            Collection clientRequirements =               // need mutable set
                new ArrayList(clientConstraints.requirements());
            Iterator itClient = clientRequirements.iterator();
            boolean gotConfidentialityNo = false;
            boolean gotServerAuthNo = false;
            while (itClient.hasNext()) {
                InvocationConstraint c1 = (InvocationConstraint) itClient.next();
                if (c1 == Integrity.NO) {
                    return "Integrity.NO applied, "
                        + "but is not supported by the JSSE provider";
                }
                if (c1 == Delegation.YES) {
                    if ((!ProviderManager.isKerberosProvider())
                        && (clientRequirements.contains(ClientAuthentication.YES)
                        || serverRequirements
                        .contains(ClientAuthentication.YES))) {
                        return "ClientAuthentication.YES and Delegation.YES "
                            + "cannot be simultaneously supported by JSSE";
                    }
                }
                Iterator itServ = serverRequirements.iterator();
                if (c1 == Confidentiality.NO) {
                    gotConfidentialityNo = true;
                }
                if (c1 == ServerAuthentication.NO) {
                    gotServerAuthNo = true;
                }

                if (c1 == ClientAuthentication.YES && method.getName()
                    .equals("vBogus")) {
                    return "vBogus should always fail because it is not "
                        + "included in the policy file";
                }
                while (itServ.hasNext()) {
                    InvocationConstraint c2 = (InvocationConstraint) itServ.next();
                    if (c2 == Integrity.NO) {
                        return "Integrity.NO applied, but is not "
                            + "supported by JSSE";
                    }
                    if (c2 == Delegation.YES) {
                        if (clientRequirements
                            .contains(ClientAuthentication.YES)
                            || serverRequirements.contains(
                            ClientAuthentication.YES)) {
                            return "ClientAuthentication.YES and Delegation.YES "
                                + "cannot be simultaneously supported by JSSE";
                        }
                    }
                    if (c2 == ClientAuthentication.YES && method.getName()
                        .equals("vBogus")) {
                        return "vBogus should always fail because it is not "
                            + "included in the policy file";
                    }
                    /*if (c1.reduceBy(c2) == null || c2.reduceBy(c1) == null) {
                        return "the constraint " + c2 + " conflicts with "
                            + "the constraint " + c1;
                    }*/
                    if (c2 == Confidentiality.NO) {
                        gotConfidentialityNo = true;
                    }
                    if (c2 == ServerAuthentication.NO) {
                        gotServerAuthNo = true;
                    }
                }
            }

            /*
             * special case check for the JSSE provider. None of the
             * cipher suites support the combination where confidentiality
             * and server authentication are both disabled, so this
             * combination must result in an invalid call
             */
            if (gotConfidentialityNo && gotServerAuthNo) {
                return "The JSSE provider does not support simultaneous "
                    + "assetion of ServerAuthentication.NO and "
                    + "Confidentiality.NO";
            }
            return null;
        }
    }

    /**
     * Determine whether a call to verifyObjectTrust should have succeeded.
     * This method assumes that a non-RMI proxy is being verified.
     * The client constraints being set are used to determine whether
     * verifyObjectTrust call should fail, since the constraints being set
     * are also applied when a trusted server is contacted to verify
     * trust in a non-RMI proxy. The servers method constraints are
     * assumed to be set up so that there are no server constraints imposed
     * for the verifyObjectTrust method.<p>
     *
     * Note: this method assumes correct behavior of the
     * <code>InvocationConstraints.combine</code> method.
     *
     * @param clientConstraints the constraints applied to the proxy
     *                          when verifyObjectTrust is called.
     *
     * @return <code>true</code> if the verifier call should have succeeded
     */
    private boolean validVerifierCall(InvocationConstraints clientConstraints) {
        /*
         * As currently implemented, conflicting client constraints are
         * never imposed, and the only client constraint which can cause
         * verifyObjectTrust to fail is Integrity.NO. The other possible
         * valid cause for failure is if no method constraints are
         * specified.
         */
        Collection clientRequirements = clientConstraints.requirements();
        Collection clientPreferences = clientConstraints.preferences();
        Iterator itClient = clientRequirements.iterator();
        if ((clientRequirements.isEmpty())&&(clientPreferences.isEmpty())) {
            return false;
        }
        while (itClient.hasNext()) {
            InvocationConstraint c1 = (InvocationConstraint) itClient.next();
            if (c1 == Integrity.NO) return false;
        }
        return true;
    }

    /**
     * Computes the expected combined constraints for the given method
     * by combining the current proxy and contextual client constraints
     * along with the constraints implied by the method name.
     *
     * @param method the method to use to compute the combined constraints
     * @return the computed combined constraints
     */
    private InvocationConstraints getConstraints(TestMethod testMethod) {
        InvocationConstraints sc = testMethod.parseConstraints();
        return InvocationConstraints.combine(clientConstraints, sc);
    }

    /**
     * Obtain the combined constraints from the JSSE call context
     *
     * @param context the OutboundRequestHandle obtained from the provider
     * @return the combined InvocationConstraints obtained from the context
     *
    private InvocationConstraints getConstraints(OutboundRequestHandle context){
        InvocationConstraints constraints = null;
        try {
            Field field = null;
            if (ProviderManager.isKerberosProvider()) {
                field = context.getClass().getDeclaredField("cs");
            } else {
                field =
                    context.getClass().getDeclaredField("requestedConstraints");
            }
            field.setAccessible(true);
            constraints = (InvocationConstraints)field.get(context);
        } catch (Exception e) {
            logger.log(ALWAYS, "Botched reflection: " + e);
            logger.log(ALWAYS, e);
        }
        return constraints;
    }*/

    /**
     * check to see whether the client constraints in use match those
     * which were passed to the proxy when it's trust validator was called.
     *
     * @param method the <code>TestMethod</code> which was called.
     */
    private void checkClientConstraints(TestMethod testMethod) {
        InvocationConstraints c = null;
        c = iface.getConstraints().getConstraints(testMethod.getMethod());
        logger.log(CLIENTCONSTRAINTS, "client constraints from proxy: " + c);
        Collection imposedRequirements = new ArrayList(    // need mutable set
            clientConstraints.requirements());
        Set observedRequirements = c.requirements();
        Iterator it = observedRequirements.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            if (!imposedRequirements.contains(obj)) {
                logFailure("unexpected client constraint " + obj + " "
                    + "found in proxy constraints "
                    + "before calling " + testMethod.getName());
            }
        }
        it = imposedRequirements.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            if (!observedRequirements.contains(obj)) {
                logFailure("client constraint " + obj + " "
                    + "missing from proxy constraints "
                    + "before calling " + testMethod.getName());
            }
        }
    }

    /*
     * Validate that the combined constraints actually being used
     * are consistant with those imposed by the client and server.
     * <code>InvocationConstraint.reduceBy</code> is assumed to be correctly
     * implemented for all constraints. The combined constraints
     * passed to this method are assumed to have been obtained
     * from the <code>OutboundRequestHandle</code> associated with the method call.
     * This method is called from the <code>writeCallback</code> after
     * obtaining the call context.
     *
     * @param method the method being called
     * @param combinedConstraints the combined constraints being used by the
     *                            client endpoint.
     */
    private void checkCombinedConstraints(TestMethod method,
        InvocationConstraints combinedConstraints) {
        logger.log(INTERNALCALLS,
            "Entering checkCombinedConstraints(method, context)");
        logger.log(COMBINEDCONSTRAINTS,
            "combined constraints: " + combinedConstraints);
        InvocationConstraints sc = method.parseConstraints();
        sc = InvocationConstraints.combine(sc, clientConstraints);
        Set imposedRequirements = sc.requirements();
        /* combined requirements must be mutable */
        Set combinedRequirements =
        new HashSet(combinedConstraints.requirements());
        Iterator it = imposedRequirements.iterator();
        while (it.hasNext()) {
            InvocationConstraint c = (InvocationConstraint)it.next();
            if (!combinedRequirements.contains(c)) {
                logFailure("Combined constraint is missing constraint " + c);
            }
            combinedRequirements.remove(c);
        }
        if (!combinedRequirements.isEmpty()) {
            InvocationConstraints residual =
                new InvocationConstraints(combinedRequirements, null);
            logFailure("Combined constraints contains extra "
                + "constraints: " + residual);
        }
        logger.log(INTERNALCALLS, "Leaving checkCombinedConstraints"
            +"(method, context)");
    }

    /*
     * An implementation of WriteCallback to be installed in the bridge.
     * The writeCallback method is called 'in the middle'
     * of a remote client call, before the call data is written.
     * Connection objects can be accessed to perform validations.
     * This class is also an InstanceCarrier, so it can be used to
     * access the client instance which created it.
     */
    protected static class ClientSideAction implements WriteCallback,
        InstanceCarrier {
        /** the client instance associated with this action */
        SecureClient instance;

        ClientSideAction(SecureClient instance) {
            this.instance = instance;
        }

        /**
         * The callback method. Call the client instance back to perform
         * validations. It is the responsbility of the client callback
         * to write a ReadCallback object to the stream.
         *
         * @param request the OutboundRequest of this remote call
         * @param context the OutboundRequestHandle of this remote call
         */
        public ReadCallback writeCallback(OutboundRequest request,
            InvocationConstraints constraints) {
            return instance.doWriteCallback(request, constraints);
        }

        /**
         * Get the instance which installed this InstanceCarrier. Primarily
         * used to allow client side components which do not have a direct
         * reference to the SecureClient instance to obtain the logger.
         *
         * @return the TestClient instance which created this InstanceCarrier
         */
        public TestClient getInstance() {
            return instance;
        }
    }

    /**
     * The callback invoked from the writeCallback method. The actions
     * performed here could have been performed directly by the
     * ClientSideAction object since it executes in the calling thread.
     * However, this method is called instead to maintain symmetry with the
     * ServerSideAction object. In addition to performing a variety of
     * validations, this method writes an instance of
     * ServerSideAction (an InstanceCarrier) to the given stream to allow
     * the server to obtain a reference to the calling client.
     *
     * @param request the OutboundRequest for this remote call
     * @param context the OutboundRequestHandle for this remote call
     */
    synchronized ReadCallback doWriteCallback(OutboundRequest request,
        InvocationConstraints constraints) {
        /*
         * the writeObject must be done after setting combined constraints
         * because the server side thread may execute immediately after
         * writeObject is executed, and combined constraints must be
         * set before the server side callback is executed. This is
         * necessary if the server side readCallData method never actually
         * reads data from the stream. In this case, it won't block
         * waiting for the writeCallData to write data to the stream.
         */
         combinedConstraints = constraints;
         ui.setCombinedConstraints(combinedConstraints);
         checkCombinedConstraints(currentMethod, combinedConstraints);
         //checkServerSubject(request.getServerSubject(), combinedConstraints);
        if (!ProviderManager.isKerberosProvider()) {
            SSLSocket socket = getServerSocket(request);
            cipherSuite =
                CipherSuite.getSuite(socket.getSession().getCipherSuite());
            checkCipherSuite(cipherSuite, socket, combinedConstraints);
            ui.setTestSuite(cipherSuite);
        }
        return new ServerSideAction(myKey);
    }

    /**
     * An implementation of ReadCallback/InstanceCarrier, an instance of
     * which is obtained from the ObjectInputStream and installed in the
     * bridge by the readCallData method in the bridge.
     * The doReadcallback method is called 'in the middle'
     * of a remote client call, immediately after the call to readCallData,
     * so connection objects can be accessed to perform validations.
     * Constraints are checked here because the server connection is
     * required in order to determine whether client authentication
     * is being performed. This object also implements InstanceCarrier,
     * so that the server side error logging can be coordinated
     * with the correct client side instance.
     */
    public static class ServerSideAction implements ReadCallback,
        InstanceCarrier, Serializable {
        Integer key;

        ServerSideAction(Integer key) {
            this.key = key;
        }

        /**
         * Obtain the SecureClient instance which invoked this remote
         * call and call it's <code>doReadCallback</code> method.
         *
         * @param request the <code>InboundRequest</code>
         *                used to receive this remote call.
         *
         * @throws 	  IllegalArgumentException if the key
	     *                                                                                                                                                                                                                                                                                                                                                                   	  passed in the constructor of this object
         *                does not map to a SecureClient instance.
         */
        public void readCallback(InboundRequest request) {
            SecureClient instance = (SecureClient) getInstance();
            if (instance == null) {
                throw new TestException("getInstance returned a "
                    + "null instance in readCallback",null) ;
            }
            instance.doReadCallback(request);
        }

        /**
         * Get the instance which created this InstanceCarrier. Primarily
         * used to allow server side components to obtain a
         * reference to the TestClient to obtain its logger.
         *
         * @return the TestClient instance which created this InstanceCarrier
         */
        public TestClient getInstance() {
            return SecureClient.getInstance(key);
        }
    }

    /**
     * The callback invoked from the readCallback method. This method
     * validates the correctness of the server constraints, the client
     * subject obtained from the readCallData method, and verifies that
     * the cipher suite being used is consistant with the constraints
     * applied for the call.
     *
     * @param connection the ServerConnection provided by readCallData
     * @param serverConstraints the ServerConstraints provided by readCallData
     * @param clientSubject the client subject provided by readCallData
     */
    public void doReadCallback(InboundRequest request) {
        logger.log(DEBUG, "In readData callback, subject = " + clientSubject);
        ui.setClientSubject(clientSubject);
    }

    /**
     * An implementation of WriteCallback/InstanceCarrier solely
     * intended to allow global access to the client instance.
     * This objects writeCallback method is called 'in the middle'
     * of a remote client call, immediately before the call to the
     * writeCallData. It is set in
     * <code>Bridge.writeCallback</code>
     * before performing all Remote calls other than those defined by the
     * <code>ConstraintsInteface</code> class.
     */
    protected static class InstancePasser implements WriteCallback,
        InstanceCarrier {
        SecureClient instance;

        InstancePasser(SecureClient instance) {
            this.instance = instance;
        }

        /**
         * Implements the WriteActionData callback method. This method
         * simply returns an instance of <code>InstanceReceiver</code>
         *
         * @param request the <code>OutboundRequest</code> for
         * the remote call
         * @param context the <code>OutboundRequestHandle</code> for the remote call
         * @return an instance of <code>InstanceReceiver</code>
         */
        public ReadCallback writeCallback(OutboundRequest request,
            InvocationConstraints constraints) {
            return new InstanceReceiver(instance.getKey());
        }

        /**
         * Get the instance which installed this InstanceCarrier. Primarily
         * used to allow client side components which do not have a direct
         * reference to the TestClient instance to obtain the logger.
         *
         * @return the TestClient instance which created this InstanceCarrier
         */
        public TestClient getInstance() {
            return instance;
        }
    }

    /**
     * An implementation of ReadCallback/InstanceCarrier solely
     * intended to allow global access to the client instance.
     */
    public static class InstanceReceiver implements ReadCallback,
        InstanceCarrier, Serializable {
        Integer key;

        InstanceReceiver(Integer key) {
            this.key = key;
        }

        /**
         * required to satisfy the ReadCallback interface.
         */
        public void readCallback(InboundRequest request) {
        }

        /**
         * Get the instance which created this InstanceCarrier.
         * Used to allow server side components to obtain a
         * reference to the TestClient to obtain its logger.
         *
         * @return the TestClient instance which created this InstanceCarrier
         */
        public TestClient getInstance() {
            return SecureClient.getInstance(key);
        }
    }

    /**
     * Check the correctness of client subject. The given client subject
     * is assumed to be the subject obtained from the readCallData
     * method. Failure is logged under the following conditions:
     * <ul>
     * <li>The client subject was authenticated (non null) when
     *     ClientAuthentication.NO was imposed.
     * <li>The client subject was not authenticated (null) when
     *     ClientAuthentication.YES was imposed.
     * <li>ClientAuthentication.YES was imposed and ClientMinPrincipal
     *     was imposed, but the authenticated client did not include
     *     all of the principals required by ClientMinPrincipal
     * </ul>
     *
     * @param clientSubject the client subject from readCallData
     */
    private void checkClientSubject(Subject clientSubject) {
        logger.log(CLIENTSUBJECT,"Authenticated client subject is "
            + clientSubject);
        if (imposed(combinedConstraints, ClientAuthentication.NO)
            && clientSubject != null) {
            logFailure("Client Subject Authenticated when "
            + "ClientAuthentication.NO was imposed");
            return;
        }
        if (imposed(combinedConstraints, ClientAuthentication.YES)) {
            if (clientSubject == null) {
                logFailure("Client Subject is null when "
                    + "ClientAuthentication.YES was imposed");
                return;
            }
            if (currentMethod.requiresAlt1() && !hasAltPrincipal(clientSubject,
                subjectProvider.getConstraintAlternatives1())) {
                logFailure("Client Subject did not authenticate as "
                + "an Alt1 principal as required by "
                + "ClientMinPrincipal constraints. "
                + "Authenticated Subject: " + clientSubject);
                return ;
            }
            if (currentMethod.requiresAlt2() && !hasAltPrincipal(clientSubject,
                subjectProvider.getConstraintAlternatives2())) {
                logFailure("Client Subject did not authenticate as "
                + "an Alt2 principal as required by "
                + "ClientMinPrincipal constraints. "
                + "Authenticated Subject: " + clientSubject);
                return ;
            }
        }
    }

    /**
     * Verifies that the server subject is valid. To be valid:
     * <ul>
     * <li>If ServerAuthentication.NO was applied, the subject must be null
     * <li>If ServerAuthentication.YES was applied, the subject must be non
     *     null.
     * <ul>
     * Because <code>ServerMinPrincipal</code> is exercised by inclusion of
     * all server principals in a <code>ConstraintAlternatives</code>, there
     * should never be a failure attributable to this constraint. Therefore
     * no explicit checking for this is done.
     *
     * @param serverSubject the authenticated server subject obtained from the
     *        Connection during the call
     * @param combinedConstraints the combined constraints that were applied
     *        for this remote call
     */
    private void checkServerSubject(Subject serverSubject,
        InvocationConstraints combinedConstraints) {
        ui.setServerSubject(serverSubject);
        Set requirements = combinedConstraints.requirements();
        if (requirements.contains(ServerAuthentication.NO)) {
            if (serverSubject != null) {
                logFailure("Server subject provided with "
                    + "ServerAuthentication.NO asserted. "
                    + "Subject: " + serverSubject);
            }
        }
        if (requirements.contains(ServerAuthentication.YES)) {
            if (serverSubject == null) {
                logFailure("Server subject is null with "
                    + "ServerAuthentication.YES asserted");
            }
        }
    }

    /**
     * Check whether the given InvocationConstraints object contains
     * the given required constraint
     *
     * @param constraints the InvocationConstraints object to check
     * @param constraint  the required constraint to check for
     *
     * @return <code>true</code> if <code>constraints</code> includes
     *         <code>constraint</code> in its set of required constraints.
     */
    private boolean imposed(InvocationConstraints constraints,
        InvocationConstraint constraint) {
        Iterator it = constraints.requirements().iterator();
        while (it.hasNext()) {
            InvocationConstraint c = (InvocationConstraint)it.next();
            if (c.equals(constraint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether the given InvocationConstraints object contains
     * the given preferred constraint
     *
     * @param constraints the InvocationConstraints object to check
     * @param constraint  the required constraint to check for
     *
     * @return <code>true</code> if <code>constraints</code> includes
     *         <code>constraint</code> in its set of preferred constraints.
     */
    private boolean preferred(InvocationConstraints constraints,
        InvocationConstraint constraint) {
        Iterator it = constraints.preferences().iterator();
        while (it.hasNext()) {
            InvocationConstraint c = (InvocationConstraint)it.next();
            if (c.equals(constraint)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check whether the given subject contains
     * principals consistant with the given ConstraintAlternatives.
     * The ConstraintAlternatives contains a set of ClientMinPrincipals,
     * and the ClientMinPrincipals contains a set of Principals. The
     * subject must contains all of the principals in one of
     * the ClientMinPrincipals.
     *
     * @param subject the subject to test. This may be null.
     * @param the ConstraintAlternatives the subject must conform to.
     *        This must not be null.
     */
     private boolean hasAltPrincipal(Subject subject,
                     ConstraintAlternatives alt)
     {
        if (subject != null) {
            Iterator itAlt = alt.elements().iterator();
            while (itAlt.hasNext()) {
                ClientMinPrincipal cminp = (ClientMinPrincipal) itAlt.next();
                int matchCount = cminp.elements().size();
                Iterator itCminp = cminp.elements().iterator();
                while (itCminp.hasNext()) {
                    Principal altP = (Principal) itCminp.next();
                    Iterator itSub = subject.getPrincipals().iterator();
                    while (itSub.hasNext()) {
                        Principal subP = (Principal)itSub.next();
                        if (altP.getName().equals(subP.getName())) matchCount--;
                    }
                    if (matchCount == 0) return true;
                }
            }
        }
        return false ;
    }

    /**
     * Extract the JSSE server socket from the server connection object
     *
     * @param connection the ServerConnection from readCallData
     *
     * @return the SSLSocket bound the the connection
     */
    private SSLSocket getServerSocket(OutboundRequest request) {
        SSLSocket socket = null;
        Field field = null;
        try {
            /* based on class SecureConnectionManager.Outbound */
            field = request.getClass().getDeclaredField("c");
            field.setAccessible(true);
            Connection c =
                (Connection) field.get(request);
            field = c.getClass().getDeclaredField("sslSocket");
            field.setAccessible(true);
            socket = (SSLSocket)field.get(c);
        } catch (Exception e) {
            if (field == null) {
                try {
                /* based on class HttpsServerEndpoint.HtttpsOutboundRequest */
                    field = request.getClass().getDeclaredField("connection");
                    field.setAccessible(true);
                    Connection c =(Connection) field.get(request);
                    field = c.getClass().getSuperclass()
                        .getDeclaredField("sslSocket");
                    field.setAccessible(true);
                    socket = (SSLSocket)field.get(c);
                } catch (Exception eHttp) {
                    eHttp.printStackTrace();
                    logger.log(ALWAYS, "Botched reflection: " + eHttp);
                    logger.log(ALWAYS, eHttp);
                }
            } else {
                e.printStackTrace();
                logger.log(ALWAYS, "Botched reflection: " + e);
                logger.log(ALWAYS, e);
            }
        }
        return socket;
    }

    /**
     * Check that the ciphersuite is consistant with the combined constraints
     * <p>
     * Failure is logged in the following cases for required constraints:
     * <ul>
     * <li>Confidentiality.YES was imposed, but the suite does not
     *     support encryption.
     * <li>Confidentiality.NO was imposed, but the suite supports encryption.
     * <li>ServerAuthentication.YES was imposed, but the suite does not support
     *     authentication.
     * <li>ServerAuthentication.NO was imposed, but the suite supports
     *     authentication.
     * <li>ClientAuthentication.YES was imposed, but either the suite does
     *     not support authentication, or client authentication was not
     *     turned on in the server socket.
     * <li>ClientAuthentication.NO was imposed, but client authentication
     *     was turned on in the server socket.
     * <li>Integrity.NO was imposed. All SSL cipherSuites impose Integrity.YES
     * </ul>
     *
     * In addition, failures will be logged if Confidentiality.YES was
     * imposed, and a preference of ConfidentialityStrength.WEAK was
     * asserted but a strong suite was selected, and conversely if a
     * preference of ConfidentialityStrength.STRONG was asserted but
     * a weak suite was selected.
     *
     * @param suite the String containing the cipherSuite designator
     * @param socket the server SSLSocket involved in the remote call
     * @param constraints the server constraints for this method call
     */
    private void checkCipherSuite(CipherSuite suite, SSLSocket socket,
        InvocationConstraints constraints) {
        boolean clientAuthenticated;
        logger.log(SUITE, "Ciphersuite for this call is " + suite);
        logger.log(COMBINEDCONSTRAINTS, "constraints parameter in "
            + "checkChipherSuite: " + constraints);
        logger.log(COMBINEDCONSTRAINTS, "combined constraints in "
            + "checkChipherSuite: " + combinedConstraints);
        if (!ProviderManager.isStrong() && suite.isStrong()) {
            logFailure("Strong encryption suite selected when the provider "
                + "should not support strong encryption.");
        }
        Set requirements = null;
        SSLSession session = socket.getSession();
        if (session.getLocalCertificates()!=null) {
            clientAuthenticated = true;
        } else {
            clientAuthenticated = false;
        }
        try {
            requirements = constraints.requirements();
        } catch (RuntimeException e) {
            logger.log(ALWAYS, "Exception caught, param = " + constraints);
            logger.log(ALWAYS, e);
            logger.endBoundary();;
            logger.writeLog();
            throw new TestException("Unexpected runtime exception", e);
        }

        for (Iterator it = requirements.iterator(); it.hasNext(); ) {
            InvocationConstraint c = (InvocationConstraint) it.next();
            if ((c == Confidentiality.YES) && !suite.isConfidential()) {
                logFailure("Confidentiality.YES is not supported "
                    + "by suite " + suite);
            }
            if ((c == Confidentiality.NO) && suite.isConfidential()) {
                logFailure("Confidentiality.NO is not supported "
                    + "by suite " + suite);
            }
            if ((c == ServerAuthentication.YES) && !suite.isAuthenticated()) {
                logFailure("ServerAuthentication.YES is not "
                    + "supported by suite " + suite);
            }
            if ((c == ServerAuthentication.NO) && suite.isAuthenticated()) {
                logFailure("ServerAuthentication.NO is not "
                + "supported by suite " + suite);
            }
            if ((c == ClientAuthentication.YES) && !suite.isAuthenticated()) {
                logFailure("ClientAuthentication.YES is not "
                    + "supported by suite " + suite);
            }
            if ((c == ClientAuthentication.YES) && !clientAuthenticated) {
                logFailure("ClientAuthentication.YES is asserted, "
                    + "but the client did not authenticate with the server");
            }
            if ((c == ClientAuthentication.NO) && clientAuthenticated) {
                logFailure("ClientAuthentication.NO is asserted, "
                    + "but client authenticated with the server");
            }
            if ((c == Integrity.YES) && !suite.isIntegrity()) {
                logFailure("Integrity.YES is not supported by suite " + suite);
            }
            if ((c == Integrity.NO) && suite.isIntegrity()) {
                logFailure("Integrity.NO is not supported by suite " + suite);
            }
        }

        /*
         * check preferences. Note that the ConfidentialityStrength
         * constraint may be ignored if Confidentiality.YES is not imposed
         */
        Set preferences = constraints.preferences();
        for (Iterator it = preferences.iterator(); it.hasNext(); ) {
            InvocationConstraint c = (InvocationConstraint) it.next();
            if ((c == ConfidentialityStrength.WEAK) && !suite.isWeak()
                && imposed(constraints,Confidentiality.YES)) {
                logFailure("ConfidentialityStrength.WEAK was "
                    + "preferred but a strong suite "
                    + "was selected:" + suite);
            }
            if ((c == ConfidentialityStrength.STRONG)
                && ProviderManager.isStrong()
                && suite.isWeak()
                && imposed(constraints,Confidentiality.YES)) {
                logFailure("ConfidentialityStrength.STRONG was "
                    + "preferred but a weak suite was selected:" + suite);
            }
        }
    }

    /**
     * Log a test failure. The failure is logged, and the failure counter
     * is incremented.
     *
     * @param errorString the message to write to the error log
     */
    public void logFailure(String errorString) {
        logFailure(errorString, null);
    }

    /**
     * Log a test failure. The failure is logged, and the failure counter
     * is incremented.
     *
     * @param errorString the message to write to the error log
     * @param t the exception thrown as a result of the failure
     */
    public void logFailure(String errorString, Throwable t) {
        /*
         * synchronized because failureCount is accessed by another
         * thread in the UserInterface
         */
        synchronized (this) {
            failureCount++;
        }
        logger.log(FAILURES, "***** Test " + testNumber + " Failed *****");
        logger.log(FAILURES, errorString);
        if (currentMethod != null) {
            logger.log(FAILURES, "Method signature: "
            + currentMethod.getSignature());
        }
        logger.log(FAILURES, "Imposed Client proxy constraints: "
            + clientConstraints);
        if (currentMethod != null) {
            logger.log(FAILURES, "Imposed Server constraints: "
                + currentMethod.parseConstraints());
        }
        logger.log(FAILURES, "Combined constraints: "
            + ((combinedConstraints == null) ? "Undefined"
            : combinedConstraints.toString()));
        logger.log(FAILURES, "Authenticated Client Subject: " + clientSubject);
        logger.log(FAILURES, "Current ciphersuite: "
            + ((cipherSuite == null) ? "Undefined" : cipherSuite.toString()));
        logger.dump(FAILURES);
        if (t != null) {
            logger.log(FAILURES, "Exception was :" + t);
            t.printStackTrace();
            //The following commented block of code is sometimes useful in
            //debugging - but can be misleading in production runs
            /*Throwable endpointException =
                EndpointWrapper.getLastEndpointException();
            if (endpointException !=null) {
                logger.log(FAILURES, "Last Exception in the endpoint was : "
                    + endpointException);
                endpointException.printStackTrace();
            }*/
            logger.log(FAILURES, t);
        }
        logger.log(FAILURES, "***************************************\n");
        ui.setFailureCount(failureCount);
        String es = logger.getLogBuffer();
        if (es != null) {
            ui.showFailure(es);
        }
        logger.endBoundary();
        logger.writeLog();
        /*
         * If the <code>UserInterface</code> indicates 'stop on failure',
         * make this thread wait. Note that if the user interface is actually
         * the <code>NullGUI</code>, then <code>stopAfterFailure</code> will
         * return <code>false</code> if the <code>e2etest.abortOnFailure</code>
         * property is not defined, and it will throw a
         * <code>TestException</code> if the property is defined.
         */
        if (ui.stopAfterFailure()) {
            synchronized (goMonitor) {
                try {
                    goMonitor.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get the logger associated with this instance of SecureClient
     *
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Get the key associated with this instance of SecureClient. Used
     * by the client side InstanceCarrier to create the server side
     * InstanceCarrier
     *
     * @return the key
     */
    private Integer getKey() {
        return myKey;
    }

    /**
     * Get the combined constraints associated with this SecureClient instance
     *
     * @return the combined constraints
     */
    public InvocationConstraints getCombinedConstraints() {
        return combinedConstraints;
    }

    /**
     * Get the unconstrained service proxy associated with this
     * SecureClient. Needed on the server side to respond to the
     * verifyObjectTrust mechanism.
     */
    public SmartInterface getUnconstrainedProxy() {
        return unconstrainedIface;
    }

    /**
     * helper to write a standard boundary header
     */
    private void writeBoundaryHeader() {
        logger.startBoundary("Test " + testNumber + " "
            + "for thread " + threadNumber);
    }

    /**
     * register the <code>UserInterface</code> to associate with this client
     *
     * @param ui the <code>UserInterface</code> to register
     */
    public void registerUserInterface(UserInterface ui) {
        this.ui = ui;
    }

    /**
     * <code>UserInterface</code> call which will cause the test to
     * run assuming it was paused.
     *
     */
    public void executeTests() {
        synchronized (goMonitor) {
            goMonitor.notifyAll();
        }
    }

    /* inherit javadoc */
    public int getTestNumber() {
        return testNumber;
    }

    /* inherit javadoc */
    public int getTestTotal() {
        return totalTests;
    }

    /* inherit javadoc */
    /* synchronized because the UserInterface is expected to access
     * failureCount from a separate thread
     */
    synchronized public int getFailureCount() {
        return failureCount;
    }

    /**
     * compute the total (approximate) number of remote method calls
     * this client will make. The number is approximate because one
     * luck thread gets to make a couple of extra calls to unexport
     * the last proxy.
     *
     * @return the total number of calls this client should execute
     */
    private int computeTotalTestCount() {
    int count = 0;
    for (int req = 0; req < (1 << constraintsArray.length); req++) {
        if (((req & BADCONF) == BADCONF)
        || ((req & BADINTEG) == BADINTEG)
        || ((req & BADDELEG) == BADDELEG)) {
        continue;
        }
        for (int pref = 0; pref < (1 << preferencesArray.length); pref++)
        {
        if ((pref & BADSTRENGTH) == BADSTRENGTH) {
            continue;
        }
        for (int m=0; m<methods.length; m++) {
            if (methods[m].getName().indexOf("Noconf") >= 0) {
            continue;
            }
            count++;
        }
        }
    }
        count += 2;  // count the unexport and callAfterUnexport calls
    return count;
    }

    /**
     * Verify that the server method constraints in the proxy are equal to
     * the server constraints set by the server. This test likely just
     * boils down to verify that arrays serialize properly.
     *
     * @param proxy the remote proxy to check
     */
    private void checkServerConstraints(SmartInterface proxy)
    {
        try {
            if (!((SmartProxy)proxy).getServerConstraints()
                .equals(SmartProxy.getMethodConstraints())) {
                logFailure("Server constraints obtained from proxy "
                    + "do not match server constraints applied "
                    + "by server");
            }
        } catch (Exception e) {
            logFailure("Exception retrieving server constraints "
                + "from the proxy because " + e.getMessage());
        }
    }
}
