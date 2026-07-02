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
package au.net.zeus.jgdms.service.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a developer-written <em>client-side logic delegate</em> — the smart
 * proxy's own behaviour — so the service-proxy annotation processor can generate
 * the constrainable, {@code @AtomicSerial} proxy shell that wraps it.
 *
 * <p>A <b>smart proxy</b> carries client-side behaviour or state rather than
 * forwarding one-to-one to the server.  Because an annotation processor can only
 * emit new files (never add members to the developer's class), the smart logic
 * is supplied through this shared delegate: a plain class (not remote, not the
 * serialized proxy) that implements the public API and translates its calls into
 * the internal {@link #protocol()} interface.  The generated shell delegates
 * every public-API method to a delegate instance reconstructed from the
 * deserialized {@code server}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @SmartProxy(api = HelloService.class, protocol = HelloProtocol.class)
 * public final class HelloSmartLogic implements HelloService {
 *     private final HelloProtocol server;         // internal wire interface
 *     private final Cache<String,String> cache;   // client-side state, not serialized
 *
 *     public HelloSmartLogic(HelloProtocol server) {
 *         this.server = server;
 *         this.cache  = new Cache<>();
 *     }
 *
 *     @Override public String greet(String name) throws RemoteException {
 *         String hit = cache.get(name);
 *         if (hit != null) return hit;                 // served client-side
 *         String r = server.greetRemote(name).text();  // translate API -> protocol
 *         cache.put(name, r);
 *         return r;
 *     }
 * }
 * }</pre>
 *
 * <h2>Serialized state versus behaviour</h2>
 * Behaviour is <em>not</em> serialized state.  Most smart proxies serialize only
 * {@code { server, proxyID }} (caches and endpoints are rebuilt lazily on the
 * client), so the generated shell stays {@code @Stateless}.  Only <em>durable</em>
 * proxy state chosen at export time needs a declared serial form; declare it with
 * {@link State}, and the processor generates the
 * {@code serialForm()}/{@code serialize()}/{@code (GetArg)} plumbing and routes
 * it through the existing marshal-delegate machinery.
 *
 * @see JiniService
 * @since 3.1.1
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SmartProxy {

    /**
     * The public API interface this delegate implements — the client-facing
     * contract the generated proxy shell exposes and forwards to the delegate.
     *
     * @return the public API interface
     */
    Class<?> api();

    /**
     * The internal service (backend / wire) interface the delegate's constructor
     * accepts — the typed {@code server} reference reconstructed from the
     * validated deserialized stub.
     *
     * <p>Defaults to {@link Void Void.class}, interpreted by the processor as
     * "same as {@link #api()}" (the delegate takes the public API interface as
     * its {@code server}).
     *
     * @return the internal wire interface, or {@code Void.class} to default to
     *         {@link #api()}
     */
    Class<?> protocol() default Void.class;

    /**
     * Declares a field of durable proxy state that the generated shell must
     * serialize (through validated atomic deserialization) and pass to the
     * delegate's constructor after {@code server}.
     *
     * <p>Repeat, in constructor-parameter order, for each durable field.  A
     * delegate with no {@code @State} declarations yields a {@code @Stateless}
     * shell that serializes only {@code { server, proxyID }}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface State {

        /**
         * The serialized field name, matching the delegate constructor parameter
         * it feeds.
         *
         * @return the field name
         */
        String name();

        /**
         * The declared type of the serialized field.
         *
         * @return the field type
         */
        Class<?> type();
    }

    /**
     * Container allowing several {@link State} declarations on one delegate.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface States {

        /**
         * The declared durable-state fields, in constructor-parameter order.
         *
         * @return the state declarations
         */
        State[] value();
    }
}
