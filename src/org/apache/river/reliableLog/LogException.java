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

package org.apache.river.reliableLog;

import java.io.IOException;

/** 
 * This class can be used to represent all exceptional conditions that
 * occur during any logging process. Whenever an exception is caught 
 * while information is being logged, the exception can be wrapped
 * in this class so as to indicate an unsuccessful log operation.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public class LogException extends IOException {

    private static final long serialVersionUID = 1870528169848832111L;

    /** @serial */
    public final Throwable detail;

    /**
     * Create a wrapper exception for exceptions that occur during a logging
     * operation.
     */
    public LogException() {
	initCause(null);
        detail = null;
    }

    /**
     * For exceptions that occur during a logging operation, create a wrapper
     * exception with the specified description string.
     * @param s description string
     */
    public LogException(String s) {
	super(s);
	initCause(null);
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

    /**
     * Produce the message; including the message from the nested exception
     * if there is one.
     * @return the message produced.
     */
    @Override
    public String getMessage() {
        Throwable cause = super.getCause();
	if (cause == null) 
	    return super.getMessage();
	else
	    return super.getMessage() + 
		"; nested exception is: \n\t" +
		cause.toString();
    }

}
