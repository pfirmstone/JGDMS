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

package org.apache.river.api.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that the processor-generated {@code org.apache.river.api.io.GeneratedMarshalDelegate}
 * is registered and dispatches the platform's package-private {@code @AtomicSerial}
 * serializer helpers, so they marshal without the {@code setAccessible} fallback.
 *
 * <p>Before the platform package gained a {@code MarshalDelegate}, strict mode failed with
 * {@code InvalidClassException: org.apache.river.api.io.MapSerializer$Ent ... no MarshalDelegate
 * is registered for package org.apache.river.api.io} on the first {@code Map}/collection in any
 * graph. The resolution test below is mode-independent (delegate dispatch is delegate-first in
 * both modes); a companion strict-mode qa run (mercury/norm) covers the no-fallback path
 * end-to-end.
 */
public class GeneratedMarshalDelegateTest {

    private static Object roundTrip(Object o) throws Exception {
        return new AtomicMarshalledInstance(o).get(false);
    }

    /**
     * The generated delegate must resolve for, and serve, the package-private helpers that
     * are otherwise unreachable under strict mode. This is the direct guard for the gap the
     * strict-verification review flagged.
     */
    @Test
    public void generatedDelegateResolvesAndServesPackagePrivateHelpers() {
        Class<?>[] needDelegate = {
            MapSerializer.Ent.class,                            // the review's exact failure case
            ProxySerializer.class,                             // package-private bootstrap-proxy helper
            SetSerializer.class,
            ListSerializer.class
        };
        for (Class<?> c : needDelegate) {
            MarshalDelegate d = MarshalDelegates.delegateFor(c);
            Assert.assertNotNull("no MarshalDelegate resolved for " + c.getName(), d);
            Assert.assertTrue(d.getClass().getName() + " must serve " + c.getName(), d.serves(c));
        }
    }

    @Test
    public void mapRoundTripsViaDelegate() throws Exception {
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put("a", 1);
        m.put("b", 2);
        m.put("c", 3);
        Assert.assertEquals(m, roundTrip(m));   // exercises MapSerializer + MapSerializer.Ent
    }

    @Test
    public void listRoundTripsViaDelegate() throws Exception {
        List<String> l = Arrays.asList("x", "y", "z");
        Assert.assertEquals(l, roundTrip(l));   // exercises ListSerializer
    }

    @Test
    public void setRoundTripsViaDelegate() throws Exception {
        Set<String> s = new HashSet<String>(Arrays.asList("p", "q", "r"));
        Assert.assertEquals(s, roundTrip(s));   // exercises SetSerializer
    }
}
