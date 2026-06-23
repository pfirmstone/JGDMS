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

import java.io.IOException;

/**
 * Per-package access broker that lets the marshalling engines invoke the three
 * author-written {@link AtomicSerial} contract members of a
 * <em>package-private</em> {@code @AtomicSerial} class without reflective field
 * access, without {@code setAccessible} on fields, and without {@code opens}.
 *
 * <p>The marshalling engines ({@code org.apache.river.api.io.ObjOutputStream} /
 * {@code AtomicMarshalInputStream} for JOSS, and the DER codec in
 * {@code jgdms-der}) live in their own packages.  A package-private
 * {@code @AtomicSerial} class — the normal case for proxy internals — cannot
 * have its {@code public static serialForm()} / {@code serialize(PutArg, T)}
 * members or its {@code (GetArg)} constructor invoked from another package
 * without {@code Method/Constructor.setAccessible(true)} (a cross-package
 * reflective grant).  This interface removes that requirement by introducing a
 * <strong>public delegate compiled into each package</strong> that needs one.
 *
 * <p>Two hops, each on the right side of every access boundary:
 * <ul>
 *   <li><b>engine&nbsp;&rarr;&nbsp;delegate:</b> the delegate is {@code public}
 *       and is called through this {@code public} interface — an ordinary
 *       virtual call, no reflection, no {@code setAccessible}, no {@code opens};
 *   <li><b>delegate&nbsp;&rarr;&nbsp;target:</b> <em>same package, same defining
 *       loader</em> — {@code SetProxy.serialForm()}, {@code new SetProxy(arg)}
 *       are plain Java.  The delegate never touches a field; it only invokes the
 *       class's own methods and constructor, which read the class's own fields
 *       as in-class code.
 * </ul>
 *
 * <p>A delegate that dispatches directly (an {@code if/else} or {@code switch}
 * on the concrete {@link Class}, as opposed to reflecting) uses <em>zero</em>
 * reflection, so under a least-privilege policy it needs no reflection
 * permission at all — not even {@code accessDeclaredMembers}, and never
 * {@code suppressAccessChecks}.
 *
 * <h2>Resolution: by the <em>defining</em> loader</h2>
 * Package-private access is governed by the <em>runtime package</em> =
 * (package&nbsp;name, <em>defining class loader</em>).  A delegate is therefore
 * only usable when it is the one <em>co-loaded with the class it serves</em>:
 * resolved via the marshalled class's own defining loader
 * ({@code obj.getClass().getClassLoader()} on write; the stream-resolved
 * {@code Class}'s loader on read).  A delegate found via any other loader would
 * be in a different runtime package and would be denied access to its target's
 * package-private members.  {@link MarshalDelegates#delegateFor(Class)}
 * performs exactly this loader-scoped resolution.
 *
 * <p>Dispatch granularity mirrors the engines:
 * <ul>
 *   <li>{@link #serialForm(Class)} and {@link #serialize(Class, AtomicSerial.PutArg, Object)}
 *       are invoked <em>per class level</em> of the serialized hierarchy, so a
 *       delegate must handle every {@code @AtomicSerial} level in its package,
 *       including abstract intermediates;
 *   <li>{@link #create(Class, AtomicSerial.GetArg)} is invoked once for the
 *       <em>concrete leaf</em> class being constructed; Java constructor
 *       chaining and {@code GetArg}'s caller-based field dispatch handle the
 *       super-levels, so a delegate need only construct the concrete classes.
 * </ul>
 *
 * <p>Implementations are discovered through {@code org.apache.river.resource.Service}
 * (see {@link MarshalDelegates}) and must therefore be {@code public} with a
 * {@code public} no-argument constructor, and registered in
 * {@code META-INF/services/org.apache.river.api.io.MarshalDelegate}.
 *
 * @see AtomicSerial
 * @see MarshalDelegates
 * @since 3.2.0
 */
public interface MarshalDelegate {

    /**
     * Returns the wire ABI for the given class level by invoking that class's
     * own {@code public static SerialForm[] serialForm()} as in-package code.
     *
     * @param c the {@code @AtomicSerial} class level whose wire form is wanted;
     *          this delegate must serve {@code c}'s package
     * @return the class level's declared {@code SerialForm[]} (never field
     *         reflection); an empty array for a {@code @Stateless} level
     */
    AtomicSerial.SerialForm[] serialForm(Class<?> c);

    /**
     * Writes the given object's state for class level {@code c} by invoking that
     * class's own {@code public static void serialize(PutArg, T)} as in-package
     * code.  The static {@code serialize} reads {@code c}'s own fields (legal,
     * in-class) and writes them through {@code arg}; the delegate never reads a
     * field itself.
     *
     * @param c   the {@code @AtomicSerial} class level being written
     * @param arg the engine-supplied {@code PutArg} sink
     * @param o   the object whose level {@code c} state is being written
     * @throws IOException if the class's {@code serialize} method does
     */
    void serialize(Class<?> c, AtomicSerial.PutArg arg, Object o) throws IOException;

    /**
     * Constructs an instance of the concrete leaf class {@code c} by invoking
     * its own invariant-enforcing {@code (GetArg)} constructor as in-package
     * code.  Deserialization never bypasses this constructor.
     *
     * @param c   the concrete leaf {@code @AtomicSerial} class to construct
     * @param arg the engine-supplied {@code GetArg} providing the wire arguments
     * @return the constructed instance
     * @throws IOException            if the constructor does
     * @throws ClassNotFoundException if the constructor does
     */
    Object create(Class<?> c, AtomicSerial.GetArg arg)
            throws IOException, ClassNotFoundException;

    /**
     * The concrete {@code @AtomicSerial} classes this delegate serves: the classes
     * of its package whose marshalling it handles in-package.  A class that needs
     * no {@code setAccessible} -- a fully public {@code @AtomicSerial} class with a
     * public {@code (GetArg)} constructor -- need not be served; the engine reaches
     * it by ordinary reflection.  {@link MarshalDelegates#delegateFor(Class)}
     * returns this delegate only for the classes it {@linkplain #serves(Class)
     * serves}, so unserved classes -- including incomplete ones that share the
     * package -- fall back to the reflective path rather than being forced here.
     *
     * <p>The returned array must not be modified.
     *
     * @return the served classes (never {@code null})
     */
    Class<?>[] servedClasses();

    /**
     * Whether this delegate serves {@code c}.  The default scans
     * {@link #servedClasses()} by identity ({@code ==}); since a delegate's served
     * classes are co-loaded with it, identity matching also enforces that {@code c}
     * is in the delegate's own runtime package.
     *
     * @param c the candidate class
     * @return {@code true} if this delegate handles {@code c}
     */
    default boolean serves(Class<?> c) {
        for (Class<?> s : servedClasses()) {
            if (s == c) {
                return true;
            }
        }
        return false;
    }

}
