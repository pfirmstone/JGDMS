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
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
 * Codec tier: the cost of marshalling and unmarshalling one payload through a
 * fresh stream, with no network and no invocation layer.
 *
 * <p>This isolates the serialization protocol itself. Compared with
 * {@link JeriBenchmark}, the difference is the invocation layer plus TCP
 * loopback, which is the same for both formats, so a format's share of a
 * remote call can be read off by dividing the two.
 *
 * <p>Three benchmarks, each for every (format, payload) pair:
 * <ul>
 *   <li>{@link #marshal}: write one object to a fresh stream over a reused
 *       {@link ByteArrayOutputStream}.</li>
 *   <li>{@link #unmarshal}: read one object from a fresh stream over
 *       pre-encoded bytes. This is the side that validates: JOSS atomic
 *       reading checks the class descriptor and invokes the {@code GetArg}
 *       constructor; DER reading verifies the schema and canonical form.</li>
 *   <li>{@link #roundTrip}: both, back to back, as one call does.</li>
 * </ul>
 *
 * <p>Wire sizes are not a JMH metric; {@link WireSize} prints them.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class CodecBenchmark {

    @Param({"JOSS", "DER"})
    public Format format;

    @Param({"point", "record", "batch100", "blob64k"})
    public String payload;

    private ClassLoader loader;
    private Object object;
    private byte[] encoded;
    private ByteArrayOutputStream scratch;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        loader = CodecBenchmark.class.getClassLoader();
        object = Payloads.create(payload);
        encoded = format.encode(object, loader);
        // Self-check: the bytes must decode to an equal object, or the timing is of garbage.
        Object back = format.decode(encoded, loader);
        if (!object.equals(back)) {
            throw new IllegalStateException(format + "/" + payload + " does not round-trip");
        }
        scratch = new ByteArrayOutputStream(Math.max(1024, encoded.length * 2));
    }

    @Benchmark
    public int marshal() throws Exception {
        scratch.reset();
        ObjectOutput out = format.output(scratch, loader);
        out.writeObject(object);
        out.flush();
        out.close();
        return scratch.size();
    }

    @Benchmark
    public Object unmarshal() throws Exception {
        ObjectInput in = format.input(new ByteArrayInputStream(encoded), loader);
        Object o = in.readObject();
        in.close();
        return o;
    }

    @Benchmark
    public Object roundTrip() throws Exception {
        scratch.reset();
        ObjectOutput out = format.output(scratch, loader);
        out.writeObject(object);
        out.flush();
        out.close();
        ObjectInput in = format.input(
                new ByteArrayInputStream(scratch.toByteArray()), loader);
        Object o = in.readObject();
        in.close();
        return o;
    }
}
