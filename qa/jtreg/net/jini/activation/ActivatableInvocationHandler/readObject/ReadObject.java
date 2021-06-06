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
/* @test
 * @bug 4842263
 * @summary AIH.readObject should throw InvalidObjectException if
 * 	    constraints inconsistent.
 * @author Ann Wollrath
 *
 * @build ReadObject
 * @run main/othervm/policy=security.policy/timeout=240 ReadObject
 */

import java.io.*;
import java.lang.reflect.Method;
import java.rmi.Remote;
import net.jini.activation.*;
import net.jini.activation.arg.*;
import java.util.HashSet;
import java.util.Iterator;
import net.jini.activation.ActivatableInvocationHandler;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.MethodConstraints;
import net.jini.core.constraint.RemoteMethodControl;
import net.jini.io.MarshalInputStream;
import net.jini.io.MarshalOutputStream;

public class ReadObject {

    public static void main(String[] args) throws Exception {
	System.err.println("\nRegression test for bug 4842263\n");

	/*
	 * Create handler.
	 */
	ActivationID id = new FakeActivationID();
	    UnderlyingProxy uproxy = new UnderlyingProxy();
	ActivatableInvocationHandler aih =
	    new ActivatableInvocationHandler(id, uproxy);
	
	/*
	 * Serialize handler.
	 */
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	MarshalOutputStream out =
	    new MarshalOutputStream(bout, new HashSet());
	out.writeObject(aih);
	out.flush();
	byte[] bytes = bout.toByteArray();

	/*
	 * Read in handler (should succeed).
	 */
	MarshalInputStream in =
	    new MarshalInputStream(new ByteArrayInputStream(bytes),
				   ClassLoader.getSystemClassLoader(),
				   false, null, new HashSet());
	ActivatableInvocationHandler aihRead =
	    (ActivatableInvocationHandler) in.readObject();
	System.err.println("Test 1 passed: handler read successfully");

	/*
	 * Set constraints on underlying proxy to make
	 * them inconsistent with handler's constraints, then
	 * serialize handler.
	 */
	uproxy.setConstraints(new Constraints());
	bout.reset();
	out = new MarshalOutputStream(bout, new HashSet());
	out.writeObject(aih);
	out.flush();
	bytes = bout.toByteArray();

	/*
	 * Read in handler (should fail with InvalidObjectException);
	 */
	try {
	    in =
		new MarshalInputStream(new ByteArrayInputStream(bytes),
				       ClassLoader.getSystemClassLoader(),
				       false, null, new HashSet());
	    aihRead = (ActivatableInvocationHandler) in.readObject();
	    System.err.println("Test 2 failed: no InvalidObjectException");
	    throw new RuntimeException(
		"Test 2 failed: no InvalidObjectException");
	} catch (InvalidObjectException e) {
	    System.err.println(
		"Test 2 passed: caught InvalidObjectException");
	}
    }

    private static class FakeActivationID
    	extends ActivationID implements Externalizable
    {
	public FakeActivationID() {
	    super(null);
	}
	
	public void writeExternal(ObjectOutput out) throws IOException {
	}
	
	public void readExternal(ObjectInput in)
	    throws IOException, ClassNotFoundException
	{
	}
    }

    private static class UnderlyingProxy
    	implements RemoteMethodControl, Remote, Serializable
    {
	private MethodConstraints mc;

	public RemoteMethodControl setConstraints(MethodConstraints mc) {
	    this.mc = mc;
	    return null;
	}
	
	public MethodConstraints getConstraints() {
	    return mc;
	}
    }

    private static class Constraints
        implements MethodConstraints, Serializable
    {
	public InvocationConstraints getConstraints(Method method) {
	    return null;
	}

	public Iterator possibleConstraints() {
	    return null;
	}
    }
}
