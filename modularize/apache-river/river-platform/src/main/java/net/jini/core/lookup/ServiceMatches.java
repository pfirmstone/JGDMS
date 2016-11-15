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
package net.jini.core.lookup;

import java.io.IOException;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * An instance of this class is used for the return value when looking up
 * multiple items in the lookup service.
 * 
 * Public fields have been made final to ensure that ServiceItem's are safely 
 * published at the time ServiceMatches is constructed.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 1.0
 */
@AtomicSerial
public class ServiceMatches implements java.io.Serializable {

    private static final long serialVersionUID = -5518280843537399398L;

    /**
     * Matching items (up to maxMatches from lookup method).
     *
     * @serial
     */
    public final ServiceItem[] items;
    /**
     * Total number of matching items.
     *
     * @serial
     */
    public final int totalMatches;

    /**
     * {@link AtomicSerial} convenience constructor.
     * 
     * Since this class is mutable it should be cloned during deserialization.
     * 
     * @param arg
     * @throws IOException 
     */
    public ServiceMatches(GetArg arg) throws IOException {
	// The only invariant is the ServiceItem[] type check, which is done
	// before super() is called.
	// arg can be null, required to pass ToStringTest legacy test.
	this(arg == null? null: arg.get("items", null, ServiceItem[].class),
	     arg == null? 0: arg.get("totalMatches", 0));
    }

    /**
     * Simple constructor.
     *
     * @param items matching items
     * @param totalMatches total number of matching items
     */
    public ServiceMatches(ServiceItem[] items, int totalMatches) {
	this.items = items;
	this.totalMatches = totalMatches;
    }
    
    /**
     * Returns a <code>String</code> representation of this 
     * <code>ServiceMatches</code>.
     * @return <code>String</code> representation of this 
     * <code>ServiceMatches</code>
     */
    public String toString() {
	StringBuffer sBuffer = new StringBuffer();
	sBuffer.append(
	       getClass().getName()).append(
	       "[totalMatches=").append(
	       totalMatches).append(
	       ", items=");
	if (items != null) {
            sBuffer.append("[");
            if (items.length > 0) {
                for (int i = 0; items.length > 0 && i < items.length - 1; i++)
                    sBuffer.append(items[i]).append(" ");
                sBuffer.append(items[items.length - 1]);
            }
            sBuffer.append("]");
	} else {
	    sBuffer.append((Object)null);
	}
	return sBuffer.append("]").toString();
    }
}
