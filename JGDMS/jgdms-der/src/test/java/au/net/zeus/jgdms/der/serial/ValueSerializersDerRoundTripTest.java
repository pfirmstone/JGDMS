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
 */
package au.net.zeus.jgdms.der.serial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.junit.jupiter.api.Test;

/**
 * URI, URL, File and StackTraceElement are carried by DER (registered in the closed serializer list) as
 * strings or plain fields and rebuilt on read, at the top level of a stream, as a field of a value, and as
 * an array. MarshalledObject, Throwable and Properties stay refused.
 */
class ValueSerializersDerRoundTripTest {

    @AtomicSerial
    public static final class Holder {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {
                new SerialForm("uri", URI.class), new SerialForm("url", URL.class),
                new SerialForm("file", File.class), new SerialForm("frame", StackTraceElement.class),
                new SerialForm("frames", StackTraceElement[].class), new SerialForm("uris", URI[].class)
            };
        }
        public static void serialize(PutArg arg, Holder h) throws IOException {
            arg.put("uri", h.uri);
            arg.put("url", h.url);
            arg.put("file", h.file);
            arg.put("frame", h.frame);
            arg.put("frames", h.frames);
            arg.put("uris", h.uris);
            arg.writeArgs();
        }
        final URI uri; final URL url; final File file; final StackTraceElement frame;
        final StackTraceElement[] frames; final URI[] uris;
        Holder(URI uri, URL url, File file, StackTraceElement frame, StackTraceElement[] frames, URI[] uris) {
            this.uri = uri; this.url = url; this.file = file; this.frame = frame; this.frames = frames; this.uris = uris;
        }
        public Holder(GetArg arg) throws IOException, ClassNotFoundException {
            this(arg.get("uri", null, URI.class), arg.get("url", null, URL.class), arg.get("file", null, File.class),
                 arg.get("frame", null, StackTraceElement.class),
                 arg.get("frames", null, StackTraceElement[].class), arg.get("uris", null, URI[].class));
        }
    }

    private static Object roundTrip(Object o) throws Exception {
        ClassLoader loader = ValueSerializersDerRoundTripTest.class.getClassLoader();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DerMarshalOutputStream out = new DerMarshalOutputStream(bos, Collections.emptyList(), loader)) {
            out.writeObject(o);
        }
        try (DerMarshalInputStream in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                new ResolutionContext(loader, false, loader), DerInputLimits.DEFAULT)) {
            return in.readObject();
        }
    }

    /** A value carried as a field of an @AtomicSerial object, the way the registry applies. */
    @AtomicSerial
    public static final class UriBox {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {new SerialForm("v", URI.class)};
        }
        public static void serialize(PutArg arg, UriBox o) throws IOException {
            arg.put("v", o.v);
            arg.writeArgs();
        }
        final URI v;
        UriBox(URI v) { this.v = v; }
        public UriBox(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("v", null, URI.class)); }
    }

    @AtomicSerial
    public static final class UrlBox {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {new SerialForm("v", URL.class)};
        }
        public static void serialize(PutArg arg, UrlBox o) throws IOException {
            arg.put("v", o.v);
            arg.writeArgs();
        }
        final URL v;
        UrlBox(URL v) { this.v = v; }
        public UrlBox(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("v", null, URL.class)); }
    }

    @AtomicSerial
    public static final class FileBox {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {new SerialForm("v", File.class)};
        }
        public static void serialize(PutArg arg, FileBox o) throws IOException {
            arg.put("v", o.v);
            arg.writeArgs();
        }
        final File v;
        FileBox(File v) { this.v = v; }
        public FileBox(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("v", null, File.class)); }
    }

    @AtomicSerial
    public static final class FrameBox {
        public static SerialForm[] serialForm() {
            return new SerialForm[] {new SerialForm("v", StackTraceElement.class)};
        }
        public static void serialize(PutArg arg, FrameBox o) throws IOException {
            arg.put("v", o.v);
            arg.writeArgs();
        }
        final StackTraceElement v;
        FrameBox(StackTraceElement v) { this.v = v; }
        public FrameBox(GetArg arg) throws IOException, ClassNotFoundException { this(arg.get("v", null, StackTraceElement.class)); }
    }

    private static URI viaField(URI v) throws Exception {
        return ((UriBox) roundTrip(new UriBox(v))).v;
    }

    private static URL viaField(URL v) throws Exception {
        return ((UrlBox) roundTrip(new UrlBox(v))).v;
    }

    private static File viaField(File v) throws Exception {
        return ((FileBox) roundTrip(new FileBox(v))).v;
    }

    private static StackTraceElement viaField(StackTraceElement v) throws Exception {
        return ((FrameBox) roundTrip(new FrameBox(v))).v;
    }

    @Test
    void uriAsAField() throws Exception {
        for (String s : new String[] {"http://example.org/a/b?x=1#frag", "mailto:a@example.org", "relative/path", "file:///tmp/x",
                                      "http://example.org/%7Euser/a%20b"}) {
            URI u = new URI(s);
            assertEquals(u, viaField(u), s);
        }
    }

    @Test
    void urlAsAFieldComparedByExternalForm() throws Exception {
        // URL.equals can do a name lookup, so compare the text
        for (String s : new String[] {"http://example.org/a?b=c#d", "file:/tmp/x", "https://example.org:8443/"}) {
            URL u = new URL(s);
            URL back = viaField(u);
            assertEquals(u.toExternalForm(), back.toExternalForm(), s);
        }
    }

    @Test
    void fileAsAField() throws Exception {
        File abs = new File("/tmp/some dir/file.txt").getAbsoluteFile();
        assertEquals(abs, viaField(abs));
        // a relative path is sent as the absolute one (File.toURI), which is what comes back
        File rel = new File("relative/x");
        assertEquals(rel.getAbsoluteFile(), viaField(rel));
    }

    @Test
    void stackTraceElementAsAField() throws Exception {
        StackTraceElement a = new StackTraceElement("com.example.C", "m", "C.java", 42);
        assertEquals(a, viaField(a));
        StackTraceElement noFile = new StackTraceElement("com.example.C", "m", null, -1);
        assertEquals(noFile, viaField(noFile));
        StackTraceElement nativeFrame = new StackTraceElement("com.example.C", "n", "C.java", -2);
        assertEquals(nativeFrame, viaField(nativeFrame));
    }

    @Test
    void theLoaderAndModuleNamesOfAFrameAreNotCarried() throws Exception {
        StackTraceElement withModule = new StackTraceElement("app", "mod", "1.0", "com.example.C", "m", "C.java", 7);
        StackTraceElement back = viaField(withModule);
        assertEquals(new StackTraceElement("com.example.C", "m", "C.java", 7), back);
        assertNull(back.getModuleName());
        assertNull(back.getClassLoaderName());
    }

    @Test
    void asTheTopLevelObjectOfAStreamTheyAreStillRefused() {
        // the registry applies to fields and arrays; a stream's top-level object must itself be @AtomicSerial
        for (Object o : new Object[] {URI.create("a:b"), new File("/tmp/x"),
                                      new StackTraceElement("a.B", "c", "B.java", 1)}) {
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> roundTrip(o), o.getClass().getName());
        }
    }

    @Test
    void asFieldsAndArraysOfAValue() throws Exception {
        StackTraceElement f = new StackTraceElement("a.B", "c", "B.java", 1);
        Holder h = new Holder(new URI("http://example.org/x"), new URL("http://example.org/y"),
                new File("/tmp/z").getAbsoluteFile(), f,
                new StackTraceElement[] {f, new StackTraceElement("d.E", "f", null, 2)},
                new URI[] {new URI("a:b"), new URI("c/d")});
        Holder back = (Holder) roundTrip(h);
        assertEquals(h.uri, back.uri);
        assertEquals(h.url.toExternalForm(), back.url.toExternalForm());
        assertEquals(h.file, back.file);
        assertEquals(h.frame, back.frame);
        assertArrayEquals(h.frames, back.frames);
        assertArrayEquals(h.uris, back.uris);
    }

    @Test
    void marshalledObjectThrowableAndPropertiesStayRefused() throws Exception {
        for (Object o : new Object[] {new java.rmi.MarshalledObject<>("x"), new java.util.Properties()}) {
            Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> roundTrip(o));
            assertTrue(String.valueOf(e.getMessage()).contains("unsupported")
                    || e.getCause() != null, o.getClass().getName() + ": " + e);
        }
    }
}
