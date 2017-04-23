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
package com.sun.jini.reliableLog;

/**
 * Provided for backward compatibility, migrate to new name space.
 */
@Deprecated
public class LogException extends org.apache.river.reliableLog.LogException {
    
    private static final long serialVersionUID = 1870528169848832111L;

    /** @serial */
    public final Throwable detail;
    
    /**
     * Create a wrapper exception for exceptions that occur during a logging
     * operation.
     */
    public LogException() {
	super();
        detail = null;
    }

    /**
     * For exceptions that occur during a logging operation, create a wrapper
     * exception with the specified description string.
     * @param s description string
     */
    public LogException(String s) {
	super(s);
        detail = null;
    }

    /**
     * For exceptions that occur during a logging operation, create a wrapper
     * exception with the specified description string and the specified
     * nested exception.
     * @param s description string
     * @param ex nested exception
     */
    public LogException(String s, Throwable ex) {
	super(s,ex);
        detail = ex;
    }
}
