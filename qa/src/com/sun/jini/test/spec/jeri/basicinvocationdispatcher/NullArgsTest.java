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
package com.sun.jini.test.spec.jeri.basicinvocationdispatcher;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;

import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.core.constraint.InvocationConstraints;

import com.sun.jini.test.spec.jeri.util.FakeInboundRequest;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;

/**
 * <pre>
 * Purpose
 *   This test verifies that NullPointerExceptions are thrown as specified
 *   in many of the BasicInvocationDispatcher public and protected methods.
 *
 * Test Cases
 *   This test contains these test cases for these methods:
 *     1) checkAccess
 *     2) checkClientPermission
 *     3) createMarshalInputStream
 *     4) createMarshalOutputStream
 *     5) dispatch
 *     6) invoke
 *     7) marshalReturn
 *     8) marshalThrow
 *     9) unmarshalArguments
 *    10) unmarshalMethod
 *
 * Infrastructure
 *   This test requires the following infrastructure:
 *     1) FakeRemoteImpl
 *          -implements Remote
 *     2) FakeInboundRequest
 *          -constructor takes four parameters (first 2 bytes, method hash,
 *           and arguments) and writes them to request stream
 *          -abort method does nothing
 *          -checkConstraints does nothing
 *          -checkPermissions does nothing
 *          -populateContext does nothing
 *          -getClientHost returns local host name
 *          -getRequestInputStream and getResponseOutputStream methods
 *           return streams created in constructor
 *     3) FakeServerCapabilities
 *          -checkConstraints method returns InvocationConstraints.EMPTY
 *     4) FakeBasicInvocationDispatcher
 *          -subclasses BasicInvocationDispatcher
 *          -gives access to protected methods
 *
 * Actions
 *   The test performs the following steps:
 *     1) construct a FakeRemoteImpl, FakeServerCapabilities,
 *        and FakeInboundRequest
 *     2) construct a FakeBasicInvocationDispatcher, passing in the
 *        Collection of FakeRemoteImpl methods, FakeServerCapabilities,
 *        and null MethodConstraints and permission Class
 *     3) call protected BasicInvocationDispatcher methods
 *        with the various combinations of null arguments and assert that
 *        NullPointerExceptions are thrown
 * </pre>
 */
public class NullArgsTest extends AbstractDispatcherTest {

    // inherit javadoc
    public void run() throws Exception {
        try {

            // construct infrastructure needed by test
            request = new FakeInboundRequest(null);
            InvocationConstraints constraints = InvocationConstraints.EMPTY;
            Object[] args = {};
            Object retVal = new Object();
            Throwable exc = new Exception();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()));

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 1: checkAccess");
            logger.log(Level.FINE,"");

            try {
                dispatcher.checkAccess(null,null,constraints,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.checkAccess(null,fakeMethod,constraints,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.checkAccess(impl,null,constraints,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.checkAccess(impl,fakeMethod,constraints,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 2: checkClientPermission");
            logger.log(Level.FINE,"");

            try {
                BasicInvocationDispatcher.checkClientPermission(null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 3: createMarshalInputStream");
            logger.log(Level.FINE,"");

            try {
                dispatcher.createMarshalInputStream(null,null,true,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalInputStream(null,request,true,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalInputStream(impl,null,true,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalInputStream(impl,request,true,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 4: createMarshalOutputStream");
            logger.log(Level.FINE,"");

            try {
                dispatcher.createMarshalOutputStream(
                    null,fakeMethod,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalOutputStream(
                    null,fakeMethod,request,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalOutputStream(
                    impl,fakeMethod,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.createMarshalOutputStream(
                    impl,fakeMethod,request,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 5: dispatch");
            logger.log(Level.FINE,"");

            try {
                dispatcher.dispatch(null,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.dispatch(null,request,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.dispatch(impl,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.dispatch(impl,request,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 6: invoke");
            logger.log(Level.FINE,"");

            try {
                dispatcher.invoke(null,null,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.invoke(null,fakeMethod,args,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.invoke(impl,null,args,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.invoke(impl,fakeMethod,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.invoke(impl,fakeMethod,args,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 7: marshalReturn");
            logger.log(Level.FINE,"");

            try {
                dispatcher.marshalReturn(null,null,retVal,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalReturn(null,fakeMethod,retVal,out,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalReturn(impl,null,retVal,out,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalReturn(impl,fakeMethod,retVal,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalReturn(impl,fakeMethod,retVal,out,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 8: marshalThrow");
            logger.log(Level.FINE,"");

            try {
                dispatcher.marshalThrow(null,fakeMethod,null,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalThrow(null,fakeMethod,exc,out,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalThrow(impl,fakeMethod,null,out,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalThrow(impl,fakeMethod,exc,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.marshalThrow(impl,fakeMethod,exc,out,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 9: unmarshalArguments");
            logger.log(Level.FINE,"");

            try {
                dispatcher.unmarshalArguments(null,null,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalArguments(null,fakeMethod,in,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalArguments(impl,null,in,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalArguments(impl,fakeMethod,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalArguments(impl,fakeMethod,in,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

            logger.log(Level.FINE,"=================================");
            logger.log(Level.FINE,"test case 10: unmarshalMethod");
            logger.log(Level.FINE,"");

            try {
                dispatcher.unmarshalMethod(null,null,null);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalMethod(null,in,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalMethod(impl,null,context);
                assertion(false);
            } catch (NullPointerException ignore) {}
            try {
                dispatcher.unmarshalMethod(impl,in,null);
                assertion(false);
            } catch (NullPointerException ignore) {}

        } catch (Throwable t) {
            logger.log(Level.FINE,"Caught unexpected exception",t);
            throw new TestException("Caught unexpected exception: ",t);
        }
    }

}

