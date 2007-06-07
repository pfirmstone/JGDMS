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
package com.sun.jini.norm;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This exception denotes a problem with the local implementation of a
 * Norm server.	 The <code>detail</code> field will give a description
 * that can be reported to Norm's developer (and may be documented
 * in that Norm's documentation).
 *
 * @author Sun Microsystems, Inc.
 */
public class InternalNormException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * The exception (if any) that triggered the internal exception.  This
     * may be <code>null</code>.
     *
     * @serial
     */
    public final Throwable nestedException;

    /**
     * Create an exception, forwarding a string to the superclass constructor.
     *
     * @param str the detail message
     */
    public InternalNormException(String str) {
	super(str);
	nestedException = null;
    }

    /**
     * Create an exception, forwarding a string and exception to the
     * superclass constructor.
     *
     * @param str the detail message
     * @param ex the cause
     */
    public InternalNormException(String str, Throwable ex) {
	super(str);
	nestedException = ex;
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    public void printStackTrace() {
	printStackTrace(System.err);
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    public void printStackTrace(PrintStream out) {
	super.printStackTrace(out);
	if (nestedException != null) {
	    out.println("nested exception:");
	    nestedException.printStackTrace(out);
	}
    }

    /**
     * Print the stack trace of this exception, plus that of the nested
     * exception, if any.
     */
    public void printStackTrace(PrintWriter out) {
	super.printStackTrace(out);
	if (nestedException != null) {
	    out.println("nested exception:");
	    nestedException.printStackTrace(out);
	}
    }
}
