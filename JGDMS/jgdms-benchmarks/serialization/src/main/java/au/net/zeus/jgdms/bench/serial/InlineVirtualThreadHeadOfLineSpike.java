// Copyright (C) 2026 the original author or authors.
// Licensed under the Apache License, Version 2.0.
// Written with AI assistance (Claude); see the README.
// Spike, head-of-line check: several connections share one reader; handler parks. Same flags as InlineVirtualThreadSpike.
package au.net.zeus.jgdms.bench.serial;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/** Head-of-line check: C clients on separate connections share ONE reader; handler parks `park` ns. */
public class InlineVirtualThreadHeadOfLineSpike {
    public static void main(String[] a) throws Exception {
        for (long park : new long[] {0, 500_000}) {
            for (String mode : new String[] {"inline", "vt-default", "vt-onreader"}) run(mode, 4, park);
        }
        System.exit(0);
    }

    static void run(String mode, int clients, long park) throws Exception {
        Path dir = Files.createTempDirectory("spike2"); Path p = dir.resolve("s");
        ServerSocketChannel ss = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ss.bind(UnixDomainSocketAddress.of(p));
        Selector sel = Selector.open();
        SocketChannel[] cl = new SocketChannel[clients];
        for (int i = 0; i < clients; i++) {
            cl[i] = SocketChannel.open(StandardProtocolFamily.UNIX); cl[i].connect(UnixDomainSocketAddress.of(p));
            SocketChannel s = ss.accept(); s.configureBlocking(false); s.register(sel, SelectionKey.OP_READ);
        }
        ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        Executor onReader = task -> { queue.add(task); sel.wakeup(); };
        Thread.Builder.OfVirtual b = null;
        if (mode.equals("vt-onreader")) {
            var c = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder").getDeclaredConstructor(Executor.class);
            c.setAccessible(true); b = (Thread.Builder.OfVirtual) c.newInstance(onReader);
        }
        final Thread.Builder.OfVirtual builder = b;
        ByteBuffer in = ByteBuffer.allocateDirect(64);
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    sel.select(); Runnable t;
                    while ((t = queue.poll()) != null) t.run();
                    for (var it = sel.selectedKeys().iterator(); it.hasNext();) {
                        SelectionKey k = it.next(); it.remove();
                        SocketChannel s = (SocketChannel) k.channel(); in.clear();
                        int n = s.read(in); if (n <= 0) continue;
                        Runnable body = () -> { try { if (park > 0) LockSupport.parkNanos(park);
                            ByteBuffer o = ByteBuffer.allocate(1); o.put((byte) 1).flip(); while (o.hasRemaining()) s.write(o); } catch (Exception e) { } };
                        switch (mode) {
                            case "inline" -> body.run();
                            case "vt-default" -> Thread.ofVirtual().start(body);
                            default -> { builder.start(body); while ((t = queue.poll()) != null) t.run(); }
                        }
                    }
                }
            } catch (Exception e) { }
        }, "reader");
        reader.setDaemon(true); reader.start();
        AtomicLong count = new AtomicLong(); AtomicBoolean stop = new AtomicBoolean();
        Thread[] ts = new Thread[clients];
        for (int i = 0; i < clients; i++) { final SocketChannel c = cl[i];
            ts[i] = new Thread(() -> { try { ByteBuffer one = ByteBuffer.allocate(1), back = ByteBuffer.allocate(1);
                while (!stop.get()) { one.clear(); one.put((byte) 7).flip(); while (one.hasRemaining()) c.write(one);
                    back.clear(); while (c.read(back) == 0) { } count.incrementAndGet(); } } catch (Exception e) { } });
            ts[i].start(); }
        Thread.sleep(1500); long c0 = count.get(); long t0 = System.nanoTime(); Thread.sleep(3000);
        long c1 = count.get(); double secs = (System.nanoTime() - t0) / 1e9; stop.set(true);
        System.out.printf("%-12s clients=%d handler park=%4d us: %.1f calls/ms total%n", mode, clients, park / 1000, (c1 - c0) / secs / 1000);
        for (SocketChannel c : cl) c.close();
    }
}
