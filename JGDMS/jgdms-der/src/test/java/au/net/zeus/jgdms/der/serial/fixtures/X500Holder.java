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

package au.net.zeus.jgdms.der.serial.fixtures;

import java.io.IOException;
import javax.security.auth.x500.X500Principal;
import org.apache.river.api.io.AtomicSerial;

/**
 * An {@code @AtomicSerial} object carrying a NON-{@code @AtomicSerial}
 * {@link X500Principal} field. The field is admitted by the schema generator only
 * because {@code X500PrincipalSerializer} is registered in the closed PRODUCTION DER
 * serializer registry, so it exercises the real production replace/resolve path
 * (WI-4/WI-6) end to end.
 */
@AtomicSerial
public final class X500Holder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("name", X500Principal.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): the replaceable X500Principal field. */
    public static void serialize(AtomicSerial.PutArg arg, X500Holder o) throws IOException {
        arg.put("name", o.name);
        arg.writeArgs();
    }

    private final X500Principal name;

    public X500Holder(X500Principal name) {
        this.name = name;
    }

    public X500Holder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.name = (X500Principal) arg.get("name", null);
    }

    public X500Principal name() {
        return name;
    }
}
