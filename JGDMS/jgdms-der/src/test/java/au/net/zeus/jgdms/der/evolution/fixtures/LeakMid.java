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

package au.net.zeus.jgdms.der.evolution.fixtures;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;

/**
 * The "inserted" middle class. Its field is deliberately named {@code "shared"} —
 * the SAME name as {@link LeakLeaf}'s field — to exercise §3.9 namespace isolation.
 * It is TOLERANT of absence (default, no non-null check), so when old data lacks
 * LeakMid's SEQUENCE its {@code shared} field MUST come back as the default
 * {@code "MID_DEFAULT"} — never leaked from LeakLeaf's namespace.
 */
@AtomicSerial
public class LeakMid extends LeakRoot {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("shared", String.class),
        };
    }

    final String midShared;

    public LeakMid(String rootName, String midShared) {
        super(rootName);
        this.midShared = midShared;
    }

    public LeakMid(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(arg);
        // Reads "shared" from LeakMid's OWN namespace. If LeakMid's SEQUENCE is absent
        // (old data), this MUST default — it must NOT leak LeakLeaf's "shared".
        this.midShared = (String) arg.get("shared", "MID_DEFAULT");
    }

    public String getMidShared() { return midShared; }
}
