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

import java.io.File;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.Subject;
import au.net.zeus.rmi.tls.TlsRMIClientSocketFactory;
import au.net.zeus.rmi.tls.TlsRMIServerSocketFactory;
import java.nio.file.StandardCopyOption;
import java.rmi.server.UnicastRemoteObject;
import net.jini.export.Exporter;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;

/**
 * Runs the echo services of {@link JeriBenchmark} and {@link RmiBenchmark} in their own JVM, so
 * a scalability sweep does not share cores or a heap between the callers and the server.
 *
 * <p>Usage: {@code ServerMain <directory>}. It exports one JERI/JOSS, one JERI/DER and one RMI
 * service and writes each proxy or stub, JDK-serialized, to {@code <directory>/JOSS.proxy},
 * {@code DER.proxy} and {@code rmi.proxy} (each written whole before it appears, so a reader that
 * sees the file may read it). It then prints {@code ready} and runs until its standard input
 * closes, so killing or losing the parent stops it ({@code -Dbench.server.keepalive=true} runs it until
 * the process is stopped instead, for a server started by hand in its own window). A benchmark JVM started with
 * {@code -Dbench.proxy.JOSS=<file>} (or {@code DER}, {@code rmi}) calls that server instead of
 * exporting its own.
 */
public final class ServerMain {

    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable t) {
            // the exporters' threads would keep the JVM, and the parent waiting for 'ready', alive
            t.printStackTrace();
            System.out.println("failed: " + t);
            System.out.flush();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        ClassLoader loader = ServerMain.class.getClassLoader();
        List<Object> keep = new ArrayList<>();      // strong references: the export tables hold the impls weakly
        for (Transport t : Transport.values()) {
            if (t == Transport.TLS && System.getProperty("bench.tls.dir") == null) {
                continue;                           // TLS needs the shared credentials of -Dbench.tls.dir
            }
            try {
                for (Format f : Format.values()) {
                    Exporter exporter = new BasicJeriExporter(t.serverEndpoint(),
                            f.ilFactory(loader, t.constraints()), false, true);
                    Echo.Impl impl = new Echo.Impl();
                    keep.add(impl);
                    keep.add(exporter);
                    Object proxy = t.asServer(() -> exporter.export(impl));
                    publishBytes(new File(dir, f.name() + "." + t.name() + ".proxy"), f.encode(proxy, loader));
                }
            } catch (Exception | LinkageError e) {
                // for example UDS on a platform where the endpoint cannot bind: report it, serve the others
                System.err.println("skipping transport " + t + ": " + e);
            }
        }
        RmiEcho.Impl rmi = new RmiEcho.Impl();
        keep.add(rmi);
        publishBytes(new File(dir, "rmi.PLAIN.proxy"), jdkBytes(UnicastRemoteObject.exportObject(rmi, 0)));
        if (System.getProperty("bench.tls.dir") != null) {
            RmiEcho.Impl tlsRmi = new RmiEcho.Impl();
            keep.add(tlsRmi);
            Object stub = Subject.callAs(TlsFixture.current().server, () -> UnicastRemoteObject.exportObject(
                    tlsRmi, 0, new TlsRMIClientSocketFactory(), new TlsRMIServerSocketFactory()));
            publishBytes(new File(dir, "rmi.TLS.proxy"), jdkBytes(stub));
        }
        System.out.println("ready");
        System.out.flush();
        if (Boolean.getBoolean("bench.server.keepalive")) {
            // For a server started by hand in its own window (Windows 'start' can give the new
            // process a standard input that is already closed): run until the process is killed.
            System.out.println("keepalive: running until the process is stopped");
            System.out.flush();
            while (true) {
                Thread.sleep(60_000L);
                java.lang.ref.Reference.reachabilityFence(keep);
            }
        }
        long begin = System.nanoTime();
        while (System.in.read() != -1) {
            // wait for the parent to go away
        }
        if (System.nanoTime() - begin < 2_000_000_000L) {
            // not a parent that went away: the standard input was closed from the start
            System.out.println("standard input is closed, so this server stops at once; start it with"
                    + " -Dbench.server.keepalive=true to keep it running without a parent");
            System.out.flush();
        }
        java.lang.ref.Reference.reachabilityFence(keep);
        System.exit(0);
    }

    private static byte[] jdkBytes(Object o) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    private static void publishBytes(File file, byte[] bytes) throws Exception {
        File tmp = new File(file.getPath() + ".tmp");
        Files.write(tmp.toPath(), bytes);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A JERI proxy written by this class, read back with the marshalling of {@code format}. */
    static Object read(Format format, String path) throws Exception {
        return format.decode(Files.readAllBytes(new File(path).toPath()), ServerMain.class.getClassLoader());
    }

    /** An RMI stub written by this class. */
    static Object readStub(String path) throws Exception {
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(path))) {
            return in.readObject();
        }
    }
}
