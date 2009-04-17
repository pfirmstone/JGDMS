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
package com.sun.jini.test.spec.policyprovider.grantPermission;

import java.util.logging.Level;

// com.sun.jini.qa.harness
import com.sun.jini.qa.harness.TestException;

// java.util.logging
import java.util.logging.Logger;
import java.util.logging.Level;

// java.security
import java.security.Permission;
import java.security.CodeSource;
import java.security.ProtectionDomain;

// davis packages
import net.jini.security.GrantPermission;

// test base class
import com.sun.jini.test.spec.policyprovider.AbstractTestBase;

// utility classes
import com.sun.jini.test.spec.policyprovider.util.Util;
import com.sun.jini.test.spec.policyprovider.util.QAPermission01;

/**
 * <b>Purpose</b><br><br>
 *
 * This test verifies that <code>GrantPermission</code> class
 * works properly.
 *
 * <b>Test Description</b><br><br>
 *
 *  This test is complex test using various constructors and implies(),
 *  equals(), getActions() methods of <code>GrantPermission</code>.
 *
 *  <br><br>
 *
 * <b>Infrastructure</b><br><br>
 *
 * <ul><lh>This test requires the following infrastructure:</lh>
 *  <li> infrastructure is not required</li>
 * </ul>
 *
 * <b>Actions</b><br><br>
 * <ol>
 *    <li> Try to construct GrantPermission object passing null as
 *         Permission and verify that NullPointerException is
 *         thrown.
 *    </li>
 *    <li> Try to construct GrantPermission object passing null as
 *         array of Permissions and verify that NullPointerException is
 *         thrown.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         various array of Permissions that contains null and verify
 *         that NullPointerExceptions are thrown.
 *    </li>
 *    <li> Try to construct GrantPermission object passing null as the
 *         string name of permission and verify that NullPointerException
 *         is thrown.
 *    </li>
 *    <li> Try to construct GrantPermission object passing empty
 *         string and verify that IllegalArgumentException is
 *         thrown.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         string that cannot be parsed and verify that
 *         IllegalArgumentExceptions are thrown.
 *        <ol><lh>Strings to be passed:</lh>
 *            <li>"Foo | \"a \\\"quoted\\\" string\""</li>
 *            <li>"Foo a \\\"quoted\\\" string\""</li>
 *            <li>"Foo \"a \"quoted\\\" string\""</li>
 *            <li>"Foo \"a \\\"quoted\" string\""</li>
 *            <li>"Foo \"a \\\"quoted\\\" string\";;"</li>
 *            <li>"delim=|| Foo | a \"quoted\" string|"</li>
 *            <li>"delim=| | Foo | a \"quoted\" string|"</li>
 *            <li>"delim=| Foo a \"quoted\" string|"</li>
 *            <li>"delim=| Foo || a \"quoted\" string|"</li>
 *            <li>"delim=| Foo | a \"quoted\" string||"</li>
 *        </ol>
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         the same string that can be parsed and verify that
 *         no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         different strings that can be parsed and verify that
 *         no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are not equal and
 *         are not implied each other.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         the same RuntimePermission and verify that
 *         no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         different RuntimePermissions and verify that
 *         no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are not equal and
 *         are not implied each other.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         the same array of RuntimePermission and verify that
 *         no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object passing
 *         different arrays of RuntimePermissions and verify that
 *         no exception is thrown.
 *         Each next array of RuntimePermissions should contain array from
 *         previous RuntimePermissions plus the new one RuntimePermission:
 *         <ul>
 *           <li>{p1}</li>
 *           <li>{p1, p2}</li>
 *           <li>{p1, p2, p3}</li>
 *           <li>....</li>
 *         </ul>
 *    </li>
 *    <li> Verify that all created GrantPermissions are not equal and
 *         are implied/not implied each other.  Also verify that if two
 *         GrantPermission instances are equal to one another then they
 *         should have the same hash code.
 *         Verify that getActions() returns empty string for all
 *         created GrantPermissions.
 *    </li>
 *    <li> Some times try to construct GrantPermission object using
 *         strings with different delimeters and not using delimeters
 *         at all and verify that no exception is thrown.
 *        <ol><lh>Strings to be passed:</lh>
 *            <li>"delim=? java.lang.RuntimePermission ?foo?"</li>
 *            <li>"delim=| java.lang.RuntimePermission |foo|"</li>
 *            <li>"delim=] java.lang.RuntimePermission ]foo]"</li>
 *            <li>"delim=[ java.lang.RuntimePermission [foo["</li>
 *            <li>"delim=* java.lang.RuntimePermission *foo8"</li>
 *            <li>"java.lang.RuntimePermission \"foo\""</li>
 *        </ol>
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *    </li>
 *    <li> For application-defined permissions type that can not be loaded
 *         do the following:
 *         <ul>
 *           <li>Two times try to construct GrantPermission object passing
 *               the same application-defined permissions type as a string.
 *           </li>
 *         </ul>
 *         Verify that no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *    </li>
 *    <li> For application-defined permissions type that can be loaded
 *         do the following:
 *         <ul>
 *           <li>Two times try to construct GrantPermission object passing
 *               the same application-defined permissions type as a string.
 *           </li>
 *           <li>Two times try to construct GrantPermission object passing
 *               the same application-defined permissions type as an object.
 *           </li>
 *         </ul>
 *         Use {@link QAPermission01} class as application-defined permission
 *         type.
 *         Verify that no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *    </li>
 *    <li> Create some nested GrantPermission objects:
 *         <ul>
 *           <li>gp01 = new GrantPermission("foo");</li>
 *           <li>gp02 = new GrantPermission(gp01);</li>
 *           <li>gp03 = new GrantPermission(gp02);</li>
 *           <li>gp04 = new GrantPermission(gp03);</li>
 *           <li>gp05 = new GrantPermission(gp04);</li>
 *         </ul>
 *         Verify that no exception is thrown.
 *    </li>
 *    <li> Verify that all created GrantPermissions are equal and
 *         have the same hash code and are implied each other.
 *    </li>
 * </ol>
 *
 */
public class GrantPermissionTest extends AbstractTestBase {

    /**
     * Run the test according <b>Test Description</b>
     */
    public void run() throws Exception {
        Permission[] pma = null;
        Permission pm01 = new RuntimePermission("A");
        Permission pm02 = new RuntimePermission("B");
        Permission pm03 = new RuntimePermission("C");
        Permission pm04 = new RuntimePermission("D");
        Permission pm05 = new RuntimePermission("E");
        Permission pm06 = new RuntimePermission("F");
        Permission[] pa01 = new Permission[] { pm01 };
        Permission[] pa02 = new Permission[] { pm01, pm02 };
        Permission[] pa03 = new Permission[] { pm01, pm02, pm03 };
        Permission[] pa04 = new Permission[] { pm01, pm02, pm03, pm04 };
        GrantPermission gp01, gp02, gp03, gp04, gp05, gp06, gp07;
        GrantPermission[] ga = null;
        String passUtil = "com.sun.jini.test.spec.policyprovider.util.";
        String nameQA01 = "QAPermission01";
        String badname01 = "Foo | \"a \\\"quoted\\\" string\"";
        String badname02 = "Foo a \\\"quoted\\\" string\"";
        String badname03 = "Foo \"a \"quoted\\\" string\"";
        String badname04 = "Foo \"a \\\"quoted\" string\"";
        String badname05 = "Foo \"a \\\"quoted\\\" string\";;";
        String badname06 = "delim=|| Foo | a \"quoted\" string|";
        String badname07 = "delim=| | Foo | a \"quoted\" string|";
        String badname08 = "delim=| Foo a \"quoted\" string|";
        String badname09 = "delim=| Foo || a \"quoted\" string|";
        String badname10 = "delim=| Foo | a \"quoted\" string||";
        String name01 = "Foo \"a \\\"quoted\\\" string\"";
        String name02 = "Bar \"a \\\"quoted\\\" string\"";
        String name03 = "delim=| Foo | a \"quoted\" string|";
        String name04 = "delim=| Bar | a \"quoted\" string|";

        /*
         * Try to construct GrantPermission object passing null as
         * Permission and verify that NullPointerException is
         * thrown.
         */
        msg = "new GrantPermission((Permission) null)";
        createGrantPermissionNPE((Permission) null, msg);

        /*
         * Try to construct GrantPermission object passing null as
         * array of Permissions and verify that NullPointerException is
         * thrown.
         */
        msg = "new GrantPermission((Permission[]) null)";
        createGrantPermissionNPE((Permission[]) null, msg);

        /*
         * Some times try to construct GrantPermission object passing
         * various array of Permissions that contains null and verify
         * that NullPointerExceptions are thrown.
         */
        msg = "new GrantPermission(new Permission[] {..., null,... })";
        pma = new Permission[] { null };
        createGrantPermissionNPE(pma, msg);
        pma = new Permission[] { null, pm01, pm02, pm03 };
        createGrantPermissionNPE(pma, msg);
        pma = new Permission[] { pm01, null, pm02, pm03 };
        createGrantPermissionNPE(pma, msg);
        pma = new Permission[] { pm01, null, pm02, pm03, null };
        createGrantPermissionNPE(pma, msg);

        /*
         * Try to construct GrantPermission object passing null as the
         * string name of permission and verify that NullPointerException
         * is thrown.
         */
        msg = "new GrantPermission((String) null)";
        createGrantPermissionNPE((String) null, msg);

        /*
         * Try to construct GrantPermission object passing empty
         * string and verify that IllegalArgumentException is
         * thrown.
         */
        createGrantPermissionIAE("");

        /*
         * Some times try to construct GrantPermission object passing
         * string that cannot be parsed and verify that
         * IllegalArgumentExceptions are thrown.
         * Strings to be passed:
         * "Foo | \"a \\\"quoted\\\" string\""
         * "Foo a \\\"quoted\\\" string\""
         * "Foo \"a \"quoted\\\" string\""
         * "Foo \"a \\\"quoted\" string\""
         * "Foo \"a \\\"quoted\\\" string\";;"
         * "delim=|| Foo | a \"quoted\" string|"
         * "delim=| | Foo | a \"quoted\" string|"
         * "delim=| Foo a \"quoted\" string|"
         * "delim=| Foo || a \"quoted\" string|"
         * "delim=| Foo | a \"quoted\" string||"
         */
        createGrantPermissionIAE(badname01);
        createGrantPermissionIAE(badname02);
        createGrantPermissionIAE(badname03);
        createGrantPermissionIAE(badname04);
        createGrantPermissionIAE(badname05);
        createGrantPermissionIAE(badname06);
        createGrantPermissionIAE(badname07);
        createGrantPermissionIAE(badname08);
        createGrantPermissionIAE(badname09);
        createGrantPermissionIAE(badname10);

        /*
         * Some times try to construct GrantPermission object passing
         * the same string that can be parsed and verify that
         * no exception is thrown.
         */
        gp01 = createGP(name01);
        gp02 = createGP(name01);
        gp03 = createGP(name01);

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03 };
        checkEquals(ga, true);
        checkImplies(ga, true);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object passing
         * different strings that can be parsed and verify that
         * no exception is thrown.
         */
        gp01 = createGP(name01);
        gp02 = createGP(name02);
        gp03 = createGP(name03);
        gp04 = createGP(name04);

        /*
         * Verify that all created GrantPermissions are not equal and
         * are not implied each other.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04 };
        checkEquals(ga, false);
        checkImplies(ga, false);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object passing
         * the same RuntimePermission and verify that
         * no exception is thrown.
         */
        gp01 = createGP(pm01);
        gp02 = createGP(pm01);
        gp03 = createGP(pm01);

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03 };
        checkEquals(ga, true);
        checkImplies(ga, true);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object passing
         * different RuntimePermissions and verify that
         * no exception is thrown.
         */
        gp01 = createGP(pm01);
        gp02 = createGP(pm02);
        gp03 = createGP(pm03);
        gp04 = createGP(pm04);

        /*
         * Verify that all created GrantPermissions are not equal and
         * are not implied each other.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04 };
        checkEquals(ga, false);
        checkImplies(ga, false);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object passing
         * the same array of RuntimePermission and verify that
         * no exception is thrown.
         */
        gp01 = createGP(pa01);
        gp02 = createGP(pa01);
        gp03 = createGP(pa01);

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03 };
        checkEquals(ga, true);
        checkImplies(ga, true);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object passing
         * different arrays of RuntimePermissions and verify that
         * no exception is thrown.
         * Each next array of RuntimePermissions should contain array from
         * previous RuntimePermissions plus the new one RuntimePermission:
         * {p1}
         * {p1, p2}
         * {p1, p2, p3}
         * ...
         */
        gp01 = createGP(pa01);
        gp02 = createGP(pa02);
        gp03 = createGP(pa03);
        gp04 = createGP(pa04);

        /*
         * Verify that all created GrantPermissions are not equal and
         * are implied/not implied each other. Also verify that if two
         * GrantPermission instances are equal to one another then they
         * should have the same hash code.
         * Verify that getActions() returns empty string for all
         * created GrantPermissions.
         */
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04 };
        checkEquals(ga, false);
        checkImplies(ga);
        checkGetActions(ga);

        /*
         * Some times try to construct GrantPermission object using
         * strings with different delimeters and not using delimeters
         * at all and verify that no exception is thrown.
         * Strings to be passed:
         * "delim=? java.lang.RuntimePermission ?foo?"
         * "delim=| java.lang.RuntimePermission |foo|"
         * "delim=] java.lang.RuntimePermission ]foo]"
         * "delim=[ java.lang.RuntimePermission [foo["
         * "delim=* java.lang.RuntimePermission *foo*"
         * "java.lang.RuntimePermission \"foo\""
         */
        gp01 = createGP("delim=? java.lang.RuntimePermission ?foo?");
        gp02 = createGP("delim=| java.lang.RuntimePermission |foo|");
        gp03 = createGP("delim=] java.lang.RuntimePermission ]foo]");
        gp04 = createGP("delim=[ java.lang.RuntimePermission [foo[");
        gp05 = createGP("delim=* java.lang.RuntimePermission *foo*");
        gp06 = createGP("java.lang.RuntimePermission \"foo\"");
	gp07 = createGP(new RuntimePermission("foo"));
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04, gp05, gp06, gp07 };

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         */
        checkEquals(ga, true);
        checkImplies(ga, true);

        /*
         * For application-defined permissions type that can not be loaded
         * do the following:
         * two times try to construct GrantPermission object passing
         * the same application-defined permissions type as a string;
         * Verify that no exception is thrown.
         */
        gp01 = createGP(passUtil + "bar" + " \"foo\"");
        gp02 = createGP(passUtil + "bar" + " \"foo\"");
        ga = new GrantPermission[] { gp01, gp02 };

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         */
        checkEquals(ga, true);
        checkImplies(ga, true);

        /*
         * For application-defined permissions type that can be loaded
         * do the following:
         * two times try to construct GrantPermission object passing
         * the same application-defined permissions type as a string;
         * two times try to construct GrantPermission object passing
         * the same application-defined permissions type as an object.
         * Use QAPermission01 class for this test case.
         * Verify that no exception is thrown.
         */
        gp01 = createGP(passUtil + nameQA01 + " \"foo\"");
        gp02 = createGP(passUtil + nameQA01 + " \"foo\"");
        gp03 = createGP(new QAPermission01("foo"));
        gp04 = createGP(new QAPermission01("foo"));
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04 };

        /*
	 * The string forms should be .equals to each other
	 * The object forms should be .equals to each other
	 * The string forms should imply the object forms, but
	 * The object forms should not imply the string forms
	 * This is because the string forms are unresolved, since 
	 * QAPermission01 isn't visible to the extension class loader,
	 * so that the string form is effectively a superset of 
	 * the object form.
         */
	if (! gp01.equals(gp02)) {
            throw new TestException("gp01 not .equals gp02");
        }

	if (! gp02.equals(gp01)) {
            throw new TestException("gp02 not .equals gp01");
        }

	if (! gp03.equals(gp04)) {
            throw new TestException("gp03 not .equals gp04");
        }

	if (! gp04.equals(gp03)) {
            throw new TestException("gp04 not .equals gp03");
        }

	if (! gp01.implies(gp03)) {
            throw new TestException("gp01 doesn't imply gp03");
        }

	if (gp03.implies(gp01)) {
            throw new TestException("gp03 incorrectly implies gp01");
        }
//            checkEquals(ga, true);
//            checkImplies(ga, true);

        /*
         * Create some nested GrantPermission objects:
         *     gp01 = new GrantPermission("foo");
         *     gp02 = new GrantPermission(gp01);
         *     gp03 = new GrantPermission(gp02);
         *     gp04 = new GrantPermission(gp03);
         *     gp05 = new GrantPermission(gp04);
         * Verify that no exception is thrown.
         */
        gp01 = createGP("foo");
        gp02 = createGP(gp01);
        gp03 = createGP(gp02);
        gp04 = createGP(gp03);
        gp05 = createGP(gp04);
        ga = new GrantPermission[] { gp01, gp02, gp03, gp04, gp05 };

        /*
         * Verify that all created GrantPermissions are equal and
         * have the same hash code and are implied each other.
         */
        checkEquals(ga, true);
        checkImplies(ga, true);
    }

    /**
     * Try to create GrantPermission passing Permission.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param p permission to be passed.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void createGrantPermissionNPE(Permission p, String msg)
            throws TestException {
        try {
            GrantPermission gp = new GrantPermission(p);
            throw new TestException(Util.fail(msg, msg, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Try to create GrantPermission passing array of Permissions.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param pa array of permissions to be passed.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void createGrantPermissionNPE(Permission[] pa, String msg)
            throws TestException {
        try {
            GrantPermission gp = new GrantPermission(pa);
            throw new TestException(Util.fail(msg, msg, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Try to create GrantPermission passing the string.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param name the name of permission to be passed.
     * @param msg string to format log message.
     *
     * @throws TestException if failed
     *
     */
    protected void createGrantPermissionNPE(String name, String msg)
            throws TestException {
        try {
            GrantPermission gp = new GrantPermission(name);
            throw new TestException(Util.fail(msg, msg, NPE));
        } catch (NullPointerException npe) {
            logger.log(Level.FINE, Util.pass(msg, npe));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, NPE));
        }
    }

    /**
     * Try to create GrantPermission passing the string.
     * Expect NullPointerException. If no exception or another
     * exception is thrown then test failed.
     *
     * @param name the name of permission to be passed.
     *
     * @throws TestException if failed
     *
     */
    protected void createGrantPermissionIAE(String name)
            throws TestException {
        msg = "new GrantPermission(" + name + ")";

        try {
            GrantPermission gp = new GrantPermission(name);
            throw new TestException(Util.fail(msg, msg, IAE));
        } catch (IllegalArgumentException iae) {
            logger.log(Level.FINE, Util.pass(msg, iae));
        } catch (TestException qae) {
            throw qae;
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, IAE));
        }
    }

    /**
     * Try to create GrantPermission passing Permission.
     * If exception is thrown then test failed.
     *
     * @param p the permission to be passed.
     *
     * @throws TestException if failed
     *
     */
    protected GrantPermission createGP(Permission p)
            throws TestException {
        msg = "new GrantPermission(" + p.getName() + ")";
        GrantPermission gp = null;

        try {
            gp = new GrantPermission(p);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, msg));
        }
        logger.log(Level.FINE, Util.pass(msg, "creates GrantPermission"));
        return gp;
    }

    /**
     * Try to create GrantPermission passing array of Permissions.
     * If exception is thrown then test failed.
     *
     * @param pa the permission array to be passed.
     *
     * @throws TestException if failed
     *
     */
    protected GrantPermission createGP(Permission[] pa)
            throws TestException {
        String arg = java.util.Arrays.asList(pa).toString();
        msg = "new GrantPermission(" + arg + ")";
        GrantPermission gp = null;

        try {
            gp = new GrantPermission(pa);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, msg));
        }
        logger.log(Level.FINE, Util.pass(msg, "creates GrantPermission"));
        return gp;
    }

    /**
     * Try to create GrantPermission passing the string.
     * If exception is thrown then test failed.
     *
     * @param name the name of permission to be passed.
     *
     * @throws TestException if failed
     *
     */
    protected GrantPermission createGP(String name)
            throws TestException {
        msg = "new GrantPermission(" + name + ")";
        GrantPermission gp = null;

        try {
            gp = new GrantPermission(name);
        } catch (Exception e) {
            throw new TestException(Util.fail(msg, e, msg));
        }
        logger.log(Level.FINE, Util.pass(msg, "creates GrantPermission"));
        return gp;
    }

    /**
     * Verify that all of passing GrantPermissions are equal/not equal
     * each other. Also verify that if two GrantPermission instances
     * are equal to one another then they should have the same hash code.
     *
     * @param pa permissions to be verified.
     * @param expected if true then GrantPermissions should be equal,
     *        otherwise should be not equal.
     *
     * @throws TestException if failed
     *
     */
    protected void checkEquals(GrantPermission[] pa, boolean expected)
            throws TestException {
        for (int i = 0; i < pa.length; i++) {
            for (int j = 0; j < pa.length; j++) {
                if (!expected && i == j) {
                    continue;
                }
                boolean returned = pa[i].equals(pa[j]);
                msg = str(pa[i]) + ".equals(" + str(pa[j]) + ")";

                if (returned != expected) {
                    String ret = "" + returned;
                    String exp = "" + expected;
                    throw new TestException(Util.fail(msg, ret, exp));
                }

                if (returned && (pa[i].hashCode() != pa[j].hashCode())) {
                    msg  = "permissions " + str(pa[i]) + " " + str(pa[j]);
                    msg += "\nare equal but have not the same hash codes";
                    String ret = "" + pa[i].hashCode();
                    String exp = "" + pa[j].hashCode();
                    throw new TestException(Util.fail(msg, ret, exp));
                } else {
                    logger.log(Level.FINE, Util.pass(msg, "" + returned));
                }
            }
        }
    }

    /**
     * Verify that all of passing GrantPermissions are implied/not implied
     * each other.
     *
     * @param pa permissions to be verified.
     * @param expected if true then GrantPermissions should be implied,
     *        otherwise should be not implied.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(GrantPermission[] pa, boolean expected)
            throws TestException {
        for (int i = 0; i < pa.length; i++) {
            for (int j = 0; j < pa.length; j++) {
                if (!expected && i == j) {
                    continue;
                }
                boolean returned = pa[i].implies(pa[j]);
                String ret = "" + returned;
                msg = str(pa[i]) + ".implies(" + str(pa[j]) + ")";

                if (returned != expected) {
                    String exp = "" + expected;
                    throw new TestException(Util.fail(msg, ret, exp));
                } else {
                    logger.log(Level.FINE, Util.pass(msg, ret));
                }
            }
        }
    }

    /**
     * Verify that all of passing GrantPermissions are implied/not implied
     * each other.
     *
     * @param pa permissions to be verified.
     *
     * @throws TestException if failed
     *
     */
    protected void checkImplies(GrantPermission[] pa)
            throws TestException {
        for (int i = 0; i < pa.length; i++) {
            for (int j = 0; j < pa.length; j++) {
                boolean expected = (i >= j);
                boolean returned = pa[i].implies(pa[j]);
                String ret = "" + returned;
                msg = str(pa[i]) + ".implies(" + str(pa[j]) + ")";

                if (returned != expected) {
                    String exp = "" + expected;
                    throw new TestException(Util.fail(msg, ret, exp));
                } else {
                    logger.log(Level.FINE, Util.pass(msg, ret));
                }
            }
        }
    }

    /**
     * Verify that all of passing GrantPermissions return empty string
     * as 'actions'.
     *
     * @param pa permissions to be verified.
     *
     * @throws TestException if failed
     *
     */
    protected void checkGetActions(GrantPermission[] pa)
            throws TestException {
        for (int i = 0; i < pa.length; i++) {
            String ret = pa[i].getActions();
            msg = str(pa[i]) + ".getActions()";

            if (ret != "") {
                throw new TestException(Util.fail(msg, ret, "empty string"));
            } else {
                logger.log(Level.FINE, Util.pass(msg, "returns empty string"));
            }
        }
    }

    private String str(GrantPermission p) {
        return "GrantPermission(" + p.getName() + ")";
    }
}
