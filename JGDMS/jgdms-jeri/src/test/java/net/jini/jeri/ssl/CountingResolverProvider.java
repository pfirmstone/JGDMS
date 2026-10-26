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
package net.jini.jeri.ssl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Passes every lookup to the built-in resolver and counts the reverse
 * lookups, so that a test can tell whether the server resolved the name of a
 * peer before the peer was authenticated.  The provider is installed for the
 * test JVM by a services file; it changes no result.
 */
public final class CountingResolverProvider extends InetAddressResolverProvider {

    /** The number of address-to-name lookups made so far. */
    static final AtomicInteger REVERSE_LOOKUPS = new AtomicInteger();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        final InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            public Stream<InetAddress> lookupByName(
                String host, LookupPolicy lookupPolicy)
                throws UnknownHostException
            {
                return builtin.lookupByName(host, lookupPolicy);
            }

            public String lookupByAddress(byte[] addr)
                throws UnknownHostException
            {
                REVERSE_LOOKUPS.incrementAndGet();
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "jgdms test counting resolver";
    }
}
