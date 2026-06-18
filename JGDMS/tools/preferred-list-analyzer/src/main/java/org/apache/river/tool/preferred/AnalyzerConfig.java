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
package org.apache.river.tool.preferred;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configurable type and method sets that drive the {@link DecisionEngine}.
 *
 * <p>The defaults encode the criteria established by the 2026-06-17
 * {@code jgdms-platform} audit (SOW &sect;2/&sect;4).  All sets use JVM internal
 * names (with {@code /} separators) except where noted.  Callers may
 * {@linkplain #builder() build} a customised configuration; field types and the
 * blocking-call catalog are deliberately overridable (SOW &sect;4 "configurable
 * list").
 *
 * <p>The blocking-method catalog is the subset of
 * {@code au.net.zeus.jgdms.bae.BlockingSinkRegistry} relevant to detecting a
 * blocking call made while holding a static monitor; it is reproduced here
 * (rather than linked) because that class is package-private to the bytecode
 * analysis engine service.
 */
public final class AnalyzerConfig {

    /**
     * Static-field types that constitute a contended / blocking resource
     * (criterion (b)): sharing the class shares these monitors, locks and
     * scarce resources, coupling a downloaded proxy's liveness to the client's.
     */
    private final Set<String> contendedFieldTypes;

    /**
     * {@code Executor}/{@code ExecutorService} field types: a contention hazard
     * <em>unless</em> the field is initialised from a virtual-thread factory
     * ({@link #virtualThreadExecutorFactories}), which has no thread-starvation
     * coupling (SOW &sect;9.2).
     */
    private final Set<String> executorFieldTypes;

    /** Factory methods ("owner/name") that yield a non-blocking virtual-thread executor. */
    private final Set<String> virtualThreadExecutorFactories;

    /**
     * Static-field types that are benign read-mostly state and never a hazard
     * on their own (loggers, reflected members, constants): excluded from
     * criterion (a).
     */
    private final Set<String> benignFieldTypes;

    /** Mutable-container types whose contents can carry per-deployment state (criterion (a)). */
    private final Set<String> mutableContainerTypes;

    /** Collection/map method names treated as mutating the container's contents. */
    private final Set<String> mutatingMethodNames;

    /** Known blocking calls ("owner/name") for the blocking-call-under-static-lock signal. */
    private final Set<String> blockingMethods;

    /** Bootstrap-proxy interfaces (internal names): cross-boundary, must share. */
    private final Set<String> bootstrapInterfaces;

    /**
     * When true, criterion (a) (semantic co-mingling) alone drives a
     * {@link Decision#PREFER}.  When false (the default), a class whose only
     * hazard is criterion (a) defaults to {@link Decision#SHARE} and is
     * surfaced in the needs-review report instead &mdash; matching the SOW's
     * "default to share, surface for review" stance for the judgement-heavy
     * criterion (a).  Criterion (b) always drives prefer regardless.
     */
    private final boolean preferOnCriterionA;

    private AnalyzerConfig(Builder b) {
        this.contendedFieldTypes            = unmod(b.contendedFieldTypes);
        this.executorFieldTypes             = unmod(b.executorFieldTypes);
        this.virtualThreadExecutorFactories = unmod(b.virtualThreadExecutorFactories);
        this.benignFieldTypes               = unmod(b.benignFieldTypes);
        this.mutableContainerTypes          = unmod(b.mutableContainerTypes);
        this.mutatingMethodNames            = unmod(b.mutatingMethodNames);
        this.blockingMethods                = unmod(b.blockingMethods);
        this.bootstrapInterfaces            = unmod(b.bootstrapInterfaces);
        this.preferOnCriterionA             = b.preferOnCriterionA;
    }

    private static Set<String> unmod(Set<String> s) {
        return Collections.unmodifiableSet(new HashSet<String>(s));
    }

    public boolean isContendedFieldType(String internalType) {
        return internalType != null && contendedFieldTypes.contains(internalType);
    }

    public boolean isExecutorFieldType(String internalType) {
        return internalType != null && executorFieldTypes.contains(internalType);
    }

    public boolean isVirtualThreadExecutorFactory(String ownerSlashName) {
        return ownerSlashName != null
                && virtualThreadExecutorFactories.contains(ownerSlashName);
    }

    public boolean isBenignFieldType(String internalType) {
        return internalType != null && benignFieldTypes.contains(internalType);
    }

    public boolean isMutableContainerType(String internalType) {
        return internalType != null && mutableContainerTypes.contains(internalType);
    }

    public boolean isMutatingMethod(String name) {
        return name != null && mutatingMethodNames.contains(name);
    }

    public boolean isBlockingMethod(String ownerSlashName) {
        return ownerSlashName != null && blockingMethods.contains(ownerSlashName);
    }

    public boolean isBootstrapInterface(String internalName) {
        return internalName != null && bootstrapInterfaces.contains(internalName);
    }

    public boolean isPreferOnCriterionA() {
        return preferOnCriterionA;
    }

    Set<String> getBootstrapInterfaces() {
        return bootstrapInterfaces;
    }

    /** Returns the default analyzer configuration. */
    public static AnalyzerConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for {@link AnalyzerConfig}; pre-loaded with the defaults. */
    public static final class Builder {

        private final Set<String> contendedFieldTypes = new HashSet<String>(Arrays.asList(
                // ---- entropy / RNG ----
                "java/security/SecureRandom",
                // ---- locks ----
                "java/util/concurrent/locks/Lock",
                "java/util/concurrent/locks/ReadWriteLock",
                "java/util/concurrent/locks/ReentrantLock",
                "java/util/concurrent/locks/ReentrantReadWriteLock",
                "java/util/concurrent/locks/StampedLock",
                "java/util/concurrent/locks/Condition",
                // ---- semaphores / latches / barriers ----
                "java/util/concurrent/Semaphore",
                "java/util/concurrent/CountDownLatch",
                "java/util/concurrent/CyclicBarrier",
                "java/util/concurrent/Phaser",
                "java/util/concurrent/Exchanger",
                // ---- blocking queues ----
                "java/util/concurrent/BlockingQueue",
                "java/util/concurrent/BlockingDeque",
                "java/util/concurrent/ArrayBlockingQueue",
                "java/util/concurrent/LinkedBlockingQueue",
                "java/util/concurrent/LinkedBlockingDeque",
                "java/util/concurrent/PriorityBlockingQueue",
                "java/util/concurrent/SynchronousQueue",
                "java/util/concurrent/DelayQueue",
                "java/util/concurrent/LinkedTransferQueue",
                // ---- River's own shared thread-pool / scheduler abstractions ----
                // (org.apache.river.thread, in jgdms-collections): a static field
                // of one of these is a shared pool whose liveness couples a
                // downloaded proxy to the client (SOW &sect;8 systemThreadPool).
                "org/apache/river/thread/Executor",
                "org/apache/river/thread/ThreadPool",
                "org/apache/river/thread/TaskManager",
                "org/apache/river/thread/WakeupManager"
        ));

        private final Set<String> executorFieldTypes = new HashSet<String>(Arrays.asList(
                "java/util/concurrent/Executor",
                "java/util/concurrent/ExecutorService",
                "java/util/concurrent/ScheduledExecutorService",
                "java/util/concurrent/AbstractExecutorService",
                "java/util/concurrent/ThreadPoolExecutor",
                "java/util/concurrent/ScheduledThreadPoolExecutor",
                "java/util/concurrent/ForkJoinPool"
        ));

        private final Set<String> virtualThreadExecutorFactories = new HashSet<String>(Arrays.asList(
                "java/util/concurrent/Executors/newVirtualThreadPerTaskExecutor"
        ));

        private final Set<String> benignFieldTypes = new HashSet<String>(Arrays.asList(
                "java/util/logging/Logger",
                "java/lang/reflect/Method",
                "java/lang/reflect/Field",
                "java/lang/reflect/Constructor",
                "java/lang/String",
                "java/lang/Class",
                "java/lang/Integer", "java/lang/Long", "java/lang/Boolean",
                "java/lang/Byte", "java/lang/Short", "java/lang/Character",
                "java/lang/Float", "java/lang/Double",
                "java/math/BigInteger", "java/math/BigDecimal"
        ));

        private final Set<String> mutableContainerTypes = new HashSet<String>(Arrays.asList(
                "java/util/Map", "java/util/HashMap", "java/util/LinkedHashMap",
                "java/util/TreeMap", "java/util/IdentityHashMap", "java/util/WeakHashMap",
                "java/util/concurrent/ConcurrentMap",
                "java/util/concurrent/ConcurrentHashMap",
                "java/util/Collection", "java/util/List", "java/util/ArrayList",
                "java/util/LinkedList", "java/util/Set", "java/util/HashSet",
                "java/util/LinkedHashSet", "java/util/TreeSet",
                "java/util/concurrent/CopyOnWriteArrayList",
                "java/util/concurrent/CopyOnWriteArraySet",
                "java/util/concurrent/ConcurrentLinkedQueue",
                "java/util/concurrent/ConcurrentLinkedDeque",
                "java/lang/ref/ReferenceQueue",
                "java/util/concurrent/atomic/AtomicInteger",
                "java/util/concurrent/atomic/AtomicLong",
                "java/util/concurrent/atomic/AtomicReference",
                "java/util/concurrent/atomic/AtomicBoolean"
        ));

        private final Set<String> mutatingMethodNames = new HashSet<String>(Arrays.asList(
                "add", "addAll", "put", "putAll", "putIfAbsent", "remove", "removeAll",
                "removeIf", "clear", "set", "offer", "offerFirst", "offerLast",
                "push", "pop", "poll", "pollFirst", "pollLast", "take", "addFirst",
                "addLast", "merge", "compute", "computeIfAbsent", "computeIfPresent",
                "replace", "replaceAll", "getAndSet", "getAndIncrement",
                "getAndDecrement", "getAndAdd", "incrementAndGet", "decrementAndGet",
                "addAndGet", "compareAndSet"
        ));

        // Subset of BlockingSinkRegistry.BLOCKING_SINKS as "owner/name" (descriptor
        // dropped: under a static lock, any overload of these blocks).
        private final Set<String> blockingMethods = new HashSet<String>(Arrays.asList(
                "java/lang/Thread/sleep", "java/lang/Thread/join",
                "java/lang/Object/wait",
                "java/util/concurrent/locks/LockSupport/park",
                "java/util/concurrent/locks/LockSupport/parkNanos",
                "java/util/concurrent/locks/Lock/lock",
                "java/util/concurrent/locks/Lock/lockInterruptibly",
                "java/util/concurrent/locks/ReentrantLock/lock",
                "java/util/concurrent/locks/ReentrantLock/lockInterruptibly",
                "java/util/concurrent/locks/Condition/await",
                "java/util/concurrent/locks/Condition/awaitNanos",
                "java/util/concurrent/locks/Condition/awaitUninterruptibly",
                "java/util/concurrent/Semaphore/acquire",
                "java/util/concurrent/Semaphore/acquireUninterruptibly",
                "java/util/concurrent/CountDownLatch/await",
                "java/util/concurrent/CyclicBarrier/await",
                "java/util/concurrent/Phaser/arriveAndAwaitAdvance",
                "java/util/concurrent/Phaser/awaitAdvance",
                "java/util/concurrent/Exchanger/exchange",
                "java/util/concurrent/BlockingQueue/take",
                "java/util/concurrent/BlockingQueue/put",
                "java/util/concurrent/Future/get",
                "java/util/concurrent/CompletableFuture/get",
                "java/util/concurrent/CompletableFuture/join",
                "java/io/InputStream/read",
                "java/io/OutputStream/write",
                "java/io/ObjectInputStream/readObject",
                "java/net/Socket/connect",
                "java/net/ServerSocket/accept",
                "java/net/URL/openStream",
                "java/net/URLConnection/getInputStream"
        ));

        private final Set<String> bootstrapInterfaces = new HashSet<String>(Arrays.asList(
                "net/jini/export/ProxyAccessor",
                "net/jini/export/CodebaseAccessor",
                "net/jini/export/DynamicProxyCodebaseAccessor"
        ));

        private boolean preferOnCriterionA = false;

        public Builder contendedFieldType(String internalType) {
            contendedFieldTypes.add(internalType);
            return this;
        }

        public Builder benignFieldType(String internalType) {
            benignFieldTypes.add(internalType);
            return this;
        }

        public Builder mutableContainerType(String internalType) {
            mutableContainerTypes.add(internalType);
            return this;
        }

        public Builder bootstrapInterface(String internalName) {
            bootstrapInterfaces.add(internalName);
            return this;
        }

        public Builder preferOnCriterionA(boolean v) {
            this.preferOnCriterionA = v;
            return this;
        }

        public AnalyzerConfig build() {
            return new AnalyzerConfig(this);
        }
    }
}
