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

package org.apache.river.test.spec.config.abstractconfiguration;

import java.util.logging.Level;
import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.TestException;
import org.apache.river.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;
import java.util.Arrays;


/**
 * <pre>
 * Purpose:
 *   This test verifies the behavior of the validIdentifier method of
 *   AbstractConfiguration class.
 *
 * Infrastructure:
 *   This test requires the following infrastructure:
 *     1) FakeAbstractConfiguration class that implements
 *        AbstractConfiguration and permit call of protected
 *        validIdentifier method.
 *
 * Actions:
 *   Test performs the following steps:
 *     1) Test generates a complete set of Identifiers with the length
 *        of one IdentifierChar, passes string with
 *        it as an argument to validIdentifier method and assert that
 *        return value is true.
 *     2) Test generates a complete set of random Identifier with the length
 *        of two IdentifierChar where first char is 'z', passes string with
 *        it as an argument to validIdentifier method and assert that
 *        return value is true.
 *     3) Test generates a set of random Identifier with the random length
 *        less or equal to 130 IdentifierChars, passes string with
 *        it as an argument to validIdentifier method and assert that
 *        return value is true.
 *     4) Test generates a complete set of strings  with the length
 *        of one non IdentifierChar, passes this string as an argument to
 *        validIdentifier method and assert that return value is false.
 *     5) Test generates a complete set of strings  with the length
 *        equal to 2 where first char is 'z', and second non IdentifierChar
 *        passes this string as an argument to validIdentifier method
 *        and assert that return value is false.
 *     6) Test generates a set of IdentifierChars one for each Keyword or
 *        BooleanLiteral or NullLiteral unicode character, passes string
 *        with it as an argument to validIdentifier method and assert
 *        that return value is false.
 *     7) Test generates a set of strings one for each ASCII digits 0-9
 *        (\u0030-\u0039) that contains this digit in the first position
 *        and contains some valid IdentifierChars in the string rest,
 *        passes this string as an argument to validIdentifier method
 *        and assert that return value is false.
 *     8) Test checks that null value as an argument will return false result.
 *     9) Test checks that empty string value as an argument will return
 *        false result.
 * </pre>
 */
public class ValidIdentifier_Test extends ValidCheck_Test {

    /**
     * This method performs all actions mentioned in class description.
     */
    public void run() throws Exception {
        // 1 valid 1 char id
        for (int i = 0; i <= 0xffff; ++i) {
            if (Character.isJavaIdentifierStart((char)i)) {
                String id = "" + (char)i;
                if (!FakeAbstractConfiguration.validIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should be valid");
                }
            }
        }

        // 2 valid 2 char id
        for (int i = 0; i <= 0xffff; ++i) {
            if (Character.isJavaIdentifierPart((char)i)) {
                String id = "z" + (char)i;
                if (!FakeAbstractConfiguration.validIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should be valid");
                }
            }
        }

        // 3 valid random char id
        for (int i = 0; i < 100000; ++i) {
            int randomIdLength = random.nextInt(130);
            String id = nextRandomId(randomIdLength);
            if (!FakeAbstractConfiguration.validIdentifierPublic(id)) {
                throw new TestException(
                        "Identifier " + id + " should be valid");
            }
        }

        // 4 invalid 1 char id
        for (int i = 0; i <= 0xffff; ++i) {
            if (!Character.isJavaIdentifierStart((char)i)) {
                String id = "" + (char)i;
                if (FakeAbstractConfiguration.validIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should not be valid");
                }
            }
        }

        // 5 invalid 2 char id
        for (int i = 0; i <= 0xffff; ++i) {
            if (!Character.isJavaIdentifierPart((char)i)) {
                String id = "z" + (char)i;
                if (FakeAbstractConfiguration.validIdentifierPublic(id)) {
                    throw new TestException(
                            "Identifier " + id + " should not be valid");
                }
            }
        }

        // 6 keywords
        for (int i = 0; i < keywords.length; ++i) {
            String id = keywords[i];
            if (FakeAbstractConfiguration.validIdentifierPublic(id)) {
                throw new TestException(
                        "Identifier " + id + " should not be valid");
            }
        }

        // 7 digits
        for (int i = 0; i <= 9; ++i) {
            String id = "" + (char)i + "zzz";
            if (FakeAbstractConfiguration.validIdentifierPublic(id)) {
                throw new TestException(
                        "Identifier " + id + " should not be valid");
            }
        }

        // 8 null
        if (FakeAbstractConfiguration.validIdentifierPublic(null)) {
            throw new TestException(
                    " Null literal should not be valid identifier");
        }

        // 9 empty string
        if (FakeAbstractConfiguration.validIdentifierPublic("")) {
            throw new TestException(
                    " Null literal should not be valid identifier");
        }
    }
}
