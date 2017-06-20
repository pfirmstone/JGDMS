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

package net.jini.io;

import java.io.IOException;

/**
 * Typically used as the nested exception of a
 * {@link java.rmi.ConnectIOException} if the constraints for a remote call
 * cannot be satisfied. Such an exception can be thrown at the point a remote
 * method is invoked for a variety of reasons, including:
 * <ul>
 * <li>A client requirement is not supported by the server.
 * <li>A client or server requirement conflicts with some other client or
 * server requirement.
 * <li>A client or server requirement cannot be satisfied by the proxy
 * implementation.
 * <li>The local subject that would be used for authentication does not
 * contain sufficient credentials to satisfy a client or server requirement.
 * <li>For a client or server requirement, the proxy implementation does not
 * implement any algorithm in common with the server (for example, because
 * the proxy implementation only uses algorithms that are available in the
 * client environment rather than downloading algorithm implementations).
 * <li>A delegated remote call is being attempted, and the current time is
 * either earlier than the granted delegation start time or later than the
 * granted delegation stop time.
 * </ul>
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 * @see net.jini.core.constraint.RemoteMethodControl
 */
public class UnsupportedConstraintException extends IOException {
    private static final long serialVersionUID = -5220259094045769772L;

    /**
     * Creates an instance with the specified detail message.
     *
     * @param s the detail message
     */
    public UnsupportedConstraintException(String s) {
	super(s);
    }

    /**
     * Creates an instance with the specified detail message and cause.
     *
     * @param s the detail message
     * @param cause the cause
     */
    public UnsupportedConstraintException(String s, Throwable cause) {
	super(s, cause);
    }
}
