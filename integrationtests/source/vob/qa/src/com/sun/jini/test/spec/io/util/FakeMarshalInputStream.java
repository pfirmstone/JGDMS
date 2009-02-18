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
/*
 */
package com.sun.jini.test.spec.io.util;

import net.jini.io.MarshalInputStream;

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * MarshalInputStream subclass used by tests to throw a specified
 * exception when the readAnnotation method is called and gives
 * access to protected methods.
 */
public class FakeMarshalInputStream extends MarshalInputStream {

    Logger logger;
    private Throwable readAnnotationException;
    private String readAnnotationReturn;

    public FakeMarshalInputStream(InputStream in, Throwable ra_exc, 
        String ra_ret, boolean verifyCodebaseIntegrity) throws IOException
    {
        super(in,null,verifyCodebaseIntegrity,null,new ArrayList());
        logger = Logger.getLogger("com.sun.jini.qa.harness.test");
        logger.entering(getClass().getName(),"constructor");
        readAnnotationException = ra_exc;
        readAnnotationReturn = ra_ret;
    }

    public FakeMarshalInputStream(InputStream in, Throwable ra_exc, 
        String ra_ret) throws IOException
    {
        this(in,ra_exc,ra_ret,true);
    }

    protected String readAnnotation() 
        throws IOException,ClassNotFoundException 
    {
        logger.entering(getClass().getName(),"readAnnotation");
        if (readAnnotationException != null) {
            if (readAnnotationException instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) readAnnotationException;
            } else if (readAnnotationException instanceof IOException) {
                throw (IOException) readAnnotationException;
            } else if (readAnnotationException instanceof RuntimeException) {
                throw (RuntimeException) readAnnotationException;
            } else if (readAnnotationException instanceof Error) {
                throw (Error) readAnnotationException;
            } else {
                throw new AssertionError();
            }
        } else {
            return readAnnotationReturn;
        }
    }

    public Class resolveClass(ObjectStreamClass classDesc)
        throws IOException, ClassNotFoundException
    {
        return super.resolveClass(classDesc);
    }

    public Class resolveProxyClass(String[] interfaceNames)
        throws IOException, ClassNotFoundException
    {
        return super.resolveProxyClass(interfaceNames);
    }

}
