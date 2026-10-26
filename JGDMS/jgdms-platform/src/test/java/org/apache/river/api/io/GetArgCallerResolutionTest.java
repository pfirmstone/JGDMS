/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Which {@code @AtomicSerial} class a {@code get} call is attributed to.
 *
 * <p>{@code GetArg.callerClass()} returns the one registered class without
 * looking at the stack, and walks the stack to find the calling class when
 * several are registered (a class and its {@code @AtomicSerial} superclasses,
 * each with its own fields). The single-class case is an optimisation, so the
 * results here are the ones the stack walk gave before it: the tests pin them,
 * and the multi-class cases pin that the walk is still used where the stack is
 * the only evidence.
 */
public class GetArgCallerResolutionTest {

    /** Records the class {@code lookup} is asked about, and answers with that class's name. */
    private static final class RecordingGetArg extends GetArg {
        final List<String> lookups = new ArrayList<String>();
        private final Class[] registered;

        RecordingGetArg(Class... registered) {
            this.registered = registered;
        }

        @Override
        protected Object lookup(Class<?> callerClass, String name) {
            lookups.add(callerClass.getSimpleName() + "." + name);
            return callerClass.getSimpleName();
        }

        @Override
        protected boolean isDefaulted(Class<?> callerClass, String name) {
            lookups.add("defaulted:" + callerClass.getSimpleName() + "." + name);
            return false;
        }

        @Override
        public Class[] serialClasses() {
            return registered;
        }

        @Override
        public Collection getObjectStreamContext() {
            return Collections.emptyList();
        }
    }

    /** Stand-ins for a class and its superclass reading their own fields. */
    static final class Sub {
        static Object read(GetArg arg, String name) throws Exception {
            return arg.get(name, null, Object.class);
        }
    }

    static final class Base {
        static Object read(GetArg arg, String name) throws Exception {
            return arg.get(name, null, Object.class);
        }
    }

    /** Not registered with any GetArg below. */
    static final class Stranger {
        static Object read(GetArg arg, String name) throws Exception {
            return arg.get(name, null, Object.class);
        }
    }

    @Test
    public void oneRegisteredClassIsTheCallerWhoeverCalls() throws Exception {
        RecordingGetArg arg = new RecordingGetArg(Sub.class);
        assertEquals("Sub", Sub.read(arg, "a"));
        // a frame that is not the registered class (a helper, a lambda, an unrelated class)
        assertEquals("Sub", Stranger.read(arg, "b"));
        IntSupplier viaLambda = () -> {
            try {
                return ((String) arg.get("c", null, Object.class)).length();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        assertEquals("Sub".length(), viaLambda.getAsInt());
        assertEquals(java.util.Arrays.asList("Sub.a", "Sub.b", "Sub.c"), arg.lookups);
    }

    @Test
    public void oneRegisteredClassServesDefaultedToo() throws Exception {
        RecordingGetArg arg = new RecordingGetArg(Base.class);
        arg.defaulted("x");
        assertEquals(Collections.singletonList("defaulted:Base.x"), arg.lookups);
    }

    @Test
    public void severalRegisteredClassesAreToldApartByTheStack() throws Exception {
        RecordingGetArg arg = new RecordingGetArg(Base.class, Sub.class);
        assertEquals("Sub", Sub.read(arg, "f"));
        assertEquals("Base", Base.read(arg, "f"));
        // the same name is a different field of each class: one lookup each, then memoized
        assertEquals("Sub", Sub.read(arg, "f"));
        assertEquals("Base", Base.read(arg, "f"));
        assertEquals(java.util.Arrays.asList("Sub.f", "Base.f"), arg.lookups);
    }

    @Test
    public void severalRegisteredClassesAndNoneOnTheStackIsAmbiguous() throws Exception {
        RecordingGetArg arg = new RecordingGetArg(Base.class, Sub.class);
        try {
            Stranger.read(arg, "f");
            fail("a caller that is none of the registered classes must be refused");
        } catch (InvalidObjectException expected) {
            // refused, as before
        }
        assertEquals(Collections.emptyList(), arg.lookups);
    }

    @Test
    public void noRegisteredClassIsRefused() throws Exception {
        RecordingGetArg arg = new RecordingGetArg();
        try {
            Sub.read(arg, "f");
            fail("no registered class: the caller cannot be determined");
        } catch (InvalidObjectException expected) {
            // refused, as before
        }
    }

    @Test
    public void theRegisteredClassSetIsReadOnceAndKept() throws Exception {
        final int[] calls = {0};
        GetArg arg = new GetArg() {
            @Override protected Object lookup(Class<?> c, String n) { return c.getSimpleName(); }
            @Override protected boolean isDefaulted(Class<?> c, String n) { return false; }
            @Override public Class[] serialClasses() {
                calls[0]++;
                return calls[0] == 1 ? new Class[]{Sub.class} : new Class[]{Base.class};
            }
            @Override public Collection getObjectStreamContext() { return Collections.emptyList(); }
        };
        assertEquals("Sub", Sub.read(arg, "a"));
        assertEquals("a registered set that changes later must not change the answer",
                "Sub", Base.read(arg, "b"));
        assertSame(1, calls[0]);
    }
}
