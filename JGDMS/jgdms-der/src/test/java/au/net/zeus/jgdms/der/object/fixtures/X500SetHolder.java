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

package au.net.zeus.jgdms.der.object.fixtures;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import org.apache.river.api.io.AtomicSerial;

/**
 * An {@code @AtomicSerial} object with a concretely-typed generic collection field
 * {@code Set<X500Principal>} — the reggie {@code MethodConstraints} principals shape.
 * The declared element type {@code X500Principal} is recovered from the field's generic
 * signature and threaded into the per-element decode-admission gate (F1); each element
 * round-trips through the registered {@code X500PrincipalSerializer} substitution.
 */
@AtomicSerial
public final class X500SetHolder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("principals", Set.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, X500SetHolder o) throws IOException {
        arg.put("principals", o.principals);
        arg.writeArgs();
    }

    private final Set<X500Principal> principals;

    public X500SetHolder(Set<X500Principal> principals) {
        this.principals = principals;
    }

    @SuppressWarnings("unchecked")
    public X500SetHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.principals = (Set<X500Principal>) arg.get("principals", Collections.emptySet(), Set.class);
    }

    public Set<X500Principal> principals() {
        return principals;
    }
}
