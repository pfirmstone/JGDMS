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
 * @summary Tests the trust verifier.
 * 
 * @library ../../../../../unittestlib
 * @build UnitTestUtilities BasicTest Test
 * @build TestUtilities
 * @run main/othervm/policy=policy TestVerifier
 */

import java.rmi.RemoteException;
import java.security.*;
import java.util.*;
import javax.security.auth.*;
import javax.security.auth.x500.*;
import net.jini.core.constraint.*;
import net.jini.jeri.*;
import net.jini.jeri.connection.*;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.jeri.ssl.HttpsEndpoint;
import net.jini.jeri.ssl.SslEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.ssl.SslTrustVerifier;
import net.jini.security.*;

public class TestVerifier extends TestUtilities implements Test {

    static class Context implements TrustVerifier.Context {
	private static final Context context = new Context();
	private static final TrustVerifier verifier = new SslTrustVerifier();

	public boolean isTrustedObject(Object obj)
	    throws RemoteException
	{
	    return verifier.isTrustedObject(obj, this);
	}

	public Collection getCallerContext() {
	    return Collections.EMPTY_SET;
	}

	public ClassLoader getClassLoader() {
	    return null;
	}

	static boolean verify(Object obj) throws RemoteException {
	    return verifier.isTrustedObject(obj, context);
	}
    }

    static Test[] tests = {
	new TestVerifier(ClientAuthentication.YES, false),
	new TestVerifier(ConfidentialityStrength.STRONG, true),
	new TestVerifier(x500("CN=Tim"), true),
        new TestVerifier(
	    new Principal() { public String getName() { return "Tim"; } },
	    false),
	new TestVerifier(SslEndpoint.getInstance("localhost", 1025), true),
	new TestVerifier(HttpsEndpoint.getInstance("localhost", 1026), true),
	new TestVerifier(
	    new Endpoint() {
		public OutboundRequestIterator newRequest(
		    InvocationConstraints constraints)
		{
		    return null;
		}
	    },
	    false),
	new TestVerifier(
	    SslServerEndpoint.getInstance(null, null, "localhost", 0), false)
     };

    private String name;
    private Object obj;
    private boolean ok;

    /** Run all Verifier tests */
    public static void main(String[] args) {
	test(tests);
    }

    TestVerifier(Object obj, boolean ok) {
	name = String.valueOf(obj);
	this.obj = obj;
	this.ok = ok;
    }

    public String name() {
	return name;
    }

    public Object run() throws RemoteException {
	return Boolean.valueOf(Context.verify(obj));
    }

    public void check(Object result) {
	if (!(result instanceof Boolean)
	    || ((Boolean) result).booleanValue() != ok)
	{
	    throw new FailedException("Expected: " + ok);
	}
    }
}
