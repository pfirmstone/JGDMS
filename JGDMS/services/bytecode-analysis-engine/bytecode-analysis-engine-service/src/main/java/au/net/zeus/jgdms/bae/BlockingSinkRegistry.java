/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bae;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Registry of known-blocking and known-safe native/blocking JVM methods.
 *
 * <p>Method entries are stored as {@code "owner/name/descriptor"} triples
 * (internal JVM names, using {@code /} separators), matching the format used
 * by ASM's {@code visitMethodInsn} callback.
 *
 * <p>The registry is used by {@link ClinitBlockingVisitor} during BFS
 * call-graph traversal from {@code <clinit>}: any call-site whose target is in
 * {@link #BLOCKING_SINKS} terminates the traversal with a
 * {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#BLOCKING} result; a
 * call-site whose target is an unregistered native method terminates the
 * traversal with
 * {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#NATIVE_OPACITY}.
 *
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
final class BlockingSinkRegistry {

    private BlockingSinkRegistry() { }

    /**
     * Method call sites that are statically known to block the calling thread.
     *
     * <p>Each entry is {@code "owner/name/descriptor"} using the full JVM
     * descriptor.  Exact string match is used; see {@link #isBlocking}.
     */
    static final Set<String> BLOCKING_SINKS;

    /**
     * Native methods that are known to be non-blocking.
     * The same {@code "owner/name/descriptor"} format applies.
     */
    static final Set<String> SAFE_NATIVES;

    /**
     * Well-known permission-guard call sites: methods that perform a
     * {@code SecurityManager.checkXxx()} check or an explicit
     * {@code AccessController.checkPermission()} before the caller proceeds.
     *
     * <p>In addition to this exact-match set, {@link #isPermissionGuard}
     * applies a prefix check for <em>any</em> method on
     * {@code java/lang/SecurityManager} whose name starts with {@code "check"},
     * so new SM check methods are automatically covered without updating this
     * set.
     */
    static final Set<String> PERMISSION_GUARD_SINKS;

    /**
     * Maps a blocking-sink key ({@code "owner/name/descriptor"}) to the
     * <em>set</em> of permission entries that the JDK (or DirtyChai) checks
     * internally immediately before the blocking operation is performed.
     *
     * <p>Only sinks that carry a <em>direct, per-call</em>
     * {@code SecurityManager.checkXxx()} or DirtyChai permission guard
     * are listed here.  Sinks whose permission check occurred at construction
     * time (e.g. {@code FileInputStream.<init>}) are intentionally excluded
     * because the link between "declaring the permission" and "the blocking
     * call being reachable" is indirect for such cases.
     *
     * <p>Most sinks carry a <em>single</em> guard, so their entry is a
     * singleton set.  Sinks with <em>dual guards</em> — where the JDK selects
     * one of two permission checks depending on the address family (e.g.
     * {@code SocketChannel.connect} uses {@code SM.checkConnect} for
     * {@code InetSocketAddress} and
     * {@code SM.checkPermission(new NetPermission("accessUnixDomainSocket"))}
     * for {@code UnixDomainSocketAddress}) — carry a two-element set.
     * {@link JarAnalyzer} promotes {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#BLOCKING_GUARDED}
     * to {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#BLOCKING_DECLARED}
     * when <em>any</em> entry in the set is declared in
     * {@code META-INF/PERMISSIONS.LIST}.
     *
     * <p><b>Value encoding</b> — each string in the set uses one of:
     * <ul>
     *   <li><em>{@code "className"}</em> — any declaration of the named
     *       permission class in {@code META-INF/PERMISSIONS.LIST} qualifies.
     *       Used for permissions with a single meaning (e.g.
     *       {@code "java.net.SocketPermission"}).</li>
     *   <li><em>{@code "className#action"}</em> — both the class name
     *       <em>and</em> the quoted action string must appear on the same
     *       {@code PERMISSIONS.LIST} line.  Used for broad permission classes
     *       (such as {@code java.lang.RuntimePermission}) where different
     *       action names have unrelated security semantics.</li>
     * </ul>
     *
     * <p>This map is used by {@link JarAnalyzer} to detect the
     * {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#BLOCKING_DECLARED}
     * condition: a blocking I/O path that is guarded by a permission that the
     * JAR itself declares in {@code META-INF/PERMISSIONS.LIST}.
     */
    static final Map<String, Set<String>> SINK_TO_PERMISSION_CLASS;

    static {
        Set<String> blocking = new HashSet<String>(Arrays.asList(
            // ---- Thread ----
            "java/lang/Thread/sleep/(J)V",
            "java/lang/Thread/sleep/(JI)V",
            "java/lang/Thread/join/()V",
            "java/lang/Thread/join/(J)V",
            "java/lang/Thread/join/(JI)V",

            // ---- Object monitor ----
            "java/lang/Object/wait/()V",
            "java/lang/Object/wait/(J)V",
            "java/lang/Object/wait/(JI)V",

            // ---- LockSupport ----
            "java/util/concurrent/locks/LockSupport/park/(Ljava/lang/Object;)V",
            "java/util/concurrent/locks/LockSupport/park/()V",
            "java/util/concurrent/locks/LockSupport/parkNanos/(Ljava/lang/Object;J)V",
            "java/util/concurrent/locks/LockSupport/parkNanos/(J)V",
            "java/util/concurrent/locks/LockSupport/parkUntil/(Ljava/lang/Object;J)V",
            "java/util/concurrent/locks/LockSupport/parkUntil/(J)V",

            // ---- InputStream / OutputStream ----
            "java/io/InputStream/read/()I",
            "java/io/InputStream/read/([B)I",
            "java/io/InputStream/read/([BII)I",
            "java/io/OutputStream/write/(I)V",
            "java/io/OutputStream/write/([B)V",
            "java/io/OutputStream/write/([BII)V",

            // ---- FileInputStream / FileOutputStream ----
            "java/io/FileInputStream/read/()I",
            "java/io/FileInputStream/read/([B)I",
            "java/io/FileInputStream/read/([BII)I",
            "java/io/FileOutputStream/write/(I)V",
            "java/io/FileOutputStream/write/([B)V",
            "java/io/FileOutputStream/write/([BII)V",

            // ---- PipedInputStream / PipedOutputStream ----
            "java/io/PipedInputStream/read/()I",
            "java/io/PipedInputStream/read/([B)I",
            "java/io/PipedInputStream/read/([BII)I",
            "java/io/PipedOutputStream/write/(I)V",
            "java/io/PipedOutputStream/write/([B)V",
            "java/io/PipedOutputStream/write/([BII)V",

            // ---- Reader / Writer ----
            "java/io/Reader/read/()I",
            "java/io/Reader/read/([C)I",
            "java/io/Reader/read/([CII)I",
            "java/io/Writer/write/(I)V",
            "java/io/Writer/write/([C)V",
            "java/io/Writer/write/([CII)V",
            "java/io/Writer/write/(Ljava/lang/String;)V",
            "java/io/Writer/write/(Ljava/lang/String;II)V",

            // ---- RandomAccessFile ----
            "java/io/RandomAccessFile/read/()I",
            "java/io/RandomAccessFile/read/([B)I",
            "java/io/RandomAccessFile/read/([BII)I",
            "java/io/RandomAccessFile/write/(I)V",
            "java/io/RandomAccessFile/write/([B)V",
            "java/io/RandomAccessFile/write/([BII)V",

            // ---- Socket ----
            "java/net/Socket/connect/(Ljava/net/SocketAddress;)V",
            "java/net/Socket/connect/(Ljava/net/SocketAddress;I)V",
            "java/net/ServerSocket/accept/()Ljava/net/Socket;",

            // ---- DatagramSocket ----
            "java/net/DatagramSocket/receive/(Ljava/net/DatagramPacket;)V",

            // ---- DNS resolution (SM.checkConnect(host, -1) → SocketPermission "resolve") ----
            "java/net/InetAddress/getByName/(Ljava/lang/String;)Ljava/net/InetAddress;",
            "java/net/InetAddress/getAllByName/(Ljava/lang/String;)[Ljava/net/InetAddress;",
            "java/net/InetAddress/getLocalHost/()Ljava/net/InetAddress;",

            // ---- Blocking deserialization / URL I/O (no direct per-call SM guard) ----
            "java/io/ObjectInputStream/readObject/()Ljava/lang/Object;",
            "java/net/URL/openStream/()Ljava/io/InputStream;",
            "java/net/URLConnection/getInputStream/()Ljava/io/InputStream;",

            // ---- NIO Selector ----
            "java/nio/channels/Selector/select/()I",
            "java/nio/channels/Selector/select/(J)I",
            "java/nio/channels/Selector/select/(Ljava/util/function/Consumer;)I",
            "java/nio/channels/Selector/select/(Ljava/util/function/Consumer;J)I",
            "java/nio/channels/Selector/selectNow/()I",

            // ---- NIO SocketChannel / ServerSocketChannel ----
            // connect() blocks during TCP handshake; for UnixDomainSocketAddress
            // the JDK additionally calls NetPermission("accessUnixDomainSocket").
            "java/nio/channels/SocketChannel/connect/(Ljava/net/SocketAddress;)Z",
            "java/nio/channels/SocketChannel/open/(Ljava/net/SocketAddress;)Ljava/nio/channels/SocketChannel;",
            "java/nio/channels/SocketChannel/read/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/SocketChannel/read/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/SocketChannel/read/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/SocketChannel/write/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/SocketChannel/write/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/SocketChannel/write/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/ServerSocketChannel/accept/()Ljava/nio/channels/SocketChannel;",

            // ---- NIO DatagramChannel (SM.checkAccept on receive; SM.checkConnect on send) ----
            "java/nio/channels/DatagramChannel/receive/(Ljava/nio/ByteBuffer;)Ljava/net/SocketAddress;",
            "java/nio/channels/DatagramChannel/send/(Ljava/nio/ByteBuffer;Ljava/net/SocketAddress;)I",

            // ---- NIO FileChannel ----
            "java/nio/channels/FileChannel/read/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/FileChannel/read/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/FileChannel/read/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/FileChannel/read/(Ljava/nio/ByteBuffer;J)I",
            "java/nio/channels/FileChannel/write/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/FileChannel/write/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/FileChannel/write/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/FileChannel/write/(Ljava/nio/ByteBuffer;J)I",
            "java/nio/channels/FileChannel/lock/()Ljava/nio/channels/FileLock;",
            "java/nio/channels/FileChannel/lock/(JJZ)Ljava/nio/channels/FileLock;",

            // ---- Process (wait for completion) ----
            "java/lang/Process/waitFor/()I",
            "java/lang/Process/waitFor/(JLjava/util/concurrent/TimeUnit;)Z",

            // ---- Process spawn (SM.checkExec(cmd) → FilePermission(cmd, "execute")) ----
            "java/lang/ProcessBuilder/start/()Ljava/lang/Process;",
            "java/lang/ProcessBuilder/startPipeline/(Ljava/util/List;)Ljava/util/List;",
            "java/lang/Runtime/exec/(Ljava/lang/String;)Ljava/lang/Process;",
            "java/lang/Runtime/exec/([Ljava/lang/String;)Ljava/lang/Process;",
            "java/lang/Runtime/exec/(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
            "java/lang/Runtime/exec/(Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;",
            "java/lang/Runtime/exec/([Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
            "java/lang/Runtime/exec/([Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;",

            // ---- CountDownLatch ----
            "java/util/concurrent/CountDownLatch/await/()V",
            "java/util/concurrent/CountDownLatch/await/(JLjava/util/concurrent/TimeUnit;)Z",

            // ---- Semaphore ----
            "java/util/concurrent/Semaphore/acquire/()V",
            "java/util/concurrent/Semaphore/acquire/(I)V",
            "java/util/concurrent/Semaphore/acquireUninterruptibly/()V",
            "java/util/concurrent/Semaphore/acquireUninterruptibly/(I)V",

            // ---- BlockingQueue family ----
            "java/util/concurrent/BlockingQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/BlockingQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/LinkedBlockingQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/LinkedBlockingQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/ArrayBlockingQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/ArrayBlockingQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/SynchronousQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/SynchronousQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/PriorityBlockingQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/PriorityBlockingQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/DelayQueue/take/()Ljava/lang/Delayed;",
            "java/util/concurrent/DelayQueue/put/(Ljava/lang/Delayed;)V",
            "java/util/concurrent/LinkedTransferQueue/take/()Ljava/lang/Object;",
            "java/util/concurrent/LinkedTransferQueue/put/(Ljava/lang/Object;)V",
            "java/util/concurrent/LinkedTransferQueue/transfer/(Ljava/lang/Object;)V",
            "java/util/concurrent/LinkedTransferQueue/tryTransfer/(Ljava/lang/Object;JLjava/util/concurrent/TimeUnit;)Z",

            // ---- Condition ----
            "java/util/concurrent/locks/Condition/await/()V",
            "java/util/concurrent/locks/Condition/await/(JLjava/util/concurrent/TimeUnit;)Z",
            "java/util/concurrent/locks/Condition/awaitNanos/(J)J",
            "java/util/concurrent/locks/Condition/awaitUninterruptibly/()V",
            "java/util/concurrent/locks/Condition/awaitUntil/(Ljava/util/Date;)Z",

            // ---- ReentrantLock / ReadWriteLock ----
            "java/util/concurrent/locks/ReentrantLock/lock/()V",
            "java/util/concurrent/locks/ReentrantLock/lockInterruptibly/()V",
            "java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock/lock/()V",
            "java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock/lockInterruptibly/()V",
            "java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock/lock/()V",
            "java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock/lockInterruptibly/()V",

            // ---- StampedLock ----
            "java/util/concurrent/locks/StampedLock/readLock/()J",
            "java/util/concurrent/locks/StampedLock/writeLock/()J",
            "java/util/concurrent/locks/StampedLock/readLockInterruptibly/()J",
            "java/util/concurrent/locks/StampedLock/writeLockInterruptibly/()J",

            // ---- Phaser ----
            "java/util/concurrent/Phaser/arriveAndAwaitAdvance/()I",
            "java/util/concurrent/Phaser/awaitAdvance/(I)I",
            "java/util/concurrent/Phaser/awaitAdvanceInterruptibly/(I)I",
            "java/util/concurrent/Phaser/awaitAdvanceInterruptibly/(IJ)I",

            // ---- CyclicBarrier ----
            "java/util/concurrent/CyclicBarrier/await/()I",
            "java/util/concurrent/CyclicBarrier/await/(JLjava/util/concurrent/TimeUnit;)I",

            // ---- Exchanger ----
            "java/util/concurrent/Exchanger/exchange/(Ljava/lang/Object;)Ljava/lang/Object;",
            "java/util/concurrent/Exchanger/exchange/(Ljava/lang/Object;JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",

            // ---- Future / CompletableFuture ----
            "java/util/concurrent/Future/get/()Ljava/lang/Object;",
            "java/util/concurrent/Future/get/(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            "java/util/concurrent/FutureTask/get/()Ljava/lang/Object;",
            "java/util/concurrent/FutureTask/get/(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            "java/util/concurrent/CompletableFuture/get/()Ljava/lang/Object;",
            "java/util/concurrent/CompletableFuture/get/(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            "java/util/concurrent/CompletableFuture/join/()Ljava/lang/Object;",

            // ---- DirtyChai: sun.nio.ch.Poller ----
            // These static methods park the calling thread (a virtual thread running
            // this <clinit> would have its carrier pinned if called inside a
            // synchronized block; either way the call blocks without any
            // SecurityManager guard).
            "sun/nio/ch/Poller/poll/(IIJLjava/util/function/BooleanSupplier;)V",
            "sun/nio/ch/Poller/pollSelector/(IJ)V",

            // ---- Native library loading (file I/O — can block on slow/network FS) ----
            // Guarded by NativeInvocationPermission in DirtyChai; also matched by
            // the standard JDK's SecurityManager.checkLink() for BLOCKING_GUARDED.
            "java/lang/System/loadLibrary/(Ljava/lang/String;)V",
            "java/lang/System/load/(Ljava/lang/String;)V",
            "java/lang/Runtime/loadLibrary/(Ljava/lang/String;)V",
            "java/lang/Runtime/load/(Ljava/lang/String;)V",

            // ---- FFM: SymbolLookup.libraryLookup (native library file I/O) ----
            // Guarded by NativeInvocationPermission in DirtyChai.
            "java/lang/foreign/SymbolLookup/libraryLookup/(Ljava/lang/String;Ljava/lang/foreign/Arena;)Ljava/lang/foreign/SymbolLookup;",
            "java/lang/foreign/SymbolLookup/libraryLookup/(Ljava/nio/file/Path;Ljava/lang/foreign/Arena;)Ljava/lang/foreign/SymbolLookup;",

            // ---- FFM: Arena factory methods (OS off-heap memory allocation) ----
            // mmap/malloc with large allocations, huge pages, or NUMA pinning can
            // block the calling thread.  All four are guarded by
            // NativeMemoryPermission in DirtyChai.
            "java/lang/foreign/Arena/global/()Ljava/lang/foreign/Arena;",
            "java/lang/foreign/Arena/ofShared/()Ljava/lang/foreign/Arena;",
            "java/lang/foreign/Arena/ofConfined/()Ljava/lang/foreign/Arena;",
            "java/lang/foreign/Arena/ofAuto/()Ljava/lang/foreign/Arena;",

            // ---- DirtyChai: ThreadBuilders (platform / virtual thread creation) ----
            // Creating a platform thread involves OS-level resource allocation
            // (pthread_create or equivalent) which can block under thread-count
            // pressure.  Both unstarted() and factory() are guarded by
            // RuntimePermission("createPlatformThread") /
            // RuntimePermission("createVirtualThread") in DirtyChai.
            "jdk/internal/misc/ThreadBuilders$PlatformThreadBuilder/unstarted/(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            "jdk/internal/misc/ThreadBuilders$PlatformThreadBuilder/factory/()Ljava/util/concurrent/ThreadFactory;",
            "jdk/internal/misc/ThreadBuilders$VirtualThreadBuilder/unstarted/(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            "jdk/internal/misc/ThreadBuilders$VirtualThreadBuilder/factory/()Ljava/util/concurrent/ThreadFactory;"
        ));
        BLOCKING_SINKS = Collections.unmodifiableSet(blocking);

        // Known-safe natives: these are used frequently but never block.
        Set<String> safe = new HashSet<String>(Arrays.asList(
            "java/lang/Object/hashCode/()I",
            "java/lang/System/arraycopy/(Ljava/lang/Object;ILjava/lang/Object;II)V",
            "java/lang/System/nanoTime/()J",
            "java/lang/System/currentTimeMillis/()J",
            "java/lang/System/identityHashCode/(Ljava/lang/Object;)I",
            "java/lang/Thread/currentThread/()Ljava/lang/Thread;",
            "java/lang/Class/isAssignableFrom/(Ljava/lang/Class;)Z",
            "java/lang/Class/isInstance/(Ljava/lang/Object;)Z",
            "java/lang/Class/isPrimitive/()Z",
            "java/lang/Class/isInterface/()Z",
            "java/lang/Class/isArray/()Z",
            "java/lang/Math/sqrt/(D)D",
            "java/lang/Math/floor/(D)D",
            "java/lang/Math/ceil/(D)D",
            "java/lang/Math/abs/(I)I",
            "java/lang/Math/abs/(J)J",
            "java/lang/Math/abs/(F)F",
            "java/lang/Math/abs/(D)D",
            "java/lang/Math/min/(II)I",
            "java/lang/Math/max/(II)I",
            "java/lang/Float/floatToRawIntBits/(F)I",
            "java/lang/Float/intBitsToFloat/(I)F",
            "java/lang/Double/doubleToRawLongBits/(D)J",
            "java/lang/Double/longBitsToDouble/(J)D",
            "java/lang/String/intern/()Ljava/lang/String;"
        ));
        SAFE_NATIVES = Collections.unmodifiableSet(safe);

        // Permission-guard sinks: explicit checkPermission calls.
        // SecurityManager.check* methods are matched by prefix in
        // isPermissionGuard(), so only AccessController needs to be listed here.
        Set<String> guards = new HashSet<String>(Arrays.asList(
            "java/security/AccessController/checkPermission/(Ljava/security/Permission;)V"
        ));
        PERMISSION_GUARD_SINKS = Collections.unmodifiableSet(guards);

        // Blocking sinks that carry a direct, per-call JDK-internal or
        // DirtyChai permission guard immediately before the blocking operation.
        // Maps the full "owner/name/descriptor" key to the set of permission
        // entries that guard it.  Most sinks have one guard (singleton set).
        // Dual-guard sinks (address-family-dependent JDK checks) carry two
        // entries; JarAnalyzer promotes to BLOCKING_DECLARED when ANY entry
        // in the set is declared in PERMISSIONS.LIST.
        Map<String, Set<String>> sinkPerms = new HashMap<String, Set<String>>();
        // --- Network: Socket / ServerSocket (SM.checkConnect / checkAccept) ---
        sinkPerms.put("java/net/Socket/connect/(Ljava/net/SocketAddress;)V",
                singleton("java.net.SocketPermission"));
        sinkPerms.put("java/net/Socket/connect/(Ljava/net/SocketAddress;I)V",
                singleton("java.net.SocketPermission"));
        sinkPerms.put("java/net/ServerSocket/accept/()Ljava/net/Socket;",
                singleton("java.net.SocketPermission"));
        sinkPerms.put("java/net/DatagramSocket/receive/(Ljava/net/DatagramPacket;)V",
                singleton("java.net.SocketPermission"));
        // --- NIO: ServerSocketChannel ---
        // InetSocketAddress path: SM.checkAccept → SocketPermission.
        // UnixDomainSocketAddress path: SM.checkPermission(new
        //   NetPermission("accessUnixDomainSocket")) → NetPermission.
        // Dual-guard: either permission declaration promotes to BLOCKING_DECLARED.
        sinkPerms.put(
                "java/nio/channels/ServerSocketChannel/accept/()Ljava/nio/channels/SocketChannel;",
                dual("java.net.SocketPermission",
                     "java.net.NetPermission#accessUnixDomainSocket"));
        // --- NIO: FileChannel.lock (SM.checkWrite / checkRead inside lock()) ---
        sinkPerms.put("java/nio/channels/FileChannel/lock/()Ljava/nio/channels/FileLock;",
                singleton("java.io.FilePermission"));
        sinkPerms.put("java/nio/channels/FileChannel/lock/(JJZ)Ljava/nio/channels/FileLock;",
                singleton("java.io.FilePermission"));

        // --- NIO: SocketChannel.connect / open(SocketAddress) ---
        // InetSocketAddress path: SM.checkConnect(host, port) → SocketPermission.
        // UnixDomainSocketAddress path: SM.checkPermission(new
        //   NetPermission("accessUnixDomainSocket")) → NetPermission.
        // Dual-guard: either permission declaration promotes to BLOCKING_DECLARED.
        sinkPerms.put("java/nio/channels/SocketChannel/connect/(Ljava/net/SocketAddress;)Z",
                dual("java.net.SocketPermission",
                     "java.net.NetPermission#accessUnixDomainSocket"));
        sinkPerms.put(
                "java/nio/channels/SocketChannel/open/(Ljava/net/SocketAddress;)Ljava/nio/channels/SocketChannel;",
                dual("java.net.SocketPermission",
                     "java.net.NetPermission#accessUnixDomainSocket"));

        // --- NIO: DatagramChannel (SM.checkAccept on receive; SM.checkConnect on send) ---
        sinkPerms.put(
                "java/nio/channels/DatagramChannel/receive/(Ljava/nio/ByteBuffer;)Ljava/net/SocketAddress;",
                singleton("java.net.SocketPermission"));
        sinkPerms.put(
                "java/nio/channels/DatagramChannel/send/(Ljava/nio/ByteBuffer;Ljava/net/SocketAddress;)I",
                singleton("java.net.SocketPermission"));

        // --- DNS resolution: InetAddress (SM.checkConnect(host, -1) → SocketPermission "resolve") ---
        sinkPerms.put("java/net/InetAddress/getByName/(Ljava/lang/String;)Ljava/net/InetAddress;",
                singleton("java.net.SocketPermission"));
        sinkPerms.put("java/net/InetAddress/getAllByName/(Ljava/lang/String;)[Ljava/net/InetAddress;",
                singleton("java.net.SocketPermission"));
        sinkPerms.put("java/net/InetAddress/getLocalHost/()Ljava/net/InetAddress;",
                singleton("java.net.SocketPermission"));

        // --- Process spawn: SM.checkExec(cmd) → FilePermission(cmd, "execute") ---
        // "className#action" encoding: both "java.io.FilePermission" and the
        // quoted action "execute" must appear on the same PERMISSIONS.LIST line.
        // This prevents false promotion from JARs that declare FilePermission
        // with only "read" or "write" actions (unrelated to process execution).
        sinkPerms.put("java/lang/ProcessBuilder/start/()Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/ProcessBuilder/startPipeline/(Ljava/util/List;)Ljava/util/List;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/(Ljava/lang/String;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/([Ljava/lang/String;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/(Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/([Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));
        sinkPerms.put("java/lang/Runtime/exec/([Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Ljava/lang/Process;",
                singleton("java.io.FilePermission#execute"));

        // --- Native library loading: DirtyChai NativeInvocationPermission ---
        // Standard JDK's SM.checkLink() also guards these; DirtyChai adds
        // NativeInvocationPermission on top.
        sinkPerms.put("java/lang/System/loadLibrary/(Ljava/lang/String;)V",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));
        sinkPerms.put("java/lang/System/load/(Ljava/lang/String;)V",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));
        sinkPerms.put("java/lang/Runtime/loadLibrary/(Ljava/lang/String;)V",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));
        sinkPerms.put("java/lang/Runtime/load/(Ljava/lang/String;)V",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));
        // FFM SymbolLookup.libraryLookup also loads a native library from disk.
        sinkPerms.put(
                "java/lang/foreign/SymbolLookup/libraryLookup/(Ljava/lang/String;Ljava/lang/foreign/Arena;)Ljava/lang/foreign/SymbolLookup;",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));
        sinkPerms.put(
                "java/lang/foreign/SymbolLookup/libraryLookup/(Ljava/nio/file/Path;Ljava/lang/foreign/Arena;)Ljava/lang/foreign/SymbolLookup;",
                singleton("au.zeus.jdk.authorization.guards.NativeInvocationPermission"));

        // --- FFM Arena factories: DirtyChai NativeMemoryPermission ---
        sinkPerms.put("java/lang/foreign/Arena/global/()Ljava/lang/foreign/Arena;",
                singleton("au.zeus.jdk.authorization.guards.NativeMemoryPermission"));
        sinkPerms.put("java/lang/foreign/Arena/ofShared/()Ljava/lang/foreign/Arena;",
                singleton("au.zeus.jdk.authorization.guards.NativeMemoryPermission"));
        sinkPerms.put("java/lang/foreign/Arena/ofConfined/()Ljava/lang/foreign/Arena;",
                singleton("au.zeus.jdk.authorization.guards.NativeMemoryPermission"));
        sinkPerms.put("java/lang/foreign/Arena/ofAuto/()Ljava/lang/foreign/Arena;",
                singleton("au.zeus.jdk.authorization.guards.NativeMemoryPermission"));

        // --- DirtyChai ThreadBuilders: RuntimePermission (class#action format) ---
        // "className#action" encoding: declaresPermissionClass() requires both
        // "java.lang.RuntimePermission" AND the quoted action to appear on the
        // same PERMISSIONS.LIST line, preventing false positives from unrelated
        // RuntimePermission grants (e.g. "getenv", "shutdownHooks").
        sinkPerms.put(
                "jdk/internal/misc/ThreadBuilders$PlatformThreadBuilder/unstarted/(Ljava/lang/Runnable;)Ljava/lang/Thread;",
                singleton("java.lang.RuntimePermission#createPlatformThread"));
        sinkPerms.put(
                "jdk/internal/misc/ThreadBuilders$PlatformThreadBuilder/factory/()Ljava/util/concurrent/ThreadFactory;",
                singleton("java.lang.RuntimePermission#createPlatformThread"));
        sinkPerms.put(
                "jdk/internal/misc/ThreadBuilders$VirtualThreadBuilder/unstarted/(Ljava/lang/Runnable;)Ljava/lang/Thread;",
                singleton("java.lang.RuntimePermission#createVirtualThread"));
        sinkPerms.put(
                "jdk/internal/misc/ThreadBuilders$VirtualThreadBuilder/factory/()Ljava/util/concurrent/ThreadFactory;",
                singleton("java.lang.RuntimePermission#createVirtualThread"));
        SINK_TO_PERMISSION_CLASS = Collections.unmodifiableMap(sinkPerms);
    }

    /**
     * Returns {@code true} if the given call site is a known blocking sink.
     *
     * <p>The lookup is exact: the entry must be present in
     * {@link #BLOCKING_SINKS} as {@code "owner/name/descriptor"}.
     *
     * @param owner      the internal class name of the called method's owner
     * @param name       the method name
     * @param descriptor the method descriptor
     * @return {@code true} if the call site is a known blocker
     */
    static boolean isBlocking(String owner, String name, String descriptor) {
        return BLOCKING_SINKS.contains(owner + "/" + name + "/" + descriptor);
    }

    /**
     * Returns {@code true} if the given native method is in the known-safe set.
     *
     * @param owner      the internal class name
     * @param name       the method name
     * @param descriptor the method descriptor
     * @return {@code true} if the native is known to be non-blocking
     */
    static boolean isSafeNative(String owner, String name, String descriptor) {
        return SAFE_NATIVES.contains(owner + "/" + name + "/" + descriptor);
    }

    /**
     * Returns the set of permission entries that the JDK (or DirtyChai) checks
     * internally immediately before executing the blocking operation identified
     * by {@code sinkKey}, or {@code null} if the sink carries no such per-call
     * guard.
     *
     * <p>Most sinks return a singleton set.  Sinks with dual guards (e.g.
     * {@code SocketChannel.connect}, which checks {@code SocketPermission} for
     * TCP addresses and {@code NetPermission("accessUnixDomainSocket")} for
     * Unix-domain addresses) return a two-element set.
     *
     * <p>Each string in the returned set uses one of two encodings:
     * <ul>
     *   <li><em>{@code "className"}</em> — any declaration of that permission
     *       class in {@code META-INF/PERMISSIONS.LIST} qualifies (e.g.
     *       {@code "java.net.SocketPermission"}).</li>
     *   <li><em>{@code "className#action"}</em> — both the class name
     *       <em>and</em> the quoted action string must appear on the same
     *       {@code PERMISSIONS.LIST} line (e.g.
     *       {@code "java.lang.RuntimePermission#createVirtualThread"}).
     *       This prevents false positives from unrelated grants of the same
     *       broad permission class.</li>
     * </ul>
     *
     * <p>This is used by {@link JarAnalyzer} to detect the
     * {@link au.net.zeus.jgdms.api.codebase.ClinitVerdict#BLOCKING_DECLARED}
     * condition: the verdict is promoted when <em>any</em> entry in the
     * returned set is declared in {@code META-INF/PERMISSIONS.LIST}.
     *
     * @param sinkKey the composite key {@code "owner/name/descriptor"}
     * @return the set of permission entries (see encoding above),
     *         or {@code null} if no guard is mapped
     */
    static Set<String> getRequiredPermissionClasses(String sinkKey) {
        return SINK_TO_PERMISSION_CLASS.get(sinkKey);
    }

    /** Builds an immutable singleton permission-guard set. */
    private static Set<String> singleton(String permEntry) {
        return Collections.singleton(permEntry);
    }

    /** Builds an immutable two-element permission-guard set (dual-guard sinks). */
    private static Set<String> dual(String first, String second) {
        Set<String> s = new LinkedHashSet<String>(4);
        s.add(first);
        s.add(second);
        return Collections.unmodifiableSet(s);
    }

    /**
     * Returns {@code true} if the given call site constitutes a
     * <em>permission guard</em> — a check that, if failed, throws
     * {@code SecurityException} before any blocking operation can be reached.
     *
     * <p>Two categories are recognised:
     * <ol>
     *   <li>Any method on {@code java.lang.SecurityManager} whose name starts
     *       with {@code "check"} (e.g. {@code checkPermission},
     *       {@code checkConnect}, {@code checkRead}, {@code checkAccess},
     *       …).</li>
     *   <li>{@code java.security.AccessController.checkPermission(Permission)}
     *       listed explicitly in {@link #PERMISSION_GUARD_SINKS}.</li>
     * </ol>
     *
     * <p>Note: {@code AccessController.doPrivileged()} is intentionally
     * <em>not</em> treated as a permission guard — it elevates privilege but
     * does not prevent the call from proceeding.
     *
     * @param owner      the internal class name of the called method's owner
     * @param name       the method name
     * @param descriptor the method descriptor (unused for the prefix match)
     * @return {@code true} if the call site is a permission guard
     */
    static boolean isPermissionGuard(String owner, String name, String descriptor) {
        // Any SecurityManager.check* method
        if ("java/lang/SecurityManager".equals(owner) && name.startsWith("check")) {
            return true;
        }
        // Explicit AccessController.checkPermission
        return PERMISSION_GUARD_SINKS.contains(owner + "/" + name + "/" + descriptor);
    }

    /**
     * Returns {@code true} if the given <em>full callee key</em>
     * ({@code "owner/name/descriptor"}) constitutes a permission guard.
     *
     * <p>This variant is used internally when the key has not yet been
     * decomposed.  Because method descriptors can themselves contain {@code /}
     * characters (for object-type parameters), decomposition is performed by
     * locating the {@code "/("}  boundary that separates the {@code name}
     * component from the descriptor.
     *
     * @param calleeKey the composite key {@code "owner/name/descriptor"}
     * @return {@code true} if the call site is a permission guard
     */
    static boolean isPermissionGuardKey(String calleeKey) {
        // Method descriptors always start with '(', so the separator between
        // "name" and "descriptor" in the key is the last "/(" sequence.
        // Using lastIndexOf handles any edge-cases in malformed keys.
        int descSlash = calleeKey.lastIndexOf("/(");
        if (descSlash < 0) return false;
        String ownerAndName = calleeKey.substring(0, descSlash);
        int lastSep = ownerAndName.lastIndexOf('/');
        if (lastSep < 0) return false;
        String owner = ownerAndName.substring(0, lastSep);
        String name  = ownerAndName.substring(lastSep + 1);
        String descriptor = calleeKey.substring(descSlash + 1); // includes the '('
        return isPermissionGuard(owner, name, descriptor);
    }
}
