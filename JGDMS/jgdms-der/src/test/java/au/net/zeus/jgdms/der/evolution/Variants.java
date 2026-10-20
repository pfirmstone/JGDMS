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
package au.net.zeus.jgdms.der.evolution;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.apache.river.api.io.AtomicSerial;

/**
 * Compiles two versions of the same class names into separate class loaders, so a stream written by one
 * version of a class can be read by another, as two releases of an application would.
 */
final class Variants {

    private static final Collection<?> NO_CONTEXT = Collections.unmodifiableCollection(Collections.emptyList());

    private Variants() {}

    /** Compiles {@code sources} (file name relative to the package root, to source text) and returns a loader for them. */
    static ClassLoader load(Map<String, String> sources) throws Exception {
        Path dir = Files.createTempDirectory("evolution");
        for (Map.Entry<String, String> e : sources.entrySet()) {
            Path f = dir.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String cp = AtomicSerial.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        List<String> args = new java.util.ArrayList<>(List.of("-d", dir.toString(), "-cp", cp, "-proc:none", "-nowarn"));
        for (String name : sources.keySet()) {
            args.add(dir.resolve(name).toString());
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        if (javac.run(null, null, err, args.toArray(new String[0])) != 0) {
            throw new IllegalStateException("compile failed: " + err);
        }
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, AtomicSerial.class.getClassLoader());
    }

    enum Codec {
        DER {
            @Override ObjectOutput out(ByteArrayOutputStream b, ClassLoader l) {
                return new DerMarshalOutputStream(b, NO_CONTEXT, l);
            }
            @Override ObjectInput in(byte[] bytes, ClassLoader l) throws IOException {
                return new DerMarshalInputStream(new ByteArrayInputStream(bytes),
                        new ResolutionContext(l, false, l), DerInputLimits.DEFAULT);
            }
        },
        JOSS {
            @Override ObjectOutput out(ByteArrayOutputStream b, ClassLoader l) throws IOException {
                return new AtomicMarshalOutputStream(b, l, NO_CONTEXT, false);
            }
            @Override ObjectInput in(byte[] bytes, ClassLoader l) throws IOException {
                return AtomicMarshalInputStream.create(new ByteArrayInputStream(bytes), l, false, l, NO_CONTEXT, false);
            }
        };

        abstract ObjectOutput out(ByteArrayOutputStream b, ClassLoader l) throws IOException;

        abstract ObjectInput in(byte[] bytes, ClassLoader l) throws IOException;

        byte[] write(Object o, ClassLoader l) throws Exception {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ObjectOutput out = out(b, l);
            out.writeObject(o);
            out.close();
            return b.toByteArray();
        }

        Object read(byte[] bytes, ClassLoader l) throws Exception {
            try (ObjectInput in = in(bytes, l)) {
                return in.readObject();
            }
        }
    }
}
