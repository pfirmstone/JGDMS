// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance; see the README.
//
// A paced flood of connections that never authenticate, to load a JERI TLS listener with the cheapest work an unauthenticated
// peer can ask for while something else is timed (TlsStorm does full authenticated handshakes). Four modes:
//   replay    one captured ClientHello is sent on each new connection; the server computes its first flight (key exchange,
//             signature), the flood reads it and closes
//   silent    each new connection is held open and sends nothing until the listener closes it or the hold time (30 s) passes
//   fragment  each new connection sends the 6 bytes of a TLS record that holds the first byte of a ClientHello, then is held
//             open, as silent is (the server cannot tell the ClientHello is incomplete if it reads one record only)
//   stall     as replay, but after the server's flight the connection is held open and answers nothing (a peer that makes the
//             server compute its flight and then holds the place it was given)
//   For the held modes the time until the server closes each connection is recorded and the median and longest are printed.
//   java -Dbench.tls.dir=<dir> -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsFlood <serverdir> <threads> <seconds> replay|silent|fragment|stall <connectionsPerSecond> [group,group...]
package au.net.zeus.jgdms.bench.serial;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public class TlsFlood {
    static byte[] clientHello(Path dir, String[] groups) throws Exception {
        SSLContext ctx = TlsAcceptRate.context(dir);
        SSLEngine engine = ctx.createSSLEngine("localhost", 0);
        engine.setUseClientMode(true);
        if (groups.length > 0) {
            SSLParameters p = engine.getSSLParameters();
            p.setNamedGroups(groups);
            engine.setSSLParameters(p);
        }
        ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        engine.beginHandshake();
        engine.wrap(ByteBuffer.allocate(0), out);
        out.flip();
        byte[] hello = new byte[out.remaining()];
        out.get(hello);
        return hello;
    }

    public static void main(String[] a) throws Exception {
        Object proxy = ServerMain.read(Format.DER, a[0] + "/DER.TLS.proxy");
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("SslEndpoint\\[[^:]*:(\\d+)\\]").matcher(java.lang.reflect.Proxy.getInvocationHandler(proxy).toString());
        if (!pm.find()) throw new IllegalStateException("no TLS port in the proxy");
        int port = Integer.parseInt(pm.group(1)), threads = Integer.parseInt(a[1]), seconds = Integer.parseInt(a[2]);
        boolean replay = a[3].equals("replay"), fragment = a[3].equals("fragment"), stall = a[3].equals("stall");
        double rate = Double.parseDouble(a[4]);
        String[] groups = a.length > 5 && !a[5].isEmpty() ? a[5].split(",") : new String[0];
        long periodNanos = (long) (threads * 1e9 / rate);
        byte[] hello = replay || stall ? clientHello(Path.of(System.getProperty("bench.tls.dir")), groups) : null;
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        AtomicLong ok = new AtomicLong(), failed = new AtomicLong(), late = new AtomicLong();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                try {
                    long next = System.nanoTime() + periodNanos * id / threads;
                    while (next < end && System.nanoTime() < end) {
                        long wait = next - System.nanoTime();
                        if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); else late.incrementAndGet();
                        next += periodNanos;
                        if (replay) {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress("127.0.0.1", port), 20000);
                                s.setSoTimeout(30000); s.setTcpNoDelay(true);
                                s.getOutputStream().write(hello); s.getOutputStream().flush();
                                byte[] b = new byte[16384];
                                int r = s.getInputStream().read(b);
                                if (r > 5 && b[0] == 22) ok.incrementAndGet(); else failed.incrementAndGet();
                            } catch (Exception e) { failed.incrementAndGet(); }
                        } else {
                            // held until the server closes it or the flood ends; a virtual thread for each connection, so that a
                            // listener that does not answer cannot hold up the pacing, notes when the server closes it
                            Thread.ofVirtual().start(() -> {
                                long t0 = System.nanoTime();
                                try {
                                    Socket s = new Socket();
                                    s.connect(new InetSocketAddress("127.0.0.1", port), 20000);
                                    s.setTcpNoDelay(true);
                                    HELD.add(s);
                                    if (fragment) {
                                        s.getOutputStream().write(new byte[] { 22, 3, 1, 0, 1, 1 }); s.getOutputStream().flush();
                                    } else if (stall) {
                                        s.getOutputStream().write(hello); s.getOutputStream().flush();
                                        byte[] b = new byte[16384];
                                        int r = s.getInputStream().read(b);
                                        if (r <= 5 || b[0] != 22) throw new java.io.IOException("no flight");
                                    }
                                    ok.incrementAndGet();
                                    try {
                                        byte[] b = new byte[16384];
                                        while (s.getInputStream().read(b) >= 0) { }
                                    } catch (Exception e) { }
                                    HOLD_MS.add((System.nanoTime() - t0) / 1_000_000L);
                                } catch (Exception e) { failed.incrementAndGet(); }
                            });
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }, "flood-" + t);
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        System.out.printf("flood %s, %d threads, offered %.0f per second: %d connections in %d s = %d per second; failures %d; behind schedule %d%n",
            a[3], threads, rate, ok.get(), seconds, ok.get() / seconds, failed.get(), late.get());
        if (!replay) {
            Long[] hold = HOLD_MS.toArray(new Long[0]);
            java.util.Arrays.sort(hold);
            System.out.printf("held: %d closed by the server, %d still open at the end; time held p50 %d ms max %d ms%n",
                hold.length, Math.max(0, HELD.size() - hold.length), hold.length == 0 ? -1 : hold[hold.length / 2], hold.length == 0 ? -1 : hold[hold.length - 1]);
        }
        System.exit(0);
    }

    static final java.util.Queue<Socket> HELD = new java.util.concurrent.ConcurrentLinkedQueue<>();
    static final java.util.Queue<Long> HOLD_MS = new java.util.concurrent.ConcurrentLinkedQueue<>();
}
