// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
//
// What TLS 1.3 session resumption does to authentication, in memory (no sockets): client and server SSLEngines from one
// SSLContext each, mutual authentication, trust managers that count and log checkClientTrusted/checkServerTrusted, and a
// client key manager whose credential can be switched. Prints (1) whether the server's trust manager is called on a resumed
// handshake, (2) the creation time of resumed sessions along a chain of resumptions, with a short server session timeout,
// (3) what happens when the server stops trusting the client's certificate, or the client switches to a second certificate,
// after a ticket was issued, (4) whether setEnableSessionCreation(false) before or after the NewSessionTicket stops the client
// storing the ticket, (5) whether SSLSession.invalidate() on either side stops a later resumption.
// Run it twice: as is (stateless tickets, the JDK default) and with -Djdk.tls.server.enableSessionTicketExtension=false
// (the server keeps its sessions in its cache).
//   java [-Djdk.tls.server.enableSessionTicketExtension=false] -cp serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.TlsResumption [-v]
package au.net.zeus.jgdms.bench.serial;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TlsResumption {
    static java.nio.file.Path dir;
    static KeyStore load(String f) throws Exception { KeyStore ks = KeyStore.getInstance("PKCS12"); try (var in = new FileInputStream(dir.resolve(f).toFile())) { ks.load(in, "changeit".toCharArray()); } return ks; }

    /** Makes server.p12 (alias server) and clients.p12 (aliases client1 and client2) with keytool: EC P-256, self-signed. */
    static void makeKeystores() throws Exception {
        dir = java.nio.file.Files.createTempDirectory("tls-resumption");
        String keytool = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String[][] specs = { { "server.p12", "server" }, { "clients.p12", "client1" }, { "clients.p12", "client2" } };
        for (String[] sp : specs) {
            Process pr = new ProcessBuilder(keytool, "-genkeypair", "-alias", sp[1], "-keyalg", "EC", "-groupname", "secp256r1", "-sigalg", "SHA256withECDSA",
                "-dname", "CN=" + sp[1], "-validity", "30", "-storetype", "PKCS12", "-keystore", dir.resolve(sp[0]).toString(), "-storepass", "changeit", "-keypass", "changeit")
                .redirectErrorStream(true).start();
            byte[] out = pr.getInputStream().readAllBytes();
            if (pr.waitFor() != 0) throw new java.io.IOException("keytool failed: " + new String(out));
        }
    }

    /** Counting, logging trust manager; rejects any chain whose leaf subject contains a name in 'reject'. */
    static class CountingTm implements X509TrustManager {
        final String side; final AtomicInteger calls = new AtomicInteger(); final Set<String> reject = Collections.synchronizedSet(new HashSet<>());
        CountingTm(String side) { this.side = side; }
        void check(X509Certificate[] c, String a, String m) throws java.security.cert.CertificateException {
            calls.incrementAndGet();
            String cn = c[0].getSubjectX500Principal().getName();
            if (VERBOSE) System.out.println("      [" + side + " " + m + " called for " + cn + "]");
            for (String r : reject) if (cn.contains(r)) throw new java.security.cert.CertificateException(side + " TM rejects " + cn);
        }
        public void checkClientTrusted(X509Certificate[] c, String a) throws java.security.cert.CertificateException { check(c, a, "checkClientTrusted"); }
        public void checkServerTrusted(X509Certificate[] c, String a) throws java.security.cert.CertificateException { check(c, a, "checkServerTrusted"); }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
    static boolean VERBOSE = false;

    /** Delegating client key manager whose chosen alias can be switched. */
    static class SwitchKm extends X509ExtendedKeyManager {
        final X509KeyManager d; volatile String alias;
        SwitchKm(X509KeyManager d, String a) { this.d = d; this.alias = a; }
        public String[] getClientAliases(String k, Principal[] i) { return new String[] { alias }; }
        public String chooseClientAlias(String[] k, Principal[] i, Socket s) { return alias; }
        public String chooseEngineClientAlias(String[] k, Principal[] i, SSLEngine e) { return alias; }
        public String[] getServerAliases(String k, Principal[] i) { return d.getServerAliases(k, i); }
        public String chooseServerAlias(String k, Principal[] i, Socket s) { return d.chooseServerAlias(k, i, s); }
        public X509Certificate[] getCertificateChain(String a) { return d.getCertificateChain(a); }
        public PrivateKey getPrivateKey(String a) { return d.getPrivateKey(a); }
    }

    static KeyManager[] kms(String file, String alias, SwitchKm[] holder) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); kmf.init(load(file), "changeit".toCharArray());
        X509KeyManager km = (X509KeyManager) kmf.getKeyManagers()[0];
        if (holder != null) { holder[0] = new SwitchKm(km, alias); return new KeyManager[] { holder[0] }; }
        return kmf.getKeyManagers();
    }

    /** One client SSLContext + one server SSLContext, with their trust managers. */
    static class World {
        final SSLContext cctx, sctx; final CountingTm ctm = new CountingTm("client"); CountingTm stm = new CountingTm("server"); final SwitchKm[] ckm = new SwitchKm[1];
        World() throws Exception {
            cctx = SSLContext.getInstance("TLSv1.3"); cctx.init(kms("clients.p12", "client1", ckm), new TrustManager[] { ctm }, null);
            sctx = SSLContext.getInstance("TLSv1.3"); sctx.init(kms("server.p12", "server", null), new TrustManager[] { stm }, null);
        }
        World(World share) throws Exception { // fresh client ctx, same server ctx
            cctx = SSLContext.getInstance("TLSv1.3"); cctx.init(kms("clients.p12", "client1", ckm), new TrustManager[] { ctm }, null);
            sctx = share.sctx; stm = share.stm;
        }
    }

    enum Order { NONE, BEFORE_TICKET, AFTER_TICKET }

    static class Conn {
        SSLEngine c, s; int ctm0, stm0, ctm1, stm1; int s2cHandshakeBytes, s2cPostBytes; boolean resumed; String error;
        long cCreate, sCreate; String serverSeesPrincipal, clientSeesPrincipal; boolean ticketProcessed;
        SSLSession cSession, sSession;
        boolean clientCacheHadEntryAfter;
    }

    static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    static Conn connect(World w, String host, int port, Order order) throws Exception {
        Conn r = new Conn(); r.ctm0 = w.ctm.calls.get(); r.stm0 = w.stm.calls.get();
        SSLEngine c = w.cctx.createSSLEngine(host, port), s = w.sctx.createSSLEngine();
        c.setUseClientMode(true); s.setUseClientMode(false); s.setNeedClientAuth(true);
        for (SSLEngine e : new SSLEngine[] { c, s }) { SSLParameters p = e.getSSLParameters(); p.setProtocols(new String[] { "TLSv1.3" }); e.setSSLParameters(p); }
        r.c = c; r.s = s;
        ByteBuffer c2s = ByteBuffer.allocate(65536), s2c = ByteBuffer.allocate(65536), sink = ByteBuffer.allocate(65536);
        boolean cFin = false, sFin = false;
        try {
            c.beginHandshake(); s.beginHandshake();
            for (int i = 0; i < 100 && !(cFin && sFin); i++) {
                for (int k = 0; k < 2; k++) {
                    SSLEngine x = k == 0 ? c : s; ByteBuffer out = k == 0 ? c2s : s2c, in = k == 0 ? s2c : c2s;
                    for (Runnable t; (t = x.getDelegatedTask()) != null;) t.run();
                    var hs = x.getHandshakeStatus(); SSLEngineResult res = null;
                    if (hs == SSLEngineResult.HandshakeStatus.NEED_WRAP) { int before = out.position(); res = x.wrap(EMPTY, out); if (k == 1) r.s2cHandshakeBytes += out.position() - before; }
                    else if ((hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP || hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) && (in.position() > 0 || hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN)) { in.flip(); res = x.unwrap(in, sink); in.compact(); sink.clear(); }
                    if (res != null && res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                        if (k == 0) { cFin = true; if (order == Order.BEFORE_TICKET) c.setEnableSessionCreation(false); } else sFin = true;
                    }
                }
            }
            if (!(cFin && sFin)) throw new IllegalStateException("handshake did not finish: c=" + c.getHandshakeStatus() + " s=" + s.getHandshakeStatus());
            // server sends its NewSessionTicket(s) now (post handshake), client consumes them
            ByteBuffer app = ByteBuffer.allocate(65536);
            for (int n = 0; n < 4; n++) { int before = s2c.position(); s.wrap(EMPTY, s2c); if (s2c.position() == before) break; }
            r.s2cPostBytes = s2c.position();
            s2c.flip();
            while (s2c.hasRemaining()) { var res = c.unwrap(s2c, app); if (res.getStatus() != SSLEngineResult.Status.OK) break; if (res.bytesConsumed() == 0) break; }
            r.ticketProcessed = true;
            if (order == Order.AFTER_TICKET) c.setEnableSessionCreation(false);
            // a ping/pong to prove the connection works
            ByteBuffer ping = ByteBuffer.wrap("ping".getBytes()); c2s.clear(); c.wrap(ping, c2s); c2s.flip(); ByteBuffer got = ByteBuffer.allocate(1000); s.unwrap(c2s, got);
            if (got.position() != 4) throw new IllegalStateException("ping not delivered");
            r.cSession = c.getSession(); r.sSession = s.getSession();
            r.cCreate = r.cSession.getCreationTime(); r.sCreate = r.sSession.getCreationTime();
            r.serverSeesPrincipal = r.sSession.getPeerPrincipal().getName(); r.clientSeesPrincipal = r.cSession.getPeerPrincipal().getName();
        } catch (Exception e) { r.error = e.toString(); }
        r.ctm1 = w.ctm.calls.get(); r.stm1 = w.stm.calls.get();
        r.resumed = r.error == null && r.ctm1 == r.ctm0 && r.stm1 == r.stm0;
        return r;
    }

    static long T0;
    static String show(String label, Conn r) {
        if (r.error != null) return String.format("%-34s FAILED %s (server TM calls +%d, client TM calls +%d)", label, r.error.length() > 110 ? r.error.substring(0, 110) : r.error, r.stm1 - r.stm0, r.ctm1 - r.ctm0);
        return String.format("%-34s %-8s serverTM +%d clientTM +%d  s->c bytes: handshake %4d + post-handshake tickets %4d  creation(client) T+%5d ms  creation(server) T+%5d ms  server sees %s",
            label, r.resumed ? "RESUMED" : "FULL", r.stm1 - r.stm0, r.ctm1 - r.ctm0, r.s2cHandshakeBytes, r.s2cPostBytes, r.cCreate - T0, r.sCreate - T0, r.serverSeesPrincipal);
    }

    static void sleep(long ms) throws Exception { Thread.sleep(ms); }

    public static void main(String[] a) throws Exception {
        VERBOSE = a.length > 0 && a[0].equals("-v");
        makeKeystores();
        System.out.println("tickets mode: server.enableSessionTicketExtension=" + System.getProperty("jdk.tls.server.enableSessionTicketExtension", "(default)") + " JDK " + System.getProperty("java.version"));
        // warm up classes
        { World w = new World(); connect(w, "h", 1, Order.NONE); }

        System.out.println("\n== C1/C2: chain of 6 connections, same client, 150 ms apart ==");
        {
            World w = new World(); T0 = System.currentTimeMillis();
            Conn first = null;
            for (int i = 0; i < 6; i++) {
                Conn r = connect(w, "srv", 4433, Order.BEFORE_TICKET); if (first == null) first = r;
                System.out.println(show("connection " + (i + 1), r));
                System.out.println("      session creation time == first session's? client " + (r.cCreate == first.cCreate) + ", server " + (r.sCreate == first.sCreate) + "; lastAccessed(server) T+" + (r.sSession.getLastAccessedTime() - T0) + "; server cache ids " + Collections.list(w.sctx.getServerSessionContext().getIds()).size() + " client cache ids " + Collections.list(w.cctx.getClientSessionContext().getIds()).size());
                sleep(150);
            }
        }

        System.out.println("\n== C2b: server session timeout 3 s; connect every 1 s for 9 s; then idle 4 s ==");
        {
            World w = new World(); w.sctx.getServerSessionContext().setSessionTimeout(3); T0 = System.currentTimeMillis(); Conn first = null;
            for (int i = 0; i < 10; i++) {
                Conn r = connect(w, "srv", 4433, Order.BEFORE_TICKET); if (first == null) first = r;
                System.out.println(show("t=" + (System.currentTimeMillis() - T0) + " ms", r) + "  age of original session " + (System.currentTimeMillis() - first.sCreate) + " ms");
                sleep(1000);
            }
            sleep(4500);
            Conn r = connect(w, "srv", 4433, Order.BEFORE_TICKET); System.out.println(show("after 4.5 s idle (> timeout)", r));
        }

        System.out.println("\n== C3: server stops trusting client1 after the first connection ==");
        {
            World w = new World(); T0 = System.currentTimeMillis();
            System.out.println(show("1 client1, full", connect(w, "srv", 4433, Order.BEFORE_TICKET)));
            w.stm.reject.add("CN=client1");
            System.out.println("   (server TM now rejects CN=client1)");
            System.out.println(show("2 client1 reconnects, same ctx", connect(w, "srv", 4433, Order.BEFORE_TICKET)));
            System.out.println(show("3 client1 reconnects again", connect(w, "srv", 4433, Order.BEFORE_TICKET)));
            World fresh = new World(w);
            System.out.println(show("4 client1, FRESH client ctx (no ticket)", connect(fresh, "srv", 4433, Order.BEFORE_TICKET)));
            System.out.println(show("5 client1, same ctx, other peerHost", connect(w, "srv-other", 4433, Order.BEFORE_TICKET)));
            // the client rotates its credential to client2 inside the same SSLContext (same session cache)
            w.stm.reject.clear(); w.stm.reject.add("CN=client1");
            w.ckm[0].alias = "client2";
            System.out.println("   (client key manager now offers client2; server TM still rejects client1)");
            Conn r6 = connect(w, "srv", 4433, Order.BEFORE_TICKET);
            System.out.println(show("6 client2 key, old ticket, same ctx", r6) + "  client local cert CN=" + (r6.cSession.getLocalPrincipal()) + " server sees " + r6.sSession.getPeerPrincipal());
            Conn r7 = connect(w, "srv-new", 4433, Order.BEFORE_TICKET);
            System.out.println(show("7 client2 key, new peerHost (full)", r7) + "  server sees " + r7.sSession.getPeerPrincipal());
        }

        System.out.println("\n== C4: setEnableSessionCreation(false) on the client, order vs NewSessionTicket ==");
        for (Order o : Order.values()) {
            World w = new World(); T0 = System.currentTimeMillis();
            Conn r1 = connect(w, "srv", 4433, o);
            Conn r2 = connect(w, "srv", 4433, o);
            Conn r3 = connect(w, "srv", 4433, o);
            System.out.println("order " + o + ": conn1 " + (r1.resumed ? "RESUMED" : "FULL") + ", conn2 " + (r2.resumed ? "RESUMED" : "FULL") + ", conn3 " + (r3.resumed ? "RESUMED" : "FULL") + "; client cache ids " + Collections.list(w.cctx.getClientSessionContext().getIds()).size() + "; engine.getEnableSessionCreation(conn2 client)=" + r2.c.getEnableSessionCreation());
        }
        {   // set false BEFORE the handshake on the very first connection to a peer
            World w = new World(); SSLEngine c = w.cctx.createSSLEngine("srv", 4433); c.setUseClientMode(true); c.setEnableSessionCreation(false);
            SSLEngine s = w.sctx.createSSLEngine(); s.setUseClientMode(false); s.setNeedClientAuth(true);
            ByteBuffer o = ByteBuffer.allocate(65536); String res;
            try { c.beginHandshake(); c.wrap(EMPTY, o); res = "ClientHello produced (" + o.position() + " bytes)"; } catch (Exception e) { res = e.toString(); }
            System.out.println("setEnableSessionCreation(false) before the first handshake of a new engine, no cached session: " + res);
        }

        System.out.println("\n== C5: SSLSession.invalidate() ==");
        for (String variant : new String[] { "client invalidates original session", "server invalidates original session", "client invalidates resumed session (2nd conn), then 3rd", "server invalidates resumed session (2nd conn), then 3rd", "client context: invalidate all sessions in cache", "server context: invalidate all sessions in cache (getIds)" }) {
            World w = new World(); T0 = System.currentTimeMillis();
            Conn r1 = connect(w, "srv", 4433, Order.BEFORE_TICKET);
            int sIds = Collections.list(w.sctx.getServerSessionContext().getIds()).size(), cIds = Collections.list(w.cctx.getClientSessionContext().getIds()).size();
            Conn r2;
            if (variant.startsWith("client invalidates original")) { r1.cSession.invalidate(); r2 = connect(w, "srv", 4433, Order.BEFORE_TICKET); }
            else if (variant.startsWith("server invalidates original")) { r1.sSession.invalidate(); r2 = connect(w, "srv", 4433, Order.BEFORE_TICKET); }
            else if (variant.startsWith("client context")) { var ctx = w.cctx.getClientSessionContext(); for (var en = ctx.getIds(); en.hasMoreElements();) { SSLSession s = ctx.getSession(en.nextElement()); if (s != null) s.invalidate(); } r2 = connect(w, "srv", 4433, Order.BEFORE_TICKET); }
            else if (variant.startsWith("server context")) { var ctx = w.sctx.getServerSessionContext(); int n = 0; for (var en = ctx.getIds(); en.hasMoreElements();) { SSLSession s = ctx.getSession(en.nextElement()); if (s != null) { s.invalidate(); n++; } } r2 = connect(w, "srv", 4433, Order.BEFORE_TICKET); variant += " [invalidated " + n + "]"; }
            else {
                r2 = connect(w, "srv", 4433, Order.BEFORE_TICKET); // resumed
                if (variant.startsWith("client")) r2.cSession.invalidate(); else r2.sSession.invalidate();
                Conn r3 = connect(w, "srv", 4433, Order.BEFORE_TICKET);
                System.out.println(String.format("%-62s conn1 FULL, conn2 %s, conn3 (after invalidating conn2's session) %s", variant, r2.resumed ? "RESUMED" : "FULL", r3.resumed ? "RESUMED" : "FULL"));
                continue;
            }
            System.out.println(String.format("%-62s conn1 FULL (server cache ids %d, client cache ids %d), next connection %s", variant, sIds, cIds, r2.resumed ? "RESUMED" : "FULL"));
        }
    }
}
