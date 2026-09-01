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

package net.jini.jeri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.AccessControlContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import net.jini.core.constraint.InvocationConstraints;
import net.jini.export.ServerContext;
import net.jini.io.context.ClientUserSubject;
import org.apache.river.api.security.UserSubjectSupport;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Behavioural proof that {@link BasicInvocationDispatcher} binds <em>every</em>
 * user {@code Subject} carried in a JERI request, not just the first.
 *
 * <p>Before the fix, the dispatcher's cached multi-{@code Subject} {@code callAs}
 * {@code Method} was always {@code null}: it asked for
 * {@code callAs(Callable, Subject[])}, a signature that does not exist on
 * DirtyChai (the public overload takes {@code UserSubject...}), and the
 * resulting {@code NoSuchMethodException} was swallowed.  Every dispatch
 * therefore took the "first Subject only" branch and dropped the remaining
 * client identities from the authorization context.  Fixing the lookup alone
 * would not have been enough either: {@code readUserSubjects} built plain
 * {@code Subject}s, and a {@code Subject[]} is not assignable to the
 * {@code UserSubject[]} parameter.
 *
 * <p>Both halves are exercised here against the real production code: the
 * reconstructed identities come from the private {@code readUserSubjects}, and
 * the dispatch goes through the private {@code invokeWithClientSubject}.
 */
public class BasicInvocationDispatcherMultiSubjectTest {

    private static final X500Principal ALICE = new X500Principal("CN=alice");
    private static final X500Principal BOB   = new X500Principal("CN=bob");

    // ------------------------------------------------------------------
    // Probes and fixtures
    // ------------------------------------------------------------------

    private static boolean dirtyChai() {
        try {
            Class<?> us = Class.forName("javax.security.auth.UserSubject");
            Subject.class.getMethod("callAs", Callable.class,
                    Array.newInstance(us, 0).getClass());
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException notDirtyChai) {
            return false;
        }
    }

    /** The identities observed inside the dispatched remote call. */
    public static final class Observation {
        public final int subjectCount;
        public final Set<Principal> principals;
        Observation(int subjectCount, Set<Principal> principals) {
            this.subjectCount = subjectCount;
            this.principals = principals;
        }
    }

    /** Remote interface whose implementation records the bound identities. */
    public interface IdentityProbe extends Remote {
        Observation observe() throws RemoteException;
    }

    public static final class IdentityProbeImpl implements IdentityProbe {
        @Override
        public Observation observe() {
            try {
                Method currentAll = Subject.class.getMethod("currentAll");
                Subject[] all = (Subject[]) currentAll.invoke(null);
                Set<Principal> ps = new LinkedHashSet<>();
                if (all != null) {
                    for (Subject s : all) ps.addAll(s.getPrincipals());
                }
                return new Observation(all == null ? 0 : all.length, ps);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Server-context element carrying the reconstructed client identities. */
    private static final class Element implements ClientUserSubject {
        private final Subject[] subjects;
        Element(List<Subject> subjects) { this.subjects = subjects.toArray(new Subject[0]); }
        @Override public Subject[] getUserSubjects() { return subjects.clone(); }
    }

    private static Subject wireSubject(Principal... principals) {
        Set<Principal> ps = new LinkedHashSet<>(Arrays.asList(principals));
        return new Subject(true, ps, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Runs the client-side encoder and the production server-side decoder, so the
     * Subjects under test are exactly the ones a real request would produce.
     */
    @SuppressWarnings("unchecked")
    private static List<Subject> decodeFromWire(Subject... clientSubjects) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BasicInvocationHandler.writeUserSubjects(baos, clientSubjects);
        Method readUserSubjects = BasicInvocationDispatcher.class
                .getDeclaredMethod("readUserSubjects", InputStream.class);
        readUserSubjects.setAccessible(true);
        return (List<Subject>) readUserSubjects.invoke(
                null, new ByteArrayInputStream(baos.toByteArray()));
    }

    private static BasicInvocationDispatcher newDispatcher(Method remoteMethod)
            throws Exception
    {
        return new BasicInvocationDispatcher(
                Collections.singleton(remoteMethod),
                constraints -> InvocationConstraints.EMPTY,   // ServerCapabilities
                null,   // serverConstraints
                null,   // permissionClass
                null);  // loader
    }

    /**
     * Dispatches {@code observe()} through the production
     * {@code invokeWithClientSubject}, with {@code userSubjects} presented in the
     * server context exactly as the request decoder presents them.
     */
    private static Observation dispatch(List<Subject> userSubjects) throws Exception {
        final Method remoteMethod = IdentityProbe.class.getMethod("observe");
        final BasicInvocationDispatcher dispatcher = newDispatcher(remoteMethod);
        final IdentityProbe impl = new IdentityProbeImpl();

        final Method invokeWithClientSubject = BasicInvocationDispatcher.class
                .getDeclaredMethod("invokeWithClientSubject",
                        Remote.class, Method.class, Object[].class,
                        Collection.class, AccessControlContext.class);
        invokeWithClientSubject.setAccessible(true);

        final Collection context = new ArrayList();
        context.add(new Element(userSubjects));

        final Object[] result = { null };
        final Throwable[] failure = { null };
        ServerContext.doWithServerContext(() -> {
            try {
                result[0] = invokeWithClientSubject.invoke(
                        dispatcher, impl, remoteMethod, new Object[0], context, null);
            } catch (Throwable t) {
                failure[0] = t;
            }
        }, context);

        if (failure[0] != null) {
            if (failure[0] instanceof Exception) throw (Exception) failure[0];
            throw new AssertionError(failure[0]);
        }
        return (Observation) result[0];
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * The reconstructed client identities must be {@code UserSubject}s — the only
     * type the JDK's multi-Subject {@code callAs} accepts.  A plain
     * {@code Subject} here would make the dispatch fail with
     * "argument type mismatch".
     */
    @Test
    public void reconstructedClientIdentitiesAreUserSubjects() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        List<Subject> decoded = decodeFromWire(wireSubject(ALICE), wireSubject(BOB));
        assertEquals(2, decoded.size());
        for (Subject s : decoded) {
            assertTrue("decoded identity must be a "
                            + UserSubjectSupport.USER_SUBJECT_CLASS_NAME
                            + ", was " + s.getClass().getName(),
                    UserSubjectSupport.USER_SUBJECT_CLASS.isInstance(s));
            assertTrue("decoded identity must be read-only", s.isReadOnly());
        }
        // ...and they are therefore acceptable to the multi-Subject call.
        Object arr = UserSubjectSupport.toUserSubjectArray(decoded.toArray(new Subject[0]));
        assertEquals(UserSubjectSupport.USER_SUBJECT_CLASS, arr.getClass().getComponentType());
    }

    /**
     * The behavioural proof: dispatching a request carrying two user Subjects must
     * make both visible to {@code Subject.currentAll()} inside the remote method.
     * Before the fix only one was visible.
     */
    @Test
    public void dispatchBindsEveryUserSubject() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        Observation o = dispatch(decodeFromWire(wireSubject(ALICE), wireSubject(BOB)));
        assertEquals("both client identities must be bound during dispatch",
                2, o.subjectCount);
        assertTrue("alice must be in the authorization context", o.principals.contains(ALICE));
        assertTrue("bob must be in the authorization context — dropping him is the bug",
                o.principals.contains(BOB));
    }

    /** Three identities, to show the fix is not two-specific. */
    @Test
    public void dispatchBindsThreeUserSubjects() throws Exception {
        Assume.assumeTrue("requires the DirtyChai multi-Subject API", dirtyChai());
        X500Principal carol = new X500Principal("CN=carol");
        Observation o = dispatch(decodeFromWire(
                wireSubject(ALICE), wireSubject(BOB), wireSubject(carol)));
        assertEquals(3, o.subjectCount);
        assertTrue(o.principals.contains(ALICE));
        assertTrue(o.principals.contains(BOB));
        assertTrue(o.principals.contains(carol));
    }

    /** One identity: unchanged behaviour on every platform. */
    @Test
    public void dispatchWithASingleUserSubjectBindsIt() throws Exception {
        Assume.assumeTrue("requires Subject.currentAll()", dirtyChai());
        Observation o = dispatch(decodeFromWire(wireSubject(ALICE)));
        assertEquals(1, o.subjectCount);
        assertTrue(o.principals.contains(ALICE));
    }

    /** No identities: the method still runs, with nothing bound. */
    @Test
    public void dispatchWithNoUserSubjectsStillInvokes() throws Exception {
        Assume.assumeTrue("requires Subject.currentAll()", dirtyChai());
        Observation o = dispatch(Collections.emptyList());
        assertEquals(0, o.subjectCount);
        assertTrue(o.principals.isEmpty());
    }
}
