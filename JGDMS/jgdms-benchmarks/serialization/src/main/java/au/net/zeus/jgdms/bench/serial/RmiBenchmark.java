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

import au.net.zeus.jgdms.bench.serial.JdkPayloads.Batch;
import au.net.zeus.jgdms.bench.serial.JdkPayloads.Blob;
import au.net.zeus.jgdms.bench.serial.JdkPayloads.Point;
import au.net.zeus.jgdms.bench.serial.JdkPayloads.Record;
import au.net.zeus.rmi.tls.TlsRMIClientSocketFactory;
import au.net.zeus.rmi.tls.TlsRMIServerSocketFactory;
import java.lang.reflect.Proxy;
import javax.security.auth.Subject;
import java.rmi.server.UnicastRemoteObject;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Java RMI (JRMP over JDK serialization) tier, measured as {@link JeriBenchmark} is: the
 * service exported once per trial, a stub in the same JVM, one calling thread, a complete
 * call over TCP loopback per operation, the {@link JdkPayloads} twins as arguments and
 * results. No registry, no DGC lease traffic during the timed calls, no security manager,
 * no authentication or encryption (as in the JERI tier). The self-check fails if the stub
 * is not a remote stub, so a call that stayed in-process cannot be timed by mistake.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@State(Scope.Benchmark)
public class RmiBenchmark {

    /** TLS: the jgdms-rmi-tls socket factories (mutual TLS, credentials from the Subject), the like-for-like
     *  comparison with JERI over TLS. Anything else (PLAIN, or TCP/UDS when run beside the JERI benchmark): unencrypted
     *  sockets, as in all earlier RMI results. */
    @Param({"PLAIN"})
    public String transport;

    private RmiEcho.Impl impl;   // strong ref
    private RmiEcho stub;
    private Subject clientSubject;

    private Point point;
    private Record record;
    private Batch batch;
    private Blob blob;

    @Setup(Level.Trial)
    public void export() throws Exception {
        boolean tls = transport.equals("TLS");
        String remote = System.getProperty("bench.proxy.rmi." + (tls ? "TLS" : "PLAIN"));
        clientSubject = tls ? TlsFixture.current().client : null;
        if (remote != null) {
            // the service runs in another JVM (see ServerMain): call that one
            stub = (RmiEcho) ServerMain.readStub(remote);
        } else {
            impl = new RmiEcho.Impl();
            final RmiEcho.Impl service = impl;
            stub = tls
                    ? Subject.callAs(TlsFixture.current().server, () -> (RmiEcho) UnicastRemoteObject.exportObject(
                            service, 0, new TlsRMIClientSocketFactory(), new TlsRMIServerSocketFactory()))
                    : (RmiEcho) UnicastRemoteObject.exportObject(impl, 0);
        }
        if (stub == impl || !Proxy.isProxyClass(stub.getClass())) {
            throw new IllegalStateException("RMI stub expected, got " + stub.getClass());
        }
        point  = (Point)  JdkPayloads.create("point");
        record = (Record) JdkPayloads.create("record");
        batch  = (Batch)  JdkPayloads.create("batch100");
        blob   = (Blob)   JdkPayloads.create("blob64k");

        ping();
        check(point,  point());
        check(record, record());
        check(batch,  batch100());
        check(blob,   blob64k());
        // the echo returns its argument: over the wire it comes back as a different object
        if (record() == record) {
            throw new IllegalStateException("RMI call did not cross the wire");
        }
    }

    private static void check(Object sent, Object received) {
        if (!sent.equals(received)) {
            throw new IllegalStateException(sent.getClass().getSimpleName() + " did not round-trip through RMI");
        }
    }

    @TearDown(Level.Trial)
    public void unexport() throws Exception {
        if (impl != null) UnicastRemoteObject.unexportObject(impl, true);
    }

    @Benchmark
    public void ping() throws Exception {
        if (clientSubject == null) {
            stub.ping();
        } else {
            Subject.callAs(clientSubject, () -> {
                stub.ping();
                return null;
            });
        }
    }

    @Benchmark
    public Point point() throws Exception {
        return clientSubject == null ? stub.point(point)
                : Subject.callAs(clientSubject, () -> stub.point(point));
    }

    @Benchmark
    public Record record() throws Exception {
        return clientSubject == null ? stub.record(record)
                : Subject.callAs(clientSubject, () -> stub.record(record));
    }

    @Benchmark
    public Batch batch100() throws Exception {
        return clientSubject == null ? stub.batch(batch)
                : Subject.callAs(clientSubject, () -> stub.batch(batch));
    }

    @Benchmark
    public Blob blob64k() throws Exception {
        return clientSubject == null ? stub.blob(blob)
                : Subject.callAs(clientSubject, () -> stub.blob(blob));
    }
}
