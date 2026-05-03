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
     * descriptor.  We use {@code *} as a wildcard for the descriptor to match
     * all overloads of a method with a given name.  The {@link #isBlocking}
     * lookup applies wildcard matching.
     */
    static final Set<String> BLOCKING_SINKS;

    /**
     * Native methods that are known to be non-blocking.
     * The same {@code "owner/name/descriptor"} format applies.
     */
    static final Set<String> SAFE_NATIVES;

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

            // ---- Future ----
            "java/util/concurrent/Future/get/()Ljava/lang/Object;",
            "java/util/concurrent/Future/get/(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            "java/util/concurrent/FutureTask/get/()Ljava/lang/Object;",
            "java/util/concurrent/FutureTask/get/(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;"
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
}
