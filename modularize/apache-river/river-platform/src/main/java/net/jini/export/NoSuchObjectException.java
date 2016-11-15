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

package net.jini.export;

/**
 * A <code>NoSuchObjectException</code> is thrown if an attempt is made to
 * invoke a method on an object that no longer exists in the remote virtual
 * machine.
 *
 * This class was created to preserve the cause.  RemoteException prevents
 * <code>initCause(Throwable cause)</code> from working and 
 * java.rmi.NoSuchObjectException only has a single arg String constructor.
 */
public class NoSuchObjectException extends java.rmi.NoSuchObjectException {
    private static final long serialVersionUID = 1L;
    
    private final Throwable cause;

    public NoSuchObjectException(String s, Throwable cause) {
	super(s);
	this.cause = cause;
    }
    
    @Override
    public Throwable getCause(){
	return cause;
    }
    
}
