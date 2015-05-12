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
package org.apache.river.jeri.internal.runtime;

final class SequenceEntry {

    private volatile long sequenceNum;
    private volatile boolean keep;

    SequenceEntry(long sequenceNum) {
        super();
        this.sequenceNum = sequenceNum;
        keep = false;
    }
    
    SequenceEntry(long sequenceNum, boolean strong){
        this.sequenceNum = sequenceNum;
        this.keep = strong;
    }
    
    /**
     * If the passed in sequence number is greater than the current number,
     * it is updated.
     * If 
     * @param seqNum - passed in sequence number.
     * @param strong - strong clean call is kept in the event of an update.
     * @return true if the sequence number is updated.
     */
    boolean update(long seqNum, boolean strong){
        synchronized (this){
            if (seqNum > sequenceNum){
                sequenceNum = seqNum;
                if (strong) keep = true;
                return true;
            }
            return false;
        }
    }
    
    boolean keep(){
        return keep;
    }
}
