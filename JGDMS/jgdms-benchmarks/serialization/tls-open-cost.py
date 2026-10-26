#!/usr/bin/env python3
# Copyright (C) 2026 the original author or authors.
# Licensed under the Apache License, Version 2.0.
#
# Driver for the cost of opening a TLS connection (RESULTS.md, thirty-fourth section). Linux only (/proc). It starts a
# ServerMain JVM with TLS credentials (-Dbench.tls.dir), runs a client JVM against it and stops the server. Two commands:
#
#   open    TlsOpenCost: the first call on a reconnect, resumed (TLS 1.3 PSK) or full, optionally with Java Flight Recorder
#           in both JVMs and a phase breakdown (JfrOpenPhases) from the method traces of the connection set-up:
#     python3 tls-open-cost.py open --mode resumed --samples 100 --warm 15 --sleep 120          # as ConnectionCost
#     python3 tls-open-cost.py open --mode full --jfr trace --samples 100 --warm 3000 --sleep 50 --timeout 10   # "hot" JVM
#     python3 tls-open-cost.py open --mode resumed --jfr sample --samples 150 --warm 3000 --sleep 50 --timeout 10
#     python3 tls-open-cost.py open --mode resumed --both=-Djdk.tls.namedGroups=x25519 --warm 3000 --sleep 50 --timeout 10
#     (--jfr trace2 adds method traces of the JSSE handshake messages and key exchange; --both adds JVM options to both JVMs)
#   accept  TlsAcceptRate: new connections per second a listener accepts from N raw SSLSocket client threads, with the
#           server's CPU by thread group and the kernel's listen-queue overflow counter:
#     python3 tls-open-cost.py accept --mode resumed --threads 1,4,16,64,128 --seconds 10 --warmup 5
#
# Needs the shaded benchmark jar (default target/serialization-benchmarks.jar; --cp takes a class path) and a JDK 25+
# (JAVA_HOME or java on the PATH). --timeout is org.apache.river.jeri.connectionTimeout (the idle reaper's period, ms);
# with a very short one a call can fail when the reaper closes the connection as the call starts: those are discarded.
# Results are printed; the JFR files and logs are kept in --work (default: a new temporary directory).
#
# Written with AI assistance (Claude); see the README.
import argparse, os, re, subprocess, sys, tempfile, time

PKG = "au.net.zeus.jgdms.bench.serial."
CLIENT_TRACE = [
    "net.jini.jeri.ssl.SslEngineChannel::handshake0", "net.jini.jeri.ssl.SslEngineChannel::handshakeWrap",
    "net.jini.jeri.ssl.SslEngineChannel::handshakeUnwrap", "net.jini.jeri.ssl.SslEngineChannel::fillNetIn",
    "net.jini.jeri.ssl.SslEngineChannel::fillNetInByDeadline", "net.jini.jeri.ssl.SslEngineChannel::runDelegatedTasks",
    "net.jini.jeri.ssl.SslEngineChannel::<init>", "net.jini.jeri.ssl.SslEngineChannel::flush",
    "net.jini.jeri.ssl.SslEngineChannel::unwrapMore", "javax.net.ssl.SSLContext::createSSLEngine",
    "sun.security.ssl.SSLEngineImpl::wrap", "sun.security.ssl.SSLEngineImpl::unwrap", "sun.security.ssl.SSLEngineImpl::beginHandshake",
    "sun.security.ssl.SSLEngineImpl::getSupportedCipherSuites", "sun.security.ssl.SSLEngineImpl::getSSLParameters",
    "sun.security.ssl.SSLEngineImpl::setSSLParameters", "java.nio.channels.Selector::open", "sun.nio.ch.SelectorImpl::select",
    "java.nio.channels.spi.AbstractSelectableChannel::configureBlocking", "java.nio.channels.spi.AbstractSelectableChannel::register",
    "sun.nio.ch.SocketChannelImpl::read",
    "net.jini.jeri.ssl.SslEndpointImpl::connect", "net.jini.jeri.ssl.SslConnection::<init>",
    "net.jini.jeri.ssl.Utilities::getClientSSLContextInfo", "net.jini.jeri.ssl.Utilities::releaseClientSSLContextInfo",
    "net.jini.jeri.ssl.ClientAuthManager::checkAuthentication", "net.jini.jeri.ssl.ClientAuthManager::chooseEngineClientAlias",
    "net.jini.jeri.ssl.ClientAuthManager::checkServerTrusted", "net.jini.jeri.ssl.SslConnection::establishCallContext",
    "net.jini.jeri.ssl.SslConnection::connectChannel", "net.jini.jeri.ssl.SslConnection::connectChannelAddress",
    "net.jini.jeri.ssl.SslConnection::establishEngine", "net.jini.jeri.ssl.SslConnection::removeUnsupportedCiphers",
    "net.jini.jeri.connection.ConnectionManager::connect", "net.jini.jeri.connection.ConnectionManager::newOutboundMux",
    "net.jini.jeri.connection.ConnectionManager$OutboundMux::newRequest", "org.apache.river.jeri.internal.mux.Mux::start",
    "org.apache.river.jeri.internal.mux.StreamConnectionIO::start", "java.lang.Thread::start"]
SERVER_TRACE = [t for t in CLIENT_TRACE if not t.startswith(("net.jini.jeri.ssl.SslEndpointImpl", "net.jini.jeri.ssl.SslConnection",
    "net.jini.jeri.ssl.Utilities", "net.jini.jeri.ssl.ClientAuthManager", "net.jini.jeri.connection.ConnectionManager"))] + [
    "net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle::serverConnection", "net.jini.jeri.ssl.SslServerEndpointImpl$SslServerConnection::<init>",
    "net.jini.jeri.ssl.SslServerEndpointImpl$SslServerConnection::getClientSubject", "net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle::handleConnection",
    "net.jini.jeri.ssl.ServerAuthManager::getServerCertificate", "net.jini.jeri.ssl.ServerAuthManager::chooseEngineServerAlias",
    "net.jini.jeri.ssl.ServerAuthManager::chooseServerAlias", "net.jini.jeri.connection.ServerConnectionManager::handleConnection",
    "sun.nio.ch.ServerSocketAdaptor::accept"]
JSSE_TRACE = [
    "sun.security.ec.XDHKeyPairGenerator::generateKeyPair", "sun.security.ec.XDHKeyAgreement::engineDoPhase", "sun.security.ec.XDHKeyAgreement::engineGenerateSecret",
    "sun.security.ssl.Hybrid$KeyPairGeneratorImpl::generateKeyPair", "javax.crypto.KEM$Encapsulator::encapsulate", "javax.crypto.KEM$Decapsulator::decapsulate",
    "sun.security.ssl.KAKeyDerivation::t13DeriveKey", "sun.security.ssl.KAKeyDerivation::encapsulate", "javax.crypto.KDF::deriveKey",
    "sun.security.ssl.SessionTicketExtension$SessionTicketSpec::decrypt", "sun.security.ssl.PreSharedKeyExtension::computeBinder",
    "sun.security.ssl.ClientHello$ClientHelloKickstartProducer::produce", "sun.security.ssl.ServerHello$T13ServerHelloConsumer::consume",
    "sun.security.ssl.EncryptedExtensions$EncryptedExtensionsConsumer::consume", "sun.security.ssl.Finished$T13FinishedConsumer::consume",
    "sun.security.ssl.Finished$T13FinishedProducer::produce", "sun.security.ssl.NewSessionTicket$T13NewSessionTicketConsumer::consume",
    "sun.security.ssl.ClientHello$T13ClientHelloConsumer::consume", "sun.security.ssl.ServerHello$T13ServerHelloProducer::produce",
    "sun.security.ssl.EncryptedExtensions$EncryptedExtensionsProducer::produce", "sun.security.ssl.NewSessionTicket$T13NewSessionTicketProducer::produce",
    "sun.security.ssl.CertificateVerify$T13CertificateVerifyConsumer::consume", "sun.security.ssl.CertificateVerify$T13CertificateVerifyProducer::produce",
    "sun.security.ssl.CertificateMessage$T13CertificateConsumer::consume", "sun.security.ssl.CertificateMessage$T13CertificateProducer::produce",
    "sun.security.ssl.CertificateRequest$T13CertificateRequestProducer::produce"]


def jfr_option(kind, side, path):
    base = "-XX:StartFlightRecording:settings=none,filename=%s,dumponexit=true" % path
    if kind in ("trace", "trace2"):
        filt = (CLIENT_TRACE if side == "client" else SERVER_TRACE) + (JSSE_TRACE if kind == "trace2" else [])
        return base + ",+jdk.MethodTrace#enabled=true,+jdk.MethodTrace#filter=" + ";".join(filt) + ",+jdk.TLSHandshake#enabled=true,+jdk.TLSHandshake#threshold=0ms"
    if kind == "sample":
        s = base + ",+jdk.ExecutionSample#enabled=true,+jdk.ExecutionSample#period=1ms,+jdk.JavaMonitorEnter#enabled=true,+jdk.JavaMonitorEnter#threshold=0ms,+jdk.ThreadPark#enabled=true,+jdk.ThreadPark#threshold=0ms"
        if side == "server":   # the accept thread's connections delimit the windows of the analysis
            s += ",+jdk.MethodTrace#enabled=true,+jdk.MethodTrace#filter=net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle::serverConnection"
        return s
    return None


def java(args):
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", "java") if home else "java"


def start_server(args, work, jfr):
    tls = os.path.join(work, "tls")
    if not os.path.exists(os.path.join(tls, "truststore.p12")):
        subprocess.run([java(args), "-cp", args.cp, PKG + "TlsFixture", "generate", tls], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    log = open(os.path.join(work, "srv.log"), "w")
    cmd = [java(args)] + ([jfr] if jfr else []) + args.both + ["-Dbench.tls.dir=" + tls, "-cp", args.cp, PKG + "ServerMain", os.path.join(work, "srv")]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)   # ServerMain exits when its stdin closes
    for _ in range(120):
        time.sleep(1)
        if "ready" in open(os.path.join(work, "srv.log")).read():
            return p
        if p.poll() is not None:
            break
    sys.exit("server did not start: see " + os.path.join(work, "srv.log"))


def stop_server(p):
    try:
        p.stdin.close()
        p.wait(60)
    except Exception:
        p.kill()


def thread_cpu(pid):
    m = {}
    for t in os.listdir("/proc/%d/task" % pid):
        try:
            name = open("/proc/%d/task/%s/comm" % (pid, t)).read().strip()
            ns = int(open("/proc/%d/task/%s/schedstat" % (pid, t)).read().split()[0])
        except Exception:
            continue
        g = "jit" if re.match(r"C[12] Comp", name) else ("gc" if name.startswith(("G1", "GC")) else re.sub(r"[0-9]+", "#", name))
        m[g] = m.get(g, 0) + ns
    return m


def listen_overflows():
    try:
        lines = [l.split() for l in open("/proc/net/netstat") if l.startswith("TcpExt:")]
        return int(lines[1][lines[0].index("ListenOverflows")])
    except Exception:
        return -1


def cmd_open(args):
    work = args.work or tempfile.mkdtemp(prefix="tls-open-")
    os.makedirs(work, exist_ok=True)
    cjfr = jfr_option(args.jfr, "client", os.path.join(work, "client.jfr")) if args.jfr != "none" else None
    sjfr = jfr_option(args.jfr, "server", os.path.join(work, "server.jfr")) if args.jfr != "none" else None
    srv = start_server(args, work, sjfr)
    try:
        cmd = [java(args)] + ([cjfr] if cjfr else []) + args.both + [
            "-Dbench.tls.dir=" + os.path.join(work, "tls"), "-Dorg.apache.river.jeri.connectionTimeout=%d" % args.timeout,
            "-Dtlsopen.serverpid=%d" % srv.pid, "-cp", args.cp, PKG + "TlsOpenCost", os.path.join(work, "srv"),
            args.mode, str(args.samples), str(args.warm), str(args.sleep)]
        out = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
        print("\n".join(l for l in out.splitlines() if not l.startswith("[") and not l.startswith("first-call us, in order")))
    finally:
        stop_server(srv)
    if args.jfr != "none":
        time.sleep(2)
        r = subprocess.run([java(args), "-cp", args.cp, PKG + "JfrOpenPhases", os.path.join(work, "client.jfr"), os.path.join(work, "server.jfr")], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
        open(os.path.join(work, "phases.txt"), "w").write(r)
        print(r)
    print("files in " + work)


def cmd_accept(args):
    work = args.work or tempfile.mkdtemp(prefix="tls-accept-")
    os.makedirs(work, exist_ok=True)
    sjfr = None
    if args.jfr:
        sjfr = ("-XX:StartFlightRecording:settings=none,filename=%s,dumponexit=true,+jdk.MethodTrace#enabled=true,+jdk.MethodTrace#filter=" % os.path.join(work, "server.jfr")) + ";".join([
            "net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle::serverConnection", "net.jini.jeri.ssl.SslServerEndpointImpl$SslListenHandle::handleConnection",
            "net.jini.jeri.ssl.SslEngineChannel::handshake0", "net.jini.jeri.ssl.SslEngineChannel::fillNetInByDeadline", "net.jini.jeri.ssl.SslEngineChannel::runDelegatedTasks",
            "net.jini.jeri.ssl.SslEngineChannel::handshakeWrap", "sun.nio.ch.ServerSocketAdaptor::accept", "java.nio.channels.Selector::open", "java.lang.Thread::start",
            "org.apache.river.jeri.internal.mux.Mux::start"])
    srv = start_server(args, work, sjfr)
    try:
        for t in args.threads.split(","):
            c0, o0 = thread_cpu(srv.pid), listen_overflows()
            cmd = [java(args)] + args.both + ["-Dbench.tls.dir=" + os.path.join(work, "tls"), "-cp", args.cp, PKG + "TlsAcceptRate",
                   os.path.join(work, "srv"), t, str(args.seconds), args.mode, str(args.warmup)]
            out = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
            c1, o1 = thread_cpu(srv.pid), listen_overflows()
            print("\n".join(l for l in out.splitlines() if not l.startswith("[")))
            wall = args.seconds + args.warmup
            top = sorted(((c1.get(k, 0) - c0.get(k, 0), k) for k in c1), reverse=True)[:5]
            print("   server CPU by thread group, cores over %ds: %s" % (wall, "; ".join("%s %.2f" % (k, v / 1e9 / wall) for v, k in top)))
            print("   kernel ListenOverflows during the run (whole machine): %d" % (o1 - o0))
    finally:
        stop_server(srv)
    if args.jfr:
        time.sleep(2)
        print(subprocess.run([java(args), "-cp", args.cp, PKG + "JfrAcceptThread", os.path.join(work, "server.jfr")], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout)
    print("files in " + work)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("open", "accept"):
        p = sub.add_parser(name)
        p.add_argument("--cp", default="target/serialization-benchmarks.jar")
        p.add_argument("--work", default=None)
        p.add_argument("--mode", choices=["resumed", "full"], default="resumed")
        p.add_argument("--both", action="append", default=[], help="a JVM option for both JVMs (repeatable), e.g. --both=-Djdk.tls.namedGroups=x25519")
        if name == "open":
            p.add_argument("--jfr", choices=["none", "trace", "trace2", "sample"], default="none")
            p.add_argument("--samples", type=int, default=100)
            p.add_argument("--warm", type=int, default=15, help="reconnects before measuring")
            p.add_argument("--sleep", type=int, default=120, help="ms between reconnects")
            p.add_argument("--timeout", type=int, default=30, help="org.apache.river.jeri.connectionTimeout in ms")
        else:
            p.add_argument("--threads", default="1,4,16,64")
            p.add_argument("--seconds", type=int, default=10)
            p.add_argument("--warmup", type=int, default=5)
            p.add_argument("--jfr", action="store_true", help="trace the accept thread in the server and break its time down")
    a = ap.parse_args()
    cmd_open(a) if a.cmd == "open" else cmd_accept(a)


if __name__ == "__main__":
    main()
