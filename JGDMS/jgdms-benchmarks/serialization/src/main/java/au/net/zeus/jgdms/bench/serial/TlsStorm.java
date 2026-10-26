// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// A paced storm of new TLS connections against a JERI listener, to load it with a fixed number of new connections a second
// while something else is timed (TlsAcceptRate runs as fast as the listener lets it, so a faster listener is loaded harder).
// The same connect/handshake/mux-header/close loop as TlsAcceptRate, from N threads that share a total rate.
//   java -Dbench.tls.dir=<dir> -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsStorm <serverdir> <threads> <seconds> resumed|full <connectionsPerSecond>
package au.net.zeus.jgdms.bench.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class TlsStorm {
    public static void main(String[] a) throws Exception {
        Object proxy = ServerMain.read(Format.DER, a[0] + "/DER.TLS.proxy");
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("SslEndpoint\\[[^:]*:(\\d+)\\]").matcher(java.lang.reflect.Proxy.getInvocationHandler(proxy).toString());
        if (!pm.find()) throw new IllegalStateException("no TLS port in the proxy");
        int port = Integer.parseInt(pm.group(1)), threads = Integer.parseInt(a[1]), seconds = Integer.parseInt(a[2]);
        boolean full = a[3].equals("full");
        double rate = Double.parseDouble(a[4]);
        long periodNanos = (long) (threads * 1e9 / rate);
        Path dir = Path.of(System.getProperty("bench.tls.dir"));
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        AtomicLong ok = new AtomicLong(), failed = new AtomicLong(), late = new AtomicLong();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                try {
                    SSLContext ctx = TlsAcceptRate.context(dir);
                    SSLSocketFactory f = ctx.getSocketFactory();
                    long next = System.nanoTime() + periodNanos * id / threads;
                    while (next < end) {
                        long wait = next - System.nanoTime();
                        if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); else late.incrementAndGet();
                        next += periodNanos;
                        if (full) { var sc = ctx.getClientSessionContext(); for (var en = sc.getIds(); en.hasMoreElements();) { var s = sc.getSession(en.nextElement()); if (s != null) s.invalidate(); } }
                        try (SSLSocket s = (SSLSocket) f.createSocket()) {
                            s.connect(new InetSocketAddress("127.0.0.1", port), 20000);
                            s.setSoTimeout(30000); s.setTcpNoDelay(true);
                            s.setEnabledProtocols(new String[] { "TLSv1.3" });
                            s.startHandshake();
                            OutputStream out = s.getOutputStream(); out.write(TlsAcceptRate.HEADER); out.flush();
                            InputStream in = s.getInputStream(); byte[] b = new byte[8]; int got = 0;
                            while (got < 8) { int n = in.read(b, got, 8 - got); if (n < 0) throw new java.io.EOFException(); got += n; }
                            ok.incrementAndGet();
                        } catch (Exception e) { failed.incrementAndGet(); }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }, "storm-" + t);
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        System.out.printf("storm %s, %d threads, offered %.0f per second: %d connections in %d s = %d per second; failures %d; behind schedule %d%n",
            a[3], threads, rate, ok.get(), seconds, ok.get() / seconds, failed.get(), late.get());
        System.exit(0);
    }
}
