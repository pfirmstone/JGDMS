/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.jini.test.spec.lookupservice;

import net.jini.core.event.RemoteEvent;
import java.util.Comparator;

/**
 * Comparator to order RemoteEvents by their event id, followed by sequence 
 * number.
 * 
 * Enables clients receiving RemoteEvent's to order those events.
 * 
 * @author Peter Firmstone
 * @since 3.0.0
 */
public class RemoteEventComparator implements Comparator<RemoteEvent>{

    @Override
    public int compare(RemoteEvent o1, RemoteEvent o2) {
        boolean Null1 = o1 == null;
        boolean Null2 = o2 == null;
        if (Null1 && Null2) return 0;
        if (Null1 && !Null2) return -1;
        if (!Null1 && Null2) return 1;
        long eventID1 = o1.getID();
        long eventID2 = o2.getID();
        if (eventID1 < eventID2) return -1;
        if (eventID1 > eventID2) return 1;
        long seq1 = o1.getSequenceNumber();
        long seq2 = o2.getSequenceNumber();
        if (seq1 < seq2) return -1;
        if (seq1 > seq2) return 1;
        return 0;
    }
    
}
