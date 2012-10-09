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
 * @bug 5024489
 * @summary Allow BasicJeriTrustVerifier subclasses to customize
 * invocation handler check
 * 
 *
 * @build BjtvSubclassTest
 * @run main/othervm/timeout=240 BjtvSubclassTest
 */

import java.lang.reflect.InvocationHandler;
import java.rmi.Remote;
import java.util.Collection;
import java.util.HashSet;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.BasicJeriTrustVerifier;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.TrustVerifier;

public class BjtvSubclassTest {

    static class Impl implements Remote {}

    static class TestContext implements TrustVerifier.Context {

	public boolean isTrustedObject(Object obj) {
	    return true;
	}

	public ClassLoader getClassLoader() {
	    return Thread.currentThread().getContextClassLoader();
	}

	public Collection getCallerContext() {
	    return new HashSet();
	}
	    
    }

    static class Subclass extends BasicJeriTrustVerifier {

	private final boolean hasTrustedProxyClass;
	private final boolean isTrustedInvocationHandler;

	Subclass(boolean hasTrustedProxyClass,
		 boolean isTrustedInvocationHandler)
	{
	    this.hasTrustedProxyClass = hasTrustedProxyClass;
	    this.isTrustedInvocationHandler = isTrustedInvocationHandler;
	}

	protected boolean isTrustedInvocationHandler(
				InvocationHandler handler,
				TrustVerifier.Context ctx)
	{
	    return isTrustedInvocationHandler;
	}

	protected boolean hasTrustedProxyClass(
				Object proxy,
				final TrustVerifier.Context ctx)
	{
	    return hasTrustedProxyClass;
	}
    }

    public static void main(String[] args) throws Exception {

	Exporter exporter = null;
	
	try {
	    System.err.println("Regression test for 5024489\n");

	    exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0),
					     new BasicILFactory());

	    Remote impl = new Impl();
	    Remote proxy = exporter.export(impl);

	    TrustVerifier.Context ctx = new TestContext();
	    
	    TrustVerifier tv = new Subclass(true, true);
	    if (tv.isTrustedObject(proxy, ctx) == true) {
		System.err.println("Test 1 passed");
	    } else {
		throw new RuntimeException("Test 1 FAILED");
	    }

	    tv = new Subclass(true, false);
	    if (tv.isTrustedObject(proxy, ctx) == false) {
		System.err.println("Test 2 passed");
	    } else {
		throw new RuntimeException("Test 2 FAILED");
	    }

	    tv = new Subclass(false, true);
	    if (tv.isTrustedObject(proxy, ctx) == false) {
		System.err.println("Test 3 passed");
	    } else {
		throw new RuntimeException("Test 3 FAILED");
	    }

	    tv = new Subclass(false, false);
	    if (tv.isTrustedObject(proxy, ctx) == false) {
		System.err.println("Test 4 passed");
	    } else {
		throw new RuntimeException("Test 4 FAILED");
	    }

	    System.err.println("ALL TESTS PASSSED");

	} finally {
	    if (exporter != null) {
		exporter.unexport(true);
	    }
	}
    }
}
