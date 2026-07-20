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

import au.net.zeus.jgdms.der.object.RawWireFormRetaining;
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
 *
 * <p>Also implements {@link RawWireFormRetaining} exactly as the real JERI handlers do -- the
 * {@code jgdms-der} test module cannot depend on {@code jgdms-jeri} (jeri depends on der, not the
 * reverse), so this fixture stands in for a JERI handler to exercise the raw-wire-form retention
 * that a re-forward of an interface-narrowed proxy relies on. {@code rawForm} is {@code transient}
 * and absent from {@link #serialForm()}, so a retaining instance serializes byte-identically to an
 * otherwise-equal non-retaining one. It is captured at deserialization time from the framework's
 * {@link AtomicSerial.GetArg#getInjected(String) GetArg injection channel} (under
 * {@link RawWireFormRetaining#RAW_FORM_KEY}) -- there is no copy constructor and no {@code
 * withRawForm}, exactly as the real JERI handlers.
 */
@AtomicSerial
public final class GreeterHandler implements InvocationHandler, RawWireFormRetaining {

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
    /** Retained original [8] wire bytes, or null; transient and absent from serialForm. */
    private final transient byte[] rawForm;

    public GreeterHandler(String greeting) {
        this(greeting, null);
    }

    private GreeterHandler(String greeting, byte[] rawForm) {
        this.greeting = Objects.requireNonNull(greeting, "greeting");
        this.rawForm = (rawForm == null ? null : rawForm.clone());
    }

    public GreeterHandler(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this((String) check(arg).get("greeting", null),
             injectedRawForm(arg));
    }

    /** Captures the decode-injected [8] raw form (DC-1: disjoint from the wire field store). */
    private static byte[] injectedRawForm(AtomicSerial.GetArg arg) {
        Object injected = arg.getInjected(RawWireFormRetaining.RAW_FORM_KEY);
        return (injected instanceof byte[]) ? ((byte[]) injected).clone() : null;
    }

    @Override
    public byte[] rawForm() {
        return rawForm == null ? null : rawForm.clone();
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
