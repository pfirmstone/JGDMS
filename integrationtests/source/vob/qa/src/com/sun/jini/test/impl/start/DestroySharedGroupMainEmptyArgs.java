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
package com.sun.jini.test.impl.start;

import java.util.logging.Level;

import com.sun.jini.qa.harness.TestException;
import com.sun.jini.start.DestroySharedGroup;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;

import java.util.Arrays;

public class DestroySharedGroupMainEmptyArgs extends StarterBase {
    public void run() throws Exception {
	DestroySharedGroup.main(new String[] {});
	String[] keys = new String[] {"service.config.empty"};
	if (!checkReport(Arrays.asList(keys), handler.getKeys())) {
            throw new TestException(
                "Required keys not generated.");
        }
	return;
    }
}

