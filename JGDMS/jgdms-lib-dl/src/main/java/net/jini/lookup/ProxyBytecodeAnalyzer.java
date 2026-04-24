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

package net.jini.lookup;

/**
 * Policy-driven bytecode inspector for candidate service proxies.
 *
 * <p>Implementations examine the bytecode of a proxy class and return an
 * {@link AnalysisVerdict} indicating whether the proxy is safe for local
 * use, should be isolated in an out-of-process {@link RemoteProxyHost}, or
 * whether the analysis is inconclusive and should be retried later.</p>
 *
 * <p>Implementations must be thread-safe; a single instance may be shared
 * among multiple filter invocations running concurrently on the
 * {@code cacheExecutorService} thread pool.</p>
 *
 * @see StandardProxyBytecodeAnalyzer
 * @see ProxyIsolationFilter
 * @since 3.1
 */
public abstract class ProxyBytecodeAnalyzer {

    /**
     * Analyzes the bytecode of the class identified by {@code className} and
     * loaded from {@code classBytes}, and returns a verdict.
     *
     * <p>Implementations should not attempt to <em>instantiate</em> the
     * class; analysis must be purely structural so that it is safe to call
     * this method before the proxy class is trusted.</p>
     *
     * @param className  the binary name of the top-level proxy class being
     *                   analyzed (e.g. {@code "com.example.MyProxy"}).  Must
     *                   not be {@code null}.
     * @param classBytes the raw {@code .class} file bytes for
     *                   {@code className}.  Must not be {@code null}.
     * @param codebase   the codebase annotation string from which
     *                   {@code className} was downloaded, or {@code null} if
     *                   the class was loaded from the local class path.
     * @return an {@link AnalysisVerdict} — never {@code null}.
     */
    public abstract AnalysisVerdict analyze(String className,
                                            byte[] classBytes,
                                            String codebase);
}
