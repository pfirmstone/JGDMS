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
package com.sun.jini.test.spec.jeri.basicinvocationhandler;

import java.util.logging.Level;

import com.sun.jini.test.spec.jeri.util.FakeBasicInvocationHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies that NullPointerExceptions are thrown as specified
 *   in many of the BasicInvocationHandler protected methods and that
 *   Throwable is thrown in certain calls to createMarshalInputStream.
 *
 * Test Cases
 *   This test contains these test cases for these methods:
 *     1) createMarshalInputStream
 *     2) createMarshalOutputStream
 *     3) marshalArguments
 *     4) marshalMethod
 *     5) unmarshalReturn
 *     6) unmarshalThrow
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeInterface
 *          -extends Remote and contains one method
 *     2) FakeOutboundRequest
 *          -abort method does nothing
 *          -getDeliveryStatus does nothing
 *          -getUnfulfilledConstraints does nothing
 *          -populateContext does nothing
 *          -getRequestOutputStream and getResponseInputStream methods
 *           return streams created in constructor
 *     3) FakeBasicInvocationHandler
 *          -subclasses BasicInvocationHandler
 *          -gives access to protected methods
 *     4) FakeObjectEndpoint
 *          -methods do nothing
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeOutboundRequest, FakeBasicInvocationHandler, and
 *        FakeObjectEndpoint
 *     2) construct a proxy for FakeInterface, passing in 
 *        FakeBasicInvocationHandler,
 *     3) call protected BasicInvocationHandler methods
 *        with the various combinations of null arguments and assert that
 *        NullPointerExceptions are thrown
 * </pre>
 */
public class NullArgsTest extends AbstractInvokeTest {

    // inherit javadoc
    public void run() throws Exception {
        // construct additional infrastructure needed by test
        ArrayList c = new ArrayList();
        Method method = impl.getClass().getMethod("fakeMethod",null);
        Object[] args = {};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(baos.toByteArray()));

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 1: createMarshalInputStream");
        logger.log(Level.FINE,"");

        try {
            handler.createMarshalInputStream(null,null,null,true,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalInputStream(null,method,request,true,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalInputStream(impl,null,request,true,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalInputStream(impl,method,null,true,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalInputStream(impl,method,request,true,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalInputStream(
                new Object(),method,request,true,c);
            assertion(false);
        } catch (Throwable ignore) {}
        try {
            FakeBasicInvocationHandler handler2 = 
                new FakeBasicInvocationHandler(
                    objectEndpoint,      // objectEndpoint
                    null);               // serverConstraints
            handler2.createMarshalInputStream(impl,method,request,true,c);
            assertion(false);
        } catch (Throwable ignore) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 2: createMarshalOutputStream");
        logger.log(Level.FINE,"");

        try {
            handler.createMarshalOutputStream(null,null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalOutputStream(null,method,request,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalOutputStream(impl,null,request,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalOutputStream(impl,method,null,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.createMarshalOutputStream(impl,method,request,null);
            assertion(false);
        } catch (NullPointerException ignore) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 3: marshalArguments");
        logger.log(Level.FINE,"");

        try {
            handler.marshalArguments(null,null,null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalArguments(null,method,args,out,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalArguments(impl,null,args,out,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalArguments(impl,method,null,out,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalArguments(impl,method,args,null,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalArguments(impl,method,args,out,null);
            assertion(false);
        } catch (NullPointerException ignore) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 4: marshalMethod");
        logger.log(Level.FINE,"");

        try {
            handler.marshalMethod(null,null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalMethod(null,method,out,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalMethod(impl,null,out,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalMethod(impl,method,null,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.marshalMethod(impl,method,out,null);
            assertion(false);
        } catch (NullPointerException ignore) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 5: unmarshalReturn");
        logger.log(Level.FINE,"");

        try {
            handler.unmarshalReturn(null,null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalReturn(null,method,in,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalReturn(impl,null,in,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalReturn(impl,method,null,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalReturn(impl,method,in,null);
            assertion(false);
        } catch (NullPointerException ignore) {}

        logger.log(Level.FINE,"=================================");
        logger.log(Level.FINE,"test case 6: unmarshalThrow");
        logger.log(Level.FINE,"");

        try {
            handler.unmarshalThrow(null,null,null,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalThrow(null,method,in,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalThrow(impl,null,in,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalThrow(impl,method,null,c);
            assertion(false);
        } catch (NullPointerException ignore) {}
        try {
            handler.unmarshalThrow(impl,method,in,null);
            assertion(false);
        } catch (NullPointerException ignore) {}
    }

}

