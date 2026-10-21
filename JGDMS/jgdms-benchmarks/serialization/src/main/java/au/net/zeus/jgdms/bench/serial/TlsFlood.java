// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// A paced flood of connections that never authenticate, to load a JERI TLS listener with the cheapest work an unauthenticated
// peer can ask for while something else is timed (TlsStorm does full authenticated handshakes). Two modes:
//   replay  one captured ClientHello is sent on each new connection; the server computes its first flight (key exchange,
//           signature), the flood reads it and closes
//   silent  each new connection is held open and sends nothing until the listener closes it or the hold time (30 s) passes
//   java -Dbench.tls.dir=<dir> -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsFlood <serverdir> <threads> <seconds> replay|silent <connectionsPerSecond> [group,group...]
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
        boolean replay = a[3].equals("replay");
        double rate = Double.parseDouble(a[4]);
        String[] groups = a.length > 5 && !a[5].isEmpty() ? a[5].split(",") : new String[0];
        long periodNanos = (long) (threads * 1e9 / rate);
        byte[] hello = replay ? clientHello(Path.of(System.getProperty("bench.tls.dir")), groups) : null;
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        AtomicLong ok = new AtomicLong(), failed = new AtomicLong(), late = new AtomicLong();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                try {
                    long next = System.nanoTime() + periodNanos * id / threads;
                    while (next < end) {
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
                            // held until the server closes it or 30 s: a helper thread per held connection would distort the client, so
                            // the connection is parked on a timer instead
                            try {
                                Socket s = new Socket();
                                s.connect(new InetSocketAddress("127.0.0.1", port), 20000);
                                HELD.add(s);
                                ok.incrementAndGet();
                            } catch (Exception e) { failed.incrementAndGet(); }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }, "flood-" + t);
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        System.out.printf("flood %s, %d threads, offered %.0f per second: %d connections in %d s = %d per second; failures %d; behind schedule %d%n",
            a[3], threads, rate, ok.get(), seconds, ok.get() / seconds, failed.get(), late.get());
        System.exit(0);
    }

    static final java.util.Queue<Socket> HELD = new java.util.concurrent.ConcurrentLinkedQueue<>();
}
