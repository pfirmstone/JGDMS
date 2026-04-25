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
package org.apache.river.bae;

import java.rmi.RemoteException;
import java.util.Set;
import org.apache.river.api.codebase.BytecodeAnalysisEngine;
import org.apache.river.api.net.Uri;

/**
 * Server-side implementation of the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class runs in a dedicated Phoenix activation group with restrictive
 * security permissions.  It downloads and deep-parses untrusted bytecode from
 * the supplied codebase URLs, produces a {@code SignedVerdict}, and submits
 * it to the configured {@code VerdictRegistry}.
 *
 * @see BytecodeAnalysisEngine
 * @since 3.1.1
 */
public class BytecodeAnalysisEngineImpl implements BytecodeAnalysisEngine {

    /**
     * Creates a new {@code BytecodeAnalysisEngineImpl}.
     */
    public BytecodeAnalysisEngineImpl() {
    }

    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        if (codebaseUrls == null) throw new NullPointerException("codebaseUrls");
        if (codebaseUrls.isEmpty()) throw new IllegalArgumentException("codebaseUrls must not be empty");
        // TODO: download JARs, run bytecode analysis, produce SignedVerdict,
        //       and submit it to the VerdictRegistry.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
