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
package com.sun.jini.test.spec.jeri.util;

import net.jini.jeri.ObjectEndpoint;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;
import net.jini.core.constraint.InvocationConstraints;

import java.rmi.RemoteException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A fake implementation of the <code>ObjectEndpoint</code>
 * interface.
 * <p>
 * The method return values or throws are configurable.
 * <p>
 * Used by:
 * <ul>
 *   <li>com.sun.jini.test.spec.jeri.basicilfactory.CreateInstancesTest
 *   <li>com.sun.jini.test.spec.jeri.basicilfactory.CreateInvocationHandlerTest
 * </ul>
 */
public class FakeObjectEndpoint implements ObjectEndpoint {

    Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    private Throwable executeCallException;
    private RemoteException executeCallReturn;
    private FakeOutboundRequestIterator newCallReturn;
    private Integer equalityToken;

    /**
     * Constructs a FakeObjectEndpoint.
     *
     * <code>executeCall</code> method throws AssertionError.
     * <code>newCall</code> method returns null.
     */
    public FakeObjectEndpoint() {
        logger.entering(getClass().getName(),"constructor()");
        executeCallException = new AssertionError();
        newCallReturn = null;
        equalityToken = null;
    }

    /**
     * Constructs a FakeObjectEndpoint.
     *
     * <code>executeCall</code> method throws AssertionError.
     * <code>newCall</code> method returns null.
     * <code>equals</code> method return based on equalityToken.
     */
    public FakeObjectEndpoint(int equalityToken) {
        logger.entering(getClass().getName(),"constructor(int)");
        executeCallException = new AssertionError();
        newCallReturn = null;
        this.equalityToken = new Integer(equalityToken);
    }

    /**
     * Constructs a FakeObjectEndpoint. 
     *
     * @param newCallReturn the value that <code>newCall</code> should return
     */
    public FakeObjectEndpoint(FakeOutboundRequestIterator newCallReturn) {
        logger.entering(getClass().getName(),"constructor("
            + "newCallReturn:" + newCallReturn + ")");
        this.newCallReturn = newCallReturn;
        equalityToken = null;
    }

    /***
     *** The following methods configure the return values or exceptions 
     *** to throw from the various methods.  Mapping from these "set" methods
     *** to the corresponding method should be obvious.
     ***/

    public void setExecuteCallException(Throwable t) {
        executeCallException = t;
    }
    public void setExecuteCallReturn(RemoteException re) {
        executeCallReturn = re;
    }
    public void setNewCallReturn(FakeOutboundRequestIterator ncr) {
        newCallReturn = ncr;
    }

    /**
     * Implementation of interface method.
     *
     * @return executeCallReturn if executeCallException is null
     * @throw executeCallException if executeCallException is not null
     * @throw AssertionError if executeCallException is not null and
     *        executeCallException is not instanceof 
     *        IOException, RuntimeException, or Error
     */
    public RemoteException executeCall(OutboundRequest call)
        throws IOException
    {
        logger.entering(getClass().getName(),"executeCall");
        if (executeCallException != null) {
            if (executeCallException instanceof IOException) {
                throw (IOException) executeCallException;
            } else if (executeCallException instanceof RuntimeException) {
                throw (RuntimeException) executeCallException;
            } else if (executeCallException instanceof Error) {
                throw (Error) executeCallException;
            } else {
                throw new AssertionError();
            }
        }
        return executeCallReturn;
    }

    /**
     * Implementation of interface method.
     *
     * @return newCallReturn
     */
    public OutboundRequestIterator newCall(InvocationConstraints constraints) {
        logger.entering(getClass().getName(),"newCall");
        return newCallReturn;
    }

    /**
     * Overloads <code>Object.equals</code>.
     *
     * @return if equalityToken set return
     *         obj.equalityToken == this.equalityToken; 
     *         otherwise return super.equals
     */
    public boolean equals(Object obj) {
        logger.entering(getClass().getName(),"equals",obj);
        if (equalityToken != null) {
            return equalityToken.equals(
                ((FakeObjectEndpoint)obj).equalityToken);
        } else {
            return super.equals(obj);
        }
    }

    /**
     * Overloads <code>Object.hashCode</code>.
     *
     * @return 13 if equalityToken set; otherwise returns super.hashCode
     */
    public int hashCode() {
        logger.entering(getClass().getName(),"hashCode");
        return (equalityToken == null ? super.hashCode() : 13);
    }
}
