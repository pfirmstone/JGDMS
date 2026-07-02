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
 * Marks a JGDMS service's public API interface so the service-proxy annotation
 * processor can generate the mechanical service-proxy boilerplate for it: the
 * server-side backend (internal wire) interface, the single constrainable proxy
 * (with a fail-closed {@code create} factory), and, optionally, the service
 * wrapper.
 *
 * <p>The annotation is placed on the <em>public API interface</em> — the clean,
 * client-facing contract (for example {@code HelloService}) — <em>not</em> on
 * the service implementation.  The processor never edits the annotated type; it
 * only emits new source files, exactly like the sibling
 * {@code MarshalDelegateProcessor}.  In the common (thin, one-to-one forwarding)
 * case nothing else needs to be written by hand.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JiniService(
 *     protocol  = HelloService.class,   // internal wire interface (default: the annotated API)
 *     component = "net.example.hello",  // config component for the generated wrapper
 *     generate  = { Generate.BACKEND, Generate.PROXY, Generate.WRAPPER })
 * public interface HelloService extends Remote {
 *     String greet(String name) throws RemoteException;
 * }
 * }</pre>
 *
 * <h2>The three roles</h2>
 * The design keeps three interface roles distinct (see the design note
 * <cite>Service Proxy Annotation Processor</cite>):
 * <ul>
 *   <li>the <b>public API interface</b> — the type carrying this annotation,
 *       shipped in the {@code -api} jar and written by the developer;</li>
 *   <li>the <b>internal service (backend) interface</b> — the remote (wire)
 *       methods the exported stub implements and the proxy invokes on the
 *       {@code server}; named by {@link #protocol()}, defaulting to the public
 *       API itself;</li>
 *   <li>the <b>constrainable wrapping</b> — the sole {@code RemoteMethodControl}
 *       proxy, always generated.</li>
 * </ul>
 *
 * <p>Every generated proxy is constrainable: there is no non-constrainable
 * variant.  A JGDMS service stub exported through JERI always implements
 * {@code RemoteMethodControl}, so the generated {@code create} factory fails
 * closed — it throws rather than degrading to a plain proxy when the
 * {@code server} reference is not a {@code RemoteMethodControl}.
 *
 * @see SmartProxy
 * @since 3.1.1
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface JiniService {

    /**
     * The internal service (backend / wire) interface the exported server stub
     * implements and the generated proxy invokes on its {@code server}
     * reference.
     *
     * <p>Defaults to {@link Void Void.class}, which the processor interprets as
     * "same as the annotated public API interface" — the thin, one-to-one
     * forwarding case, where the public API and the wire interface coincide.
     * Supply a distinct interface when the proxy translates public-API calls
     * into a coarser or finer internal protocol (a smart proxy).
     *
     * @return the internal wire interface, or {@code Void.class} to default to
     *         the annotated API interface
     */
    Class<?> protocol() default Void.class;

    /**
     * The configuration component name passed to the generated service wrapper's
     * {@code AbstractJiniService} superclass constructors (the
     * {@code COMPONENT} argument).  Ignored when {@link Generate#WRAPPER} is not
     * requested.
     *
     * @return the configuration component name; empty when no wrapper is
     *         generated
     */
    String component() default "";

    /**
     * Which artifacts the processor should generate for this service.
     *
     * <p>Defaults to all three ({@link Generate#BACKEND}, {@link Generate#PROXY},
     * {@link Generate#WRAPPER}).  Narrow the set when a hand-written artifact
     * already exists — for example omit {@link Generate#BACKEND} to keep a
     * hand-written backend interface while still generating the proxy.
     *
     * @return the artifacts to generate
     */
    Generate[] generate() default { Generate.BACKEND, Generate.PROXY, Generate.WRAPPER };

    /**
     * The artifacts the processor can generate for a {@link JiniService}.
     */
    enum Generate {

        /**
         * The server-side backend (internal wire) interface,
         * {@code <Api>Backend}, aggregating the public API with the fixed set of
         * JGDMS infrastructure interfaces (bootstrap accessors,
         * {@code Administrable}, {@code JoinAdmin}, {@code DestroyAdmin}).
         */
        BACKEND,

        /**
         * The single constrainable proxy, {@code Constrainable<Api>Proxy}, with
         * its fail-closed {@code create} factory.
         */
        PROXY,

        /**
         * The service wrapper, {@code <Api>ServiceImpl}, extending
         * {@code AbstractJiniService} and implementing the backend interface.
         */
        WRAPPER
    }
}
