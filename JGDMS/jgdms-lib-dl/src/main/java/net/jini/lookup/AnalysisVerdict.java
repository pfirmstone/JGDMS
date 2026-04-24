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
 * Verdict returned by {@link ProxyBytecodeAnalyzer} after examining a
 * candidate proxy's bytecode.
 *
 * @since 3.1
 */
enum AnalysisVerdict {

    /**
     * The bytecode does not contain any operations that are considered
     * dangerous by the active policy.  The proxy may be used locally without
     * out-of-process isolation.
     */
    SAFE,

    /**
     * The bytecode contains one or more operations that are considered
     * dangerous by the active policy.  The proxy should be forwarded to a
     * {@link RemoteProxyHost} for out-of-process isolation before being
     * returned to the caller.
     */
    UNSAFE,

    /**
     * The analysis could not reach a definite conclusion (e.g. the class
     * bytes could not be retrieved, or an internal error occurred).  The
     * {@link ProxyIsolationFilter} will treat this as an indefinite result
     * and schedule a retry.
     */
    INCONCLUSIVE
}
