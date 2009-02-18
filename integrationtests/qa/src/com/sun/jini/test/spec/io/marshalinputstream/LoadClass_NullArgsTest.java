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
package com.sun.jini.test.spec.io.marshalinputstream;

import java.util.logging.Level;

import com.sun.jini.qa.harness.QATest;
import com.sun.jini.qa.harness.QAConfig;

import net.jini.loader.ClassLoading;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies that NullPointerExceptions are thrown as specified
 *   in the ClassLoading methods.
 *
 * Test Cases
 *   This test contains these test cases for these methods:
 *     1) loadClass(null,null,null,true,null)
 *     2) loadProxyClass(null,null,null,true,null)
 *     3) loadProxyClass(null,String[] with a null element,null,true,null)
 *
 * Infrastructure
 *   This test requires no infrastructure.
 *
 * Actions
 *   The test performs the following steps:
 *     1) call static ClassLoading methods
 *        with the various combinations of null arguments and assert that
 *        NullPointerExceptions are thrown
 * </pre>
 */
public class LoadClass_NullArgsTest extends QATest {

    // inherit javadoc
    public void setup(QAConfig sysConfig) throws Exception {
    }

    // inherit javadoc
    public void run() throws Exception {
        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: "
            + "loadClass(null,null,null,true,null)");
        logger.log(Level.FINE,"");

        try {
            ClassLoading.loadClass(null,null,null,true,null);
        } catch (NullPointerException ignore) { }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: "
            + "loadProxyClass(null,null,null,true,null)");
        logger.log(Level.FINE,"");

        try {
            ClassLoading.loadProxyClass(null,null,null,true,null);
        } catch (NullPointerException ignore) { }

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: loadProxyClass("
            + "null,String[] with a null element,null,true,null)");
        logger.log(Level.FINE,"");

        try {
            ClassLoading.loadProxyClass(
                null,new String[] {null,"bar"},null,true,null);
        } catch (NullPointerException ignore) { }
    }

    // inherit javadoc
    public void tearDown() {
    }

}

