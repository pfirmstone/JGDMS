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
package org.apache.river.bae.proxy;

import java.rmi.RemoteException;
import java.util.Set;
import org.apache.river.api.codebase.BytecodeAnalysisEngine;
import org.apache.river.api.net.Uri;

/**
 * Client-side smart proxy for the {@link BytecodeAnalysisEngine} service.
 *
 * <p>This class is downloaded to client JVMs and forwards
 * {@link #requestAnalysis} calls to the remote server-side implementation
 * over a JERI transport channel.
 *
 * @see BytecodeAnalysisEngine
 * @since 3.1.1
 */
public class BytecodeAnalysisEngineProxy implements BytecodeAnalysisEngine {

    private final BytecodeAnalysisEngine server;

    /**
     * Creates a new proxy that delegates to the given remote {@code server}.
     *
     * @param server the remote server endpoint; must be non-null
     */
    public BytecodeAnalysisEngineProxy(BytecodeAnalysisEngine server) {
        if (server == null) throw new NullPointerException("server");
        this.server = server;
    }

    @Override
    public void requestAnalysis(Set<Uri> codebaseUrls) throws RemoteException {
        server.requestAnalysis(codebaseUrls);
    }
}
