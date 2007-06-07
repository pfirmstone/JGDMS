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

package com.sun.jini.jeri.internal.mux;

/**
 * ProtocolException is thrown inside the implementation of this package
 * to signal that a protocol violation has occurred at various levels.
 *
 * This exception type should never be thrown to code outside this
 * package; it is only used internally as an implementation technique
 * (like a C setjmp/longjmp).
 *
 * @author	Sun Microsystems, Inc.
 * 
 */
class ProtocolException extends Exception {

    private static final long serialVersionUID = -501814787956740472L;

    /**
     * Constructs a ProtocolException.
     */
    ProtocolException() {
	super();
    }

    /**
     * Constructs a ProtocolException with the specified detail message.
     */
    ProtocolException(String s) {
	super(s);
    }
}
