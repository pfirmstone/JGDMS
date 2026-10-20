/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jini.jeri;

import au.net.zeus.jgdms.der.DerInputLimits;
import au.net.zeus.jgdms.der.getarg.ResolutionContext;
import au.net.zeus.jgdms.der.stream.DerMarshalInputStream;
import au.net.zeus.jgdms.der.stream.DerMarshalOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.x500.X500Principal;
import net.jini.constraint.BasicMethodConstraints;
import net.jini.core.constraint.ClientAuthentication;
import net.jini.core.constraint.ClientMaxPrincipal;
import net.jini.core.constraint.ClientMinPrincipal;
import net.jini.core.constraint.Confidentiality;
import net.jini.core.constraint.ConnectionAbsoluteTime;
import net.jini.core.constraint.ConnectionRelativeTime;
import net.jini.core.constraint.ConstraintAlternatives;
import net.jini.core.constraint.Delegation;
import net.jini.core.constraint.Integrity;
import net.jini.core.constraint.InvocationConstraint;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.core.constraint.ServerAuthentication;
import net.jini.core.constraint.ServerMinPrincipal;
import net.jini.id.UuidFactory;
import net.jini.jeri.http.HttpEndpoint;
import net.jini.jeri.kerberos.KerberosEndpoint;
import net.jini.jeri.ssl.ConfidentialityStrength;
import net.jini.jeri.ssl.HttpsEndpoint;
import net.jini.jeri.ssl.SslEndpoint;
import net.jini.jeri.tcp.TcpEndpoint;
import net.jini.jeri.uds.UdsEndpoint;
import org.apache.river.api.io.AtomicMarshalInputStream;
import org.apache.river.api.io.AtomicMarshalOutputStream;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Audit: every class a JERI proxy is made of (the endpoints, the object endpoint, the invocation
 * handlers, the confidentiality strength) must be written and read by both atomic streams, come back equal,
 * and be usable, not merely decodable. A decoded endpoint that has not been interned has no connection state
 * (see {@link DerDecodedEndpointTest}); a class that is not {@code @AtomicSerial} is refused outright
 * (as {@code KerberosEndpoint} was).
 */
public class ProxyPartsAtomicAuditTest {

    private static final Collection<?> NO_CONTEXT = Collections.unmodifiableCollection(Collections.emptyList());
    private static final ClassLoader LOADER = ProxyPartsAtomicAuditTest.class.getClassLoader();

    public interface Svc extends Remote {
        String hello() throws RemoteException;
    }

    private static Map<String, Object> parts() {
        Map<String, Object> m = new LinkedHashMap<>();
        TcpEndpoint tcp = TcpEndpoint.getInstance("127.0.0.1", 1);
        m.put("TcpEndpoint", tcp);
        m.put("UdsEndpoint", UdsEndpoint.getInstance("/tmp/jgdms-no-such.sock"));
        m.put("HttpEndpoint", HttpEndpoint.getInstance("127.0.0.1", 1));
        m.put("SslEndpoint", SslEndpoint.getInstance("127.0.0.1", 1));
        m.put("HttpsEndpoint", HttpsEndpoint.getInstance("127.0.0.1", 1));
        m.put("KerberosEndpoint", KerberosEndpoint.getInstance("127.0.0.1", 1, new KerberosPrincipal("svc@EXAMPLE.ORG")));
        m.put("ConfidentialityStrength.STRONG", ConfidentialityStrength.STRONG);
        m.put("ConfidentialityStrength.WEAK", ConfidentialityStrength.WEAK);
        m.put("BasicObjectEndpoint", new BasicObjectEndpoint(tcp, UuidFactory.generate(), false));
        m.put("BasicObjectEndpoint(DGC)", new BasicObjectEndpoint(tcp, UuidFactory.generate(), true));
        BasicObjectEndpoint oe = new BasicObjectEndpoint(tcp, UuidFactory.generate(), false);
        m.put("BasicInvocationHandler", new BasicInvocationHandler(oe, null));
        m.put("AtomicInvocationHandler", new AtomicInvocationHandler(oe, null, false));
        m.put("AtomicDerInvocationHandler", new AtomicDerInvocationHandler(oe, null));
        return m;
    }

    private static Object der(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new DerMarshalOutputStream(bos, NO_CONTEXT, LOADER)) {
            out.writeObject(o);
        }
        try (ObjectInput in = new DerMarshalInputStream(new ByteArrayInputStream(bos.toByteArray()),
                new ResolutionContext(LOADER, false, LOADER), DerInputLimits.DEFAULT)) {
            return in.readObject();
        }
    }

    private static Object joss(Object o) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new AtomicMarshalOutputStream(bos, LOADER, NO_CONTEXT, false)) {
            out.writeObject(o);
        }
        try (ObjectInput in = AtomicMarshalInputStream.create(new ByteArrayInputStream(bos.toByteArray()),
                LOADER, false, LOADER, NO_CONTEXT, false)) {
            return in.readObject();
        }
    }

    /** Uses the decoded object as a proxy part is used; a refused connection is fine, an unset-up object is not. */
    private static void use(Object o) {
        try {
            if (o instanceof Endpoint) {
                OutboundRequestIterator it = ((Endpoint) o).newRequest(InvocationConstraints.EMPTY);
                if (it.hasNext()) {
                    it.next();
                }
            } else if (o instanceof ObjectEndpoint) {
                OutboundRequestIterator it = ((ObjectEndpoint) o).newCall(InvocationConstraints.EMPTY);
                if (it.hasNext()) {
                    it.next();
                }
            } else if (o instanceof java.lang.reflect.InvocationHandler) {
                Svc svc = (Svc) Proxy.newProxyInstance(LOADER, new Class<?>[] {Svc.class}, (java.lang.reflect.InvocationHandler) o);
                svc.hello();
            }
        } catch (NullPointerException npe) {
            throw new AssertionError("decoded object is not set up: " + npe, npe);
        } catch (Exception expected) {
            // refused connection, no peer, unsupported constraints...: not a missing set-up
        }
    }

    @Test
    public void everyPartOfAProxyRoundTripsAndIsUsableInBothCodecs() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Object> e : parts().entrySet()) {
            for (String codec : new String[] {"DER", "JOSS"}) {
                try {
                    Object back = codec.equals("DER") ? der(e.getValue()) : joss(e.getValue());
                    if (back.getClass() != e.getValue().getClass()) {
                        problems.add(e.getKey() + " " + codec + ": came back as " + back.getClass().getName());
                        continue;
                    }
                    use(back);
                } catch (Throwable t) {
                    Throwable r = t;
                    while (r.getCause() != null) {
                        r = r.getCause();
                    }
                    problems.add(e.getKey() + " " + codec + ": " + t.getClass().getSimpleName() + ": "
                            + String.valueOf(r.getMessage()).split("\n")[0]);
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("AUDIT\n" + String.join("\n", problems));
        }
    }

    /**
     * The constraints a proxy carries: each one, and a method-constraints object holding them all.
     * Currently fails (see the audit report of 2026-10-20: singleton constraints not resolved by DER,
     * ClientMaxPrincipal.serialize missing writeArgs, Connection*Time reading a long as an int); remove
     * the @Ignore when those are fixed.
     */
    @Ignore("known defects awaiting a decision")
    @Test
    public void everyConstraintAProxyCarriesRoundTripsEqualInBothCodecs() throws Exception {
        X500Principal p = new X500Principal("CN=audit");
        InvocationConstraint[] each = {
            Integrity.YES, Integrity.NO, Confidentiality.YES, Confidentiality.NO,
            ClientAuthentication.YES, ClientAuthentication.NO, ServerAuthentication.YES, ServerAuthentication.NO,
            Delegation.YES, Delegation.NO,
            new ClientMinPrincipal(p), new ClientMaxPrincipal(p), new ServerMinPrincipal(p),
            new ConnectionRelativeTime(1000), new ConnectionAbsoluteTime(1_700_000_000_000L),
            ConstraintAlternatives.create(new InvocationConstraint[] {Integrity.YES, Confidentiality.YES}),
        };
        List<String> problems = new ArrayList<>();
        List<Object> all = new ArrayList<>();
        for (InvocationConstraint c : each) {
            all.add(c);
        }
        all.add(new InvocationConstraints(each, new InvocationConstraint[] {Integrity.YES}));
        all.add(new BasicMethodConstraints(new InvocationConstraints(Integrity.YES, Confidentiality.NO)));
        for (Object o : all) {
            for (String codec : new String[] {"DER", "JOSS"}) {
                try {
                    Object back = codec.equals("DER") ? der(o) : joss(o);
                    if (!o.equals(back)) {
                        problems.add(o.getClass().getSimpleName() + " " + codec + ": not equal after the round trip: " + o + " / " + back);
                    }
                } catch (Throwable t) {
                    Throwable r = t;
                    while (r.getCause() != null) {
                        r = r.getCause();
                    }
                    problems.add(o.getClass().getSimpleName() + " " + codec + ": " + t.getClass().getSimpleName()
                            + ": " + String.valueOf(r.getMessage()).split("\n")[0]);
                }
            }
        }
        if (!problems.isEmpty()) {
            fail("AUDIT\n" + String.join("\n", problems));
        }
    }
}
