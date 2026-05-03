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
     * {@code LockSupport.park}, blocking I/O).  The call chain is reported in
     * {@link ClassAnalysisResult#getBlockingCallPath()}.
     */
    BLOCKING,

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
