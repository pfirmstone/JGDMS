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
package com.sun.jini.test.spec.activation.util;
import java.rmi.Remote;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationException;
import java.rmi.activation.UnknownObjectException;
import java.rmi.RemoteException;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.jini.security.proxytrust.TrustEquivalence;


/**
 * A fake implementation of the <code>ActivationID</code>
 * class. It doesn't make real activation, only emulate it.
 */
public class FakeActivationID extends ActivationID implements TrustEquivalence {
    private Remote proxy;
    private Logger logger;
    private boolean fakeEquals;
    private boolean fakeTrustEquivalence;

    /**
     * Construct FakeActivationID object storing pointed logger, proxy
     * and fakeEquals values. fakeTrustEquivalence is set to false.
     */
    public FakeActivationID(Logger logger, Remote proxy, boolean fakeEquals) {
        super(null);
        this.proxy = proxy;
        this.logger = logger;
        this.fakeEquals = fakeEquals;
        this.fakeTrustEquivalence = false;
    }

    /**
     * Construct FakeActivationID object storing pointed logger and proxy
     * values. fakeTrustEquivalence and fakeEquals values are set to false.
     */
    public FakeActivationID(Logger logger, Remote proxy) {
        this(logger, proxy, false);
    }

    /**
     * Construct FakeActivationID object storing pointed logger
     * fakeTrustEquivalence and fakeEquals values are set to false,
     * proxy is set to null
     */
    public FakeActivationID(Logger logger) {
        this(logger, null);
    }

    /**
     * Emulate activation, but doesn't do it, instead works
     * as activation was performed and returns pointed in constructor
     * proxy
     */
    public Remote activate(boolean force)
            throws ActivationException, UnknownObjectException,
            RemoteException {
        logger.log(Level.FINEST, "FakeActivationID.activate(" + force + ")");
        return proxy;
    }
    
    /**
     * Set emulated trustEquivalence value that could be returned by 
     * checkTrustEquivalence method
     */
    public void setTrustEquivalence(boolean fakeTrustEquivalence) {
        this.fakeTrustEquivalence = fakeTrustEquivalence;
    }
    
    /**
     * Returnes stored value to emulate different cases of trustEquivalence 
     */
    public boolean checkTrustEquivalence(Object obj) {
        return fakeTrustEquivalence;
    }
    
    /**
     * Returns <code>true</code> in case if fakeEquals is true, valid
     * value otherwise.
     */
    public boolean equals(Object obj) {
        if (fakeEquals) {
            return true;
        }
        return (this == obj);
    }
}
