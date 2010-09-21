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

import net.jini.jeri.OutboundRequest;
import net.jini.jeri.OutboundRequestIterator;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.NoSuchElementException;

/**
 * A fake implementation of the <code>OutboundRequestIterator</code>
 * interface.
 */
public class FakeOutboundRequestIterator implements OutboundRequestIterator {

    Logger logger = Logger.getLogger("com.sun.jini.qa.harness.test");

    private Throwable nextException;
    private FakeOutboundRequest[] nextReturn;
    private int nextReturnCounter;
    private boolean[] hasNextReturn;
    private int hasNextReturnCounter;
    private int hasNextCalls;
    private boolean hasNextThrowsAssertion;

    /**
     * Constructs a FakeOutboundRequestIterator.  hasNext throws
     * AssertionError after set of return values are exhausted.
     *
     * @param nr the OutboundRequests to return from <code>next</code> method
     */
    public FakeOutboundRequestIterator(FakeOutboundRequest nr) {
        this(nr,true);
    }

    /**
     * Constructs a FakeOutboundRequestIterator.  Whether or not hasNext throws
     * AssertionError is configurable by hasNextThrowsAssertion 
     * boolean parameter.
     *
     * @param nr the OutboundRequests to return from <code>next</code> method
     * @param hasNextThrowsAssertion if true, hasNext will throw an 
     *        AssertionError if its set of values is exhausted;
     *        otherwise, hasNext will return false once it's values 
     *        are exhausted
     */
    public FakeOutboundRequestIterator(
        FakeOutboundRequest nr, boolean hasNextThrowsAssertion) 
    {
        logger.entering(getClass().getName(),"constructor(nextReturn:" 
            + nextReturn + ")");
        nextReturn = new FakeOutboundRequest[] { nr };
        nextReturnCounter = 0;
        hasNextReturn = new boolean[] { true, false };
        hasNextReturnCounter = 0;
        this.hasNextThrowsAssertion = hasNextThrowsAssertion;
    }

    public void init() {
        nextReturnCounter = 0;
        hasNextReturnCounter = 0;
        hasNextCalls = 0;
        if (nextReturn != null) {
            for (int i = 0; i < nextReturn.length; i++) {
                if (nextReturn[i] != null) {
                    nextReturn[i].init();
                }
            }
        }
    }

    /***
     *** The following methods configure the return values for the
     *** various methods.  Mapping from these "set" methods to
     *** the corresponding method should be obvious.
     ***/

    public void setHasNextReturn(boolean[] hnr) {
        hasNextReturn = hnr;
        hasNextReturnCounter = 0;
    }
    public void setNextReturn(FakeOutboundRequest[] nr) {
        nextReturn = nr;
        nextReturnCounter = 0;
    }
    public void setNextException(Throwable ne) {
        nextException = ne;
    }
    public int getHasNextCalls() {
        return hasNextCalls;
    }

    /**
     * Implementation of interface method.
     *
     * @return next <code>hasNextReturn</code> array element
     * @throws AssertionError if <code>hasNextReturn</code> is null or
     *        all elements have been returned
     */
    public boolean hasNext() {
        logger.entering(getClass().getName(),"hasNext");
        hasNextCalls++;
        if (hasNextReturn == null || 
            hasNextReturnCounter >= hasNextReturn.length) 
        {
            if (hasNextThrowsAssertion) {
                throw new AssertionError();
            } else {
                return false;
            }
        }
        return hasNextReturn[hasNextReturnCounter++];
    }

    /**
     * Implementation of interface method.
     * 
     * @return next <code>nextReturn</code> array element if nextExc is null
     * @throws nextException if non-null
     * @throws AssertionError if <code>nextReturn</code> is null or
     * @throws NoSuchElementException if all elements have been returned
     */
    public OutboundRequest next() throws IOException {
        logger.entering(getClass().getName(),"next");

        if (nextException != null) {
            if (nextException instanceof IOException) {
                throw (IOException) nextException;
            } else if (nextException instanceof RuntimeException) {
                throw (RuntimeException) nextException;
            } else if (nextException instanceof Error) {
                throw (Error) nextException;
            } else {
                throw new AssertionError();
            }
        }

        if (nextReturn == null) {
            throw new AssertionError();
        }
        if (nextReturnCounter >= nextReturn.length) {
            throw new NoSuchElementException();
        }
        return nextReturn[nextReturnCounter++];
    }

}

