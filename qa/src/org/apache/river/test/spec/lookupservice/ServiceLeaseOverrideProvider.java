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
package org.apache.river.test.spec.lookupservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;
import org.apache.river.api.io.AtomicSerial.PutArg;
import org.apache.river.api.io.AtomicSerial.SerialForm;
import org.apache.river.qa.harness.OverrideProvider;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.TestException;

@AtomicSerial
public class ServiceLeaseOverrideProvider implements OverrideProvider {

    private static final long serialVersionUID = 1L;

    public static SerialForm[] serialForm() {
        return new SerialForm[] {
            new SerialForm("serviceLeaseDuration", long.class),
            new SerialForm("eventLeaseDuration", long.class)
        };
    }

    public static void serialize(PutArg arg, ServiceLeaseOverrideProvider o) throws IOException {
        arg.put("serviceLeaseDuration", o.serviceLeaseDuration);
        arg.put("eventLeaseDuration", o.eventLeaseDuration);
        arg.writeArgs();
    }

    private long serviceLeaseDuration;
    private long eventLeaseDuration;

    public ServiceLeaseOverrideProvider(QAConfig sysConfig,
        long serviceLeaseDuration, long eventLeaseDuration)
    {
        this.serviceLeaseDuration = serviceLeaseDuration;
        this.eventLeaseDuration = eventLeaseDuration;
    }

    /** {@code @AtomicSerial} deserialization constructor. */
    public ServiceLeaseOverrideProvider(GetArg arg) throws IOException, ClassNotFoundException {
        this.serviceLeaseDuration = arg.get("serviceLeaseDuration", 0L);
        this.eventLeaseDuration = arg.get("eventLeaseDuration", 0L);
    }
    
    public String[] getOverrides(QAConfig config, String servicePrefix, int index) throws TestException {
        List list = new ArrayList();
	// servicePrefix may be null for test overrides
        if ("net.jini.core.lookup.ServiceRegistrar".equals(servicePrefix)) {
            list.add("org.apache.river.reggie.minMaxServiceLease");
            list.add("" + serviceLeaseDuration + "L");
            list.add("org.apache.river.reggie.minMaxEventLease");
            list.add("" + eventLeaseDuration + "L");
        }
        return (String[]) list.toArray(new String[0]);
    }
}

