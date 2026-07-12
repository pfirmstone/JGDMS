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
 * the constrainable, {@code @AtomicSerial} proxy shell that forwards to it.
 *
 * <p>This annotation is a pure <b>marker</b> plus a carrier for the delegate's
 * durable-state ({@link State}) declarations.  The api/protocol wiring lives on the
 * service side: the service's {@link JiniService @JiniService} names both its
 * public {@link JiniService#api() api()} and its internal
 * {@link JiniService#protocol() protocol()} once, and points at this class with
 * {@link JiniService#smartProxy()}.  Declaring api/protocol here too would just
 * duplicate them; the delegate simply <em>implements</em> the api and <em>accepts
 * the wire type in its constructor</em>.
 *
 * <p>A <b>smart proxy</b> carries client-side behaviour or state rather than
 * forwarding one-to-one to the server.  Because an annotation processor can only
 * emit new files (never add members to the developer's class), the smart logic is
 * supplied through this shared delegate: a plain class (not remote, not the
 * serialized proxy) that implements the public API and translates its calls into
 * the internal protocol interface.  The generated shell delegates every public-API
 * method to a delegate instance reconstructed from the deserialized {@code server}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // On the service implementation: api + protocol declared ONCE, pointing here.
 * @JiniService(proxy = ProxyType.SMART,
 *              api = { HelloService.class },
 *              protocol = { HelloProtocol.class },
 *              smartProxy = HelloSmartLogic.class)
 * public class HelloWorldServiceImpl extends AbstractJiniService
 *         implements HelloProtocol { ... }
 *
 * // The delegate: a bare @SmartProxy marker; implements the api, takes the wire type.
 * @SmartProxy
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
 * <h2>Multi-protocol delegates</h2>
 * When {@link JiniService#protocol()} names more than one interface, the exported
 * server stub is an aggregate {@code <Api>Backend} that the processor synthesizes
 * (or, for a hand-written backend, the type the service developer already wrote and
 * named). Unless the backend is hand-written, its name is an internal convention the
 * delegate author cannot know in advance -- it does not exist until this same
 * compilation generates it. In that case declare the delegate constructor's first
 * parameter as {@code java.rmi.Remote} rather than guessing the generated backend's
 * name, and cast internally to whichever protocol interface(s) the delegate actually
 * calls:
 * <pre>{@code
 * @SmartProxy
 * public final class MultiLogic implements Api {
 *     private final ProtocolA a;
 *     private final ProtocolB b;
 *
 *     public MultiLogic(java.rmi.Remote server) {
 *         this.a = (ProtocolA) server;
 *         this.b = (ProtocolB) server;   // the aggregate backend implements every protocol
 *     }
 *     // ...
 * }
 * }</pre>
 * A single-protocol delegate (the common case, shown above) has no such problem --
 * the protocol interface is the developer's own declaration, so the constructor may
 * name it directly.
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
 * <p><b>{@code @State} values are not validated by the shell.</b>  The generated
 * {@code (GetArg)} constructor reads each durable field and passes it straight to
 * the delegate constructor; the <em>delegate constructor is the validation
 * seam</em>.  A delegate that rejects a bad state value must throw from its
 * constructor (an {@code IllegalArgumentException} or, to signal a corrupt stream,
 * an {@code java.io.InvalidObjectException}); the atomic engine surfaces the throw
 * and the object is never published.
 *
 * @see JiniService
 * @since 3.1.1
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SmartProxy {

    /**
     * Declares a field of durable proxy state that the generated shell must
     * serialize (through validated atomic deserialization) and pass to the
     * delegate's constructor after {@code server}.
     *
     * <p>Repeat (via {@link States}), in constructor-parameter order, for each
     * durable field.  A delegate with no {@code @State} declarations yields a
     * {@code @Stateless} shell that serializes only {@code { server, proxyID }}.
     *
     * <p>The declared value is passed to the delegate constructor <em>unvalidated</em>
     * by the shell — the delegate constructor is the validation seam (throw to
     * reject a bad or hostile value).
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
