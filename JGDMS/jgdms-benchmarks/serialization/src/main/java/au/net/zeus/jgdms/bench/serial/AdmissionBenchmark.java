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

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * B4: what the whole-stream admission costs, stage by stage, and what an unauthenticated peer can make a server pay
 * before {@code checkAccess}.
 *
 * <p>A server dispatcher builds its request reader with {@code forDispatcher} (cheap stage), reads the method hash, calls
 * {@code checkAccess}, and only then admits fully ({@code admitStream}) and reads the arguments. So, per stream:
 * <ul>
 *   <li>{@link #floor}: the framed, bounded read alone. This is what construction cost BEFORE B2 (commit {@code 2d3dde9cf}),
 *       i.e. "without B2" for an unauthenticated peer.</li>
 *   <li>{@link #cheap}: {@code forDispatcher}. This is what an unauthenticated peer costs WITH B2.</li>
 *   <li>{@link #cheapPlusFull}: {@code forDispatcher} then {@code admitStream}: what an authorized request adds.</li>
 *   <li>{@link #decode}: the public constructor (full admission at construction) and every read: the whole
 *       client-side / delivered cost, to read the admission against.</li>
 * </ul>
 * <p>Payloads: the four benchmark payloads; {@code request8}, a synthetic 8-item request (method hash and seven arguments) AND,
 * as the dispatcher reads it, a separate authorization-context block read through the public constructor (so full admission
 * runs on it before authorization) -- {@link #context} measures that block alone and {@link #unauthenticatedWithContext}
 * the pre-authorization total; {@code blob16m} (one octet string just under the 16 MiB cap) and {@code records16m} (about 16 MiB of
 * small records: the shape with the most admission work per byte).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@State(Scope.Thread)
public class AdmissionBenchmark {

    @Param({"point", "record", "batch100", "blob64k", "request8", "blob16m", "records16m"})
    public String payload;

    private ClassLoader loader;
    private ResolutionContext resolution;
    private DerInputLimits limits;
    private byte[] encoded;
    private byte[] contextBlock;
    private boolean isRequest;

    @Setup
    public void setUp() throws Exception {
        loader = AdmissionBenchmark.class.getClassLoader();
        resolution = new ResolutionContext(loader, false, loader);
        limits = DerInputLimits.DEFAULT;
        isRequest = payload.equals("request8");
        switch (payload) {
            case "request8":
                encoded = request8();
                break;
            case "blob16m":
                encoded = Format.DER.encode(Payloads.blob(16 * 1024 * 1024 - 4096), loader);
                break;
            case "records16m":
                encoded = records16m();
                break;
            default:
                encoded = Format.DER.encode(Payloads.create(payload), loader);
        }
        if (encoded.length >= limits.maxInputBytes()) {
            throw new IllegalStateException(payload + " is " + encoded.length + " bytes, over the cap");
        }
        // The authorization-context block a dispatcher reads before checkAccess: a small object (the DER side-band block holds the caller's context entries).
        contextBlock = Format.DER.encode(Payloads.batch(2), loader);
        // Self-check: the cheap stage then the full stage then the reads must succeed.
        DerMarshalInputStream s = DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(encoded), resolution, limits);
        s.admitStream();
        readItems(s);
        System.out.println("# " + payload + ": " + encoded.length + " bytes");
    }

    private byte[] request8() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutput out = new DerMarshalOutputStream(bo, Collections.emptyList(), loader);
        out.writeLong(0x1234567890ABCDEFL);                 // method hash
        out.writeObject(Payloads.record(1));
        out.writeObject(new Payloads.Point(3, 4));
        out.writeObject(Payloads.record(2));
        out.writeObject("a string argument");
        out.writeObject(Payloads.batch(10));
        out.writeObject(new Payloads.Point(5, 6));
        out.writeObject(Payloads.blob(1024));
        out.flush();
        out.close();
        return bo.toByteArray();
    }

    private byte[] records16m() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutput out = new DerMarshalOutputStream(bo, Collections.emptyList(), loader);
        out.writeLong(1L);
        int batches = 0;
        // Batches of 4096 records until just under the cap; measure the encoded size as we go.
        long size = 0;
        long id = 0;
        while (size < 15L * 1024 * 1024) {
            List<Payloads.Record> list = new ArrayList<>(4096);
            for (int i = 0; i < 4096; i++) list.add(Payloads.record(id++));
            out.writeObject(new Payloads.Batch(list));
            out.flush();
            size = bo.size();
            batches++;
        }
        out.close();
        return bo.toByteArray();
    }

    private Object readItems(DerMarshalInputStream s) throws Exception {
        Object last = null;
        if (isRequest || payload.equals("records16m")) {
            s.readLong();
            int n = isRequest ? 7 : -1;
            if (n > 0) {
                for (int i = 0; i < n; i++) last = s.readObject();
            } else {
                for (;;) {
                    try { last = s.readObject(); } catch (java.io.IOException e) { break; }
                }
            }
        } else {
            last = s.readObject();
        }
        return last;
    }

    /** The framed bounded read alone: construction before B2. */
    @Benchmark
    public byte[] floor() throws Exception {
        return limits.readFramedBounded(new ByteArrayInputStream(encoded));
    }

    /** Cheap stage: all an unauthenticated peer's request stream costs with B2 (before {@code checkAccess}). */
    @Benchmark
    public Object cheap() throws Exception {
        return DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(encoded), resolution, limits);
    }

    /** Cheap then full stage: what an authorized request adds over the floor before it reads its arguments. */
    @Benchmark
    public Object cheapPlusFull() throws Exception {
        DerMarshalInputStream s =
                DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(encoded), resolution, limits);
        s.admitStream();
        return s;
    }

    /** Public constructor (full admission at construction) and every read: the delivered cost. */
    @Benchmark
    public Object decode() throws Exception {
        DerMarshalInputStream s = new DerMarshalInputStream(new ByteArrayInputStream(encoded), resolution, limits);
        return readItems(s);
    }

    /** The whole dispatcher path for an authorized request: cheap, admit, read. */
    @Benchmark
    public Object dispatcherPath() throws Exception {
        DerMarshalInputStream s =
                DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(encoded), resolution, limits);
        s.admitStream();
        return readItems(s);
    }

    /** The authorization-context block alone: public constructor, so full admission, before authorization. */
    @Benchmark
    public Object context() throws Exception {
        ObjectInput in = new DerMarshalInputStream(new ByteArrayInputStream(contextBlock), resolution, limits);
        return in.readObject();
    }

    /** Everything an unauthenticated peer makes the server do before checkAccess: the context block plus the cheap stage. */
    @Benchmark
    public Object unauthenticatedWithContext() throws Exception {
        ObjectInput in = new DerMarshalInputStream(new ByteArrayInputStream(contextBlock), resolution, limits);
        Object c = in.readObject();
        Object s = DerMarshalInputStream.forDispatcher(new ByteArrayInputStream(encoded), resolution, limits);
        return new Object[] {c, s};
    }
}
