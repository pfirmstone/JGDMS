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
import com.sun.jini.qa.harness.QAConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;
import java.util.Arrays;


/**
 * <pre>
 * Common parts for Valid*_Test.
 * </pre>
 */
public abstract class ValidCheck_Test extends QATest {


    /**
     * Keyword list, BooleanLiteral and NullLiteral from the Java spec.
     */
    final static String [] keywords = {
        "abstract", "default", "if", "private", "this",
        "boolean", "do", "implements", "protected", "throw",
        "break", "double", "import", "public", "throws",
        "byte", "else", "instanceof", "return", "transient",
        "case", "extends", "int", "short", "try",
        "catch", "final", "interface", "static", "void",
        "char", "finally", "long", "strictfp", "volatile",
        "class", "float", "native", "super", "while",
        "const", "for", "new", "switch",
        "continue", "goto", "package", "synchronized",
        "true", "false", "null"
    };

    /**
     * Random generator for chars.
     */
    final static Random random = new Random( 0xc76098a6 );

    /**
     * Random char
     */
    protected char nextRandomChar() {
        return (char)(random.nextInt(0x10000));
    }

    /**
     * Random Java Identifier Start char
     */
    protected char nextJavaIdentifierStartChar() {
        char nextChar = nextRandomChar();
        while (!(Character.isJavaIdentifierStart(nextChar))) {
            nextChar = nextRandomChar();
        };
        return nextChar;
    }

    /**
     * Random Java Identifier Part char
     */
    protected char nextJavaIdentifierPartChar() {
        char nextChar = nextRandomChar();
        while (!(Character.isJavaIdentifierPart(nextChar))) {
            nextChar = nextRandomChar();
        };
        return nextChar;
    }

    /**
     * Random non Java Identifier Part char
     */
    protected char nextNonJavaIdentifierPartChar() {
        char nextChar = nextRandomChar();
        while (Character.isJavaIdentifierPart(nextChar)) {
            nextChar = nextRandomChar();
        };
        return nextChar;
    }

    /**
     * Random valid Identifier
     */
    protected String nextRandomId(int idLength) {
        String result = "" + nextJavaIdentifierStartChar();
        for (int i = 0; i < idLength; ++i) {
            result += nextJavaIdentifierPartChar();
        }
        if (Arrays.binarySearch(keywords, result) >= 0) {
            result = nextRandomId(idLength);
        }
        return result;
    }
}
