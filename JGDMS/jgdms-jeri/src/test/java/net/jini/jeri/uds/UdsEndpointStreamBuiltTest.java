/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.jeri.uds;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.jeri.OutboundRequestIterator;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;

/**
 * An {@link UdsEndpoint} built straight from a {@code GetArg}, without {@code getInstance} or
 * {@code readResolve}, is still usable: it takes the connection state of its canonical instance on first
 * use. (Streams now resolve to the canonical instance, so this is the guard for any path that does not.)
 */
public class UdsEndpointStreamBuiltTest {

    private static final class MapGetArg extends GetArg {
        private final Map<String, Object> values;

        MapGetArg(Map<String, Object> values) {
            this.values = values;
        }

        @Override protected Object lookup(Class<?> c, String name) {
            return values.containsKey(name) ? values.get(name) : ABSENT;
        }

        @Override protected boolean isDefaulted(Class<?> c, String name) {
            return !values.containsKey(name);
        }

        @Override public Class[] serialClasses() {
            return new Class[] {UdsEndpoint.class};
        }

        @Override public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    @Test
    public void aGetArgBuiltEndpointIsUsable() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("path", "/tmp/jgdms-no-such.sock");
        UdsEndpoint e = new UdsEndpoint(new MapGetArg(m));
        try {
            OutboundRequestIterator it = e.newRequest(InvocationConstraints.EMPTY);
            assertNotNull(it);
            if (it.hasNext()) {
                it.next();
            }
        } catch (NullPointerException npe) {
            throw new AssertionError("a GetArg-built endpoint is not set up: " + npe, npe);
        } catch (Exception expected) {
            // no peer: any failure but a missing set-up is fine
        }
    }
}
