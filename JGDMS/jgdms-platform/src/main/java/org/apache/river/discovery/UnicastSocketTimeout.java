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

package org.apache.river.discovery;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import net.jini.core.constraint.InvocationConstraint;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;

/**
 * Represents a constraint on the timeout set on sockets used for unicast
 * discovery.  Lookup services and discovery clients can use this constraint to
 * specify the maximum length of time that reads of unicast discovery data will
 * block.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
@AtomicSerial
public final class UnicastSocketTimeout 
    implements InvocationConstraint, Serializable
{
    private static final long serialVersionUID = 6500477426762925657L;
    
    public static SerialForm [] serialForm(){
        return new SerialForm[]{
            new SerialForm("timeout", Integer.TYPE)
        };
    }
    
    public static void serialize(PutArg arg, UnicastSocketTimeout u) throws IOException{
        arg.put("timeout", u.timeout);
        arg.writeArgs();
    }

    /**
     * The socket timeout.
     *
     * @serial
     */
    private final int timeout;

    /**
     * Creates a <code>UnicastSocketTimeout</code> constraint for the given
     * timeout.
     *
     * @param timeout the socket timeout
     * @throws IllegalArgumentException if the given timeout is negative
     */
    public UnicastSocketTimeout(int timeout) {
	this(timeout, check(timeout));
    }
    
    UnicastSocketTimeout(GetArg arg) throws IOException {
	this(arg.get("timeout", -1));
    }
    
    private UnicastSocketTimeout(int timeout, boolean check){
	this.timeout = timeout;
    }
    
    private static boolean check(int timeout){
	if (timeout < 0) {
	    throw new IllegalArgumentException("invalid timeout");
	}
	return true;
    }

    /**
     * Returns the socket timeout.
     *
     * @return the socket timeout
     */
    public int getTimeout() {
	return timeout;
    }

    @Override
    public int hashCode() {
	return UnicastSocketTimeout.class.hashCode() + timeout;
    }

    @Override
    public boolean equals(Object obj) {
	return obj instanceof UnicastSocketTimeout &&
	       timeout == ((UnicastSocketTimeout) obj).timeout;
    }

    @Override
    public String toString() {
	return "UnicastSocketTimeout[" + timeout + "]";
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (timeout < 0) {
	    throw new InvalidObjectException("invalid timeout");
	}
    }
}
