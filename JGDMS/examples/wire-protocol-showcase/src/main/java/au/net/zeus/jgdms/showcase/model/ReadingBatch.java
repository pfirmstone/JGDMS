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

package au.net.zeus.jgdms.showcase.model;

import org.apache.river.api.io.AtomicSerial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch that holds a list of readings. Used by the "same object, same bytes"
 * demonstration: two batches that are equal by value — one where the same reading
 * object appears twice, one where two separate but equal reading objects appear —
 * to show that the canonical wire format depends only on the values, while Java's
 * built-in serialization also depends on whether the objects were the same instance.
 */
@AtomicSerial
public final class ReadingBatch {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("readings", List.class),
        };
    }

    public static void serialize(AtomicSerial.PutArg arg, ReadingBatch o) throws IOException {
        arg.put("readings", o.readings);
        arg.writeArgs();
    }

    private final List<CalibratedReading> readings;

    public ReadingBatch(List<CalibratedReading> readings) {
        this.readings = new ArrayList<>(readings);
    }

    @SuppressWarnings("unchecked")
    public ReadingBatch(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        Object v = arg.get("readings", null);
        this.readings = (v == null) ? null : new ArrayList<>((List<CalibratedReading>) v);
    }

    public List<CalibratedReading> getReadings() { return readings; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadingBatch that)) return false;
        return java.util.Objects.equals(readings, that.readings);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(readings);
    }
}
