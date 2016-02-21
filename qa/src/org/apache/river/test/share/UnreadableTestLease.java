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


package org.apache.river.test.share;

// java.io
import java.io.IOException;
import java.io.ObjectInputStream;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * UnreadableTestLease isA TestLease that will throw an IOException
 * when an attempt is made to deserialize the Lease.
 *
 * @author Steven Harris - SMI Software Development 
 */
@AtomicSerial
public class UnreadableTestLease extends TestLease {
    
    private static boolean failMode = false;

    // javadoc purposefully inherited from parent class
    public UnreadableTestLease(int id, LeaseBackEnd home, long expiration) {
	super(id, home, expiration);
    }
    
    public UnreadableTestLease(GetArg arg) throws IOException{
	super(check(arg));
    }
    
    private static GetArg check(GetArg arg) throws IOException{
	if (failMode) {
	    TestLease lease = new TestLease(arg);
	    String message = "UnreadableTestLease: deserialization refused! " +
		"Lease id = " + lease.id();
	    throw new IOException(message);
	}
	return arg;
    }
    
    /**
     * Just refuse to deserialize by throwing an IOException.
     */
    private void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException
    {
	if (failMode) {
	    String message = "UnreadableTestLease: deserialization refused! " +
		"Lease id = " + id();
	    throw new IOException(message);
	}

	stream.defaultReadObject();
    }

    /**
     * Turn failure mode on (true) or off (false).
     * 
     * <P>Notes:<BR>failMode controls how read object behaves. If failMode is
     * true then readObject throws an Exception. If failMode is false then
     * deserialization is allowed to proceed normally.</P>
     * 
     * @param onOff a boolean value assigned to failMode.
     *
     */
    public static void setFailMode(boolean onOff) {
	failMode = onOff;
    }

} // UnreadableTestLease
