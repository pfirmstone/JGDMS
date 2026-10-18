/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jini.jeri;

import au.net.zeus.jgdms.der.stream.DerStreamPolicy;
import java.lang.reflect.Method;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Collection;
import net.jini.core.constraint.InvocationConstraints;
import org.junit.Assert;
import org.junit.Test;

/**
 * B5: a malformed whole-stream policy property fails the dispatcher's EXPORT with a clear message, instead of failing every
 * request later. (The policy is read once per JVM; the test resets the cached reading around itself.)
 */
public class AtomicDerPolicyExportTest {

    public interface Service extends java.rmi.Remote {
        void ping() throws java.rmi.RemoteException;
    }

    private static final class FakeServerCaps implements ServerCapabilities {
        public InvocationConstraints checkConstraints(InvocationConstraints c) { return InvocationConstraints.EMPTY; }
    }

    private static void resetPolicyCache() throws Exception {
        Method reset = DerStreamPolicy.class.getDeclaredMethod("resetDefaultsForTest");
        reset.setAccessible(true);
        reset.invoke(null);
    }

    @Test
    public void aMalformedDenyEntryFailsTheExportWithTheReason() throws Exception {
        String prop = DerStreamPolicy.PROP_DENY_CLASSES;
        String before = System.getProperty(prop);
        Collection<Method> methods = new ArrayList<>();
        methods.add(Service.class.getMethod("ping"));
        try {
            System.setProperty(prop, "good.Name, not a class!");
            resetPolicyCache();
            try {
                new AtomicDerInvocationDispatcher(methods, new FakeServerCaps(), null, null,
                        AtomicDerPolicyExportTest.class.getClassLoader());
                Assert.fail("a malformed policy must fail the export");
            } catch (ExportException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("not a class!"));
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("refusing to start"));
            }
        } finally {
            if (before == null) System.clearProperty(prop); else System.setProperty(prop, before);
            resetPolicyCache();
        }
        // and with the property fixed the same export works
        new AtomicDerInvocationDispatcher(methods, new FakeServerCaps(), null, null,
                AtomicDerPolicyExportTest.class.getClassLoader());
    }
}
