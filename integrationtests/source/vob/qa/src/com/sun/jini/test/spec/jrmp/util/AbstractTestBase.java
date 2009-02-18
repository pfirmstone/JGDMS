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
package com.sun.jini.test.spec.jrmp.util;

import java.util.logging.Level;

// java.rmi
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.activation.Activator;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import java.rmi.activation.UnknownObjectException;
import java.rmi.RemoteException;
import java.rmi.MarshalledObject;

// java.io
import java.io.IOException;

// java.net
import java.net.Socket;
import java.net.ServerSocket;

// java.util
import java.util.logging.Level;

// com.sun.jini
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import net.jini.jrmp.JrmpExporter;


/**
 * Abstract Test base for all jrmp spec tests.
 */
public abstract class AbstractTestBase extends QATest {

    /** Constant for no-arg constructor type */
    public static final int NOARG_FACTORY = 0;

    /** Constant for JrmpExporter(int port) constructor type */
    public static final int PORT_FACTORY = 1;

    /**
     * Constant for JrmpExporter(int port,
     *                           RMIClientSocketFactory csf,
     *                           RMIServerSocketFactory ssf)
     * constructor type
     */
    public static final int SOCKS_FACTORY = 2;

    /**
     * Constant for JrmpExporter(ActivationID id, int port) constructor type
     */
    public static final int ID_FACTORY = 3;

    /**
     * Constant for JrmpExporter(ActivationID id, int port,
     *                           RMIClientSocketFactory csf,
     *                           RMIServerSocketFactory ssf)
     * constructor type
     */
    public static final int ID_SOCKS_FACTORY = 4;

    /** Type of constructor being used for constructing JrmpExporter. */
    protected int cType;

    /** Port number being used for constructing JrmpExporter. */
    protected int cPort;

    /**
     * Constructed ActivationID or null if none.
     */
    protected ActivationID cId;

    /**
     * Constructed RMIClientSocketFactory or null if none.
     */
    protected RMIClientSocketFactory cCsf;

    /**
     * Constructed RMIServerSocketFactory or null if none.
     */
    protected RMIServerSocketFactory cSsf;

    /**
     * Sets up the testing environment.
     *
     * @param config Configuration for setup.
     */
    public void setup(QAConfig sysConfig) throws Exception {
        // mandatory call to parent
        super.setup(sysConfig);

        // obtain type of constructor being used
        cType = sysConfig.getIntConfigVal("jrmp.util.constructorType", 0);

        // obtain port number for constructor
        cPort = sysConfig.getIntConfigVal("jrmp.util.constructorPort", 0);

        if ((cType < NOARG_FACTORY) || (cType > ID_SOCKS_FACTORY)) {
            throw new TestException("Invalid constructorType: " + cType
                    + ". Should be from " + NOARG_FACTORY + " to "
                    + ID_SOCKS_FACTORY + " inclusive.");
        }

        // construct ActivationID if needed
        if (cType == ID_FACTORY || cType == ID_SOCKS_FACTORY) {
            EmptyActivatorImpl eai = new EmptyActivatorImpl();
            cId = new ActivationID(eai);
        } else {
            cId = null;
        }

        // construct RMIClientSocketFactory if needed
        if (cType == SOCKS_FACTORY || cType == ID_SOCKS_FACTORY) {
            cCsf = (RMIClientSocketFactory) new EmptyClientSocketFactoryImpl();
        } else {
            cCsf = null;
        }

        // construct RMIServerSocketFactory if needed
        if (cType == SOCKS_FACTORY || cType == ID_SOCKS_FACTORY) {
            cSsf = (RMIServerSocketFactory) new EmptyServerSocketFactoryImpl();
        } else {
            cSsf = null;
        }
    }

    /**
     * Constructs JrmpExporter using appropriate constructor.
     *
     * @param cType  Constuctor being used for constructing JrmpExporter.
     *        Possible values:
     *        NOARG_FACTORY - public JrmpExporter()
     *        PORT_FACTORY - public JrmpExporter(int port)
     *        SOCKS_FACTORY - public JrmpExporter(int port,
     *                                            RMIClientSocketFactory csf,
     *                                            RMIServerSocketFactory ssf)
     *        ID_FACTORY - public JrmpExporter(ActivationID id, int port)
     *        ID_SOCKS_FACTORY - public JrmpExporter(ActivationID id, int port,
     *                                               RMIClientSocketFactory csf,
     *                                               RMIServerSocketFactory ssf)
     *
     * @return Constructed JrmpExporter
     */
    public JrmpExporter createJrmpExporter() {
        logger.log(Level.FINE,
                "We will use the following constructor for constructing "
                + "JrmpExporter:");

        switch (cType) {
        case NOARG_FACTORY:
            logger.log(Level.FINE, "        JrmpExporter()");
            return new JrmpExporter();
        case PORT_FACTORY:
            logger.log(Level.FINE, "        JrmpExporter(" + cPort + ");");
            return new JrmpExporter(cPort);
        case SOCKS_FACTORY:
            logger.log(Level.FINE,
                    "        JrmpExporter(" + cPort + ", " + cCsf + ", " + cSsf
                    + ");");
            return new JrmpExporter(cPort, cCsf, cSsf);
        case ID_FACTORY:
            logger.log(Level.FINE,
                    "        JrmpExporter(" + cId + ", " + cPort + ");");
            return new JrmpExporter(cId, cPort);
        case ID_SOCKS_FACTORY:
            logger.log(Level.FINE,
                    "        JrmpExporter(" + cId + ", " + cPort + ", " + cCsf
                    + ", " + cSsf + ");");
            return new JrmpExporter(cId, cPort, cCsf, cSsf);
        default:
            throw new java.lang.AssertionError(
                    "ERROR: unknown type of constructor " + cType
                    + " for constructing JrmpExporter in createJrmpExporter"
                    + " method.");
        }
    }


    /**
     * Auxiliary class which implements
     * java.rmi.server.RMIClientSocketFactory interface.
     */
    class EmptyClientSocketFactoryImpl implements RMIClientSocketFactory {

        public EmptyClientSocketFactoryImpl() {}

        public Socket createSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }
    }


    /**
     * Auxiliary class which implements
     * java.rmi.server.RMIServerSocketFactory interface.
     */
    class EmptyServerSocketFactoryImpl implements RMIServerSocketFactory {

        public EmptyServerSocketFactoryImpl() {}

        public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(port);
        }
    }


    /**
     * Auxiliary class which implements java.rmi.activation.Activator
     * interface.
     */
    class EmptyActivatorImpl implements Activator {

        public EmptyActivatorImpl() {}

        public MarshalledObject activate(ActivationID id, boolean force)
                throws ActivationException, UnknownObjectException,
                RemoteException {
            return null;
        }
    }
}
