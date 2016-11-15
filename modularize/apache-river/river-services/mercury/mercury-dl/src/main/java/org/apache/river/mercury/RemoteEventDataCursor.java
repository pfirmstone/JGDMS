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
package org.apache.river.mercury;

import java.io.IOException;
import java.io.Serializable;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
/**
 * Trivial class (struct) that simply holds the current read count
 * and the associated (next unread) read position. U?sed as the client-side
 * cookie for PersistentEventLog.
 * @since 2.1
 */
@AtomicSerial
class RemoteEventDataCursor implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long readCount;
    private final long readPosition;

    RemoteEventDataCursor(long count, long cursor) {
        readCount = count;
        readPosition = cursor;
    }
    
    public RemoteEventDataCursor(GetArg arg) throws IOException{
	this(arg.get("readCount", 0L),
	     arg.get("readPosition", 0L));
    }

    long getReadCount() { return readCount; }
    long getReadPosition() { return readPosition; }
}
