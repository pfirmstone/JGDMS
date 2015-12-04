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
package org.apache.river.test.impl.start;

import org.apache.river.qa.harness.QATestEnvironment;
import org.apache.river.qa.harness.QAConfig;
import org.apache.river.qa.harness.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;


public abstract class StarterBase extends QATestEnvironment {

    protected MyHandler handler = null;

    public Test construct(QAConfig sysConfig) throws Exception {
	super.construct(sysConfig);
	Logger l = Logger.getLogger("org.apache.river.start.service.starter");
	l.setLevel(Level.ALL);
	handler = new MyHandler();
	l.addHandler(handler);
        if (getConfig().getBooleanConfigVal("org.apache.river.qa.harness.shared",
                                       true))
        {
            getManager().startService("sharedGroup");
        }
        return new Test() {

            public void run() throws Exception {
                // do nothing.
            }
            
        };
    }

    static class MyHandler extends Handler {
        private final ArrayList keys = new ArrayList();
        public void publish(LogRecord record) {
	    keys.add(record.getMessage());
	}
        public void flush() {;}
	public void close() throws SecurityException {;}
	public List getKeys() { return keys; }

    }
    
    public static boolean checkReport(List required, List generated) {
        boolean containsAll = true;
        Object key = null;
	for (int i=0; i < required.size(); i++) {
	    key = required.get(i);
	    if (!generated.contains(key)) {
	        containsAll = false;
		System.out.println("Key [" + key 
                    + "] not found in generated output");
	    }
	}
	if (!containsAll) {
	    System.out.println("Required keys: " + required);
	    System.out.println("Generated keys: " + generated);
	}
	return containsAll;
    }
}
