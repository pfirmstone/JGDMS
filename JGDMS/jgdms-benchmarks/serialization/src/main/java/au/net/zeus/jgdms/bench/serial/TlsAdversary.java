// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// How long a legitimate peer takes to open a TLS connection to a JERI listener while other peers misbehave: some connect and
// send nothing ("silent"), some send bytes that are not TLS and are closed ("junk"). Raw SSLSocket clients with the benchmark
// credentials (-Dbench.tls.dir, as TlsFixture). The legitimate peer connects, handshakes, sends the 8-byte mux client header,
// reads the 8-byte server header and closes, as TlsAcceptRate does; each open is timed from the connect to the mux header.
// Needs a ServerMain on the same -Dbench.tls.dir (<serverdir> is the directory it was started with).
//   java -Dbench.tls.dir=<dir> -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsAdversary <serverdir>
//        silent=K junk=J opens=M [junkevery=ms] [timeout=ms] [hold=ms]
//   silent  K connections that are opened first and held, sending nothing, until the run ends.
//   junk    J connections that send 10 bytes and are closed, opened one after another before the first legitimate open;
//           with junkevery=ms, J more are opened every ms milliseconds in the background while the legitimate peer opens.
//   opens   M legitimate opens, one after another (after a warm-up of 20 opens before the adversaries start).
//   timeout how long a legitimate open may take before it counts as failed (default 30000 ms).
//   hold    ms to keep the silent connections open after the last legitimate open (default 0).
// The client shares the machine with the server, so the figures are for comparison between builds, not absolute.
package au.net.zeus.jgdms.bench.serial;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class TlsAdversary {

    static long open(SSLContext ctx, int port, int timeoutMillis) throws Exception {
        long t0 = System.nanoTime();
        try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), timeoutMillis);
            s.setSoTimeout(timeoutMillis); s.setTcpNoDelay(true);
            s.setEnabledProtocols(new String[] { "TLSv1.3" });
            s.startHandshake();
            OutputStream out = s.getOutputStream(); out.write(TlsAcceptRate.HEADER); out.flush();
            InputStream in = s.getInputStream(); byte[] b = new byte[8]; int got = 0;
            while (got < 8) { int n = in.read(b, got, 8 - got); if (n < 0) throw new java.io.EOFException("closed before the mux header"); got += n; }
        }
        return System.nanoTime() - t0;
    }

    static void junk(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            s.setSoTimeout(2000);
            s.getOutputStream().write("GARBAGE!\r\n".getBytes()); s.getOutputStream().flush();
            try { s.getInputStream().read(); } catch (java.io.IOException e) { }
        } catch (java.io.IOException e) { }
    }

    public static void main(String[] a) throws Exception {
        Object proxy = ServerMain.read(Format.DER, a[0] + "/DER.TLS.proxy");
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("SslEndpoint\\[[^:]*:(\\d+)\\]").matcher(java.lang.reflect.Proxy.getInvocationHandler(proxy).toString());
        if (!pm.find()) throw new IllegalStateException("no TLS port in the proxy");
        int port = Integer.parseInt(pm.group(1));
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < a.length; i++) { int eq = a[i].indexOf('='); o.put(a[i].substring(0, eq), a[i].substring(eq + 1)); }
        int silent = Integer.parseInt(o.getOrDefault("silent", "0")), junk = Integer.parseInt(o.getOrDefault("junk", "0"));
        int opens = Integer.parseInt(o.getOrDefault("opens", "20")), timeout = Integer.parseInt(o.getOrDefault("timeout", "30000"));
        long junkEvery = Long.parseLong(o.getOrDefault("junkevery", "0")), hold = Long.parseLong(o.getOrDefault("hold", "0"));
        SSLContext ctx = TlsAcceptRate.context(Path.of(System.getProperty("bench.tls.dir")));
        for (int i = 0; i < 20; i++) open(ctx, port, timeout);       // warm-up, and a ticket for the resumptions below

        List<Socket> held = new ArrayList<>();
        for (int i = 0; i < silent; i++) { Socket s = new Socket(); s.connect(new InetSocketAddress("127.0.0.1", port), 20000); held.add(s); }
        for (int i = 0; i < junk; i++) junk(port);
        AtomicBoolean stop = new AtomicBoolean();
        Thread bg = null;
        if (junkEvery > 0 && junk > 0) {
            bg = new Thread(() -> { try { while (!stop.get()) { Thread.sleep(junkEvery); for (int i = 0; i < junk && !stop.get(); i++) junk(port); } } catch (InterruptedException e) { } }, "junk");
            bg.start();
        }
        long[] lat = new long[opens]; int k = 0, failed = 0; String firstError = null;
        long wall0 = System.nanoTime();
        for (int i = 0; i < opens; i++) {
            try { lat[k++] = open(ctx, port, timeout); }
            catch (Exception e) { failed++; if (firstError == null) firstError = e.toString(); }
        }
        long wall = System.nanoTime() - wall0;
        stop.set(true);
        if (bg != null) bg.join();
        if (hold > 0) Thread.sleep(hold);
        for (Socket s : held) try { s.close(); } catch (java.io.IOException e) { }
        long[] ok = Arrays.copyOf(lat, k); Arrays.sort(ok);
        System.out.printf("adversary: %d silent, %d junk%s: %d legitimate opens in %.1f s, %d failed; open p50 %.1f ms p99 %.1f ms max %.1f ms%s%n",
            silent, junk, junkEvery > 0 ? " every " + junkEvery + " ms" : "", opens, wall / 1e9, failed,
            ok.length == 0 ? 0 : ok[ok.length / 2] / 1e6, ok.length == 0 ? 0 : ok[(int) (ok.length * 0.99)] / 1e6, ok.length == 0 ? 0 : ok[ok.length - 1] / 1e6,
            firstError == null ? "" : "; first failure: " + firstError);
        System.exit(0);
    }
}
