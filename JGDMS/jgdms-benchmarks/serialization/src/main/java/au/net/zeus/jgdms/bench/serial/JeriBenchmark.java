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

import au.net.zeus.jgdms.bench.serial.Payloads.Batch;
import au.net.zeus.jgdms.bench.serial.Payloads.Blob;
import au.net.zeus.jgdms.bench.serial.Payloads.Point;
import au.net.zeus.jgdms.bench.serial.Payloads.Record;
import java.util.concurrent.TimeUnit;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JERI tier: a complete remote call over TCP loopback, with the service
 * exported through {@code AtomicILFactory} (JOSS) or {@code AtomicDerILFactory}
 * (DER), as a deployed JGDMS service would be.
 *
 * <p>Each benchmark method calls the matching {@link Echo} method, so the
 * payload is marshalled twice per call (argument and result) and unmarshalled
 * twice (dispatcher and handler). {@link #ping} carries no payload and is the
 * per-call floor: the invocation layer, the mux, and the loopback round trip.
 *
 * <p>The service is exported once per trial (per fork), so the client-side
 * connection cache is warm and what is timed is the steady-state cost of a
 * call on a live proxy, not export or connection set-up. DGC is off and the
 * impl is held strongly, as in the JERI round-trip tests.
 *
 * <p>One benchmark thread by default (call latency). Pass {@code -t N} to
 * JMH to measure throughput with N concurrent callers on the one proxy.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@State(Scope.Benchmark)
public class JeriBenchmark {

    @Param({"JOSS", "DER"})
    public Format format;

    private Exporter exporter;
    private Echo.Impl impl;   // strong ref: the export table holds it weakly
    private Echo proxy;

    private Point point;
    private Record record;
    private Batch batch;
    private Blob blob;

    @Setup(Level.Trial)
    public void export() throws Exception {
        ClassLoader loader = JeriBenchmark.class.getClassLoader();
        exporter = System.getProperty("bench.proxy." + format.name()) != null ? null : new BasicJeriExporter(
                TcpServerEndpoint.getInstance("127.0.0.1", 0),
                format.ilFactory(loader),
                false,   // no DGC
                true);   // keep alive
        String remote = System.getProperty("bench.proxy." + format.name());
        if (remote != null) {
            // the service runs in another JVM (see ServerMain): call that one
            proxy = (Echo) ServerMain.read(format, remote);
        } else {
            impl = new Echo.Impl();
            proxy = (Echo) exporter.export(impl);
        }

        point  = (Point)  Payloads.create("point");
        record = (Record) Payloads.create("record");
        batch  = (Batch)  Payloads.create("batch100");
        blob   = (Blob)   Payloads.create("blob64k");

        // Self-check: every method must round-trip an equal value through
        // this format before anything is timed.
        proxy.ping();
        check(point,  proxy.point(point));
        check(record, proxy.record(record));
        check(batch,  proxy.batch(batch));
        check(blob,   proxy.blob(blob));
    }

    private void check(Object sent, Object received) {
        if (!sent.equals(received)) {
            throw new IllegalStateException(format + ": " + sent.getClass().getSimpleName()
                    + " did not round-trip through JERI");
        }
    }

    @TearDown(Level.Trial)
    public void unexport() {
        if (exporter != null) exporter.unexport(true);
    }

    @Benchmark
    public void ping() throws Exception {
        proxy.ping();
    }

    @Benchmark
    public Point point() throws Exception {
        return proxy.point(point);
    }

    @Benchmark
    public Record record() throws Exception {
        return proxy.record(record);
    }

    @Benchmark
    public Batch batch100() throws Exception {
        return proxy.batch(batch);
    }

    @Benchmark
    public Blob blob64k() throws Exception {
        return proxy.blob(blob);
    }
}
