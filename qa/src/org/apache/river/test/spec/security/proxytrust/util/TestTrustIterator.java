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
package org.apache.river.test.spec.security.proxytrust.util;

import java.util.logging.Level;

// java
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.NoSuchElementException;

// net.jini
import net.jini.security.proxytrust.ProxyTrustIterator;


/**
 * Class implementing ProxyTrustIterator interface having constructor with 1
 * paramter: Object[], whose 'next' method returns objects specified in
 * constructor's parameter in the same sequence (i.e. obj[0] first,
 * then obj[1] ... etc.)
 */
public class TestTrustIterator extends BaseIsTrustedObjectClass
        implements ProxyTrustIterator {

    /** Array of objects. */
    protected Object[] objs;

    /** Index to next available record of array. */
    protected int curIdx;

    /** True if setException can be called */
    protected boolean settable;

    /** List of RemoteExceptions passed to 'setException' method. */
    protected ArrayList exList;

    /** Index to next available RemoteException in exList. */
    protected int exIdx;

    /**
     * Constructs iterator from given array.
     *
     * @param objs array of objects
     * @throws NullPointerException if objs is null
     */
    public TestTrustIterator(Object[] objs) {
        if (objs == null) {
            throw new NullPointerException("Object's array can not be null.");
        }
        this.objs = objs;
        curIdx = 0;
        settable = false;
        exList = new ArrayList();
        exIdx = 0;
    }

    /**
     * Returns true if the iteration has more elements, and false otherwise.
     *
     * @return true if the iteration has more elements, and false otherwise
     */
    public boolean hasNext() {
        settable = false;
        return (curIdx < objs.length);
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    public Object next() throws RemoteException {
        if (curIdx >= objs.length) {
            throw new NoSuchElementException();
        }
        settable = true;
        return objs[curIdx++];
    }

    /**
     * Provides the iteration with a RemoteException thrown from
     * a remote call made while attempting to obtain a TrustVerifier from the
     * object returned by the most recent call to next.
     *
     * @param e RemoteException thrown from a remote call
     * @throws NullPointerException if the argument is null
     * @throws IllegalStateException if next has never been
     *         called, or if this method has already been called since the most
     *         recent call to next, or if hasNext has been
     *         called since the most recent call to next, or if the most
     *         recent call to next threw a RemoteException
     */
    public void setException(RemoteException e) {
        if (e == null) {
            throw new NullPointerException("exception cannot be null");
        } else if (!settable) {
            throw new IllegalStateException();
        }
        exList.add(e);
        settable = false;
        srcArray.add(this);
    }

    /**
     * Returns next RemoteException passed to 'setException' method,
     * or null if none.
     *
     * @return next RemoteException passed to 'setException' method or null if
     *         none
     */
    public RemoteException nextException() {
        RemoteException re = null;

        try {
            re = (RemoteException) exList.get(exIdx++);
        } catch (IndexOutOfBoundsException ioobe) {}
        return re;
    }

    /**
     * Returns name of checked method.
     *
     * @return 'checked method' name
     */
    public String getMethodName() {
        return "setException";
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object
     */
    public String toString() {
        String str = "TestTrustIterator[ ";

        for (int i = 0; i < objs.length; ++i) {
            str += objs[i].getClass().getName() + " ";
        }
        str += "]";
        return str;
    }
}
