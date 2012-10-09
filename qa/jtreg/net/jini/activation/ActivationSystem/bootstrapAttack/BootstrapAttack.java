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
 * @bug 4398973
 * 
 * @summary test that code downloading is disabled for registry
 * 
 * 
 * @library ../../../../../testlibrary
 * @build TestLibrary RMID
 * @build BootstrapAttack
 * @build Foo
 * @run shell classpath.sh main/othervm/policy=security.policy/timeout=240 BootstrapAttack
 */
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import java.rmi.activation.ActivationSystem;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;

public class BootstrapAttack {
    /** Method hash for Registry.lookup */
    private static long LOOKUP = -7538657168040752697L;

    /**
     * Check that the ServerException contains an UnmarshalException
     * that contains a ClassNotFoundException with detail "Foo".
     */
    private static void check(ServerException e) throws ServerException {
	if (e.detail instanceof UnmarshalException) {
	    Throwable t = ((UnmarshalException) e.detail).detail;
	    if (t instanceof ClassNotFoundException &&
		"Foo".equals(t.getMessage()))
	    {
		return;
	    }
	}
	throw e;
    }

    public static void main(String[] args) throws Exception {
	if (System.getSecurityManager() == null) {
	    System.setSecurityManager(new SecurityManager());
	}
	URL remoteCodebase =
	    TestLibrary.installClassInCodebase("Foo", "remote_codebase");
	ClassLoader remoteLoader =
	    URLClassLoader.newInstance(new URL[]{remoteCodebase});
	Object foo = remoteLoader.loadClass("Foo").newInstance();
	RMID.removeLog();
	RMID rmid = RMID.createRMID();
	rmid.start();
	try {
	    RemoteObject stub = (RemoteObject)
		LocateRegistry.getRegistry(ActivationSystem.SYSTEM_PORT);
	    Method method =
		Registry.class.getMethod("lookup", new Class[]{String.class});
	    RemoteRef ref = stub.getRef();
	    try {
		// call lookup() on the registry ref with a Foo
		ref.invoke(stub, method, new Object[]{foo}, LOOKUP);
		throw new RuntimeException("lookup did not fail");
	    } catch (ServerException e) {
		check(e);
	    }
	} finally {
	    ActivationLibrary.rmidCleanup(rmid);
	}
    }
}
