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
import java.util.HashSet;
import java.util.Set;

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

            // ---- NIO Selector ----
            "java/nio/channels/Selector/select/()I",
            "java/nio/channels/Selector/select/(J)I",
            "java/nio/channels/Selector/select/(Ljava/util/function/Consumer;)I",
            "java/nio/channels/Selector/select/(Ljava/util/function/Consumer;J)I",
            "java/nio/channels/Selector/selectNow/()I",

            // ---- NIO SocketChannel / ServerSocketChannel ----
            "java/nio/channels/SocketChannel/read/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/SocketChannel/read/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/SocketChannel/read/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/SocketChannel/write/(Ljava/nio/ByteBuffer;)I",
            "java/nio/channels/SocketChannel/write/([Ljava/nio/ByteBuffer;)J",
            "java/nio/channels/SocketChannel/write/([Ljava/nio/ByteBuffer;IJ)J",
            "java/nio/channels/ServerSocketChannel/accept/()Ljava/nio/channels/SocketChannel;",

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

            // ---- Process ----
            "java/lang/Process/waitFor/()I",
            "java/lang/Process/waitFor/(JLjava/util/concurrent/TimeUnit;)Z",

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
            "sun/nio/ch/Poller/pollSelector/(IJ)V"
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
