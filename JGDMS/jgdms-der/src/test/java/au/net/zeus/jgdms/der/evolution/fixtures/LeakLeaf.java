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
 * Leaf of {@code LeakLeaf -> LeakMid -> LeakRoot}. Its field is named {@code "shared"},
 * colliding with {@link LeakMid#midShared}'s wire name -- the two are independent
 * namespaces (S3.9). In the regression test, old data carries LeakLeaf's "shared"
 * but NOT LeakMid's (LeakMid was inserted later); a correct decoder must give LeakMid
 * its default, not LeakLeaf's value.
 */
@AtomicSerial
public class LeakLeaf extends LeakMid {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("shared", String.class),
        };
    }

    /** @AtomicSerial WRITE contract (STD-008): wire name "shared" carries LeakLeaf's leafShared. */
    public static void serialize(AtomicSerial.PutArg arg, LeakLeaf o) throws IOException {
        arg.put("shared", o.leafShared);
        arg.writeArgs();
    }

    final String leafShared;

    public LeakLeaf(String rootName, String midShared, String leafShared) {
        super(rootName, midShared);
        this.leafShared = leafShared;
    }

    public LeakLeaf(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        super(arg);
        this.leafShared = (String) arg.get("shared", "LEAF_DEFAULT");
    }

    public String getLeafShared() { return leafShared; }
}
