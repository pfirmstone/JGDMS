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

import net.jini.jeri.BasicInvocationHandler;
import net.jini.jeri.OutboundRequest;
import net.jini.jeri.ObjectEndpoint;
import net.jini.core.constraint.MethodConstraints;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.logging.Logger;
import java.util.Collection;

/**
 * A fake BasicInvocationHandler implementation that can be configured
 * to throw exceptions in it's protected methods.
 */
public class FakeBasicInvocationHandler extends BasicInvocationHandler {

    Logger logger;
    Throwable marshalArgumentsException;
    Throwable marshalMethodException;
    Throwable unmarshalReturnException;
    Throwable unmarshalThrowException;
    Throwable createMarshalInputStreamException;
    Throwable createMarshalOutputStreamException;
    Collection context;

    /**
     * Constructs a FakeBasicInvocationHandler.
     */
    public FakeBasicInvocationHandler(ObjectEndpoint oe,
        MethodConstraints serverConstraints) 
    {
        super(oe,serverConstraints);
        logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
    }

    /**
     * Constructs a FakeBasicInvocationHandler.
     */
    public FakeBasicInvocationHandler(FakeBasicInvocationHandler bih,
        MethodConstraints clientConstraints) 
    {
        super(bih,clientConstraints);
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
 
    public void setCreateMarshalInputStreamException(Throwable t) {
        createMarshalInputStreamException = t;
    }
    public void setCreateMarshalOutputStreamException(Throwable t) {
        createMarshalOutputStreamException = t;
    }
    public void setMarshalArgumentsException(Throwable t) {
        marshalArgumentsException = t;
    }
    public void setMarshalMethodException(Throwable t) {
        marshalMethodException = t;
    }
    public void setUnmarshalReturnException(Throwable t) {
        unmarshalReturnException = t;
    }
    public void setUnmarshalThrowException(Throwable t) {
        unmarshalThrowException = t;
    }
    public Collection getContext() {
        return context;
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

    public ObjectInput createMarshalInputStream(Object proxy,
        Method method, OutboundRequest request, 
        boolean verifyCodebaseIntegrity, Collection context) throws IOException
    {
        logger.entering(getClass().getName(),"createMarshalInputStream");
        if (createMarshalInputStreamException != null) {
            throwIOE(createMarshalInputStreamException);
            throw new AssertionError();
        }
        return super.createMarshalInputStream(
            proxy,method,request,verifyCodebaseIntegrity,context);
    }
 
    public ObjectOutput createMarshalOutputStream(Object proxy,
        Method method, OutboundRequest request, Collection context)
        throws IOException
    {
        logger.entering(getClass().getName(),"createMarshalOutputStream");
        this.context = context;
        if (createMarshalOutputStreamException != null) {
            throwIOE(createMarshalOutputStreamException);
            throw new AssertionError();
        }
        return super.createMarshalOutputStream(proxy,method,request,context);
    }

    public void marshalArguments(Object proxy, Method method, 
        Object[] args, ObjectOutputStream out, Collection c) throws IOException
    {
        logger.entering(getClass().getName(),"marshalArguments");
        if (marshalArgumentsException != null) {
            throwIOE(marshalArgumentsException);
            throw new AssertionError();
        }
        super.marshalArguments(proxy,method,args,out,c);
    }

    public void marshalMethod(Object proxy, Method method,
        ObjectOutputStream out, Collection c) throws IOException
    {
        logger.entering(getClass().getName(),"marshalMethod");
        if (marshalMethodException != null) {
            throwIOE(marshalMethodException);
            throw new AssertionError();
        }
        super.marshalMethod(proxy,method,out,c);
    }

    public Object unmarshalReturn(Object proxy, Method method,
        ObjectInputStream in, Collection c) 
        throws IOException, ClassNotFoundException
    {
        logger.entering(getClass().getName(),"unmarshalReturn");
        if (unmarshalReturnException != null) {
            throwIOE(unmarshalReturnException);
            throwCNFE(unmarshalReturnException);
            throw new AssertionError();
        }
        return super.unmarshalReturn(proxy,method,in,c);
    }

    public Throwable unmarshalThrow(Object proxy, Method method,
        ObjectInputStream in, Collection c) 
        throws IOException, ClassNotFoundException
    {
        logger.entering(getClass().getName(),"unmarshalThrow");
        if (unmarshalThrowException != null) {
            throwIOE(unmarshalThrowException);
            throwCNFE(unmarshalThrowException);
            throw new AssertionError();
        }
        return super.unmarshalThrow(proxy,method,in,c);
    }

    public InvocationHandler setClientConstraints(MethodConstraints c) {
        return super.setClientConstraints(c);
    }

    // ********************************************** //
 
    private void throwCNFE(Throwable t) throws ClassNotFoundException {
        if (t instanceof ClassNotFoundException) {
            throw (ClassNotFoundException) t;
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
