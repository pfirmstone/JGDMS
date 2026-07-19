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
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.apache.river.api.io.AtomicSerial;

/**
 * An {@code @AtomicSerial} object with a concretely-typed generic map field
 * {@code Map<String, X500Principal>}. The declared KEY type ({@code String}) and VALUE type
 * ({@code X500Principal}) are recovered separately from the field's generic signature and
 * threaded into the per-key / per-value decode-admission gate (F1).
 */
@AtomicSerial
public final class X500MapHolder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("byName", Map.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, X500MapHolder o) throws IOException {
        arg.put("byName", o.byName);
        arg.writeArgs();
    }

    private final Map<String, X500Principal> byName;

    public X500MapHolder(Map<String, X500Principal> byName) {
        this.byName = byName;
    }

    @SuppressWarnings("unchecked")
    public X500MapHolder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.byName = (Map<String, X500Principal>) arg.get("byName", Collections.emptyMap(), Map.class);
    }

    public Map<String, X500Principal> byName() {
        return byName;
    }
}
