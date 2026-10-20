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
 * closes, so killing or losing the parent stops it. A benchmark JVM started with
 * {@code -Dbench.proxy.JOSS=<file>} (or {@code DER}, {@code rmi}) calls that server instead of
 * exporting its own.
 */
public final class ServerMain {

    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        ClassLoader loader = ServerMain.class.getClassLoader();
        Object[] keep = new Object[8];     // strong references: the export tables hold the impls weakly
        int n = 0;
        for (Format f : Format.values()) {
            Exporter exporter = new BasicJeriExporter(
                    TcpServerEndpoint.getInstance("127.0.0.1", 0), f.ilFactory(loader), false, true);
            Echo.Impl impl = new Echo.Impl();
            keep[n++] = impl;
            keep[n++] = exporter;
            publishBytes(new File(dir, f.name() + ".proxy"), f.encode(exporter.export(impl), loader));
        }
        RmiEcho.Impl rmi = new RmiEcho.Impl();
        keep[n++] = rmi;
        publishBytes(new File(dir, "rmi.proxy"), jdkBytes(UnicastRemoteObject.exportObject(rmi, 0)));
        System.out.println("ready");
        System.out.flush();
        while (System.in.read() != -1) {
            // wait for the parent to go away
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
