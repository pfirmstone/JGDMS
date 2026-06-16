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
import org.apache.river.api.io.AtomicSerial;

/**
 * An {@code @AtomicSerial} object with a field of the NON-{@code @AtomicSerial}
 * type {@link Marker}. The field is admitted by the schema generator only because
 * {@link MarkerProvider} is registered, and round-trips through the replace/resolve
 * seam.
 */
@AtomicSerial
public final class Holder {

    public static AtomicSerial.SerialForm[] serialForm() {
        return new AtomicSerial.SerialForm[] {
            new AtomicSerial.SerialForm("marker", Marker.class),
        };
    }

    private final Marker marker;

    public Holder(Marker marker) {
        this.marker = marker;
    }

    public Holder(AtomicSerial.GetArg arg) throws IOException, ClassNotFoundException {
        this.marker = (Marker) arg.get("marker", null);
    }

    public Marker marker() {
        return marker;
    }
}
