// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// How many new TLS connections per second a JERI TLS listener accepts when many clients connect at once (the server handshakes
// on its accept thread). Raw SSLSocket clients with the benchmark credentials (-Dbench.tls.dir, as TlsFixture), N threads that
// each loop: connect, handshake, send the 8-byte mux client header, read the 8-byte mux server header (which the server writes
// after its NewSessionTicket, so the client gets a ticket for the next connection), close. Needs a ServerMain on the same
// -Dbench.tls.dir (<serverdir> is the directory it was started with; the TLS port is read from its published proxy).
//   java -Dbench.tls.dir=<dir> -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsAcceptRate <serverdir> <threads> <seconds> resumed|full [warmupSeconds=3]
//   resumed: each thread has its own SSLContext and so its own ticket cache; after its first connection it resumes.
//   full:    each thread drops its cached sessions before every connection, so every handshake is a full one.
// The client shares the machine with the server, so the rate is a lower bound on the server's.
package au.net.zeus.jgdms.bench.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

public class TlsAcceptRate {
    static final byte[] HEADER = { 'J', 'm', 'u', 'x', 1, 0, 0x40, 0 };

    static SSLContext context(Path dir) throws Exception {
        char[] pw = "changeit".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12"), ts = KeyStore.getInstance("PKCS12");
        try (var in = java.nio.file.Files.newInputStream(dir.resolve("client.p12"))) { ks.load(in, pw); }
        try (var in = java.nio.file.Files.newInputStream(dir.resolve("truststore.p12"))) { ts.load(in, pw); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); kmf.init(ks, pw);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX"); tmf.init(ts);
        SSLContext c = SSLContext.getInstance("TLSv1.3"); c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return c;
    }

    public static void main(String[] a) throws Exception {
        Object proxy = ServerMain.read(Format.DER, a[0] + "/DER.TLS.proxy");
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("SslEndpoint\\[[^:]*:(\\d+)\\]").matcher(java.lang.reflect.Proxy.getInvocationHandler(proxy).toString());
        if (!pm.find()) throw new IllegalStateException("no TLS port in the proxy");
        int port = Integer.parseInt(pm.group(1)), threads = Integer.parseInt(a[1]), seconds = Integer.parseInt(a[2]);
        boolean full = a[3].equals("full");
        int warmup = a.length > 4 ? Integer.parseInt(a[4]) : 3;
        Path dir = Path.of(System.getProperty("bench.tls.dir"));
        AtomicBoolean stop = new AtomicBoolean(), measuring = new AtomicBoolean();
        AtomicLong ok = new AtomicLong(), failed = new AtomicLong(), resumedCount = new AtomicLong();
        Map<String, AtomicLong> errors = new java.util.concurrent.ConcurrentHashMap<>();
        List<long[]> lat = Collections.synchronizedList(new ArrayList<>());
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            ts[t] = new Thread(() -> {
                try {
                    SSLContext ctx = context(dir);
                    SSLSocketFactory f = ctx.getSocketFactory();
                    long[] mine = new long[1 << 16]; int k = 0;
                    while (!stop.get()) {
                        if (full) { var sc = ctx.getClientSessionContext(); for (var en = sc.getIds(); en.hasMoreElements();) { SSLSession s = sc.getSession(en.nextElement()); if (s != null) s.invalidate(); } }
                        long t0 = System.nanoTime();
                        try (SSLSocket s = (SSLSocket) f.createSocket()) {
                            s.connect(new InetSocketAddress("127.0.0.1", port), 20000);
                            s.setSoTimeout(30000); s.setTcpNoDelay(true);
                            s.setEnabledProtocols(new String[] { "TLSv1.3" });
                            s.startHandshake();
                            OutputStream out = s.getOutputStream(); out.write(HEADER); out.flush();
                            InputStream in = s.getInputStream(); byte[] b = new byte[8]; int got = 0;
                            while (got < 8) { int n = in.read(b, got, 8 - got); if (n < 0) throw new java.io.EOFException("closed before the mux header"); got += n; }
                            long dt = System.nanoTime() - t0;
                            if (measuring.get()) { ok.incrementAndGet(); if (k < mine.length) mine[k++] = dt; }
                        } catch (Exception e) {
                            if (measuring.get()) { failed.incrementAndGet(); errors.computeIfAbsent(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replaceAll("[0-9]+", "#"), x -> new AtomicLong()).incrementAndGet(); }
                            Thread.sleep(20);
                        }
                    }
                    lat.add(Arrays.copyOf(mine, k));
                } catch (Exception e) { e.printStackTrace(); }
            }, "client-" + t);
            ts[t].start();
        }
        Thread.sleep(warmup * 1000L);
        measuring.set(true); long t0 = System.nanoTime();
        Thread.sleep(seconds * 1000L);
        measuring.set(false); long elapsed = System.nanoTime() - t0;
        stop.set(true);
        for (Thread t : ts) t.join(40000);
        List<Long> all = new ArrayList<>(); for (long[] l : lat) for (long v : l) all.add(v);
        Collections.sort(all);
        System.out.printf("%s, %d threads: %d connections in %.1f s = %.0f per second; failures %d; latency (connect to mux header) p50 %.1f ms p99 %.1f ms max %.1f ms%n",
            a[3], threads, ok.get(), elapsed / 1e9, ok.get() / (elapsed / 1e9), failed.get(),
            all.isEmpty() ? 0 : all.get(all.size() / 2) / 1e6, all.isEmpty() ? 0 : all.get((int) (all.size() * 0.99)) / 1e6, all.isEmpty() ? 0 : all.get(all.size() - 1) / 1e6);
        errors.forEach((k, v) -> System.out.println("   " + v + " x " + k));
        System.exit(0);
    }
}
