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

package au.net.zeus.jgdms.der.object.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Test fixture: an {@code @AtomicSerial} {@link InvocationHandler}, the wire-bearing half of a
 * dynamic {@code java.lang.reflect.Proxy}. This mirrors a real JERI proxy whose handler (e.g.
 * {@code BasicInvocationHandler}) is {@code @AtomicSerial}: the proxy class itself carries no
 * serializable state, so the handler is what travels on the DER wire (interface names + handler).
 */
@AtomicSerial
public final class GreeterHandler implements InvocationHandler {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("greeting", String.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, GreeterHandler o) throws IOException {
        arg.put("greeting", o.greeting);
        arg.writeArgs();
    }

    private final String greeting;

    public GreeterHandler(String greeting) {
        this.greeting = Objects.requireNonNull(greeting, "greeting");
    }

    public GreeterHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this((String) check(arg).get("greeting", null));
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("greeting", null) == null) {
            throw new InvalidObjectException("GreeterHandler: greeting must not be null");
        }
        return arg;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "greet"    -> greeting + ": " + (args != null && args.length > 0 ? args[0] : null);
            case "equals"   -> proxy == (args != null ? args[0] : null);
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "GreeterProxy[" + greeting + "]";
            default         -> null;
        };
    }

    public String getGreeting() { return greeting; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GreeterHandler that)) return false;
        return greeting.equals(that.greeting);
    }

    @Override
    public int hashCode() { return greeting.hashCode(); }

    @Override
    public String toString() { return "GreeterHandler{greeting='" + greeting + "'}"; }
}
