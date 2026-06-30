/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.der.object;

import au.net.zeus.jgdms.der.object.fixtures.Greeter;
import au.net.zeus.jgdms.der.object.fixtures.GreeterHandler;
import au.net.zeus.jgdms.der.object.fixtures.OuterWithProxy;
import au.net.zeus.jgdms.der.schema.SchemaChain;
import au.net.zeus.jgdms.der.schema.SchemaGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gap 2: a nested {@code java.lang.reflect.Proxy} held in an {@code @AtomicSerial} interface
 * field (e.g. {@code AdminProxy.admin} declared as the {@code OutriggerAdmin} remote interface).
 * The declared interface type maps to wireType {@code "@AtomicSerial"}; the runtime value is a
 * dynamic {@code Proxy} over an {@code @AtomicSerial} InvocationHandler. Before the fix,
 * {@code ObjectCodec.encodeNested} rejected such a value ("runtime type $ProxyN has no
 * @AtomicSerial class in its hierarchy"); now it travels as a {@code [8]} CTX_PROXY field record
 * (interface names + handler), the field-level counterpart of the object-stream {@code [8]} path.
 */
class NestedProxyTest {

    private static Greeter newGreeterProxy(String greeting) {
        return (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] { Greeter.class },
                new GreeterHandler(greeting));
    }

    @Test
    void roundTripNestedProxyField() throws Exception {
        Greeter proxy = newGreeterProxy("hello");
        OuterWithProxy outer = new OuterWithProxy("space-admin", proxy);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithProxy.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(outer, chain);
        OuterWithProxy decoded = ObjectCodec.decodeHierarchy(OuterWithProxy.class, chain, encoded);

        assertEquals("space-admin", decoded.getTag(), "tag must round-trip");
        Greeter g = decoded.getGreeter();
        assertNotNull(g, "greeter must not be null after round-trip");
        assertTrue(Proxy.isProxyClass(g.getClass()),
                "decoded greeter must be a reconstructed dynamic Proxy");
        assertTrue(g instanceof Greeter, "proxy must implement the declared interface");
        assertNotSame(proxy, g, "decoded proxy must be a new instance (value copy)");

        // The @AtomicSerial handler behind the proxy must round-trip by value.
        Object handler = Proxy.getInvocationHandler(g);
        assertTrue(handler instanceof GreeterHandler, "handler must be the @AtomicSerial GreeterHandler");
        assertEquals(new GreeterHandler("hello"), handler, "handler state must round-trip");

        // The reconstructed proxy must actually dispatch through the decoded handler.
        assertEquals("hello: world", g.greet("world"), "proxy invocation must work post-decode");

        assertEquals(outer, decoded, "OuterWithProxy must round-trip (tag + handler value)");
    }

    @Test
    void roundTripNullProxyField() throws Exception {
        OuterWithProxy outer = new OuterWithProxy("no-admin", null);

        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithProxy.class);
        byte[] encoded = ObjectCodec.encodeHierarchy(outer, chain);
        OuterWithProxy decoded = ObjectCodec.decodeHierarchy(OuterWithProxy.class, chain, encoded);

        assertEquals("no-admin", decoded.getTag());
        assertNull(decoded.getGreeter(), "null proxy field must round-trip as null");
    }

    @Test
    void multipleEncodesAreByteIdentical() throws Exception {
        OuterWithProxy outer = new OuterWithProxy("admin", newGreeterProxy("hi"));
        SchemaChain.Result chain = SchemaGenerator.generateChain(OuterWithProxy.class);
        byte[] a = ObjectCodec.encodeHierarchy(outer, chain);
        byte[] b = ObjectCodec.encodeHierarchy(outer, chain);
        assertArrayEquals(a, b, "nested-proxy encoding must be deterministic (canonical DER)");
    }
}
