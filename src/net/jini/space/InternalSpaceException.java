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
package net.jini.space;

import java.io.*;

/**
 * This exception denotes a problem with the local implementation of the
 * <code>JavaSpace</code> interface.  The <code>detail</code> field
 * will give a description that can be reported to the space developer
 * (and may be documented in that space's external documentation).
 *
 * @author Sun Microsystems, Inc.
 *
 * @see JavaSpace
 */
public class InternalSpaceException extends RuntimeException {
    static final long serialVersionUID = -4167507833172939849L;


    /**
     * The exception (if any) that triggered the internal exception.  This
     * may be <code>null</code>.
     *
     * @serial
     */
    public final Throwable nestedException;

    /**
     * Create an exception, forwarding a string to the superclass constructor.
     * @param str  a detail message
     */
    public InternalSpaceException(String str) {
	super(str);
	nestedException = null;
    }

    /**
     * Create an exception, forwarding a string and exception to the
     * superclass constructor.
     * 
     * @param str  a detail message
     * @param ex a nested exception
     */
    public InternalSpaceException(String str, Throwable ex) {
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
