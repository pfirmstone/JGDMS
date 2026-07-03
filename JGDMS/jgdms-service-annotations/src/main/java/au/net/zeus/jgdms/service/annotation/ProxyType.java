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

/**
 * Selects how a {@link JiniService}'s behaviour reaches the caller: as a dynamic
 * proxy invoked remotely ({@link #DYNAMIC}), or as a downloaded smart proxy that
 * runs locally ({@link #SMART}).
 *
 * <p>This enum is the {@code @JiniService} axis. A {@code @RemoteFunction} has its
 * own {@code { DYNAMIC, FUNCTION }} enum: a remote function's non-dynamic form is a
 * standalone mobile-code function (a filter) with no {@code server} to forward to —
 * not a smart proxy — so it is a {@code FUNCTION} rather than {@code SMART}.
 *
 * <p>Together with the {@code codebase} flag, {@code ProxyType} determines the
 * generated proxy shape (see the design note <cite>Service Proxy Annotation
 * Processor</cite> and JGDMS-STD-009):
 *
 * <ul>
 *   <li>{@link #DYNAMIC} with no codebase — a {@code java.lang.reflect.Proxy}
 *       whose interfaces the receiver already holds; no downloadable jar, no
 *       generated proxy class.</li>
 *   <li>{@link #DYNAMIC} with a codebase — the interface classes are downloaded
 *       (an interfaces-only {@code -dl} jar); still no generated proxy class.</li>
 *   <li>{@link #SMART} — a smart proxy whose behaviour is downloaded; the sole
 *       shape that generates a constrainable proxy <em>class</em> wrapping the
 *       developer's {@link SmartProxy} delegate.</li>
 * </ul>
 *
 * @see JiniService
 * @see SmartProxy
 * @since 3.1.1
 */
public enum ProxyType {

    /**
     * A {@code java.lang.reflect.Proxy}: the behaviour stays with its author and
     * is invoked remotely.  Constrainable by JERI construction; no proxy class is
     * generated.  This is the default and the common case (a service whose public
     * API interface the client already holds).
     */
    DYNAMIC,

    /**
     * A smart proxy: behaviour (and any durable state) is downloaded and runs in
     * the receiver's JVM.  The only shape for which the processor generates a
     * constrainable, {@code @AtomicSerial} proxy class wrapping the developer's
     * {@link SmartProxy} delegate.  A smart proxy always resolves its behaviour
     * from a codebase — its own {@code -dl} jar ({@code codebase = true}) or a
     * shared proxy codebase ({@code codebase = false}).
     */
    SMART
}
