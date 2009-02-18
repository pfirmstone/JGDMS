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
 * @summary Test getRemoteInterfaces
 * @build GetRemoteInterfaces
 * @run main/othervm GetRemoteInterfaces
 */
import com.sun.jini.jeri.internal.runtime.Util;
import java.rmi.Remote;


public class GetRemoteInterfaces {

    private static Class[] resultInterfaces = new Class[] {
	T.class, V.class, Foo.class, Bar.class, Baz.class, A.class, B.class};

    public static void main(String[] args) throws Exception {
	Class[] interfaces = Util.getRemoteInterfaces(Impl3.class);
	if (interfaces.length != resultInterfaces.length) {
	    throw new RuntimeException("unexpected array length");
	}
	for (int i=0; i < interfaces.length; i++) {
	    if (interfaces[i] != resultInterfaces[i]) {
		throw new RuntimeException("unexpected interface");
	    }
	    System.err.println("interfaces[" + i + "] = " + interfaces[i]);
	}
	System.err.println("TEST PASSED");
    }
}

interface  T extends Remote {
    void t() throws Exception;
}

interface V extends Remote {
    void v() throws Exception;
}

interface Baz extends Remote {
    void baz() throws Exception;
}

interface Bar extends Remote {
    void bar() throws Exception;
}

interface Foo extends Remote {
    void foo() throws Exception;
}

interface  X extends Remote {
    void x() throws Exception;
}

interface Y extends Remote {
    void y() throws Exception;
}

interface A extends X, Y {
    void a() throws Exception;
}

interface B extends Remote {
    void b() throws Exception;
}

interface C {
    void c();
}

abstract class Impl1 implements T, V {}
abstract class Impl2 extends Impl1 implements Foo, Bar, Baz {}
abstract class Impl3 extends Impl2  implements A, B, C, T, V {}
