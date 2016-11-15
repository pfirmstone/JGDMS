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
package org.apache.river.outrigger;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Object <code>OutriggerServerImpl</code> uses to pass context between
 * related sub-queries.
 */
@AtomicSerial
class OutriggerQueryCookie 
    implements OutriggerServer.QueryCookie, java.io.Serializable 
{
    private static final long serialVersionUID = 1L;

    /**
     * The time the first sub-query in a given series was started
     */
    final long startTime;

    /**
     * Create a new <code>OutriggerQueryCookie</code> with
     * the specified value for the <code>startTime</code>.
     */
    OutriggerQueryCookie(long startTime) {
	this.startTime = startTime;
    }

    OutriggerQueryCookie(GetArg arg)throws IOException {
	this(arg.get("startTime", 0L));
    }

    @Override
    public String toString() {
	return "OutriggerQueryCookie startTime:" + startTime;
    }
}

