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
package org.apache.river.test.spec.jeri.util;

import net.jini.jeri.BasicInvocationDispatcher;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.ServerCapabilities;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;

import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.ExportException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Collection;

/**
 * A fake BasicInvocationDispatcher implementation that can be configured
 * to throw exceptions in it's protected methods.
 */
public class FakeBasicInvocationDispatcher extends BasicInvocationDispatcher {

    Logger logger;
    Throwable unmarshalArgumentsException;
    Throwable unmarshalMethodException;
    Throwable marshalReturnException;
    Throwable marshalThrowException;
    Throwable createMarshalInputStreamException;
    Throwable createMarshalOutputStreamException;
    Throwable checkAccessException;
    Throwable invokeException;

    /**
     * Constructs a FakeBasicInvocationDispatcher.
     */
    public FakeBasicInvocationDispatcher(Collection methods,
        ServerCapabilities serverCaps, MethodConstraints serverConstraints,
        Class permClass, ClassLoader classLoader) throws ExportException
    {
        super(methods,serverCaps,serverConstraints,permClass,classLoader);
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
    }

    /***
     *** The following methods configure the exceptions to throw from the
     *** various protected methods.  Mapping from these "set" methods to
     *** the corresponding protected method should be obvious.
     ***
     *** @param t the exception to throw
     ***/

    public void setCheckAccessException(Throwable t) {
        checkAccessException = t;
    }
    public void setCreateMarshalInputStreamException(Throwable t) {
        createMarshalInputStreamException = t;
    }
    public void setCreateMarshalOutputStreamException(Throwable t) {
        createMarshalOutputStreamException = t;
    }
    public void setInvokeException(Throwable t) {
        invokeException = t;
    }
    public void setUnmarshalMethodException(Throwable t) {
        unmarshalMethodException = t;
    }
    public void setUnmarshalArgumentsException(Throwable t) {
        unmarshalArgumentsException = t;
    }
    public void setMarshalReturnException(Throwable t) {
        marshalReturnException = t;
    }
    public void setMarshalThrowException(Throwable t) {
        marshalThrowException = t;
    }

    /***
     *** Overridden protected methods that will throw a
     *** configured exception set with the corresponding
     *** "set" method above. If the configured exception is null, then
     *** these methods will call <code>super.<method>(...)</code>.
     ***
     *** @throws configured exception if not null
     *** @throws AssertionError if configured exception was set but
     ***        is not an instance of an exception thrown by the method
     ***/

    public void checkAccess(Remote impl, Method method,
        InvocationConstraints constraints, Collection context)
    {
        logger.entering(getClass().getName(),"checkAccess");
        if (checkAccessException != null) {
            throwUnchecked(checkAccessException);
            throw new AssertionError();
        }
        super.checkAccess(impl,method,constraints,context);
    }

    public ObjectInputStream createMarshalInputStream(Object impl,
        InboundRequest request, boolean integrity, Collection context)
        throws IOException
    {
        logger.entering(getClass().getName(),"createMarshalInputStream");
        if (createMarshalInputStreamException != null) {
            throwIOE(createMarshalInputStreamException);
            throw new AssertionError();
        }
        return super.createMarshalInputStream(impl,request,integrity,context);
    }

    public ObjectOutputStream createMarshalOutputStream(Object impl,
        Method method, InboundRequest request, Collection context)
        throws IOException
    {
        logger.entering(getClass().getName(),"createMarshalOutputStream");
        if (createMarshalOutputStreamException != null) {
            throwIOE(createMarshalOutputStreamException);
            throw new AssertionError();
        }
        return super.createMarshalOutputStream(impl,method,request,context);
    }

    public Object invoke(Remote impl, Method method, Object[] args, 
        Collection context) throws Throwable 
    {
        logger.entering(getClass().getName(),"invoke");
        if (invokeException != null) {
            throw invokeException;
        }
        return super.invoke(impl,method,args,context);
    }

    public Object[] unmarshalArguments(Remote impl, Method method, 
        ObjectInputStream in, Collection c) 
        throws IOException, ClassNotFoundException
    {
        logger.entering(getClass().getName(),"unmarshalArguments");
        if (unmarshalArgumentsException != null) {
            throwIOE(unmarshalArgumentsException);
            throwCNFE(unmarshalArgumentsException);
            throw new AssertionError();
        }
        return super.unmarshalArguments(impl,method,in,c);
    }

    public Method unmarshalMethod(Remote impl, ObjectInputStream in, 
        Collection c)
        throws IOException, ClassNotFoundException, NoSuchMethodException
    {
        logger.entering(getClass().getName(),"unmarshalMethod");
        if (unmarshalMethodException != null) {
            throwIOE(unmarshalMethodException);
            throwCNFE(unmarshalMethodException);
            throwNSME(unmarshalMethodException);
            throw new AssertionError();
        }
        return super.unmarshalMethod(impl,in,c);
    }

    public void marshalReturn(Remote impl, Method method,
        Object returnValue, ObjectOutputStream out, Collection c) 
        throws IOException
    {
        logger.entering(getClass().getName(),"marshalReturn");
        if (marshalReturnException != null) {
            throwIOE(marshalReturnException);
            throw new AssertionError();
        }
        super.marshalReturn(impl,method,returnValue,out,c);
    }

    public void marshalThrow(Remote impl, Method method,
        Throwable throwable, ObjectOutputStream out, Collection c) 
        throws IOException
    {
        logger.entering(getClass().getName(),"marshalThrow");
        if (marshalThrowException != null) {
            throwIOE(marshalThrowException);
            throw new AssertionError();
        }
        super.marshalThrow(impl,method,throwable,out,c);
    }

    public ClassLoader getClassLoader0() {
        return super.getClassLoader();
    }

    // ********************************************** //

    private void throwCNFE(Throwable t) throws ClassNotFoundException {
        if (t instanceof ClassNotFoundException) {
            throw (ClassNotFoundException) t;
        } 
        throwUnchecked(t);
    }
    private void throwNSME(Throwable t) throws NoSuchMethodException {
        if (t instanceof NoSuchMethodException) {
            throw (NoSuchMethodException) t;
        } 
        throwUnchecked(t);
    }
    private void throwIOE(Throwable t) throws IOException {
        if (t instanceof IOException) {
            throw (IOException) t;
        } 
        throwUnchecked(t);
    }
    private void throwUnchecked(Throwable t) throws RuntimeException, Error {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } 
    }
}
