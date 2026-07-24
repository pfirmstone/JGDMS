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

package au.net.zeus.jgdms.showcase.entry;

import net.jini.core.entry.Entry;

/**
 * A record placed into a shared space. This is the CLIENT's own class. In the
 * demonstration it is deliberately compiled into a separate folder so it can be put
 * on the producer's classpath and left OFF the matching server's classpath — proving
 * the server matches these records without ever having the class.
 *
 * <p>Fields are public objects, the shared-space convention: a field left null in a
 * template means "match anything here".
 */
public class SensorReadingEntry implements Entry {

    public String  stationName;
    public String  measuredQuantity;
    public Integer sequenceNumber;

    /** Required no-argument constructor. */
    public SensorReadingEntry() {}

    public SensorReadingEntry(String stationName, String measuredQuantity, Integer sequenceNumber) {
        this.stationName      = stationName;
        this.measuredQuantity = measuredQuantity;
        this.sequenceNumber   = sequenceNumber;
    }

    @Override
    public String toString() {
        return "SensorReadingEntry{stationName=" + stationName
                + ", measuredQuantity=" + measuredQuantity
                + ", sequenceNumber=" + sequenceNumber + "}";
    }
}
