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
package com.sun.jini.test.impl.mercury;

import java.rmi.NoSuchObjectException;

import net.jini.core.event.UnknownEventException;

/**
 * Class used as a crude hack to cause a NoSuchObjectException to occur
 * on the caller-side connection without actually throwing a
 * NoSuchObjectException on the server-side (which would get wrapped
 * as ServerException).
 */

public class MyUnknownEventException extends UnknownEventException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an UnknownEventException with the specified detail message.
     *
     * @param reason  a <tt>String</tt> containing a detail message
     */
    public MyUnknownEventException(String reason) 
    {
	super(reason);
    }

    /**
     * Constructs an UnknownEventException with no detail message.
     */
    public MyUnknownEventException()
    {
	super();
    }
    
    private Object writeReplace() throws java.io.ObjectStreamException {
	return new NoSuchObjectException(getMessage());
    }
}

