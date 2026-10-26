/*
 * Copyright (C) 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.net.zeus.jgdms.bench.serial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.jeri.ServerEndpoint;
import net.jini.jeri.ssl.SslServerEndpoint;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.jeri.uds.UdsServerEndpoint;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

/**
 * The JERI transports the benchmarks run over. {@code TCP} (plain, loopback) is the transport of all the earlier
 * measurements and is for tests only; production uses {@code TLS} (mutual TLS, X.500 identities from
 * {@link TlsFixture}) and {@code UDS} (a Unix domain socket for process isolation).
 */
public enum Transport {
    TCP, UDS, TLS;

    /** A server endpoint of this transport. */
    public ServerEndpoint serverEndpoint() throws Exception {
        switch (this) {
            case UDS: {
                Path dir = Files.createTempDirectory("bench-uds");
                return UdsServerEndpoint.getInstance(dir.resolve("s").toString());
            }
            case TLS: {
                TlsFixture f = TlsFixture.current();
                return SslServerEndpoint.getInstance(f.server, new X500Principal[] {f.serverPrincipal}, "localhost", 0);
            }
            default:
                return TcpServerEndpoint.getInstance("127.0.0.1", 0);
        }
    }

    /** The server constraints of the invocation layer; mutual, confidential, integrity-protected TLS for TLS. */
    public BasicMethodConstraints constraints() {
        if (this != TLS) {
            return null;
        }
        return new BasicMethodConstraints(new InvocationConstraints(
                new InvocationConstraint[] {
                    ClientAuthentication.YES, ServerAuthentication.YES, Integrity.YES, Confidentiality.YES
                }, null));
    }

    /** The Subject calls are made as (the client identity for TLS), or null. */
    public Subject clientSubject() throws Exception {
        return this == TLS ? TlsFixture.current().client : null;
    }

    /** Runs {@code action} as the server identity for TLS (export needs it), directly otherwise. */
    public <T> T asServer(Callable<T> action) throws Exception {
        return this == TLS ? Subject.callAs(TlsFixture.current().server, action) : action.call();
    }
}
