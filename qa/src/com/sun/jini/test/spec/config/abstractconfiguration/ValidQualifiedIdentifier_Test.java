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

package com.sun.jini.test.spec.config.abstractconfiguration;

import java.util.logging.Level;
import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.TestException;
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;

/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the validQualifiedIdentifier method of
 *   AbstractConfiguration class.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeAbstractConfiguration class that implements
 *        AbstractConfiguration and permit call of protected
 *        validQualifiedIdentifier method.
 *
 * Actions:
 *   Test performs the following steps:
 *     1) Test generates a complete set of Identifiers with the length
 *        of one IdentifierChar, passes string with
 *        it as an argument to validQualifiedIdentifier method and assert that
 *        return value is true.
 *     2) Test generates a complete set of random Identifier with the length
 *        of two IdentifierChar where first char is 'z', passes string with
 *        it as an argument to validIdentifier method and assert that
 *        return value is true.
 *     3) Test generates random set of valid Identifiers (same as in
 *        ValidIdentifier_Test), collect it
 *        in QualifiedIdentifier, passes string with them
 *        as an argument to validQualifiedIdentifier method and
 *        assert that return value is true.
 *     4) Test generates a complete set of strings  with the length
 *        of one non IdentifierChar, passes this string as an argument to
 *        validQualifiedIdentifier method and assert that return value is false.
 *     5) Test generates a complete set of strings  with the length
 *        equal to 2 where first char is 'z', and second non IdentifierChar
 *        passes this string as an argument to validQualifiedIdentifier method
 *        and assert that return value is false.
 *     6) Test generates a complete set of strings  with the length
 *        equal to 3 where first two chars is 'z.', and third is non
 *        IdentifierChar passes this string as an argument to
 *        validQualifiedIdentifier method and assert that return value is false.
 *     7) Test generates a set of strings one for each ASCII digits 0-9
 *        (\u0030-\u0039) that contains this digit in the first position
 *        and contains some valid IdentifierChars in the string rest,
 *        passes this string as an argument to validQualifiedIdentifier method
 *        and assert that return value is false.
 *     8) Test passes empty string as an argument to validQualifiedIdentifier
 *        method and assert that return value is false.
 *     9) Test passes "." string as an argument to validQualifiedIdentifier
 *        method and assert that return value is false.
 *     10) Test passes null as an argument to validQualifiedIdentifier
 *        method and assert that return value is false.
 * </pre>
 */
public class ValidQualifiedIdentifier_Test extends ValidCheck_Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // 1 valid 1 char qid
        for (int i = 0; i <= 0xffff; ++i) {
            if (Character.isJavaIdentifierStart((char)i)) {
                String id = "" + (char)i;
                if (!FakeAbstractConfiguration
                        .validQualifiedIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should be valid");
                }
            }
        }

        // 2 valid 2 char qid
        for (int i = 0; i <= 0xffff; ++i) {
            if (Character.isJavaIdentifierPart((char)i)) {
                String id = "z" + (char)i;
                if (!FakeAbstractConfiguration
                        .validQualifiedIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should be valid");
                }
            }
        }

        // 3 valid random char qid
        for (int i = 0; i < 1000; ++i) {
            int randomPartsLength = random.nextInt(18);
            int randomIdLength = random.nextInt(130);
            String id = nextRandomId(randomIdLength);
            for (int j = 0; j < randomPartsLength; ++j) {
                randomIdLength = random.nextInt(130);
                id += "." + nextRandomId(randomIdLength);
            }
            if (!FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic(id)) {
                throw new TestException(
                        "Identifier " + id + " should be valid");
            }
        }

       logger.log(Level.INFO, "--> " + FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic("" + (char)0));

        // 4 invalid 1 char qid
        for (int i = 0; i <= 0xffff; ++i) {
            if (!Character.isJavaIdentifierStart((char)i)) {
                String id = "" + (char)i;
                if (FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should not be valid");
                }
            }
        }

        // 5 invalid 2 char qid
        for (int i = 0; i <= 0xffff; ++i) {
            if (!Character.isJavaIdentifierPart((char)i)) {
                String id = "z" + (char)i;
                if (FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should not be valid");
                }
            }
        }

        // 6 invalid "z.?" qid 
        for (int i = 0; i <= 0xffff; ++i) {
            if (!Character.isJavaIdentifierStart((char)i)) {
                String id = "z." + (char)i;
                if (FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should not be valid");
                }
            }
        }

        // 7 digits
        for (int i = 0; i <= 9; ++i) {
            String id = "" + (char)i + "zzz";
            if (FakeAbstractConfiguration
                    .validQualifiedIdentifierPublic(id)) {
                throw new TestException(
                        "Identifier " + id + " should not be valid");
            }
        }

        // 8 empty string
        if (FakeAbstractConfiguration
                .validQualifiedIdentifierPublic("")) {
            throw new TestException(
                    " Empty string not be valid identifier");
        }

        // 9 "."
        if (FakeAbstractConfiguration
                .validQualifiedIdentifierPublic(".")) {
            throw new TestException(
                    " \".\" should not be valid identifier");
        }

        // 10 null
        if (FakeAbstractConfiguration
                .validQualifiedIdentifierPublic(null)) {
            throw new TestException(
                    " Null literal should not be valid identifier");
        }
    }
}
