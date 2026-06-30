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
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Test fixture: an {@code @AtomicSerial} class whose {@code greeter} field is declared as the
 * {@link Greeter} interface but holds a dynamic {@code java.lang.reflect.Proxy} at runtime --
 * the nested-proxy DER case (mirrors {@code AdminProxy.admin} declared as {@code OutriggerAdmin}).
 * The declared interface type maps to wireType {@code "@AtomicSerial"}; the runtime value is a
 * {@code Proxy}, exercising {@code ObjectCodec.encodeProxy}/{@code decodeProxy}.
 */
@AtomicSerial
public final class OuterWithProxy {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("tag",     String.class),
            // Declared type Greeter is an interface -> wireType "@AtomicSerial" (polymorphic slot).
            new AtomicSerial.SerialForm("greeter", Greeter.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, OuterWithProxy o) throws IOException {
        arg.put("tag",     o.tag);
        arg.put("greeter", o.greeter);
        arg.writeArgs();
    }

    private final String  tag;
    private final Greeter greeter; // a dynamic Proxy at runtime; may be null

    public OuterWithProxy(String tag, Greeter greeter) {
        this.tag     = Objects.requireNonNull(tag, "tag");
        this.greeter = greeter;
    }

    public OuterWithProxy(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        check(arg);
        this.tag     = (String)  arg.get("tag",     null);
        this.greeter = (Greeter) arg.get("greeter", null);
    }

    public static AtomicSerial.GetArg check(AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException {
        if (arg.get("tag", null) == null) {
            throw new InvalidObjectException("OuterWithProxy: tag must not be null");
        }
        return arg;
    }

    public String  getTag()     { return tag; }
    public Greeter getGreeter() { return greeter; }

    /** The {@code @AtomicSerial} handler behind the proxy, or {@code null} if the field is null. */
    private static Object handlerOf(Greeter g) {
        return (g != null && Proxy.isProxyClass(g.getClass()))
                ? Proxy.getInvocationHandler(g) : g;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OuterWithProxy that)) return false;
        return Objects.equals(tag, that.tag)
                && Objects.equals(handlerOf(greeter), handlerOf(that.greeter));
    }

    @Override
    public int hashCode() { return Objects.hash(tag, handlerOf(greeter)); }

    @Override
    public String toString() { return "OuterWithProxy{tag='" + tag + "', greeter=" + greeter + '}'; }
}
