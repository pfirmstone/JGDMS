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
package org.apache.river.test.spec.jeri.util;

import java.rmi.Remote;
import java.util.logging.Logger;

public class FakeRemoteImpl implements Remote {

    Object fakeMethodReturn;
    Throwable fakeMethodException;

    public Object fakeMethod(Object o) throws Throwable {
        Logger logger = Logger.getLogger("org.apache.river.qa.harness.test");
        logger.entering(getClass().getName(),"fakeMethod(Object:" + o + ")");

        if (fakeMethodException != null) {
            throw fakeMethodException;
        } else {
            return fakeMethodReturn;
        }
    }

    public void setFakeMethodReturn(Object o) {
        fakeMethodReturn = o;
    }
    public void setFakeMethodException(Throwable r) {
        fakeMethodException = r;
    }

}
