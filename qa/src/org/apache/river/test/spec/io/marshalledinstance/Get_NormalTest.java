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
package org.apache.river.test.spec.io.marshalledinstance;

import java.util.logging.Level;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.AdminManager;
import org.apache.river.qa.harness.Test;

import net.jini.io.MarshalledInstance;
import net.jini.jeri.BasicInvocationHandler;

import org.apache.river.test.spec.io.util.FakeObject;
import org.apache.river.test.spec.io.util.FakeObjectEndpoint;
import org.apache.river.test.spec.io.util.FakeClassLoader;

import java.io.File;
import java.rmi.server.RMIClassLoader;
import java.util.logging.Level;
import java.lang.reflect.Proxy;
import net.jini.loader.ClassLoading;

/**
 * <pre>
 * Purpose
 *   This test verifies the behavior of MarshalledInstance
 *   get method in cases where it should return normally.
 * 
 * Test Cases
 *   This test iterates over a 2-tuple.  Each 2-tuple denotes one test case
 *   and is defined by the cross-product of these variables:
 *      Object  storeObject
 *      boolean requestIntegrity
 *   where storeObject is one of:
 *      a File instance
 *      a FakeArgument instance created by call to RMIClassLoader.loadClass
 *      a dynamic proxy for FakeInterface
 *   requestIntegrity is one of:
 *      true
 *      false
 * 
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeIntegrityVerifier
 *          -installed in test JAR
 *          -overrides the providesIntegrity method so it returns a value
 *           based on a system property setting
 *     2) FakeArgument
 *          -implements Serializable
 *          -class file contained in a JAR file separate from the test
 *     3) FakeInterface
 *          -an interface which declares no methods
 *     4) FakeClassLoader
 *          -delegates to system class loader
 * 
 * Actions
 *   The test performs the following steps:
 *     1) construct a MarshalledInstance with storeObject
 *     2) set system property used by FakeIntegrityVerifier to true
 *     3) call the MarshalledInstance get methods with
 *        verifyCodebaseIntegrity set to requestIntegrity
 *     4) assert an object equivalent to storeObject is returned
 * </pre>
 */
public class Get_NormalTest extends QATestEnvironment implements Test {

    QAConfig config;
    Object[][] cases;
    String interfaceName = "org.apache.river.test.spec.io.util.FakeInterface";
    AdminManager manager;

    public Test construct(QAConfig sysConfig) throws Exception {
        this.config = (QAConfig) sysConfig;
        config.setDynamicParameter(
                "qaClassServer.port",
                config.getStringConfigVal("org.apache.river.test.port", "8082"));
        manager = new AdminManager(sysConfig);
        manager.startService("testClassServer");

        String codebase = config.getStringConfigVal(
            "org.apache.river.test.spec.io.util.fakeArgumentJar","Error");

        // storeObject field values
        File fakeFile = new File("fakeFile");
        Object fakeArg = ClassLoading.loadClass(codebase,
            "org.apache.river.test.spec.io.util.FakeArgument", null, false, null).newInstance();
        Object proxy = Proxy.newProxyInstance(
                    ClassLoading.getClassLoader(codebase),
                    new Class[] {ClassLoading.loadClass(
                        codebase,interfaceName, null, false, null)},
                    new BasicInvocationHandler(new FakeObjectEndpoint(),null));

        // requestIntegrity
        Boolean f = Boolean.FALSE;
        Boolean t = Boolean.TRUE;

        cases = new Object[][] {
            // storeObject, requestIntegrity
            {fakeFile,   f},
            {fakeFile,   t},
            {fakeArg,    f},
            {fakeArg,    t},
            {proxy,      f},
            {proxy,      t}
        };
        return this;
    }

    public void run() throws Exception {
        for (int i = 0; i < cases.length; i++) {
            logger.log(Level.FINE,"=================================");
            Object storeObject = cases[i][0]; 
            boolean requestIntegrity = 
                ((Boolean)cases[i][1]).booleanValue();
            logger.log(Level.FINE,"test case " + (i+1) + ": "
                + "storeObject:" + storeObject
                + ",requestIntegrity:" + requestIntegrity);
            logger.log(Level.FINE,"");

            MarshalledInstance mi = new MarshalledInstance(storeObject);

            // set return value for verifier's providesIntegrity method
            System.setProperty(
                "org.apache.river.test.spec.io.util.FakeIntegrityVerifier."
                + "throwException","" + (!requestIntegrity) );
            System.setProperty(
                "org.apache.river.test.spec.io.util.FakeIntegrityVerifier."
                + "providesIntegrity","true");

            Object retObject = mi.get(
                null,requestIntegrity,new FakeClassLoader(),null);
            assertion(storeObject.equals(retObject),retObject.toString());

            retObject = mi.get(requestIntegrity);
            assertion(storeObject.equals(retObject),retObject.toString());
        }
    }

    public void tearDown() {
    }

}

