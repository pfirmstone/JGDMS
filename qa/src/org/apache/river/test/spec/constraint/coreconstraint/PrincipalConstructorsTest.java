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
package org.apache.river.test.spec.constraint.coreconstraint;

import java.util.logging.Level;

// org.apache.river.qa.harness
import org.apache.river.qa.harness.TestException;

// AbstractConstructorsTest
import org.apache.river.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest;

// java.util
import java.util.logging.Level;
import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;

// javax.security
import javax.security.auth.x500.X500Principal;

// java.security
import java.security.Principal;

// Davis packages
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the following constructors:
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#ClientMaxPrincipal(Principal)}
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#ClientMaxPrincipal(Principal[])}
 *     {@link net.jini.core.constraint.ClientMaxPrincipal#ClientMaxPrincipal(Collection)}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#ClientMinPrincipal(Principal)}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#ClientMinPrincipal(Principal[])}
 *     {@link net.jini.core.constraint.ClientMinPrincipal#ClientMinPrincipal(Collection)}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#ServerMinPrincipal(Principal)}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#ServerMinPrincipal(Principal[])}
 *     {@link net.jini.core.constraint.ServerMinPrincipal#ServerMinPrincipal(Collection)}
 *   All these constructors create the corresponding constraint object
 *   containing the specified principals, with duplicates removed. The arguments
 *   passed to the constructors are neither modified nor retained; subsequent
 *   changes to that arguments have no effect on the instance created.
 *   The following exceptions are thrown by the following constructors:
 *     {@link java.lang.NullPointerException} - if the argument is null
 *       ClientMaxPrincipal(Principal p)
 *       ClientMinPrincipal(Principal p)
 *       ServerMinPrincipal(Principal p)
 *       ClientMaxPrincipal(Principal[] principals)
 *       ClientMinPrincipal(Principal[] principals)
 *       ServerMinPrincipal(Principal[] principals)
 *       ClientMaxPrincipal(Collection c)
 *       ClientMinPrincipal(Collection c)
 *       ServerMinPrincipal(Collection c)
 *     {@link java.lang.NullPointerException} - if any element is null
 *       ClientMaxPrincipal(Principal[] principals)
 *       ClientMinPrincipal(Principal[] principals)
 *       ServerMinPrincipal(Principal[] principals)
 *       ClientMaxPrincipal(Collection c)
 *       ClientMinPrincipal(Collection c)
 *       ServerMinPrincipal(Collection c)
 *     {@link java.lang.IllegalArgumentException} - if the argument is empty
 *       ClientMaxPrincipal(Principal[] principals)
 *       ClientMinPrincipal(Principal[] principals)
 *       ServerMinPrincipal(Principal[] principals)
 *       ClientMaxPrincipal(Collection c)
 *       ClientMinPrincipal(Collection c)
 *       ServerMinPrincipal(Collection c)
 *     {@link java.lang.IllegalArgumentException} - if the elements do not all
 *     implement the Principal interface
 *       ClientMaxPrincipal(Collection c)
 *       ClientMinPrincipal(Collection c)
 *       ServerMinPrincipal(Collection c)
 *
 * Test Cases:
 *   TestCase #1
 *     invoking constructor
 *       public ClientMaxPrincipal(Principal p)
 *     it's expected that ClientMaxPrincipal object is created and contains
 *     the specified principal; it's expected that the argument passed to the
 *     constructor is neither modified nor retained; subsequent changes to that
 *     argument have no effect on the instance created.
 *   TestCase #2
 *     invoking constructor
 *       public ClientMaxPrincipal((Principal) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #3
 *     invoking constructor
 *       public ClientMaxPrincipal(Principal[] principals)
 *     it's expected that ClientMaxPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #4
 *     invoking constructor
 *       public ClientMaxPrincipal((Principal[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #5
 *     invoking constructor
 *       public ClientMaxPrincipal(Principal[] principals),
 *       where principals contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #6
 *     invoking constructor
 *       public ClientMaxPrincipal(new Principal[0])
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #7
 *     invoking constructor
 *       public ClientMaxPrincipal(Collection c)
 *     it's expected that ClientMaxPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #8
 *     invoking constructor
 *       public ClientMaxPrincipal((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #9
 *     invoking constructor
 *       public ClientMaxPrincipal(Collection c)
 *       where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #10
 *     invoking constructor
 *       public ClientMaxPrincipal((Collection) Collections.EMPTY_SET)
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #11
 *     invoking constructor
 *       public ClientMaxPrincipal(Collection c)
 *     where c contains an element that doesn't implement Principal interface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #12
 *     invoking constructor
 *       public ClientMinPrincipal(Principal p)
 *     it's expected that ClientMinPrincipal object is created and contains
 *     the specified principal;
 *   TestCase #13
 *     invoking constructor
 *       public ClientMinPrincipal((Principal) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #14
 *     invoking constructor
 *       public ClientMinPrincipal(Principal[] principals)
 *     it's expected that ClientMinPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #15
 *     invoking constructor
 *       public ClientMinPrincipal((Principal[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #16
 *     invoking constructor
 *       public ClientMinPrincipal(Principal[] principals),
 *       where principals contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #17
 *     invoking constructor
 *       public ClientMinPrincipal(new Principal[0])
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #18
 *     invoking constructor
 *       public ClientMinPrincipal(Collection c)
 *     it's expected that ClientMinPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #19
 *     invoking constructor
 *       public ClientMinPrincipal((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #20
 *     invoking constructor
 *       public ClientMinPrincipal(Collection c)
 *       where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #21
 *     invoking constructor
 *       public ClientMinPrincipal((Collection) Collections.EMPTY_SET)
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #22
 *     invoking constructor
 *       public ClientMinPrincipal(Collection c)
 *       where c contains an element that doesn't implement Principal interface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #23
 *     invoking constructor
 *       public ServerMinPrincipal(Principal p)
 *     it's expected that ServerMinPrincipal object is created and contains
 *     the specified principal; it's expected that the argument passed to the
 *     constructor is neither modified nor retained; subsequent changes to that
 *     argument have no effect on the instance created.
 *   TestCase #24
 *     invoking constructor
 *       public ServerMinPrincipal((Principal) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #25
 *     invoking constructor
 *       public ServerMinPrincipal(Principal[] principals)
 *     it's expected that ServerMinPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #26
 *     invoking constructor
 *       public ServerMinPrincipal((Principal[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #27
 *     invoking constructor
 *       public ServerMinPrincipal(Principal[] principals),
 *       where principals contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #28
 *     invoking constructor
 *       public ServerMinPrincipal(new Principal[0])
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #29
 *     invoking constructor
 *       public ServerMinPrincipal(Collection c)
 *     it's expected that ServerMinPrincipal object is created and contains
 *     the specified principals with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #30
 *     invoking constructor
 *       public ServerMinPrincipal((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #31
 *     invoking constructor
 *       public ServerMinPrincipal(Collection c)
 *       where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #32
 *     invoking constructor
 *       public ServerMinPrincipal((Collection) Collections.EMPTY_SET)
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #33
 *     invoking constructor
 *       public ServerMinPrincipal(Collection c)
 *       where c contains an element that doesn't implement Principal interface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *
 * Infrastructure:
 *     - {@link PrincipalConstructorsTest}
 *         performs actions; this file
 *     - {@link org.apache.river.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest}
 *         auxiliary abstract class that defines some methods
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - constructing the argument for the constructor;
 *     - invoking the corresponding constructor;
 *     - checking that the corresponding object is created with the principal(s)
 *       specified as the argument or the corresponding exception of the
 *       expected type is thrown (see a Test Case description);
 * </pre>
 */
public class PrincipalConstructorsTest extends AbstractConstructorsTest {
    // ClientMaxPrincipal constructors

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Principal p)
     */
    Object CL_MAX_PR__PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Principal p)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal((Principal) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR__PRINCIPAL_NULL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal((Principal) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Principal[] principals)
     */
    Object CL_MAX_PR__PRINCIPALS_ARRAY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Principal[] principals)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal((Principal[]) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_NULL_ARRAY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal((Principal[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Principal[] principals),
     *     where principals contains null element
     *     NullPointerException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_ARRAY_NULL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Principal[] principals),"
                    + " where principals contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(new Principal[0])
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_EMPTY_ARRAY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(new Principal[0])";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Collection c)
     */
    Object CL_MAX_PR__PRINCIPALS_COLL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal((Collection) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_NULL_COLL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Collection c)
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_COLL_NULL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal((Collection) Collections.EMPTY_SET)
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_EMPTY_COLL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal("
                    + "(Collection) Collections.EMPTY_SET)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipal(Collection c)
     *     where c contains an element that doesn't implement Principal interface
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR__PRINCIPALS_COLL_ILL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipal(Collection c),"
                    + " where c contains an element that doesn't implement"
                    + " Principal interface";
        }
    };

    // ClientMinPrincipal constructors

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Principal p)
     */
    Object CL_MIN_PR__PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Principal p)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal((Principal) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR__PRINCIPAL_NULL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal((Principal) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Principal[] principals)
     */
    Object CL_MIN_PR__PRINCIPALS_ARRAY = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Principal[] principals)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal((Principal[]) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_NULL_ARRAY = new Object() {
        public String toString() {
            return "public ClientMinPrincipal((Principal[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Principal[] principals),
     *     where principals contains null element
     *     NullPointerException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_ARRAY_NULL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Principal[] principals),"
                    + " where principals contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(new Principal[0])
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_EMPTY_ARRAY = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(new Principal[0])";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Collection c)
     */
    Object CL_MIN_PR__PRINCIPALS_COLL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal((Collection) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_NULL_COLL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Collection c)
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_COLL_NULL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal((Collection) Collections.EMPTY_SET)
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_EMPTY_COLL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal("
                    + "(Collection) Collections.EMPTY_SET)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipal(Collection c)
     *     where c contains an element that doesn't implement Principal interface
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR__PRINCIPALS_COLL_ILL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipal(Collection c),"
                    + " where c contains an element that doesn't implement"
                    + " Principal interface";
        }
    };

    // ServerMinPrincipal constructors

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Principal p)
     */
    Object SRV_MIN_PR__PRINCIPAL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Principal p)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal((Principal) null)
     *     NullPointerException is expected
     */
    Object SRV_MIN_PR__PRINCIPAL_NULL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal((Principal) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Principal[] principals)
     */
    Object SRV_MIN_PR__PRINCIPALS_ARRAY = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Principal[] principals)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal((Principal[]) null)
     *     NullPointerException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_NULL_ARRAY = new Object() {
        public String toString() {
            return "public ServerMinPrincipal((Principal[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Principal[] principals),
     *     where principals contains null element
     *     NullPointerException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_ARRAY_NULL_EL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Principal[] principals),"
                    + " where principals contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(new Principal[0])
     *     IllegalArgumentException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_EMPTY_ARRAY = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(new Principal[0])";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Collection c)
     */
    Object SRV_MIN_PR__PRINCIPALS_COLL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal((Collection) null)
     *     NullPointerException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_NULL_COLL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Collection c)
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_COLL_NULL_EL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal((Collection) Collections.EMPTY_SET)
     *     IllegalArgumentException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_EMPTY_COLL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal("
                    + "(Collection) Collections.EMPTY_SET)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ServerMinPrincipal(Collection c)
     *     where c contains an element that doesn't implement Principal interface
     *     IllegalArgumentException is expected
     */
    Object SRV_MIN_PR__PRINCIPALS_COLL_ILL_EL = new Object() {
        public String toString() {
            return "public ServerMinPrincipal(Collection c),"
                    + " where c contains an element that doesn't implement"
                    + " Principal interface";
        }
    };

    /**
     * Test Cases.
     */
    Object[] testCases = new Object[] {

        // ClientMaxPrincipal constructors
            CL_MAX_PR__PRINCIPAL,
            CL_MAX_PR__PRINCIPAL_NULL,
            CL_MAX_PR__PRINCIPALS_ARRAY,
            CL_MAX_PR__PRINCIPALS_NULL_ARRAY,
            CL_MAX_PR__PRINCIPALS_ARRAY_NULL_EL,
            CL_MAX_PR__PRINCIPALS_EMPTY_ARRAY,
            CL_MAX_PR__PRINCIPALS_COLL,
            CL_MAX_PR__PRINCIPALS_NULL_COLL,
            CL_MAX_PR__PRINCIPALS_COLL_NULL_EL,
            CL_MAX_PR__PRINCIPALS_EMPTY_COLL,
            CL_MAX_PR__PRINCIPALS_COLL_ILL_EL,

        // ClientMinPrincipal constructors
            CL_MIN_PR__PRINCIPAL,
            CL_MIN_PR__PRINCIPAL_NULL,
            CL_MIN_PR__PRINCIPALS_ARRAY,
            CL_MIN_PR__PRINCIPALS_NULL_ARRAY,
            CL_MIN_PR__PRINCIPALS_ARRAY_NULL_EL,
            CL_MIN_PR__PRINCIPALS_EMPTY_ARRAY,
            CL_MIN_PR__PRINCIPALS_COLL,
            CL_MIN_PR__PRINCIPALS_NULL_COLL,
            CL_MIN_PR__PRINCIPALS_COLL_NULL_EL,
            CL_MIN_PR__PRINCIPALS_EMPTY_COLL,
            CL_MIN_PR__PRINCIPALS_COLL_ILL_EL,

        // ServerMinPrincipal constructors
            SRV_MIN_PR__PRINCIPAL,
            SRV_MIN_PR__PRINCIPAL_NULL,
            SRV_MIN_PR__PRINCIPALS_ARRAY,
            SRV_MIN_PR__PRINCIPALS_NULL_ARRAY,
            SRV_MIN_PR__PRINCIPALS_ARRAY_NULL_EL,
            SRV_MIN_PR__PRINCIPALS_EMPTY_ARRAY,
            SRV_MIN_PR__PRINCIPALS_COLL,
            SRV_MIN_PR__PRINCIPALS_NULL_COLL,
            SRV_MIN_PR__PRINCIPALS_COLL_NULL_EL,
            SRV_MIN_PR__PRINCIPALS_EMPTY_COLL,
            SRV_MIN_PR__PRINCIPALS_COLL_ILL_EL
    };
    
    /**
     * Auxiliary method to obtain the array of the Objects that describe
     * Test Cases.
     * @return array of the Objects that describe Test Cases
     */
    public Object[] getTestCases() {
        return testCases;
    }

    /**
     * Execution of a Test Case.
     * For each Test Case the corresponding callConstructor() method is invoked.
     */
    public void runTestCase(Object testCase) throws TestException {
        logger.log(Level.INFO,
                "===== invoking constructor: " + testCase.toString());

        try {
            if (       testCase == CL_MAX_PR__PRINCIPAL
                    || testCase == CL_MIN_PR__PRINCIPAL
                    || testCase == SRV_MIN_PR__PRINCIPAL) {
                callConstructor(testCase,
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"),
                        null);
            } else if (testCase == CL_MAX_PR__PRINCIPAL_NULL
                    || testCase == CL_MIN_PR__PRINCIPAL_NULL
                    || testCase == SRV_MIN_PR__PRINCIPAL_NULL) {
                callConstructor(testCase, (X500Principal) null,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_ARRAY
                    || testCase == CL_MIN_PR__PRINCIPALS_ARRAY
                    || testCase == SRV_MIN_PR__PRINCIPALS_ARRAY) {
                X500Principal[] principals = {
                    new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"),
                    new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"),
                    new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=RU")
                };
                callConstructor(testCase, principals, null);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_NULL_ARRAY
                    || testCase == CL_MIN_PR__PRINCIPALS_NULL_ARRAY
                    || testCase == SRV_MIN_PR__PRINCIPALS_NULL_ARRAY) {
                callConstructor(testCase, (X500Principal[]) null,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_ARRAY_NULL_EL
                    || testCase == CL_MIN_PR__PRINCIPALS_ARRAY_NULL_EL
                    || testCase == SRV_MIN_PR__PRINCIPALS_ARRAY_NULL_EL) {
                X500Principal[] principals = {
                        new X500Principal(
                          "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"),
                        new X500Principal(
                          "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"),
                        null 
                };
                callConstructor(testCase, principals,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_EMPTY_ARRAY
                    || testCase == CL_MIN_PR__PRINCIPALS_EMPTY_ARRAY
                    || testCase == SRV_MIN_PR__PRINCIPALS_EMPTY_ARRAY) {
                callConstructor(testCase, new X500Principal[0],
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_COLL
                    || testCase == CL_MIN_PR__PRINCIPALS_COLL
                    || testCase == SRV_MIN_PR__PRINCIPALS_COLL) {
                ArrayList principals = new ArrayList();
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=RU"));
                callConstructor(testCase, (Collection) principals, null);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_NULL_COLL
                    || testCase == CL_MIN_PR__PRINCIPALS_NULL_COLL
                    || testCase == SRV_MIN_PR__PRINCIPALS_NULL_COLL) {
                callConstructor(testCase, (Collection) null,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_COLL_NULL_EL
                    || testCase == CL_MIN_PR__PRINCIPALS_COLL_NULL_EL
                    || testCase == SRV_MIN_PR__PRINCIPALS_COLL_NULL_EL) {
                ArrayList principals = new ArrayList();
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(null);
                callConstructor(testCase, (Collection) principals,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_EMPTY_COLL
                    || testCase == CL_MIN_PR__PRINCIPALS_EMPTY_COLL
                    || testCase == SRV_MIN_PR__PRINCIPALS_EMPTY_COLL) {
                callConstructor(testCase, (Collection) Collections.EMPTY_SET,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR__PRINCIPALS_COLL_ILL_EL
                    || testCase == CL_MIN_PR__PRINCIPALS_COLL_ILL_EL
                    || testCase == SRV_MIN_PR__PRINCIPALS_COLL_ILL_EL) {
                ArrayList principals = new ArrayList();
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(
                        new X500Principal(
                        "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
                principals.add(
                        "Element that doesn't implement Principal interface");
                callConstructor(testCase, (Collection) principals,
                        IllegalArgumentException.class);
            } else {
                logger.log(Level.FINE, "Bad Test Case: " + testCase.toString());
                throw new TestException("" + " test failed");
            }
        } catch (TestException e) {
            logger.log(Level.FINE, "Test Case failed: " + e);
            throw new TestException("" + " test failed");
        }
        return;
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes one of the following constructors depending on
     * Test Case object specified as the first argument:
     *   public ClientMaxPrincipal(Principal p)
     *   public ClientMinPrincipal(Principal p)
     *   public ServerMinPrincipal(Principal p)
     *
     * Then the following verifications are performed:
     *   - verify that the corresponding constraint object is created;
     *   - verify that the argument passed to the constructor isn't modified;
     *   - verify that the argument passed to the constructor isn't retained,
     *     i.e. subsequent changes to that argument have no effect on the instance
     *     instance created.
     * </pre>
     * @param tc Test Case object
     * @param pr principal to be used as the argument for the constructor
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, X500Principal pr, Class ex)
            throws TestException {

        /*
         * Get the distinguished name of the X500Principal object specified
         * as an argument for the constructors before invoking the constructors.
         */
        String pr_name_before = null;

        if (       tc == CL_MAX_PR__PRINCIPAL
                || tc == CL_MIN_PR__PRINCIPAL
                || tc == SRV_MIN_PR__PRINCIPAL) {
            pr_name_before = pr.getName(X500Principal.CANONICAL);
        }

        InvocationConstraint ic = null;

        try {
            if (       tc == CL_MAX_PR__PRINCIPAL
                    || tc == CL_MAX_PR__PRINCIPAL_NULL) {
                ic = new ClientMaxPrincipal((Principal) pr);
            } else if (tc == CL_MIN_PR__PRINCIPAL
                    || tc == CL_MIN_PR__PRINCIPAL_NULL) {
                ic = new ClientMinPrincipal((Principal) pr);
            } else if (tc == SRV_MIN_PR__PRINCIPAL
                    || tc == SRV_MIN_PR__PRINCIPAL_NULL) {
                ic = new ServerMinPrincipal((Principal) pr);
            }

            // If some Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPAL_NULL
                    || tc == CL_MIN_PR__PRINCIPAL_NULL
                    || tc == SRV_MIN_PR__PRINCIPAL_NULL) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor "
                        + tc.toString());
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            // If no Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPAL
                    || tc == CL_MIN_PR__PRINCIPAL
                    || tc == SRV_MIN_PR__PRINCIPAL) {
                throw new TestException("Exception while invoking constructor "
                        + tc.toString(), e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor " + tc.toString());
            } else {
                return;
            }
        }

        // logger.log(Level.INFO, "Returned object: " + ic.toString());

        /*
         * Verify that the corresponding constraint object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE, "Constraint object hasn't been created");
            throw new TestException("Constraint object hasn't been created");
        }

        if (tc == CL_MAX_PR__PRINCIPAL
                && !(ic instanceof ClientMaxPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMaxPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMaxPrincipal object is returned");
        } else if (tc == CL_MIN_PR__PRINCIPAL
                && !(ic instanceof ClientMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMinPrincipal object is returned");
        } else if (tc == SRV_MIN_PR__PRINCIPAL
                && !(ic instanceof ServerMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ServerMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ServerMinPrincipal object is returned");
        }

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Get the distinguished name of the X500Principal object specified
         * as an argument for the constructors after invoking the constructors.
         */
        String pr_name_after = pr.getName(X500Principal.CANONICAL);
        // logger.log(Level.INFO, "The distinguished name of X500Principal object"
        // + " before invoking the constructor: " + pr_name_before);
        // logger.log(Level.INFO, "The distinguished name of X500Principal object"
        // + " after invoking the constructor:  " + pr_name_after);
        if (!pr_name_after.equals(pr_name_before)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is modified");
            throw new TestException("The argument passed to the constructor"
                    + " is modified");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't modified");

        /*
         * Verify that the argument passed to the constructor isn't retained;
         * subsequent changes to that argument have no effect on the instance
         * created. Compare set of all of the principals from the created
         * object before and after changing the argument.
         */
        Set pr_before = null;
        Set pr_after = null;

        if (       tc == CL_MAX_PR__PRINCIPAL) {
            pr_before = ((ClientMaxPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMaxPrincipal) ic).elements();
        } else if (tc == CL_MIN_PR__PRINCIPAL) {
            pr_before = ((ClientMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMinPrincipal) ic).elements();
        } else if (tc == SRV_MIN_PR__PRINCIPAL) {
            pr_before = ((ServerMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ServerMinPrincipal) ic).elements();
        }

        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " before invoking the constructor: " + pr_before);
        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " after invoking the constructor : " + pr_after);
        if (!pr_after.equals(pr_before)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is retained");
            throw new TestException("IThe argument passed to the constructor"
                    + " is retained");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes one of the following constructors depending on
     * Test Case object specified as the first argument:
     *   public ClientMaxPrincipal(Principal[] principals)
     *   public ClientMinPrincipal(Principal[] principals)
     *   public ServerMinPrincipal(Principal[] principals)
     *
     * Then the following verifications are performed:
     *   - verify that the corresponding constraint object is created with
     *     duplicates removed;
     *   - verify that the argument passed to the constructor isn't modified;
     *   - verify that the argument passed to the constructor isn't retained,
     *     i.e. subsequent changes to that argument have no effect on the instance
     *     instance created.
     * </pre>
     * @param tc Test Case object
     * @param pr principal to be used as the argument for the constructor
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, X500Principal[] pr, Class ex)
            throws TestException {

        /*
         * Copy object specified as an argument for the constructors before
         * invoking the constructors.
         */
        X500Principal[] pr_copy = null;

        if (       tc == CL_MAX_PR__PRINCIPALS_ARRAY
                || tc == CL_MIN_PR__PRINCIPALS_ARRAY
                || tc == SRV_MIN_PR__PRINCIPALS_ARRAY) {
            pr_copy = new X500Principal[pr.length];
            System.arraycopy(pr, 0, pr_copy, 0, pr.length);
        }

        InvocationConstraint ic = null;

        try {
            if (       tc == CL_MAX_PR__PRINCIPALS_ARRAY
                    || tc == CL_MAX_PR__PRINCIPALS_NULL_ARRAY
                    || tc == CL_MAX_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == CL_MAX_PR__PRINCIPALS_EMPTY_ARRAY) {
                ic = new ClientMaxPrincipal((Principal[]) pr);
            } else if (tc == CL_MIN_PR__PRINCIPALS_ARRAY
                    || tc == CL_MIN_PR__PRINCIPALS_NULL_ARRAY
                    || tc == CL_MIN_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == CL_MIN_PR__PRINCIPALS_EMPTY_ARRAY) {
                ic = new ClientMinPrincipal((Principal[]) pr);
            } else if (tc == SRV_MIN_PR__PRINCIPALS_ARRAY
                    || tc == SRV_MIN_PR__PRINCIPALS_NULL_ARRAY
                    || tc == SRV_MIN_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == SRV_MIN_PR__PRINCIPALS_EMPTY_ARRAY) {
                ic = new ServerMinPrincipal((Principal[]) pr);
            }

            // If some Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPALS_NULL_ARRAY
                    || tc == CL_MAX_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == CL_MAX_PR__PRINCIPALS_EMPTY_ARRAY
                    || tc == CL_MIN_PR__PRINCIPALS_NULL_ARRAY
                    || tc == CL_MIN_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == CL_MIN_PR__PRINCIPALS_EMPTY_ARRAY
                    || tc == SRV_MIN_PR__PRINCIPALS_NULL_ARRAY
                    || tc == SRV_MIN_PR__PRINCIPALS_ARRAY_NULL_EL
                    || tc == SRV_MIN_PR__PRINCIPALS_EMPTY_ARRAY) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor "
                        + tc.toString());
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            // If no Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPALS_ARRAY
                    || tc == CL_MIN_PR__PRINCIPALS_ARRAY
                    || tc == SRV_MIN_PR__PRINCIPALS_ARRAY) {
                throw new TestException("Exception while invoking constructor "
                        + tc.toString(), e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor " + tc.toString());
            } else {
                return;
            }
        }

        // logger.log(Level.INFO, "Returned object: " + ic.toString());

        /*
         * Verify that the corresponding constraint object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE, "Constraint object hasn't been created");
            throw new TestException("Constraint object hasn't been created");
        }

        if (tc == CL_MAX_PR__PRINCIPALS_ARRAY
                && !(ic instanceof ClientMaxPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMaxPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMaxPrincipal object is returned");
        } else if (tc == CL_MIN_PR__PRINCIPALS_ARRAY
                && !(ic instanceof ClientMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMinPrincipal object is returned");
        } else if (tc == SRV_MIN_PR__PRINCIPALS_ARRAY
                && !(ic instanceof ServerMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ServerMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ServerMinPrincipal object is returned");
        }

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */

        // logger.log(Level.INFO, "Argument before invoking the constructor:");
        // for (int i = 0; i < pr_copy.length; i++) {
        // logger.log(Level.INFO, "pr_copy[" + i + "]:: " + pr_copy[i]);
        // }
        // logger.log(Level.INFO, "Argument after invoking the constructor :");
        // for (int i = 0; i < pr.length; i++) {
        // logger.log(Level.INFO, "pr[" + i + "]:: " + pr[i]);
        // }
        if (!Arrays.equals(pr, pr_copy)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is modified");
            throw new TestException("The argument passed to the constructor"
                    + " is modified");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't modified");

        /*
         * Verify that the argument passed to the constructor isn't retained;
         * subsequent changes to that argument have no effect on the instance
         * created. Compare set of all of the principals from the created
         * object before and after changing the argument.
         */
        Set pr_before = null;
        Set pr_after = null;

        if (       tc == CL_MAX_PR__PRINCIPALS_ARRAY) {
            pr_before = ((ClientMaxPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMaxPrincipal) ic).elements();
        } else if (tc == CL_MIN_PR__PRINCIPALS_ARRAY) {
            pr_before = ((ClientMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMinPrincipal) ic).elements();
        } else if (tc == SRV_MIN_PR__PRINCIPALS_ARRAY) {
            pr_before = ((ServerMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ServerMinPrincipal) ic).elements();
        }

        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " before invoking the constructor: " + pr_before);
        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " after invoking the constructor : " + pr_after);
        if (!pr_after.equals(pr_before)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is retained");
            throw new TestException("IThe argument passed to the constructor"
                    + " is retained");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(pr_before);
        logger.log(Level.FINE, "Duplicates have been removed");
    }

    /**
     * This method invokes constructor and checks the result.
     * <pre>
     * The method invokes one of the following constructors depending on
     * Test Case object specified as the first argument:
     *   public ClientMaxPrincipal(Collection c)
     *   public ClientMinPrincipal(Collection c)
     *   public ServerMinPrincipal(Collection c)
     *
     * Then the following verifications are performed:
     *   - verify that the corresponding constraint object is created with
     *     duplicates removed;
     *   - verify that the argument passed to the constructor isn't modified;
     *   - verify that the argument passed to the constructor isn't retained,
     *     i.e. subsequent changes to that argument have no effect on the instance
     *     instance created.
     * </pre>
     * @param tc Test Case object
     * @param pr principal to be used as the argument for the constructor
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, Collection pr, Class ex)
            throws TestException {

        /*
         * Copy object specified as an argument for the constructors before
         * invoking the constructors.
         */
        ArrayList pr_copy = null;

        if (       tc == CL_MAX_PR__PRINCIPALS_COLL
                || tc == CL_MIN_PR__PRINCIPALS_COLL
                || tc == SRV_MIN_PR__PRINCIPALS_COLL) {
            pr_copy = new ArrayList(pr);
        }

        InvocationConstraint ic = null;

        try {
            if (       tc == CL_MAX_PR__PRINCIPALS_COLL
                    || tc == CL_MAX_PR__PRINCIPALS_NULL_COLL
                    || tc == CL_MAX_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == CL_MAX_PR__PRINCIPALS_EMPTY_COLL
                    || tc == CL_MAX_PR__PRINCIPALS_COLL_ILL_EL) {
                ic = new ClientMaxPrincipal((Collection) pr);
            } else if (tc == CL_MIN_PR__PRINCIPALS_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_NULL_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == CL_MIN_PR__PRINCIPALS_EMPTY_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_COLL_ILL_EL) {
                ic = new ClientMinPrincipal((Collection) pr);
            } else if (tc == SRV_MIN_PR__PRINCIPALS_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_NULL_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == SRV_MIN_PR__PRINCIPALS_EMPTY_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_COLL_ILL_EL) {
                ic = new ServerMinPrincipal((Collection) pr);
            }

            // If some Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPALS_NULL_COLL
                    || tc == CL_MAX_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == CL_MAX_PR__PRINCIPALS_EMPTY_COLL
                    || tc == CL_MAX_PR__PRINCIPALS_COLL_ILL_EL
                    || tc == CL_MIN_PR__PRINCIPALS_NULL_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == CL_MIN_PR__PRINCIPALS_EMPTY_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_COLL_ILL_EL
                    || tc == SRV_MIN_PR__PRINCIPALS_NULL_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_COLL_NULL_EL
                    || tc == SRV_MIN_PR__PRINCIPALS_EMPTY_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_COLL_ILL_EL) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor "
                        + tc.toString());
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception while invoking constructor " + tc.toString()
                    + ":: " + e);
            // If no Exception is expected
            if (       tc == CL_MAX_PR__PRINCIPALS_COLL
                    || tc == CL_MIN_PR__PRINCIPALS_COLL
                    || tc == SRV_MIN_PR__PRINCIPALS_COLL) {
                throw new TestException("Exception while invoking constructor "
                        + tc.toString(), e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor " + tc.toString());
            } else {
                return;
            }
        }

        // logger.log(Level.INFO, "Returned object: " + ic.toString());

        /*
         * Verify that the corresponding constraint object is created.
         */
        if (ic == null) {
            logger.log(Level.FINE, "Constraint object hasn't been created");
            throw new TestException("Constraint object hasn't been created");
        }

        if (tc == CL_MAX_PR__PRINCIPALS_COLL
                && !(ic instanceof ClientMaxPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMaxPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMaxPrincipal object is returned");
        } else if (tc == CL_MIN_PR__PRINCIPALS_COLL
                && !(ic instanceof ClientMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ClientMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ClientMinPrincipal object is returned");
        } else if (tc == SRV_MIN_PR__PRINCIPALS_COLL
                && !(ic instanceof ServerMinPrincipal)) {
            logger.log(Level.FINE,
                    "Expected that ServerMinPrincipal object is returned");
            throw new TestException(
                    "Expected that ServerMinPrincipal object is returned");
        }

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */

        // logger.log(Level.INFO, "Argument before invoking the constructor: "
        // + pr_copy);
        // logger.log(Level.INFO, "Argument after invoking the constructor : "
        // + pr);
        if (!pr.equals(pr_copy)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is modified");
            throw new TestException("The argument passed to the constructor"
                    + " is modified");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't modified");

        /*
         * Verify that the argument passed to the constructor isn't retained;
         * subsequent changes to that argument have no effect on the instance
         * created. Compare set of all of the principals from the created
         * object before and after changing the argument.
         */
        Set pr_before = null;
        Set pr_after = null;

        if (       tc == CL_MAX_PR__PRINCIPALS_COLL) {
            pr_before = ((ClientMaxPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMaxPrincipal) ic).elements();
        } else if (tc == CL_MIN_PR__PRINCIPALS_COLL) {
            pr_before = ((ClientMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ClientMinPrincipal) ic).elements();
        } else if (tc == SRV_MIN_PR__PRINCIPALS_COLL) {
            pr_before = ((ServerMinPrincipal) ic).elements();
            pr = null;
            pr_after = ((ServerMinPrincipal) ic).elements();
        }

        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " before invoking the constructor: " + pr_before);
        // logger.log(Level.INFO, "Set of all of the principals of created object"
        // + " after invoking the constructor : " + pr_after);
        if (!pr_after.equals(pr_before)) {
            logger.log(Level.FINE,
                    "The argument passed to the constructor is retained");
            throw new TestException("IThe argument passed to the constructor"
                    + " is retained");
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(pr_before);
        logger.log(Level.FINE, "Duplicates have been removed");
    }

    /**
     * Verify if the specified set contains duplicates.
     *
     * @param set set to be verified
     * @throws TestException if there are duplicates in the specified set
     */
    private void checkDuplicates(Set set) throws TestException {
        Object[] arr = set.toArray();

        for (int j = 0; j < arr.length - 1; j++) {
            for (int i = j + 1; i < arr.length; i++) {
                if (arr[i].equals(arr[j])) {
                    logger.log(Level.FINE, "Duplicates aren't removed");
                    logger.log(Level.FINE, "arr[" + j + "]:: " + arr[j]);
                    logger.log(Level.FINE, "arr[" + i + "]:: " + arr[i]);
                    throw new TestException("Duplicates aren't removed");
                }
            }
        }
    }
}
