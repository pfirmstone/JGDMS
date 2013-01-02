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
package com.sun.jini.test.spec.constraint.coreconstraint;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.QAConfig;

// java.util
import com.sun.jini.qa.harness.Test;
import java.util.logging.Level;
import java.util.Set;
import java.util.HashSet;

// AbstractImmutableSetTest
import com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest;

// javax.security
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.ServerMinPrincipal;


/**
 * <pre>
 *
 * Purpose:
 *   This test verifies the behavior of the following methods:
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#elements()}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#elements()}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#elements()}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#elements()}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#elements()}
 *   All of these methods should return an immutable set of all of the
 *   principals (ClientMaxPrincipal, ClientMinPrincipal, ServerMinPrincipal) or
 *   classes (ClientMaxPrincipalType, ClientMinPrincipalType). Any attempt to
 *   modify this set results in an
 *   {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * Test Cases:
 *   TestCase #1
 *    {@link net.jini.core.constraint.ClientMaxPrincipal#elements()} method
 *    is verified
 *   TestCase #2
 *    {@link net.jini.core.constraint.ClientMaxPrincipalType#elements()} method
 *     is verified
 *   TestCase #3
 *    {@link net.jini.core.constraint.ClientMinPrincipal#elements()} method
 *     is verified
 *   TestCase #4
 *    {@link net.jini.core.constraint.ClientMinPrincipalType#elements()} method
 *     is verified
 *   TestCase #5
 *    {@link net.jini.core.constraint.ServerMinPrincipal#elements()} method
 *     is verified
 *
 * Infrastructure:
 *     - {@link PrincipalElementsTest}
 *         performs actions; this file
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest}
 *         auxiliary abstract class that contains checker() method and is
 *         extended by {@link PrincipalElementsTest}
 *
 * Actions:
 *   Test performs the following steps:
 *     - creating objects on which elements() method will be tested:
 *         {@link net.jini.core.constraint.ClientMaxPrincipal}
 *         {@link net.jini.core.constraint.ClientMaxPrincipalType}
 *         {@link net.jini.core.constraint.ClientMinPrincipal}
 *         {@link net.jini.core.constraint.ClientMinPrincipalType}
 *         {@link net.jini.core.constraint.ServerMinPrincipal};
 *       these objects (test objects) contain the specified principals/classes;
 *     - in each test case the following actions are performed:
 *        - invoking the corresponding elements() method on the corresponding
 *          test object;
 *        - invoking
 *          {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractImmutableSetTest#checker(Object, Set)}
 *          method to verify the object returned by elements() method,
 *          i.e. that:
 *           - the returned object class implements {@link java.util.Set};
 *           - the returned set contains all of the principals/classes, i.e. is
 *             equal to to the set given to the constructor while creating this
 *             test object (this set doesn't contain duplicate
 *             principals/classes);
 *           - the returned set is an immutable set, i.e. attempt to modify this
 *             set (remove all of the elements from this set using
 *             {@link java.util.Set#clear()}) results in an
 *             {@link java.lang.UnsupportedOperationException} being thrown.
 *
 * </pre>
 */
public class PrincipalElementsTest extends AbstractImmutableSetTest {

    /**
     * Test Objects.
     * elements() method is invoked on these objects.
     */
    public Object obj[] = new Object[5];

    /**
     * Principals and principal types.
     */
    private Set set[] = new Set[5];

    /**
     * This method performs all preparations.
     * Test Objects are created here.
     */
    public Test construct(QAConfig config) throws Exception {
        super.construct(config);
        this.config = (QAConfig) config; // or this.config = getConfig();

        for (int i = 0; i < set.length; i++) {
            set[i] = new HashSet();
        }

        /*
         * Create ClientMaxPrincipal object
         */
        try {
            set[0].add(new KerberosPrincipal("duke@FOO.COM"));
            set[0].add(new KerberosPrincipal("duke"));
            set[0].add(new
                    X500Principal(
                    "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
            obj[0] = new ClientMaxPrincipal(set[0]);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ClientMaxPrincipal object", e);
        }

        /*
         * Create ClientMaxPrincipalType object
         */
        set[1].add(KerberosPrincipal.class);
        set[1].add(X500Principal.class);

        try {
            obj[1] = new ClientMaxPrincipalType(set[1]);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ClientMaxPrincipalType object", e);
        }

        /*
         * Create ClientMinPrincipal object
         */
        set[2] = set[0];

        try {
            obj[2] = new ClientMinPrincipal(set[2]);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ClientMinPrincipal object", e);
        }

        /*
         * Create ClientMinPrincipalType object
         */
        set[3] = set[1];

        try {
            obj[3] = new ClientMinPrincipalType(set[3]);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ClientMinPrincipalType object", e);
        }

        /*
         * Create ServerMinPrincipal object
         */
        set[4] = set[0];

        try {
            obj[4] = new ServerMinPrincipal(set[4]);
        } catch (Exception e) {
            throw new TestException("Exception has been thrown while creating"
                    + " ServerMinPrincipal object", e);
        }
        return this;
    }

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        for (int i = 0; i < obj.length; i++) {
            Object retObj = null;
            logger.log(Level.FINE, "\n\t+++++ " + (i + (int) 1) + ": "
                    + obj[i].getClass().getName());

            /*
             * Invoking elements() method on various test objects
             */
            try {
                Method elementsM =
                        obj[i].getClass().getMethod("elements", null);
                retObj = elementsM.invoke(obj[i], null);
            } catch (Exception e) {
                logger.log(Level.FINE, "Exception has been thrown while"
                        + " getting Method object for elements() method"
                        + " or while invoking the underlying method represented"
                        + " by the created Method object: " + e);
                throw new TestException(""
                        + " test failed");
            }

            /*
             * Checking result of elements() method
             */
            if (!checker(retObj, set[i])) {
                throw new TestException(""
                        + " test failed");
            }
        }
        return;
    }
}
