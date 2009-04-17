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
package com.sun.jini.test.spec.security.proxytrust.singletonproxytrustiterator;

import java.util.logging.Level;

// java
import java.rmi.RemoteException;
import java.util.NoSuchElementException;

// net.jini
import net.jini.security.proxytrust.ProxyTrustIterator;
import net.jini.security.proxytrust.SingletonProxyTrustIterator;

// com.sun.jini
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.test.spec.security.proxytrust.util.AbstractTestBase;


/**
 * <pre>
 * Purpose
 *   This test verifies the following:
 *     SingletonProxyTrustIterator represents an implementation of
 *     ProxyTrustIterator interface with the only one element of the iteration.
 *     Constructor of SingletonProxyTrustIterator throws NullPointerException if
 *     the argument is null.
 *     'hasNext' method of SingletonProxyTrustIterator returns true if the
 *     iteration has more elements, and false otherwise.
 *     'next' method of SingletonProxyTrustIterator returns the next element in
 *     the iteration and throws NoSuchElementException if the iteration has no
 *     more elements.
 *     'setException' method of SingletonProxyTrustIterator throws
 *     IllegalStateException if 'next' has never been called, or if this method
 *     has already been called since the most recent call to next, or if
 *     'hasNext' has been called since the most recent call to next.
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     TestObject - ordinal class
 *
 * Action
 *   The test performs the following steps:
 *     1) construct SingletonProxyTrustIterator with null as a parameter
 *     2) assert that NullPointerException will be thrown
 *     3) construct SingletonProxyTrustIterator1 with TestObject as a parameter
 *     4) call 'setException' method of constructed SingletonProxyTrustIterator1
 *     5) assert that IllegalStateException will be thrown
 *     6) call 'hasNext' method of constructed SingletonProxyTrustIterator1
 *     7) assert that true will be returned
 *     8) call 'setException' method of constructed SingletonProxyTrustIterator1
 *     9) assert that IllegalStateException will be thrown
 *     10) call 'next' method of constructed SingletonProxyTrustIterator1
 *     11) assert that TestObject will be returned
 *     12) call 'setException' method of constructed
 *         SingletonProxyTrustIterator1
 *     13) assert that IllegalStateException will not be thrown
 *     14) call 'setException' method of constructed
 *         SingletonProxyTrustIterator1 again
 *     15) assert that IllegalStateException will be thrown
 *     16) call 'hasNext' method of constructed SingletonProxyTrustIterator1
 *         again
 *     17) assert that false will be returned
 *     18) call 'next' method of constructed SingletonProxyTrustIterator1 again
 *     19) assert that NoSuchElementException will be thrown
 *     20) construct SingletonProxyTrustIterator2 with TestObject as a parameter
 *     21) call 'next' method of constructed SingletonProxyTrustIterator2
 *     22) call 'hasNext' method of constructed SingletonProxyTrustIterator2
 *     23) call 'setException' method of constructed
 *         SingletonProxyTrustIterator2
 *     24) assert that IllegalStateException will be thrown
 * </pre>
 */
public class SingletonProxyTrustIteratorTest extends AbstractTestBase {

    /**
     * This method performs all actions mentioned in class description.
     *
     */
    public void run() throws Exception {
        ProxyTrustIterator pti = null;
        Object obj = new Object();
        RemoteException re = new RemoteException();

        try {
            pti = new SingletonProxyTrustIterator(null);

            // FAIL
            throw new TestException(
                    "Constructor invocation of SingletonProxyTrustIterator "
                    + "with null parameter did not throw any exception "
                    + "while NullPointerException was expected.");
        } catch (NullPointerException npe) {
            // PASS
            logger.fine("Constructor invocation of "
                    + "SingletonProxyTrustIterator with null parameter "
                    + "threw NullPointerException as expected.");
        }
        pti = new SingletonProxyTrustIterator(obj);
        logger.fine("Constructed SingletonProxyTrustIterator(" + obj + ").");

            try {
            pti.setException(re);

            // FAIL
            throw new TestException(
                    "'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator just after it's "
                    + "construction did not throw any exception "
                    + "while IllegalStateException was expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator just after it's "
                    + "construction threw IllegalStateException "
                    + "as expected.");
        }

        if (!pti.hasNext()) {
            // FAIL
            throw new TestException(
                    "First 'hasNext' method invocation of constructed "
                    + "SingletonProxyTrustIterator just after it's "
                    + "construction returned false while true was "
                    + "expected");
        }

        // PASS
        logger.fine("First 'hasNext' method invocation of constructed "
                + "SingletonProxyTrustIterator just after it's "
                + "construction returned true as expected");

        try {
            pti.setException(re);

            // FAIL
            throw new TestException(
                    "'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator after it's 'hasNext' "
                    + "method invocation did not throw any exception "
                    + "while IllegalStateException was expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator after it's 'hasNext' "
                    + "method invocation threw IllegalStateException "
                    + "as expected.");
        }
        Object res = pti.next();

        if (res != obj) {
            // FAIL
            throw new TestException(
                    "First 'next' method invocation of constructed "
                    + "SingletonProxyTrustIterator returned " + res
                    + " while " + obj + " was expected.");
        }

        // PASS
        logger.fine("First 'next' method invocation of constructed "
                + "SingletonProxyTrustIterator returned " + obj
                + " as expected.");
        pti.setException(re);

        // PASS
        logger.fine("'setExcepion' method invocation of constructed "
                + "SingletonProxyTrustIterator after it's 'next' "
                + "method invocation did not throw any exception "
                + "as expected.");

        try {
            pti.setException(re);

            // FAIL
            throw new TestException(
                    "2-nd 'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator after it's 'next' "
                    + "method invocation did not throw any exception "
                    + "while IllegalStateException was expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("2-nd 'setExcepion' method invocation of "
                    + "constructed SingletonProxyTrustIterator after it's "
                    + "'next' method invocation threw "
                    + "IllegalStateException as expected.");
        }

        if (pti.hasNext()) {
            // FAIL
            throw new TestException(
                    "2-nd 'hasNext' method invocation of constructed "
                    + "SingletonProxyTrustIterator returned true while "
                    + "false was expected");
        }

        // PASS
        logger.fine("2-nd 'hasNext' method invocation of constructed "
                + "SingletonProxyTrustIterator returned false as expected");

        try {
            res = pti.next();

            // FAIL
            throw new TestException(
                    "2-nd 'next' method invocation of constructed "
                    + "SingletonProxyTrustIterator did not throw any "
                    + "exception while NoSuchElementException was "
                    + "expected.");
        } catch (NoSuchElementException nsee) {
            // PASS
            logger.fine("2-nd 'next' method invocation of constructed "
                    + "SingletonProxyTrustIterator threw "
                    + "NoSuchElementException as expected.");
        }
        pti = new SingletonProxyTrustIterator(obj);
        logger.fine("Constructed SingletonProxyTrustIterator(" + obj
                + ") again.");
        pti.next();
        pti.hasNext();

        try {
            pti.setException(re);

            // FAIL
            throw new TestException(
                    "'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator after it's 'next' and "
                    + "'hasNext' methods invocations did not throw any "
                    + "exception while IllegalStateException was "
                    + "expected.");
        } catch (IllegalStateException ise) {
            // PASS
            logger.fine("'setExcepion' method invocation of constructed "
                    + "SingletonProxyTrustIterator after it's 'next' and "
                    + "'hasNext' methods invocations threw "
                    + "IllegalStateException as expected.");
        }
    }
}
