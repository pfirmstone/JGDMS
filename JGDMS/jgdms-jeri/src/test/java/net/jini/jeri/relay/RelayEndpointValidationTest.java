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

package net.jini.jeri.relay;

import java.io.InvalidObjectException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Assert;
import org.junit.Test;

/**
 * Destination validation, through {@link RelayEndpoint#getInstance} and
 * through the {@code GetArg} constructor used when decoding.
 */
public class RelayEndpointValidationTest {

    private static final String[] BAD = {
        "",
        repeat('a', 129),
        "a/b",
        "a b",
        "a:b",
        "..\u0000",
        "caf\u00e9",
        "a\nb"
    };

    private static final String[] GOOD = {
        "a",
        repeat('Z', 128),
        "A-z.0_9"
    };

    private static String repeat(char c, int n) {
        char[] a = new char[n];
        Arrays.fill(a, c);
        return new String(a);
    }

    @Test
    public void factoryRejectsInvalidDestinations() {
        for (String s : BAD) {
            try {
                RelayEndpoint.getInstance(s);
                Assert.fail("accepted " + s);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void factoryRejectsNull() {
        RelayEndpoint.getInstance(null);
    }

    @Test
    public void factoryAcceptsValidDestinations() {
        for (String s : GOOD) {
            Assert.assertEquals(s, RelayEndpoint.getInstance(s).getDestination());
        }
    }

    @Test
    public void decodingRejectsInvalidDestinations() throws Exception {
        for (String s : BAD) {
            try {
                new RelayEndpoint(new FakeGetArg(s));
                Assert.fail("accepted " + s);
            } catch (InvalidObjectException expected) {
            }
        }
        try {
            new RelayEndpoint(new FakeGetArg(null));
            Assert.fail("accepted null");
        } catch (InvalidObjectException expected) {
        }
    }

    @Test
    public void decodingAcceptsValidDestinations() throws Exception {
        for (String s : GOOD) {
            Assert.assertEquals(RelayEndpoint.getInstance(s),
                new RelayEndpoint(new FakeGetArg(s)));
        }
    }

    /** Supplies one {@code destination} field to the GetArg constructor. */
    private static final class FakeGetArg extends GetArg {
        private final String destination;

        FakeGetArg(String destination) {
            this.destination = destination;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            return "destination".equals(name) ? destination : ABSENT;
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            return false;
        }

        @Override
        public Class[] serialClasses() {
            return new Class[]{RelayEndpoint.class};
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }
}
