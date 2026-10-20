// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
// Spike: handler inline on the reader / virtual thread on the default scheduler / virtual thread scheduled on the reader itself.
// Run: java --add-opens java.base/java.lang=ALL-UNNAMED -cp target/serialization-benchmarks.jar au.net.zeus.jgdms.bench.serial.InlineVirtualThreadSpike
package au.net.zeus.jgdms.bench.serial;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * Spike: what does it cost to run a request handler (a) inline on the reader, (b) as a virtual thread on the
 * default scheduler (today), (c) as a virtual thread whose scheduler runs on the reader thread itself?
 * One client thread sends 1 byte over a Unix socket and blocks reading the 1-byte reply; the reader (a platform
 * thread blocked in a Selector) runs the handler. Handler variants: quick (no blocking) and slow (parks 100 us).
 */
public class InlineVirtualThreadSpike {
    static final int N = Integer.getInteger("n", 100_000);

    interface Handler { void handle(SocketChannel ch) throws Exception; }

    public static void main(String[] a) throws Exception {
        for (String mode : new String[] {"inline", "vt-default", "vt-onreader"}) {
            for (boolean slow : new boolean[] {false, true}) {
                run(mode, slow);
            }
        }
        System.exit(0);
    }

    static void run(String mode, boolean slow) throws Exception {
        Path dir = Files.createTempDirectory("spike");
        Path p = dir.resolve("s");
        ServerSocketChannel ss = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ss.bind(UnixDomainSocketAddress.of(p));
        SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX);
        client.connect(UnixDomainSocketAddress.of(p));
        SocketChannel server = ss.accept();
        server.configureBlocking(false);
        Selector sel = Selector.open();
        server.register(sel, SelectionKey.OP_READ);

        // work queue for the on-reader scheduler: tasks produced by any thread, consumed by the reader
        ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        Executor onReader = task -> { queue.add(task); sel.wakeup(); };
        Thread.Builder.OfVirtual onReaderBuilder = null;
        if (mode.equals("vt-onreader")) {
            var c = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder").getDeclaredConstructor(Executor.class);
            c.setAccessible(true);
            onReaderBuilder = (Thread.Builder.OfVirtual) c.newInstance(onReader);
        }
        final Thread.Builder.OfVirtual builder = onReaderBuilder;
        final ByteBuffer reply = ByteBuffer.allocateDirect(1);
        Runnable handlerBody = () -> {
            try {
                if (slow) LockSupport.parkNanos(100_000);
                ByteBuffer out = ByteBuffer.allocate(1); out.put((byte) 1).flip();
                while (out.hasRemaining()) server.write(out);
            } catch (Exception e) { e.printStackTrace(); }
        };
        ByteBuffer in = ByteBuffer.allocateDirect(64);
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    sel.select();
                    Runnable t;
                    while ((t = queue.poll()) != null) t.run();     // continuations scheduled onto this thread
                    if (!sel.selectedKeys().isEmpty()) {
                        sel.selectedKeys().clear();
                        in.clear();
                        int n = server.read(in);
                        if (n < 0) return;
                        if (n > 0) {
                            switch (mode) {
                                case "inline" -> handlerBody.run();
                                case "vt-default" -> Thread.ofVirtual().start(handlerBody);
                                case "vt-onreader" -> {
                                    builder.start(handlerBody);          // execute() queues the continuation
                                    while ((t = queue.poll()) != null) t.run();    // run it right here
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) { if (!(e instanceof ClosedSelectorException)) e.printStackTrace(); }
        }, "reader");
        reader.setDaemon(true); reader.start();

        ByteBuffer one = ByteBuffer.allocate(1), back = ByteBuffer.allocate(1);
        // blocking client
        for (int i = 0; i < N / 5; i++) rt(client, one, back);
        long t0 = System.nanoTime();
        int n = slow ? N / 4 : N;
        for (int i = 0; i < n; i++) rt(client, one, back);
        double us = (System.nanoTime() - t0) / 1e3 / n;
        System.out.printf("%-12s %-5s %.1f us per round trip%n", mode, slow ? "slow" : "quick", us);
        client.close(); server.close(); ss.close(); sel.close();
    }

    static void rt(SocketChannel c, ByteBuffer one, ByteBuffer back) throws Exception {
        one.clear(); one.put((byte) 7).flip();
        while (one.hasRemaining()) c.write(one);
        back.clear();
        while (c.read(back) == 0) { }
    }
}
