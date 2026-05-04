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
package au.net.zeus.jgdms.api.codebase;

/**
 * Result of the {@code <clinit>} (static initializer) blocking analysis
 * performed by a {@link BytecodeAnalysisEngine} on a single class.
 *
 * <p>Virtual-thread carrier pinning can be caused by class loading when the
 * class initializer ({@code <clinit>}) blocks.  The BAE analyses the call graph
 * reachable from {@code <clinit>} (via BFS) to detect such patterns.
 *
 * @see ClassAnalysisResult
 * @since 3.1.1
 * @author Peter Firmstone
 * @author GitHub Copilot
 */
public enum ClinitVerdict {

    /**
     * The {@code <clinit>} method (or class, if none exists) is free of
     * blocking call paths and participates in no initialisation cycle.
     */
    CLEAN,

    /**
     * A call path was found from {@code <clinit>} to at least one known
     * blocking sink (e.g. {@code Thread.sleep}, {@code Object.wait},
     * {@code LockSupport.park}, blocking I/O) with <em>no</em> intervening
     * permission check.  An attacker can trigger this path without holding
     * any special Java permission.  The call chain is reported in
     * {@link ClassAnalysisResult#getBlockingCallPath()}.
     */
    BLOCKING,

    /**
     * A call path was found from {@code <clinit>} to at least one known
     * blocking sink, but every such path passes through a
     * {@code SecurityManager.checkXxx()} or
     * {@code AccessController.checkPermission()} call before reaching the
     * blocking operation.
     *
     * <p>This means:
     * <ul>
     *   <li>With no {@code SecurityManager} installed the blocking path is
     *       still reachable and behaves like {@link #BLOCKING}.</li>
     *   <li>With a {@code SecurityManager} that denies the guarding
     *       permission, a {@code SecurityException} is thrown before the
     *       blocking call is reached, preventing carrier-thread pinning.</li>
     * </ul>
     *
     * <p>Policy decision: granting the guarding permission implicitly accepts
     * that this class may block a virtual-thread carrier during static
     * initialisation.  The call chain is reported in
     * {@link ClassAnalysisResult#getBlockingCallPath()}.
     *
     * <p><strong>Note:</strong> if the permission guarding this blocking path
     * is also declared in the JAR's {@code META-INF/PERMISSIONS.LIST}, the
     * verdict is upgraded to {@link #BLOCKING_DECLARED} (which maps to
     * {@link VerdictType#DANGEROUS}) because the declaration signals that the
     * developer intends the permission to be granted, making the blocking path
     * reachable and creating a potential Denial of Service (DoS) vector.
     */
    BLOCKING_GUARDED,

    /**
     * A call path was found from {@code <clinit>} to a known I/O blocking
     * sink that carries a JDK-internal {@code SecurityManager.checkXxx()}
     * guard (e.g. {@code Socket.connect} internally calls
     * {@code SecurityManager.checkConnect}), <em>and</em> the corresponding
     * permission class is explicitly declared in the JAR's
     * {@code META-INF/PERMISSIONS.LIST}.
     *
     * <p><strong>Security warning — potential Denial of Service (DoS)
     * vector:</strong> because the JAR advertises that it requires the
     * guarding permission, an administrator who follows the declarations in
     * {@code PERMISSIONS.LIST} will grant that permission to the proxy.
     * Granting the permission allows the JDK's internal check to pass,
     * enabling the blocking call to proceed during {@code <clinit>}
     * execution.  When a proxy class is loaded on a virtual thread, this
     * blocks the underlying carrier thread.  If an attacker can repeatedly
     * trigger class loading (e.g. by causing cache misses), the carrier
     * thread pool can be exhausted, causing a <em>Denial of Service</em>
     * against all virtual threads on the JVM.
     *
     * <p>Unlike {@link #BLOCKING_GUARDED}, which is treated as
     * {@link VerdictType#INCONCLUSIVE} because the blocking path may never
     * be reached if the permission is withheld, {@code BLOCKING_DECLARED}
     * is treated as {@link VerdictType#DANGEROUS} because the JAR's own
     * {@code PERMISSIONS.LIST} makes it evident that the permission is
     * intended to be granted.
     *
     * <p><strong>Administrators:</strong> do <em>not</em> grant this proxy
     * the permission identified in the blocking call path.  Contact the
     * service provider and request a fix that removes or defers the blocking
     * network or file I/O from the static initializer.
     *
     * <p>The call chain is reported in
     * {@link ClassAnalysisResult#getBlockingCallPath()}.
     */
    BLOCKING_DECLARED,

    /**
     * A call path from {@code <clinit>} reaches a <em>native</em> method that
     * is not listed in the known-safe or known-blocking native registry.  The
     * native method may or may not block; the engine cannot determine this
     * statically.
     */
    NATIVE_OPACITY,

    /**
     * This class participates in a circular static-initializer dependency
     * cycle.  At least two classes in the JAR have mutually reachable
     * {@code <clinit>} methods.  The participants are reported in
     * {@link ClassAnalysisResult#getCycleParticipants()}.
     */
    CYCLE
}
