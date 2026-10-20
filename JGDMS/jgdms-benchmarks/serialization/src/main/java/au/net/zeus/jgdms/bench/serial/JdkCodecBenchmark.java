/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Codec tier for the JDK's own serialization ({@link ObjectOutputStream} and
 * {@link ObjectInputStream}, which is what Java RMI marshals with), measured exactly as
 * {@link CodecBenchmark} measures JOSS and DER: a fresh stream per operation over the
 * {@link JdkPayloads} twins of the payloads. There is no input filter and no class
 * validation, so this is the unconstrained baseline.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class JdkCodecBenchmark {

    @Param({"point", "record", "batch100", "blob64k"})
    public String payload;

    private Object object;
    private byte[] encoded;
    private ByteArrayOutputStream scratch;

    static byte[] encode(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        object = JdkPayloads.create(payload);
        encoded = encode(object);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(encoded))) {
            if (!object.equals(in.readObject())) {
                throw new IllegalStateException("JDK/" + payload + " does not round-trip");
            }
        }
        scratch = new ByteArrayOutputStream(Math.max(1024, encoded.length * 2));
    }

    @Benchmark
    public int marshal() throws Exception {
        scratch.reset();
        ObjectOutputStream out = new ObjectOutputStream(scratch);
        out.writeObject(object);
        out.flush();
        out.close();
        return scratch.size();
    }

    @Benchmark
    public Object unmarshal() throws Exception {
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(encoded));
        Object o = in.readObject();
        in.close();
        return o;
    }

    @Benchmark
    public Object roundTrip() throws Exception {
        scratch.reset();
        ObjectOutputStream out = new ObjectOutputStream(scratch);
        out.writeObject(object);
        out.flush();
        out.close();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(scratch.toByteArray()));
        Object o = in.readObject();
        in.close();
        return o;
    }
}
