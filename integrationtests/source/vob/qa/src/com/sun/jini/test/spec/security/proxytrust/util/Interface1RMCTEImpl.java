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
package com.sun.jini.test.spec.security.proxytrust.util;

import java.util.logging.Level;

/**
 * Remote class implementing RemoteMethodControl, TrustEquivalence and
 * TestInterface1 interfaces.
 */
public class Interface1RMCTEImpl extends RMCTEImpl implements TestInterface1 {

    // number of 'test' method invocations
    protected int testNum;

    // number of 'test1' method invocations
    protected int test1Num;

    // number of 'exTest' method invocations
    protected int exTestNum;

    /**
     * Default constructor.
     */
    public Interface1RMCTEImpl() {
        testNum = 0;
        test1Num = 0;
        exTestNum = 0;
    }

    /**
     * Test method #1. Increase number of this method invocations by one.
     */
    public void test() {
        ++testNum;
    }

    /**
     * Test method #2. Increase number of this method invocations by one.
     * Returns argument specified.
     *
     * @param i test argument
     * @return i
     */
    public int test1(int i) {
        ++test1Num;
        return i;
    }

    /**
     * Test method #3. Increase number of this method invocations by one.
     * Always throws FakeException.
     *
     * @throws FakeException (always)
     */
    public void exTest() throws FakeException {
        ++exTestNum;
        throw new FakeException("TestException");
    }

    /**
     * Returns number of 'test' method invocations.
     *
     * @return number of 'test' method invocations
     */
    public int getTestNum() {
        return testNum;
    }

    /**
     * Returns number of 'test1' method invocations.
     *
     * @return number of 'test1' method invocations
     */
    public int getTest1Num() {
        return test1Num;
    }

    /**
     * Returns number of 'exTest' method invocations.
     *
     * @return number of 'exTest' method invocations
     */
    public int getExTestNum() {
        return exTestNum;
    }

    /**
     * Returns true if argument implements the same interfaces as this class
     * and false otherwise.
     *
     * @return true if argument implements the same interfaces as this class
     *         and false otherwise
     */
    public boolean checkTrustEquivalence(Object obj) {
        return ProxyTrustUtil.sameInterfaces(obj, this);
    }
}
