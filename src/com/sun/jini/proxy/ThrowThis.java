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
package com.sun.jini.proxy;

import java.rmi.RemoteException;

/**
 * The semi-official way for remote methods on registration objects
 * (e.g <code>LeaseRenewalSet</code>,
 * <code>LookupDiscoveryRegistration</code>,
 * <code>MailboxRegistration</code>) to indicate that the registration
 * no longer exists is to throw <code>NoSuchObjectException</code>.
 * Unfortunately if the registration object is implemented as a smart
 * proxy that uses RMI to communicate back to the server (instead of a
 * straight RMI object) it is not possible for the server to throw
 * <code>NoSuchObjectException</code> directly since RMI will wrap
 * such an exception in a <code>ServerException</code>.
 * <p>
 * <code>ThrowThis</code> provides a solution to this problem.  When
 * the server wants to throw a <code>RemoteException</code> it creates a
 * <code>ThrowThis</code> object that wraps the
 * <code>RemoteException</code> and then throws the
 * <code>ThrowThis</code>.  The proxy catches <code>ThrowThis</code>
 * exceptions and in the catch block and calls
 * <code>throwRemoteException</code> to throw the intended exception.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see java.rmi.NoSuchObjectException
 * @see java.rmi.ServerException 
 */
public class ThrowThis extends Exception {
    private static final long serialVersionUID = 956456443698267251L;

    /**
     * Exception we want the proxy to throw
     * @serial
     */
    final private RemoteException toThrow;

    /**
     * Simple constructor
     * @param toThrow <code>RemoteException</code> you proxy to throw
     */
    public ThrowThis(RemoteException toThrow) {
	super();
	this.toThrow = toThrow;
    }

    /**
     * Simple constructor.  Note that, in general, objects of this class
     * are just a detail of the private protocol between the proxy and
     * its server, and are not intended to leak out of a public api.
     * 
     * @param toThrow <code>RemoteException</code> you proxy to throw
     * @param message Message to construct super object with 
     */
    public ThrowThis(RemoteException toThrow, String message) {
	super(message);
	this.toThrow = toThrow;
    }

    /**
     * Throw the <code>RemoteException</code> the server wants thrown
     */
    public void throwRemoteException() throws RemoteException {
	throw toThrow;
    }

    /**
     * Returns the detail message, including the message from the nested
     * exception the server wants thrown
     */
    public String getMessage() {
	return super.getMessage() +
		"; that is transporting: \n\t" +
		toThrow.toString();
    }

    /**
     * Prints the composite message to <code>System.err</code>.
     */
    public void printStackTrace()
    {
	printStackTrace(System.err);
    }

    /**
     * Prints the composite message and the embedded stack trace to
     * the specified print writer <code>pw</code>
     * @param pw the print writer
     */
    public void printStackTrace(java.io.PrintWriter pw)
    {
	synchronized(pw) {
	    pw.println(this);
	    toThrow.printStackTrace(pw);
	}
    }

    /**
     * Prints the composite message and the embedded stack trace to
     * the specified stream <code>ps</code>.
     * @param ps the print stream
     */
    public void printStackTrace(java.io.PrintStream ps)
    {
	synchronized(ps) {
	    ps.println(this);
	    toThrow.printStackTrace(ps);
	}
    }
}



