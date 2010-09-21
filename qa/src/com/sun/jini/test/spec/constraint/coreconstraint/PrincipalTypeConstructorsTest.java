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
import com.sun.jini.qa.harness.TestException;

// AbstractConstructorsTest
import com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest;

// java.util
import java.util.logging.Level;
import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;

// javax.security
import javax.security.auth.x500.X500Principal;
import javax.security.auth.kerberos.KerberosPrincipal;

// java.lang.reflect
import java.lang.reflect.Method;

// Davis packages
import net.jini.core.constraint.ClientMaxPrincipalType;
import net.jini.core.constraint.ClientMinPrincipalType;
import net.jini.core.constraint.InvocationConstraint;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the following constructors:
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#ClientMaxPrincipalType(Class)}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#ClientMaxPrincipalType(Class[])}
 *     {@link net.jini.core.constraint.ClientMaxPrincipalType#ClientMaxPrincipalType(Collection)}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#ClientMinPrincipalType(Class)}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#ClientMinPrincipalType(Class[])}
 *     {@link net.jini.core.constraint.ClientMinPrincipalType#ClientMinPrincipalType(Collection)}
 *   All these constructors create the corresponding constraint object
 *   containing the specified classes, with duplicates removed. The arguments
 *   passed to the constructors are neither modified nor retained; subsequent
 *   changes to that arguments have no effect on the instance created.
 *   The following exceptions are thrown by the following constructors:
 *     {@link java.lang.NullPointerException} - if the argument is null
 *       ClientMaxPrincipalType(Class p)
 *       ClientMinPrincipalType(Class p)
 *       ClientMaxPrincipalType(Class[] classes)
 *       ClientMinPrincipalType(Class[] classes)
 *       ClientMaxPrincipalType(Collection c)
 *       ClientMinPrincipalType(Collection c)
 *     {@link java.lang.NullPointerException} - if any element is null
 *       ClientMaxPrincipalType(Class[] classes)
 *       ClientMinPrincipalType(Class[] classes)
 *       ClientMaxPrincipalType(Collection c)
 *       ClientMinPrincipalType(Collection c)
 *     {@link java.lang.IllegalArgumentException} - if the argument is a
 *     primitive type, an array type, or a final class that does not have
 *     {@link java.security.Principal} as a superinterface
 *       ClientMaxPrincipalType(Class p)
 *       ClientMinPrincipalType(Class p)
 *     {@link java.lang.IllegalArgumentException} - if the argument is empty, or
 *     if any element is a primitive type, an array type, or a final class that
 *     does not have {@link java.security.Principal} as a superinterface
 *       ClientMaxPrincipalType(Class[] classes)
 *       ClientMinPrincipalType(Class[] classes)
 *       ClientMaxPrincipalType(Collection c)
 *       ClientMinPrincipalType(Collection c)
 *     {@link java.lang.IllegalArgumentException} - if any element is not a
 *     {@link java.lang.Class}
 *       ClientMaxPrincipalType(Collection c)
 *       ClientMinPrincipalType(Collection c)
 *
 * Test Cases:
 *   TestCase #1
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class clazz)
 *     it's expected that ClientMaxPrincipalType object is created and contains
 *     the specified class; it's expected that the argument passed to the
 *     constructor is neither modified nor retained; subsequent changes to that
 *     argument have no effect on the instance created.
 *   TestCase #2
 *     invoking constructor
 *       public ClientMaxPrincipalType((Class) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #3
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class[] classes)
 *     it's expected that ClientMaxPrincipalType object is created and contains
 *     the specified classes with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #4
 *     invoking constructor
 *       public ClientMaxPrincipalType((Class[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #5
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class[] classes),
 *         where classes contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #6
 *     invoking constructor
 *       public ClientMaxPrincipalType(new Class[0])
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #7
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class[] classes)
 *         where classes contains element that is a primitive type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #8
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class[] classes)
 *         where classes contains element that is a array type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #9
 *     invoking constructor
 *       public ClientMaxPrincipalType(Class[] classes)
 *         where classes contains element that is a final class that does not
 *               have Principal as a superinterface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #10
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *     it's expected that ClientMaxPrincipalType object is created and contains
 *     the specified classes with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #11
 *     invoking constructor
 *       public ClientMaxPrincipalType((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #12
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *         where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #13
 *     invoking constructor
 *       public ClientMaxPrincipalType((Collection) Collections.EMPTY_SET)
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #14
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *         where c contains element that is not a Class
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #15
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *         where c contains element that is a primitive type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #16
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *         where c contains element that is a array type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #17
 *     invoking constructor
 *       public ClientMaxPrincipalType(Collection c)
 *         where c contains element that is a final class that does not
 *               have Principal as a superinterface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #18
 *     invoking constructor
 *       public ClientMinPrincipalType(Class clazz)
 *     it's expected that ClientMinPrincipalType object is created and contains
 *     the specified class; it's expected that the argument passed to the
 *     constructor is neither modified nor retained; subsequent changes to that
 *     argument have no effect on the instance created.
 *   TestCase #19
 *     invoking constructor
 *       public ClientMinPrincipalType((Class) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #20
 *     invoking constructor
 *       public ClientMinPrincipalType(Class[] classes)
 *     it's expected that ClientMinPrincipalType object is created and contains
 *     the specified classes with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #21
 *     invoking constructor
 *       public ClientMinPrincipalType((Class[]) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #22
 *     invoking constructor
 *       public ClientMinPrincipalType(Class[] classes),
 *         where classes contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #23
 *     invoking constructor
 *       public ClientMinPrincipalType(new Class[0])
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #24
 *     invoking constructor
 *       public ClientMinPrincipalType(Class[] classes)
 *         where classes contains element that is a primitive type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #25
 *     invoking constructor
 *       public ClientMinPrincipalType(Class[] classes)
 *         where classes contains element that is a array type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #26
 *     invoking constructor
 *       public ClientMinPrincipalType(Class[] classes)
 *         where classes contains element that is a final class that does not
 *               have Principal as a superinterface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #27
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *     it's expected that ClientMinPrincipalType object is created and contains
 *     the specified classes with duplicates removed; it's expected that the
 *     argument passed to the constructor is neither modified nor retained;
 *     subsequent changes to that argument have no effect on the instance
 *     created.
 *   TestCase #28
 *     invoking constructor
 *       public ClientMinPrincipalType((Collection) null)
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #29
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *         where c contains null element
 *     it's expected that java.lang.NullPointerException is thrown;
 *   TestCase #30
 *     invoking constructor
 *       public ClientMinPrincipalType((Collection) Collections.EMPTY_SET)
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #31
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *         where c contains element that is not a Class
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #32
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *         where c contains element that is a primitive type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #33
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *         where c contains element that is a array type
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *   TestCase #34
 *     invoking constructor
 *       public ClientMinPrincipalType(Collection c)
 *         where c contains element that is a final class that does not
 *               have Principal as a superinterface
 *     it's expected that java.lang.IllegalArgumentException is thrown;
 *
 * Infrastructure:
 *     - {@link PrincipalTypeConstructorsTest}
 *         performs actions; this file
 *     - {@link PrincipalTypeConstructorsTest.NotPrincipal}
 *         an auxiliary final class that does not have {@link java.security.Principal}
 *         as a superinterface
 *     - {@link com.sun.jini.test.spec.constraint.coreconstraint.util.AbstractConstructorsTest}
 *         auxiliary abstract class that defines some methods
 *
 * Actions:
 *   Test performs the following steps in each Test Case:
 *     - constructing the argument for the constructor;
 *     - invoking the corresponding constructor;
 *     - checking that the corresponding object is created with the class(es)
 *       specified as the argument or the corresponding exception of the
 *       expected type is thrown (see a Test Case description);
 * </pre>
 */
public class PrincipalTypeConstructorsTest extends AbstractConstructorsTest {

    // ClientMaxPrincipalType constructors

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class clazz)
     */
    Object CL_MAX_PR_TYPE__CLASS = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class clazz)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType((Class) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR_TYPE__CLASS__NULL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType((Class) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class[] classes)
     */
    Object CL_MAX_PR_TYPE__ARRAY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class[] classes)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType((Class[]) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__NULL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType((Class[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class[] classes),
     *     where classes contains null element
     *     NullPointerException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__NULL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class[] classes),"
                    + " where classes contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(new Class[0])
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__EMPTY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(new Class[0])";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class[] classes)
     *     where classes contains element that is a primitive type
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__PRIM_TYPE = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class[] classes),"
                    + " where classes contains element that is a primitive"
                    + " type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class[] classes)
     *     where classes contains element that is a array type
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__ARRAY_TYPE = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class[] classes),"
                    + " where classes contains element that is a array type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Class[] classes)
     *     where classes contains element that is a final class that does not
     *           have Principal as a superinterface
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__ARRAY__NOT_PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Class[] classes),"
                    + " where classes contains element that is a final class"
                    + " that does not have Principal as a superinterface";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     */
    Object CL_MAX_PR_TYPE__COLL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType((Collection) null)
     *     NullPointerException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__NULL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__NULL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType((Collection) Collections.EMPTY_SET)
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__EMPTY = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType("
                    + "(Collection) Collections.EMPTY_SET)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     *     where c contains element that is not a Class
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__ILL_EL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c),"
                    + " where c contains element that is not a Class";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     *     where c contains element that is a primitive type
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__PRIM_TYPE = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c),"
                    + " where c contains element that is a primitive type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     *     where c contains element that is a array type
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__ARRAY_TYPE = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c),"
                    + " where c contains element that is a array type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMaxPrincipalType(Collection c)
     *     where c contains element that is a final class that does not
     *           have Principal as a superinterface
     *     IllegalArgumentException is expected
     */
    Object CL_MAX_PR_TYPE__COLL__NOT_PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMaxPrincipalType(Collection c),"
                    + " where c contains element that is a final class"
                    + " that does not have Principal as a superinterface";
        }
    };

    // ClientMinPrincipalType constructors

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class clazz)
     */
    Object CL_MIN_PR_TYPE__CLASS = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class clazz)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType((Class) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR_TYPE__CLASS__NULL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType((Class) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class[] classes)
     */
    Object CL_MIN_PR_TYPE__ARRAY = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class[] classes)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType((Class[]) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__NULL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType((Class[]) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class[] classes),
     *     where classes contains null element
     *     NullPointerException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__NULL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class[] classes),"
                    + " where classes contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(new Class[0])
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__EMPTY = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(new Class[0])";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class[] classes)
     *     where classes contains element that is a primitive type
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__PRIM_TYPE = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class[] classes),"
                    + " where classes contains element that is a primitive"
                    + " type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class[] classes)
     *     where classes contains element that is a array type
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__ARRAY_TYPE = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class[] classes),"
                    + " where classes contains element that is a array type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Class[] classes)
     *     where classes contains element that is a final class that does not
     *           have Principal as a superinterface
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__ARRAY__NOT_PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Class[] classes),"
                    + " where classes contains element that is a final class"
                    + " that does not have Principal as a superinterface";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     */
    Object CL_MIN_PR_TYPE__COLL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType((Collection) null)
     *     NullPointerException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__NULL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType((Collection) null)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     *     where c contains null element
     *     NullPointerException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__NULL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c),"
                    + " where c contains null element";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType((Collection) Collections.EMPTY_SET)
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__EMPTY = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType("
                    + "(Collection) Collections.EMPTY_SET)";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     *     where c contains element that is not a Class
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__ILL_EL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c),"
                    + " where c contains element that is not a Class";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     *     where c contains element that is a primitive type
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__PRIM_TYPE = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c),"
                    + " where c contains element that is a primitive type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     *     where c contains element that is a array type
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__ARRAY_TYPE = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c),"
                    + " where c contains element that is a array type";
        }
    };

    /**
     * An object to point to Test Case using constructor:
     *   public ClientMinPrincipalType(Collection c)
     *     where c contains element that is a final class that does not
     *           have Principal as a superinterface
     *     IllegalArgumentException is expected
     */
    Object CL_MIN_PR_TYPE__COLL__NOT_PRINCIPAL = new Object() {
        public String toString() {
            return "public ClientMinPrincipalType(Collection c),"
                    + " where c contains element that is a final class"
                    + " that does not have Principal as a superinterface";
        }
    };


    /**
     * An auxiliary final class that does not have {@link java.security.Principal}
     * interface as a superinterface.
     */
    public final class NotPrincipal extends Object {

        public String toString() {
            return "A final class that does not have Principal as a"
                    + " superinterface";
        }
    }

    /**
     * Test Cases.
     */
    Object[] testCases = new Object[] {
        // ClientMaxPrincipalType constructors
            CL_MAX_PR_TYPE__CLASS,
            CL_MAX_PR_TYPE__CLASS__NULL,
            CL_MAX_PR_TYPE__ARRAY,
            CL_MAX_PR_TYPE__ARRAY__NULL,
            CL_MAX_PR_TYPE__ARRAY__NULL_EL,
            CL_MAX_PR_TYPE__ARRAY__EMPTY,
            CL_MAX_PR_TYPE__ARRAY__PRIM_TYPE,
            CL_MAX_PR_TYPE__ARRAY__ARRAY_TYPE,
            CL_MAX_PR_TYPE__ARRAY__NOT_PRINCIPAL,
            CL_MAX_PR_TYPE__COLL,
            CL_MAX_PR_TYPE__COLL__NULL,
            CL_MAX_PR_TYPE__COLL__NULL_EL,
            CL_MAX_PR_TYPE__COLL__EMPTY,
            CL_MAX_PR_TYPE__COLL__ILL_EL,
            CL_MAX_PR_TYPE__COLL__PRIM_TYPE,
            CL_MAX_PR_TYPE__COLL__ARRAY_TYPE,
            CL_MAX_PR_TYPE__COLL__NOT_PRINCIPAL,

        // ClientMinPrincipalType constructors
            CL_MIN_PR_TYPE__CLASS,
            CL_MIN_PR_TYPE__CLASS__NULL,
            CL_MIN_PR_TYPE__ARRAY,
            CL_MIN_PR_TYPE__ARRAY__NULL,
            CL_MIN_PR_TYPE__ARRAY__NULL_EL,
            CL_MIN_PR_TYPE__ARRAY__EMPTY,
            CL_MIN_PR_TYPE__ARRAY__PRIM_TYPE,
            CL_MIN_PR_TYPE__ARRAY__ARRAY_TYPE,
            CL_MIN_PR_TYPE__ARRAY__NOT_PRINCIPAL,
            CL_MIN_PR_TYPE__COLL,
            CL_MIN_PR_TYPE__COLL__NULL,
            CL_MIN_PR_TYPE__COLL__NULL_EL,
            CL_MIN_PR_TYPE__COLL__EMPTY,
            CL_MIN_PR_TYPE__COLL__ILL_EL,
            CL_MIN_PR_TYPE__COLL__PRIM_TYPE,
            CL_MIN_PR_TYPE__COLL__ARRAY_TYPE,
            CL_MIN_PR_TYPE__COLL__NOT_PRINCIPAL
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
            if (       testCase == CL_MAX_PR_TYPE__CLASS
                    || testCase == CL_MIN_PR_TYPE__CLASS) {
                callConstructor(testCase,
                        new Class[] { X500Principal.class },
                        null);
            } else if (testCase == CL_MAX_PR_TYPE__CLASS__NULL
                    || testCase == CL_MIN_PR_TYPE__CLASS__NULL) {
                callConstructor(testCase,
                        new Class[] { null },
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY
                    || testCase == CL_MIN_PR_TYPE__ARRAY) {
                Class[] classes = {
                        X500Principal.class,
                        X500Principal.class,
                        KerberosPrincipal.class
                };
                callConstructor(testCase, classes, null);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__NULL
                    || testCase == CL_MIN_PR_TYPE__ARRAY__NULL) {
                callConstructor(testCase, (Class[]) null,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__NULL_EL
                    || testCase == CL_MIN_PR_TYPE__ARRAY__NULL_EL) {
                Class[] classes = {
                        X500Principal.class,
                        KerberosPrincipal.class,
                        null
                };
                callConstructor(testCase, classes, NullPointerException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__EMPTY
                    || testCase == CL_MIN_PR_TYPE__ARRAY__EMPTY) {
                callConstructor(testCase, new Class[0],
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__PRIM_TYPE
                    || testCase == CL_MIN_PR_TYPE__ARRAY__PRIM_TYPE) {
                Class[] classes = {
                        X500Principal.class,
                        KerberosPrincipal.class,
                        int.class
                };
                callConstructor(testCase, classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__ARRAY_TYPE
                    || testCase == CL_MIN_PR_TYPE__ARRAY__ARRAY_TYPE) {
                Class[] classes = {
                        X500Principal.class,
                        KerberosPrincipal.class,
                        int[].class
                };
                callConstructor(testCase, classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__ARRAY__NOT_PRINCIPAL
                    || testCase == CL_MIN_PR_TYPE__ARRAY__NOT_PRINCIPAL) {
                Class[] classes = {
                        X500Principal.class,
                        KerberosPrincipal.class,
                        NotPrincipal.class
                };
                callConstructor(testCase, classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL
                    || testCase == CL_MIN_PR_TYPE__COLL) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                callConstructor(testCase, (Collection) classes, null);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__NULL
                    || testCase == CL_MIN_PR_TYPE__COLL__NULL) {
                callConstructor(testCase, (Collection) null,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__NULL_EL
                    || testCase == CL_MIN_PR_TYPE__COLL__NULL_EL) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                classes.add(null);
                callConstructor(testCase, (Collection) classes,
                        NullPointerException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__EMPTY
                    || testCase == CL_MIN_PR_TYPE__COLL__EMPTY) {
                callConstructor(testCase, (Collection) Collections.EMPTY_SET,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__ILL_EL
                    || testCase == CL_MIN_PR_TYPE__COLL__ILL_EL) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                classes.add("Element that is not a Class");
                callConstructor(testCase, (Collection) classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__PRIM_TYPE
                    || testCase == CL_MIN_PR_TYPE__COLL__PRIM_TYPE) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                classes.add(int.class);
                callConstructor(testCase, (Collection) classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__ARRAY_TYPE
                    || testCase == CL_MIN_PR_TYPE__COLL__ARRAY_TYPE) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                classes.add(int[].class);
                callConstructor(testCase, (Collection) classes,
                        IllegalArgumentException.class);
            } else if (testCase == CL_MAX_PR_TYPE__COLL__NOT_PRINCIPAL
                    || testCase == CL_MIN_PR_TYPE__COLL__NOT_PRINCIPAL) {
                ArrayList classes = new ArrayList();
                classes.add(X500Principal.class);
                classes.add(KerberosPrincipal.class);
                classes.add(NotPrincipal.class);
                callConstructor(testCase, (Collection) classes,
                        IllegalArgumentException.class);
            } else {
                logger.log(Level.FINE, "Bad Test Case: " + testCase.toString());
                throw new TestException(""
                        + " test failed");
            }
        } catch (TestException e) {
            logger.log(Level.FINE, "Test Case failed: " + e);
            throw new TestException(""
                    + " test failed");
        }
        return;
    }

    /**
     * This method invokes the constructor specified by the Test Case and checks
     * the result.
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
     * @param ex expected type of exception that should be thrown by the
     * constructor or null if no exception is expected
     * @throws TestException if any verification fails
     */
    protected void callConstructor(Object tc, Class[] cl, Class ex)
            throws TestException {

        /*
         * Copy object specified as an argument for the constructor before
         * invoking the constructor.
         */
        Class[] cl_copy = null;
        if (       tc == CL_MAX_PR_TYPE__CLASS
                || tc == CL_MIN_PR_TYPE__CLASS
                || tc == CL_MAX_PR_TYPE__ARRAY
                || tc == CL_MIN_PR_TYPE__ARRAY) {
            cl_copy = new Class[cl.length];
            System.arraycopy(cl, 0, cl_copy, 0, cl.length);
        }
        InvocationConstraint ic = null;

        try {
            if (       tc == CL_MAX_PR_TYPE__CLASS
                    || tc == CL_MAX_PR_TYPE__CLASS__NULL) {
                // Invoking ClientMaxPrincipalType(Class clazz) constructor
                ic = new ClientMaxPrincipalType((Class) cl[0]);
            } else if (tc == CL_MIN_PR_TYPE__CLASS
                    || tc == CL_MIN_PR_TYPE__CLASS__NULL) {
                // Invoking ClientMinPrincipalType(Class clazz) constructor
                ic = new ClientMinPrincipalType((Class) cl[0]);
            } else if (tc == CL_MAX_PR_TYPE__ARRAY
                    || tc == CL_MAX_PR_TYPE__ARRAY__NULL
                    || tc == CL_MAX_PR_TYPE__ARRAY__NULL_EL
                    || tc == CL_MAX_PR_TYPE__ARRAY__EMPTY
                    || tc == CL_MAX_PR_TYPE__ARRAY__PRIM_TYPE
                    || tc == CL_MAX_PR_TYPE__ARRAY__ARRAY_TYPE
                    || tc == CL_MAX_PR_TYPE__ARRAY__NOT_PRINCIPAL) {
                // Invoking ClientMaxPrincipalType(Class[] classes) constructor
                ic = new ClientMaxPrincipalType((Class[]) cl);
            } else if (tc == CL_MIN_PR_TYPE__ARRAY
                    || tc == CL_MIN_PR_TYPE__ARRAY__NULL
                    || tc == CL_MIN_PR_TYPE__ARRAY__NULL_EL
                    || tc == CL_MIN_PR_TYPE__ARRAY__EMPTY
                    || tc == CL_MIN_PR_TYPE__ARRAY__PRIM_TYPE
                    || tc == CL_MIN_PR_TYPE__ARRAY__ARRAY_TYPE
                    || tc == CL_MIN_PR_TYPE__ARRAY__NOT_PRINCIPAL) {
                // Invoking ClientMinPrincipalType(Class[] classes) constructor
                ic = new ClientMinPrincipalType((Class[]) cl);
            }

            // If some Exception is expected
            if (       tc == CL_MAX_PR_TYPE__CLASS__NULL
                    || tc == CL_MIN_PR_TYPE__CLASS__NULL
                    || tc == CL_MAX_PR_TYPE__ARRAY__NULL
                    || tc == CL_MAX_PR_TYPE__ARRAY__NULL_EL
                    || tc == CL_MAX_PR_TYPE__ARRAY__EMPTY
                    || tc == CL_MAX_PR_TYPE__ARRAY__PRIM_TYPE
                    || tc == CL_MAX_PR_TYPE__ARRAY__ARRAY_TYPE
                    || tc == CL_MAX_PR_TYPE__ARRAY__NOT_PRINCIPAL
                    || tc == CL_MIN_PR_TYPE__ARRAY__NULL
                    || tc == CL_MIN_PR_TYPE__ARRAY__NULL_EL
                    || tc == CL_MIN_PR_TYPE__ARRAY__EMPTY
                    || tc == CL_MIN_PR_TYPE__ARRAY__PRIM_TYPE
                    || tc == CL_MIN_PR_TYPE__ARRAY__ARRAY_TYPE
                    || tc == CL_MIN_PR_TYPE__ARRAY__NOT_PRINCIPAL) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while invoking constructor " + e);
            // If no Exception is expected
            if (       tc == CL_MAX_PR_TYPE__CLASS
                    || tc == CL_MIN_PR_TYPE__CLASS
                    || tc == CL_MAX_PR_TYPE__ARRAY
                    || tc == CL_MIN_PR_TYPE__ARRAY) {
                throw new TestException("Exception while invoking constructor ",
                        e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor");
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

        if (       (tc == CL_MAX_PR_TYPE__CLASS
                 || tc == CL_MAX_PR_TYPE__ARRAY)
                && !(ic instanceof ClientMaxPrincipalType)) {
            logger.log(Level.FINE,
                    "Instead of ClientMaxPrincipalType " + ic.getClass()
                    + " object is returned");
            throw new TestException("Instead of ClientMaxPrincipalType "
                    + ic.getClass() + " object is returned");
        } else if ((tc == CL_MIN_PR_TYPE__CLASS
                 || tc == CL_MIN_PR_TYPE__ARRAY)
                && !(ic instanceof ClientMinPrincipalType)) {
            logger.log(Level.FINE,
                    "Instead of ClientMinPrincipalType " + ic.getClass()
                    + " object is returned");
            throw new TestException("Instead of ClientMinPrincipalType "
                    + ic.getClass() + " object is returned");
        }

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */

        // logger.log(Level.INFO, "Argument before invoking the constructor:");
        // for (int i = 0; i < cl_copy.length; i++) {
        // logger.log(Level.INFO, "cl_copy[" + i + "]:: " + cl_copy[i]);
        // }
        // logger.log(Level.INFO, "Argument after invoking the constructor :");
        // for (int i = 0; i < cl.length; i++) {
        // logger.log(Level.INFO, "cl[" + i + "]:: " + cl[i]);
        // }
        if (!Arrays.equals(cl, cl_copy)) {
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
         * created. Compare set of all of the classes from the created
         * object before and after changing the argument.
         */
        Set icSet_before = null;

        try {
            Class icClass = ic.getClass();
            Method elementsMethod = icClass.getMethod("elements", null);
            // Get set of classes from the created constraint
            icSet_before = (Set) elementsMethod.invoke(ic, null);

            /*
             * Change argument passed to the constructor while creating
             * the constraint
             */
            for (int i = 0; i < cl.length; i++) {
                cl[i] = Exception.class;
            }

            // Get set of classes from the created constraint
            Set icSet_after = (Set) elementsMethod.invoke(ic, null);
            // logger.log(Level.INFO, "Set of all of the classes of created object"
            // + " before modification of arg passed to the constructor: "
            // + icSet_before);
            // logger.log(Level.INFO, "Set of all of the classes of created object"
            // + " after modification of arg passed to the constructor : "
            // + icSet_after);
            if (!icSet_after.equals(icSet_before)) {
                logger.log(Level.FINE,
                        "The argument passed to the constructor is retained");
                throw new TestException("The argument passed to the"
                        + " constructor is retained");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception is thrown while invoking elements() method using"
                    + " reflection: " + e);
            throw new TestException("Exception is thrown while invoking"
                    + " elements() method using reflection.", e);
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(icSet_before);
        logger.log(Level.FINE, "Duplicates have been removed");
    }

    protected void callConstructor(Object tc, Collection cl, Class ex)
            throws TestException {

        /*
         * Copy object specified as an argument for the constructor before
         * invoking the constructor.
         */
        ArrayList cl_copy = null;
        if (       tc == CL_MAX_PR_TYPE__COLL
                || tc == CL_MIN_PR_TYPE__COLL) {
            cl_copy = new ArrayList(cl);
        }
        InvocationConstraint ic = null;

        try {
            if (       tc == CL_MAX_PR_TYPE__COLL
                    || tc == CL_MAX_PR_TYPE__COLL__NULL
                    || tc == CL_MAX_PR_TYPE__COLL__NULL_EL
                    || tc == CL_MAX_PR_TYPE__COLL__EMPTY
                    || tc == CL_MAX_PR_TYPE__COLL__ILL_EL
                    || tc == CL_MAX_PR_TYPE__COLL__PRIM_TYPE
                    || tc == CL_MAX_PR_TYPE__COLL__ARRAY_TYPE
                    || tc == CL_MAX_PR_TYPE__COLL__NOT_PRINCIPAL) {
                // Invoking ClientMaxPrincipalType(Collection classes) constructor
                ic = new ClientMaxPrincipalType((Collection) cl);
            } else if (tc == CL_MIN_PR_TYPE__COLL
                    || tc == CL_MIN_PR_TYPE__COLL__NULL
                    || tc == CL_MIN_PR_TYPE__COLL__NULL_EL
                    || tc == CL_MIN_PR_TYPE__COLL__EMPTY
                    || tc == CL_MIN_PR_TYPE__COLL__ILL_EL
                    || tc == CL_MIN_PR_TYPE__COLL__PRIM_TYPE
                    || tc == CL_MIN_PR_TYPE__COLL__ARRAY_TYPE
                    || tc == CL_MIN_PR_TYPE__COLL__NOT_PRINCIPAL) {
                // Invoking ClientMinPrincipalType(Collection classes) constructor
                ic = new ClientMinPrincipalType((Collection) cl);
            }

            // If some Exception is expected
            if (       tc == CL_MAX_PR_TYPE__COLL__NULL
                    || tc == CL_MAX_PR_TYPE__COLL__NULL_EL
                    || tc == CL_MAX_PR_TYPE__COLL__EMPTY
                    || tc == CL_MAX_PR_TYPE__COLL__ILL_EL
                    || tc == CL_MAX_PR_TYPE__COLL__PRIM_TYPE
                    || tc == CL_MAX_PR_TYPE__COLL__ARRAY_TYPE
                    || tc == CL_MAX_PR_TYPE__COLL__NOT_PRINCIPAL
                    || tc == CL_MIN_PR_TYPE__COLL__NULL
                    || tc == CL_MIN_PR_TYPE__COLL__NULL_EL
                    || tc == CL_MIN_PR_TYPE__COLL__EMPTY
                    || tc == CL_MIN_PR_TYPE__COLL__ILL_EL
                    || tc == CL_MIN_PR_TYPE__COLL__PRIM_TYPE
                    || tc == CL_MIN_PR_TYPE__COLL__ARRAY_TYPE
                    || tc == CL_MIN_PR_TYPE__COLL__NOT_PRINCIPAL) {
                logger.log(Level.FINE, "Expected Exception type:: " + ex);
                throw new TestException("Instead of " + ex + " no Exception"
                        + " has been thrown while invoking constructor");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception while invoking constructor: " + e);
            // If no Exception is expected
            if (       tc == CL_MAX_PR_TYPE__COLL
                    || tc == CL_MIN_PR_TYPE__COLL) {
                throw new TestException(
                        "Exception while invoking constructor", e);
            }

            // If some Exception is expected
            if (!ex.equals(e.getClass())) {
                logger.log(Level.FINE, "Expected Exception:: " + ex);
                logger.log(Level.FINE, "Thrown   Exception:: " + e.getClass());
                throw new TestException("Instead of " + ex + " "
                        + e.getClass() + " has been thrown while"
                        + " invoking constructor");
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

        if (       tc == CL_MAX_PR_TYPE__COLL
                && !(ic instanceof ClientMaxPrincipalType)) {
            logger.log(Level.FINE,
                    "Instead of ClientMaxPrincipalType " + ic.getClass()
                    + " object is returned");
            throw new TestException("Instead of ClientMaxPrincipalType "
                    + ic.getClass() + " object is returned");
        } else if (tc == CL_MIN_PR_TYPE__COLL
                && !(ic instanceof ClientMinPrincipalType)) {
            logger.log(Level.FINE,
                    "Instead of ClientMinPrincipalType " + ic.getClass()
                    + " object is returned");
            throw new TestException("Instead of ClientMinPrincipalType "
                    + ic.getClass() + " object is returned");
        }

        /*
         * Verify that the argument passed to the constructor isn't modified.
         * Compare argument for the constructor before and after invoking the
         * constructor.
         */

        // logger.log(Level.INFO, "Argument before invoking the constructor: "
        // + cl_copy);
        // logger.log(Level.INFO, "Argument after invoking the constructor : "
        // + cl);
        if (!cl.equals(cl_copy)) {
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
         * created. Compare set of all of the classes from the created
         * object before and after changing the argument.
         */
        Set icSet_before = null;

        try {
            Class icClass = ic.getClass();
            Method elementsMethod = icClass.getMethod("elements", null);
            // Get set of classes from the created constraint
            icSet_before = (Set) elementsMethod.invoke(ic, null);

            /*
             * Change argument passed to the constructor while creating
             * the constraint
             */
            cl.clear();
            // Get set of classes from the created constraint
            Set icSet_after = (Set) elementsMethod.invoke(ic, null);
            // logger.log(Level.INFO, "Set of all of the classes of created object"
            // + " before modification of arg passed to the constructor: "
            // + icSet_before);
            // logger.log(Level.INFO, "Set of all of the classes of created object"
            // + " after modification of arg passed to the constructor : "
            // + icSet_after);
            if (!icSet_after.equals(icSet_before)) {
                logger.log(Level.FINE,
                        "The argument passed to the constructor is retained");
                throw new TestException("The argument passed to the"
                        + " constructor is retained");
            }
        } catch (Exception e) {
            logger.log(Level.FINE,
                    "Exception is thrown while invoking elements() method using"
                    + " reflection: " + e);
            throw new TestException("Exception is thrown while invoking"
                    + " elements() method using reflection.", e);
        }
        logger.log(Level.FINE,
                "The argument passed to the constructor isn't retained");

        /*
         * Verify that duplicates are removed.
         */
        checkDuplicates(icSet_before);
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
