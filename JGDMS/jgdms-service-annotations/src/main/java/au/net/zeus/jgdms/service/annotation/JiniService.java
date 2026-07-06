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
 * Marks a JGDMS service's <em>implementation</em> class so the service-proxy
 * annotation processor can generate the mechanical service-proxy boilerplate for
 * it: the server-side backend (internal wire) interface, the single constrainable
 * proxy class (with a fail-closed {@code create} factory) for a
 * {@link ProxyType#SMART} service, and, optionally, the service wrapper.
 *
 * <p>The annotation belongs on the concrete service <em>implementation</em> class
 * — a concrete {@code au.net.zeus.jgdms.service.support.AbstractJiniService}
 * subclass (for example {@code HelloWorldServiceImpl}) — <em>not</em> on the
 * service API interface.  The attributes here ({@link #proxy()},
 * {@link #codebase()}, {@link #component()}, {@link #protocol()}) are
 * implementation and deployment concerns — the wire protocol, whether behaviour
 * downloads, the config component — so they are the implementor's choice, per
 * deployment.  The API interface stays a pure {@link java.rmi.Remote} contract
 * carrying nothing: two implementations of the same interface may legitimately
 * choose {@code DYNAMIC} vs {@code SMART}, different codebases, or different config
 * components.  Placing this annotation on an interface is a compile error.
 *
 * <p>The service (remote) interface(s) the proxy is generated for — the ones
 * {@code getServiceInterfaces()} returns — are named by {@link #api()}.  When
 * {@link #api()} is left empty they are <em>inferred</em> from the interfaces the
 * annotated class implements, minus the JGDMS infrastructure interfaces
 * ({@code Administrable}, {@code JoinAdmin}, {@code DestroyAdmin}, the bootstrap
 * accessors {@code ServiceProxyAccessor} / {@code ServiceIDAccessor} /
 * {@code ServiceAttributesAccessor} / {@code CodebaseAccessor}, and
 * {@code RemoteMethodControl}).
 *
 * <p>The processor never edits the annotated class; it only emits new source
 * files, exactly like the sibling {@code MarshalDelegateProcessor}.  In the common
 * (thin, one-to-one forwarding) case nothing else needs to be written by hand.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JiniService(
 *     api       = HelloService.class,   // the service (remote) API interface(s)
 *     proxy     = ProxyType.DYNAMIC,    // DYNAMIC (default) | SMART
 *     codebase  = false,                // ship a downloadable -dl jar?  (default false)
 *     protocol  = HelloService.class,   // internal wire interface (default: the api)
 *     component = "net.example.hello")  // config component for the generated wrapper
 * public class HelloWorldServiceImpl extends AbstractJiniService
 *         implements HelloService {
 *     ...
 * }
 * }</pre>
 *
 * <h2>The three roles</h2>
 * The design keeps three interface roles distinct (see the design note
 * <cite>Service Proxy Annotation Processor</cite>):
 * <ul>
 *   <li>the <b>public API interface</b> — the clean, client-facing contract named
 *       by {@link #api()}, shipped in the {@code -api} jar; a pure {@code Remote}
 *       type carrying no annotation;</li>
 *   <li>the <b>internal service (backend) interface</b> — the remote (wire)
 *       methods the exported stub implements and the proxy invokes on the
 *       {@code server}; named by {@link #protocol()}, defaulting to the
 *       {@link #api()};</li>
 *   <li>the <b>constrainable wrapping</b> — the sole {@code RemoteMethodControl}
 *       proxy <em>class</em>, generated only for a {@link ProxyType#SMART}
 *       service.</li>
 * </ul>
 *
 * <p>The constrainable proxy <em>class</em> is generated for a
 * {@link ProxyType#SMART} service only (JGDMS-STD-009 §6, shape 3): a smart proxy
 * downloads its behaviour, so a constrainable {@code @AtomicSerial} shell must
 * wrap it.  A {@link ProxyType#DYNAMIC} service (the default) is exported as a
 * runtime {@link java.lang.reflect.Proxy} the client already holds the interface
 * for — constrainable by JERI construction — and generates <em>no</em> proxy
 * class (§6, shapes 1 &amp; 2).
 *
 * <p>When a proxy class <em>is</em> generated it is the sole, constrainable form:
 * there is no non-constrainable variant.  A JGDMS service stub exported through
 * JERI always implements {@code RemoteMethodControl}, so the generated
 * {@code create} factory fails closed — it throws rather than degrading to a
 * plain proxy when the {@code server} reference is not a
 * {@code RemoteMethodControl}.
 *
 * <p>The generated proxy shape is derived from {@link #proxy()} and
 * {@link #codebase()} (see JGDMS-STD-009 §3.1, §6); it is not enumerated by hand.
 *
 * <p>This annotation is retained at {@link RetentionPolicy#RUNTIME}: besides being
 * consumed by the annotation processor at compile time, {@code AbstractJiniService}
 * reflects it at runtime to resolve {@code getServiceInterfaces()}.
 *
 * @see ProxyType
 * @see SmartProxy
 * @since 3.1.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JiniService {

    /**
     * The service (remote) API interface(s) the generated proxy is produced for,
     * and that {@code AbstractJiniService.getServiceInterfaces()} returns.
     *
     * <p>Defaults to an empty array, which both the annotation processor and the
     * runtime interpret as "infer from the implemented interfaces": the interfaces
     * the annotated class implements, minus the JGDMS infrastructure interfaces
     * ({@code net.jini.admin.Administrable}, {@code net.jini.admin.JoinAdmin},
     * {@code org.apache.river.admin.DestroyAdmin}, the bootstrap accessors
     * {@code net.jini.lookup.ServiceProxyAccessor} /
     * {@code net.jini.lookup.ServiceIDAccessor} /
     * {@code net.jini.lookup.ServiceAttributesAccessor} /
     * {@code net.jini.export.CodebaseAccessor}, and
     * {@code net.jini.core.constraint.RemoteMethodControl}).  Naming the
     * interface(s) explicitly is clearer and is required when the inference would
     * be ambiguous.
     *
     * @return the service API interface(s); empty (the default) to infer them
     */
    Class<?>[] api() default {};

    /**
     * The internal service (backend / wire) interface(s) the exported server stub
     * implements and the generated proxy invokes on its {@code server}
     * reference.
     *
     * <p>An <em>array</em>: list several wire interfaces directly and the
     * framework combines them — the processor generates the single aggregate
     * {@code <Api>Backend} {@link java.rmi.Remote} interface that extends every
     * element (plus the fixed JGDMS infrastructure accessors), so the developer
     * never hand-writes an aggregate super-interface just to bundle them.
     *
     * <p>Defaults to the empty array {@code {}}, which the processor interprets as
     * "same as the {@link #api()} interface(s)" — the thin, one-to-one forwarding
     * case, where the public API and the wire interface coincide.  Supply distinct
     * interface(s) when the proxy translates public-API calls into a coarser or
     * finer internal protocol (a smart proxy).  Naming exactly the {@link #api()}
     * set is also treated as non-translating.
     *
     * @return the internal wire interface(s); empty (the default) to default to
     *         the {@link #api()} interface(s)
     */
    Class<?>[] protocol() default {};

    /**
     * The configuration component name passed to the generated service wrapper's
     * {@code AbstractJiniService} superclass constructors (the
     * {@code COMPONENT} argument).
     *
     * @return the configuration component name; empty when unset
     */
    String component() default "";

    /**
     * How the service's behaviour reaches the caller: as a dynamic proxy invoked
     * remotely ({@link ProxyType#DYNAMIC}), or as a downloaded smart proxy that
     * runs locally in the receiver's JVM ({@link ProxyType#SMART}).
     *
     * <p>Together with {@link #codebase()} this selects the generated proxy shape
     * (see JGDMS-STD-009 §6).  Defaults to {@link ProxyType#DYNAMIC} — the common
     * case, a service whose public API interface the client already holds.
     *
     * @return the proxy type; {@link ProxyType#DYNAMIC} by default
     */
    ProxyType proxy() default ProxyType.DYNAMIC;

    /**
     * Whether the service ships a downloadable {@code -dl} jar carrying its
     * declared interface(s) so a receiver that lacks the class can resolve it.
     *
     * <p>An independent axis from {@link #proxy()} (a service may ship a codebase
     * regardless of proxy type — see JGDMS-STD-009 §3.1, §6 shape 2).  Defaults to
     * {@code false}: no downloadable jar, the receiver already holds the classes.
     *
     * @return {@code true} to ship a downloadable {@code -dl} jar; {@code false}
     *         (the default) otherwise
     */
    boolean codebase() default false;

    /**
     * The developer-written {@link SmartProxy @SmartProxy} client-side logic class
     * whose behaviour the generated smart-proxy shell forwards to — the delegate the
     * shell delegates each api call to.
     *
     * <p>Only meaningful when {@link #proxy()} is {@link ProxyType#SMART}: the
     * generated {@code Constrainable<Api>Proxy} shell forwards every {@link #api()}
     * method to an instance of this class reconstructed from the deserialized
     * {@code server}, instead of casting the {@code server} straight to the wire
     * type.  This is what enables genuinely disjoint api/protocol method-name
     * translation (client {@code currentCelsius(region)} over wire
     * {@code rawCelsius(...)}) that direct forwarding cannot express.
     *
     * <p>The referenced class must (a) be annotated {@code @SmartProxy}, (b)
     * implement every {@link #api()} interface, and (c) declare a constructor
     * {@code (<serverType> [, @State field types…])} the shell can call — the
     * {@code serverType} being the single {@link #protocol()} interface, or the
     * generated aggregate {@code <Api>Backend} when {@link #protocol()} names more
     * than one.  These are compile-time (fail-closed) checks.
     *
     * <p>Naming a smart-proxy class on a {@link ProxyType#DYNAMIC} service is a
     * compile error: a dynamic proxy carries no downloaded behaviour.  Defaults to
     * {@link Void}, meaning "none" — the shell (if any) forwards directly.
     *
     * @return the {@code @SmartProxy}-annotated client-side logic class, or
     *         {@link Void} for none
     */
    Class<?> smartProxy() default Void.class;
}
