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
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/**
 * Connections a second a JERI TLS listener accepts (see the header comment above for the arguments).
 *
 * <h2>Running out of ephemeral ports</h2>
 * Every connection the client closes first leaves its local port in TIME_WAIT for minutes, so a fast loop on one
 * (source address, destination address, destination port) can use up the client's dynamic port range
 * ({@code BindException: Address already in use} from {@code connect}); the failed attempts are then not the listener's
 * doing and the run is meaningless. Two options spread the connections over more address pairs:
 * <ul>
 * <li>{@code -Dbench.connect.hosts=a,b,c} (default {@code 127.0.0.1}): destination addresses of the listener, used
 *   round-robin per connection attempt in every thread; each thread starts at a different index, so each address gets an
 *   even share. The server listens on the wildcard address (port only), so it accepts on every local address of the
 *   machine: on one machine {@code 127.0.0.1,127.0.0.2,127.0.0.3} (Linux; other systems may only have 127.0.0.1 in the
 *   loopback range) or a second IP address of a network interface. A 4-tuple (source address, source port, destination
 *   address, destination port) with a different destination address may reuse the same local port, which multiplies the
 *   usable port budget by the number of destinations (as far as the operating system allocates ports per
 *   destination; this class does not check).</li>
 * <li>{@code -Dbench.bind.hosts=x,y} (default none): local source addresses. The socket is created unconnected, bound to
 *   one of them with port 0 and then connected. The source and the destination are chosen so that, over
 *   {@code sources x destinations} attempts, every pair is used equally often. The addresses must belong to this machine
 *   (checked at start). With bind.hosts every source address has its own port space on Windows, and the destination list
 *   matters too, so give both lists for the largest number of distinct pairs.</li>
 * </ul>
 * A connect that fails with a {@link java.net.BindException} anywhere in its cause chain is counted as a port exhaustion.
 * If more than 1% of the attempts of the measured window, or more than 50, are such failures the last output line is
 * {@code RUN INVALID: client ran out of ephemeral ports ...} and the exit code is 3 (0 otherwise).
 *
 * <p>Besides the summary line the run prints, when the system tells it, the dynamic port range and the number of TCP
 * connections in TIME_WAIT (the whole machine: client and server side) before the warm-up and after the run; "n/a" when
 * {@code netstat} (or /proc/net/tcp on Linux) could not be read in a few seconds.
 */
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

    /** Addresses from a comma separated system property; an empty array when the property is absent or empty. */
    static InetAddress[] hosts(String property, String defaultValue) throws java.io.IOException {
        String v = System.getProperty(property, defaultValue);
        List<InetAddress> l = new ArrayList<>();
        if (v != null) for (String h : v.split(",")) if (!h.trim().isEmpty()) l.add(InetAddress.getByName(h.trim()));
        return l.toArray(new InetAddress[0]);
    }

    /** Fails at start, with a clear message, if a source address is not one of this machine's. */
    static void checkBindable(InetAddress[] src) throws java.io.IOException {
        for (InetAddress a : src) {
            try (Socket s = new Socket()) { s.bind(new InetSocketAddress(a, 0)); }
            catch (java.io.IOException e) { throw new java.io.IOException("bench.bind.hosts: cannot bind to " + a.getHostAddress() + ": " + e, e); }
        }
    }

    /**
     * Binds {@code s} (unconnected) to a source address if there are any, and connects it to the listener; attempt number
     * {@code n} (a thread starts at its own index and counts up) picks the pair: destination n mod D, source (n div D) mod S.
     */
    static void bindAndConnect(Socket s, InetAddress[] dst, InetAddress[] src, long n, int port) throws java.io.IOException {
        if (src.length > 0) s.bind(new InetSocketAddress(src[(int) ((n / dst.length) % src.length)], 0));
        s.connect(new InetSocketAddress(dst[(int) (n % dst.length)], port), 20000);
    }

    /** True if a {@link BindException} is the failure or anywhere in its causes: connect found no free local port. */
    static boolean isPortExhaustion(Throwable e) {
        for (int i = 0; e != null && i < 20; i++, e = e.getCause()) if (e instanceof BindException) return true;
        return false;
    }

    /** Runs a command and returns its standard output, or null if it fails, does not end in {@code seconds}, or cannot start. */
    static String run(int seconds, String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            final InputStream in = p.getInputStream();
            Thread r = new Thread(() -> { try { in.transferTo(buf); } catch (Exception e) { } }, "bench-run-reader");
            r.setDaemon(true); r.start();
            if (!p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) return null;
            r.join(2000);
            return p.exitValue() == 0 ? buf.toString() : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    /** A /proc file as text (read with a plain stream: Files.readString/readAllBytes got 1 byte of a sysctl file on the test JDK). */
    static String readProc(String file) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) { return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII); }
    }

    /** The number of TCP connections in TIME_WAIT on this machine, as text; "n/a" when it cannot be found. */
    static String timeWait() {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
                long n = 0; boolean any = false;
                for (String f : new String[] { "/proc/net/tcp", "/proc/net/tcp6" }) {
                    try {
                        for (String l : readProc(f).split("\\R")) {
                            String[] c = l.trim().split("\\s+");
                            if (c.length > 3 && c[0].endsWith(":")) { any = true; if (c[3].equals("06")) n++; }
                        }
                    } catch (Exception e) { }
                }
                return any ? Long.toString(n) : "n/a";
            }
            String out = run(10, "netstat", "-an");
            if (out == null) return "n/a";
            long n = 0; boolean tcp = false;
            for (String l : out.split("\\R")) {
                String t = l.trim();
                if (t.startsWith("TCP") || t.startsWith("tcp")) { tcp = true; if (t.endsWith("TIME_WAIT")) n++; }
            }
            return tcp ? Long.toString(n) : "n/a";
        } catch (Throwable e) {
            return "n/a";
        }
    }

    /** The dynamic (ephemeral) TCP port range as text, or null when it cannot be found. */
    static String dynamicPortRange() {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
                String[] r = readProc("/proc/sys/net/ipv4/ip_local_port_range").trim().split("\\s+");
                return r[0] + "-" + r[1] + " (" + (Integer.parseInt(r[1]) - Integer.parseInt(r[0]) + 1) + " ports)";
            }
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                String out = run(5, "netsh", "int", "ipv4", "show", "dynamicport", "tcp");
                if (out == null) return null;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(":\\s*(\\d+)").matcher(out);
                if (!m.find()) return null;
                int start = Integer.parseInt(m.group(1));
                if (!m.find()) return null;
                int num = Integer.parseInt(m.group(1));
                return start + "-" + (start + num - 1) + " (" + num + " ports)";
            }
        } catch (Throwable e) { }
        return null;
    }

    public static void main(String[] a) throws Exception {
        Object proxy = ServerMain.read(Format.DER, a[0] + "/DER.TLS.proxy");
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("SslEndpoint\\[[^:]*:(\\d+)\\]").matcher(java.lang.reflect.Proxy.getInvocationHandler(proxy).toString());
        if (!pm.find()) throw new IllegalStateException("no TLS port in the proxy");
        int port = Integer.parseInt(pm.group(1)), threads = Integer.parseInt(a[1]), seconds = Integer.parseInt(a[2]);
        boolean full = a[3].equals("full");
        int warmup = a.length > 4 ? Integer.parseInt(a[4]) : 3;
        Path dir = Path.of(System.getProperty("bench.tls.dir"));
        final InetAddress[] dst = hosts("bench.connect.hosts", "127.0.0.1"), src = hosts("bench.bind.hosts", null);
        if (dst.length == 0) throw new IllegalArgumentException("bench.connect.hosts is empty");
        checkBindable(src);
        AtomicBoolean stop = new AtomicBoolean(), measuring = new AtomicBoolean();
        AtomicLong ok = new AtomicLong(), failed = new AtomicLong(), resumedCount = new AtomicLong(), portFailed = new AtomicLong();
        Map<String, AtomicLong> errors = new java.util.concurrent.ConcurrentHashMap<>();
        List<long[]> lat = Collections.synchronizedList(new ArrayList<>());
        if (System.getProperty("bench.connect.hosts") != null || System.getProperty("bench.bind.hosts") != null)
            System.out.println("connect hosts: " + Arrays.stream(dst).map(InetAddress::getHostAddress).toList()
                + "; bind hosts: " + (src.length == 0 ? "none" : Arrays.stream(src).map(InetAddress::getHostAddress).toList().toString()));
        String range = dynamicPortRange();
        if (range != null) System.out.println("dynamic port range (tcp): " + range);
        System.out.println("TIME_WAIT before: " + timeWait());
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                try {
                    SSLContext ctx = context(dir);
                    SSLSocketFactory f = ctx.getSocketFactory();
                    long[] mine = new long[1 << 16]; int k = 0; long attempt = id;
                    while (!stop.get()) {
                        if (full) { var sc = ctx.getClientSessionContext(); for (var en = sc.getIds(); en.hasMoreElements();) { SSLSession s = sc.getSession(en.nextElement()); if (s != null) s.invalidate(); } }
                        long t0 = System.nanoTime();
                        try (SSLSocket s = (SSLSocket) f.createSocket()) {
                            bindAndConnect(s, dst, src, attempt++, port);
                            s.setSoTimeout(30000); s.setTcpNoDelay(true);
                            s.setEnabledProtocols(new String[] { "TLSv1.3" });
                            s.startHandshake();
                            OutputStream out = s.getOutputStream(); out.write(HEADER); out.flush();
                            InputStream in = s.getInputStream(); byte[] b = new byte[8]; int got = 0;
                            while (got < 8) { int n = in.read(b, got, 8 - got); if (n < 0) throw new java.io.EOFException("closed before the mux header"); got += n; }
                            long dt = System.nanoTime() - t0;
                            if (measuring.get()) { ok.incrementAndGet(); if (k < mine.length) mine[k++] = dt; }
                        } catch (Exception e) {
                            if (measuring.get()) { failed.incrementAndGet(); if (isPortExhaustion(e)) portFailed.incrementAndGet(); errors.computeIfAbsent(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replaceAll("[0-9]+", "#"), x -> new AtomicLong()).incrementAndGet(); }
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
        System.out.println("TIME_WAIT after: " + timeWait());
        long attempts = ok.get() + failed.get(), exhausted = portFailed.get();
        boolean invalid = exhausted > 50 || exhausted * 100 > attempts;
        if (invalid) System.out.println("RUN INVALID: client ran out of ephemeral ports (" + exhausted + " of " + attempts
            + " attempts); widen the dynamic port range, wait for TIME_WAIT to drain, or add -Dbench.connect.hosts / -Dbench.bind.hosts");
        System.exit(invalid ? 3 : 0);
    }
}
