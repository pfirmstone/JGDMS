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

package au.net.zeus.jgdms.der;

/**
 * A {@link net.jini.core.constraint.RemoteMethodControl}-style control for the per-deployment
 * denial-of-service {@link DerInputLimits} a DER/JERI proxy applies when reading invocation
 * <em>return values</em>. A DER proxy implements this interface (alongside {@code RemoteMethodControl}),
 * so a client sets its OWN cap on a received proxy without depending on the invocation-handler type:
 *
 * <pre>
 *   Greeter g = (Greeter) ((DerInputLimitControl) proxy)
 *           .setInputLimits(DerInputLimits.maxBytes(8 * 1024 * 1024));
 *   g.greet(); // return value now read under the client's 8 MiB cap
 * </pre>
 *
 * <p>Like {@code RemoteMethodControl.setConstraints}, {@link #setInputLimits} returns a NEW proxy and
 * does not mutate the original. The chosen limits are the client's own: they are never serialized and
 * never sent to the server (the server cannot dictate the client's return-value cap). A proxy returned
 * by an exporter applies {@link DerInputLimits#DEFAULT} (the JVM-wide, system-property configurable
 * default) until a client overrides it here.
 */
public interface DerInputLimitControl {

    /**
     * Returns a new proxy, equivalent to this one (same remote object, same constraints), that reads
     * invocation return values under {@code limits}. Does not mutate this proxy.
     *
     * @param limits the client's DoS limits for the return-value stream (must not be {@code null})
     * @return a new proxy applying {@code limits}
     * @throws NullPointerException if {@code limits} is {@code null}
     */
    DerInputLimitControl setInputLimits(DerInputLimits limits);

    /**
     * Returns the DoS limits this proxy applies when reading invocation return values.
     *
     * @return the current return-value limits (never {@code null})
     */
    DerInputLimits getInputLimits();
}
