/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.river.api.security;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The check agrees with what the boot class loader has, whichever JVM this is. */
public class DirtyChaiCheckTest {

    @Test
    public void verifyFailsExactlyWhenJavaBaseLacksAClass() {
        List<String> missing = DirtyChaiCheck.missing();
        System.out.println("DirtyChaiCheck: java.base lacks " + missing);
        if (missing.isEmpty()) {
            DirtyChaiCheck.verify();
        } else {
            try {
                DirtyChaiCheck.verify();
                fail("verify() should refuse a JVM whose java.base lacks " + missing);
            } catch (IllegalStateException e) {
                System.out.println("DirtyChaiCheck: " + e.getMessage());
                assertTrue(e.getMessage(), e.getMessage().contains("DirtyChai"));
                for (String m : missing) assertTrue(e.getMessage(), e.getMessage().contains(m));
            }
        }
    }
}
