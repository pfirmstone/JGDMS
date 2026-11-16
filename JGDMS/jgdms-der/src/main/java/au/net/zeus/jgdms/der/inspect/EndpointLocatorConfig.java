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

package au.net.zeus.jgdms.der.inspect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What {@link EndpointLocator} recognises and what it refuses. Immutable.
 *
 * <p>Every class is named by a plain string; nothing here is ever resolved to a class.
 *
 * <p><b>Required slots are named by declaring class and field, not by declared type.</b> A field
 * declared as an interface or abstract type (such as {@code net.jini.jeri.Endpoint}) has the
 * wire type {@code "@AtomicSerial"}, the same as any other nested object field: the declared type
 * does not travel. A slot that must hold an endpoint is therefore identified as
 * {@code (declaringClassName, fieldName)}; the default is
 * {@code net.jini.jeri.BasicObjectEndpoint#ep}.
 */
public final class EndpointLocatorConfig {

    /**
     * Field names of one recognised endpoint class. Any of the three may be {@code null} when the
     * class has no such field (for example a Unix domain socket endpoint has no host or port).
     *
     * @param className          the endpoint class name
     * @param hostField          the host field's wire name, or {@code null}
     * @param portField          the port field's wire name, or {@code null}
     * @param socketFactoryField the socket factory field's wire name, or {@code null}
     */
    public record EndpointClass(String className, String hostField, String portField,
                                String socketFactoryField) {

        /**
         * Creates a description.
         */
        public EndpointClass {
            Objects.requireNonNull(className, "className");
        }
    }

    private static final EndpointLocatorConfig DEFAULTS = builder()
            .endpoint(new EndpointClass("net.jini.jeri.tcp.TcpEndpoint", "host", "port", "sf"))
            .endpoint(new EndpointClass("net.jini.jeri.http.HttpEndpoint", "host", "port", "sf"))
            .endpoint(new EndpointClass("net.jini.jeri.ssl.SslEndpoint",
                    "serverHost", "port", "socketFactory"))
            .endpoint(new EndpointClass("net.jini.jeri.ssl.HttpsEndpoint",
                    "serverHost", "port", "socketFactory"))
            .endpoint(new EndpointClass("net.jini.jeri.kerberos.KerberosEndpoint",
                    "serverHost", "serverPort", "csf"))
            .endpoint(new EndpointClass("net.jini.jeri.uds.UdsEndpoint", null, null, null))
            .requiredEndpointSlot("net.jini.jeri.BasicObjectEndpoint", "ep")
            .build();

    private final Map<String, EndpointClass> endpoints;
    private final Set<String> requiredSlots;
    private final Predicate<EndpointFinding.SocketFactory> socketFactoryPolicy;

    private EndpointLocatorConfig(Builder b) {
        this.endpoints = Collections.unmodifiableMap(new LinkedHashMap<>(b.endpoints));
        this.requiredSlots = Collections.unmodifiableSet(new LinkedHashSet<>(b.requiredSlots));
        this.socketFactoryPolicy = b.socketFactoryPolicy;
    }

    /**
     * The default configuration: the six JERI endpoint classes ({@code TcpEndpoint},
     * {@code HttpEndpoint}, {@code SslEndpoint}, {@code HttpsEndpoint},
     * {@code KerberosEndpoint}, {@code UdsEndpoint}), the required slot
     * {@code net.jini.jeri.BasicObjectEndpoint#ep}, and a socket factory policy that refuses every
     * non-null factory.
     *
     * @return the default configuration
     */
    public static EndpointLocatorConfig defaults() {
        return DEFAULTS;
    }

    /**
     * Returns an empty builder: no endpoint classes, no required slots, every non-null socket
     * factory refused.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder initialised from this configuration.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.endpoints.putAll(endpoints);
        b.requiredSlots.addAll(requiredSlots);
        b.socketFactoryPolicy = socketFactoryPolicy;
        return b;
    }

    /**
     * The recognised endpoint classes, by class name.
     *
     * @return an unmodifiable map
     */
    public Map<String, EndpointClass> endpoints() {
        return endpoints;
    }

    /**
     * The required endpoint slots, each as {@code declaringClassName + "#" + fieldName}.
     *
     * @return an unmodifiable set
     */
    public Set<String> requiredEndpointSlots() {
        return requiredSlots;
    }

    /**
     * Decides whether a non-null socket factory is acceptable local code.
     *
     * @return the policy
     */
    public Predicate<EndpointFinding.SocketFactory> socketFactoryPolicy() {
        return socketFactoryPolicy;
    }

    boolean isRequiredSlot(String declaringClassName, String fieldName) {
        return requiredSlots.contains(declaringClassName + "#" + fieldName);
    }

    /**
     * Builds an {@link EndpointLocatorConfig}.
     */
    public static final class Builder {

        private final Map<String, EndpointClass> endpoints = new LinkedHashMap<>();
        private final Set<String> requiredSlots = new LinkedHashSet<>();
        private Predicate<EndpointFinding.SocketFactory> socketFactoryPolicy = f -> false;

        private Builder() {
        }

        /**
         * Adds or replaces a recognised endpoint class.
         *
         * @param endpoint the class and its field names
         * @return this builder
         */
        public Builder endpoint(EndpointClass endpoint) {
            Objects.requireNonNull(endpoint, "endpoint");
            endpoints.put(endpoint.className(), endpoint);
            return this;
        }

        /**
         * Removes every recognised endpoint class.
         *
         * @return this builder
         */
        public Builder clearEndpoints() {
            endpoints.clear();
            return this;
        }

        /**
         * Adds a slot that must hold a recognised endpoint record.
         *
         * @param declaringClassName the class whose schema declares the field
         * @param fieldName          the field's wire name
         * @return this builder
         */
        public Builder requiredEndpointSlot(String declaringClassName, String fieldName) {
            Objects.requireNonNull(declaringClassName, "declaringClassName");
            Objects.requireNonNull(fieldName, "fieldName");
            requiredSlots.add(declaringClassName + "#" + fieldName);
            return this;
        }

        /**
         * Removes every required slot.
         *
         * @return this builder
         */
        public Builder clearRequiredEndpointSlots() {
            requiredSlots.clear();
            return this;
        }

        /**
         * Sets the policy for non-null socket factories.
         *
         * @param policy returns true for a factory that is acceptable local code
         * @return this builder
         */
        public Builder socketFactoryPolicy(Predicate<EndpointFinding.SocketFactory> policy) {
            this.socketFactoryPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration
         */
        public EndpointLocatorConfig build() {
            return new EndpointLocatorConfig(this);
        }
    }
}
