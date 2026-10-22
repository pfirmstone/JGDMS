/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The URI, URL, File and StackTraceElement serializers rebuild a value from text or plain fields received
 * in a stream. Whatever arrives, the result is the value or an {@link InvalidObjectException}: never an
 * unchecked exception, never a network call or a file access.
 */
public class ValueSerializersMalformedInputTest {

    private static final class MapGetArg extends GetArg {
        private final Map<String, Object> values;
        private final Class<?> owner;

        MapGetArg(Class<?> owner, Map<String, Object> values) {
            this.owner = owner;
            this.values = values;
        }

        @Override protected Object lookup(Class<?> c, String name) {
            return values.containsKey(name) ? values.get(name) : ABSENT;
        }

        @Override protected boolean isDefaulted(Class<?> c, String name) {
            return !values.containsKey(name);
        }

        @Override public Class[] serialClasses() {
            return new Class[] {owner};
        }

        @Override public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> with(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    @FunctionalInterface
    private interface Build {
        Resolve make() throws Exception;
    }

    private static void assertRejected(String what, Build b) throws Exception {
        try {
            b.make().readResolve();
            fail(what + " must be rejected");
        } catch (InvalidObjectException expected) {
            // fail-secure, as the other serializers
        } catch (IOException e) {
            // other checked I/O failures from the argument reader are rejections too
        } catch (RuntimeException e) {
            throw new AssertionError(what + " threw an unchecked " + e, e);
        }
    }

    private static Resolve uri(String s) throws Exception {
        return new URISerializer(new MapGetArg(URISerializer.class, with("uriExternalForm", s)));
    }

    private static Resolve url(String s) throws Exception {
        return new URLSerializer(new MapGetArg(URLSerializer.class, with("urlExternalForm", s)));
    }

    private static Resolve file(java.net.URI u) throws Exception {
        return new FileSerializer(new MapGetArg(FileSerializer.class, with("path", u)));
    }

    private static Resolve frame(String c, String m, String f, int line) throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("declaringClass", c);
        v.put("methodName", m);
        v.put("fileName", f);
        v.put("lineNumber", line);
        return new StackTraceElementValueSerializer(new MapGetArg(StackTraceElementValueSerializer.class, v));
    }

    @Test
    public void wellFormedInputIsRebuilt() throws Exception {
        assertEquals(new java.net.URI("http://example.org/a?b#c"), uri("http://example.org/a?b#c").readResolve());
        assertEquals("http://example.org/x", ((java.net.URL) url("http://example.org/x").readResolve()).toExternalForm());
        /* A File rebuilt from a file: URI is absolute; "/tmp/x" is not on Windows (no drive). */
        assertEquals(new java.io.File("/tmp/x").getAbsoluteFile(),
                     file(new java.io.File("/tmp/x").toURI()).readResolve());
        assertEquals(new StackTraceElement("a.B", "c", "B.java", 3), frame("a.B", "c", "B.java", 3).readResolve());
        assertEquals(new StackTraceElement("a.B", "c", null, -2), frame("a.B", "c", null, -2).readResolve());
    }

    @Test
    public void aMalformedUriIsRejected() throws Exception {
        assertRejected("a space in a URI", () -> uri("http://exa mple.org/"));
        assertRejected("a missing URI", () -> uri(null));
        assertRejected("an unclosed IPv6 host", () -> uri("http://[::1/"));
    }

    @Test
    public void aMalformedUrlIsRejected() throws Exception {
        assertRejected("an unknown protocol", () -> url("nosuchproto://x/"));
        assertRejected("no protocol", () -> url("just text"));
        assertRejected("a missing URL", () -> url(null));
    }

    @Test
    public void aFileNeedsAFileUri() throws Exception {
        assertRejected("an http URI", () -> file(new java.net.URI("http://example.org/x")));
        assertRejected("a relative URI", () -> file(new java.net.URI("relative/x")));
        assertRejected("a URI with a fragment", () -> file(new java.net.URI("file:///tmp/x#f")));
        assertRejected("a missing URI", () -> file(null));
    }

    @Test
    public void aFrameNeedsAClassAndAMethod() throws Exception {
        assertRejected("no class", () -> frame(null, "m", "F.java", 1));
        assertRejected("no method", () -> frame("a.B", null, "F.java", 1));
    }
}
