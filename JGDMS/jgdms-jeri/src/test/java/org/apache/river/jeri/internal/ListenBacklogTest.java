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

package org.apache.river.jeri.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * The backlog helper reads one system property: a missing or non-integer value
 * requests {@code Integer.MAX_VALUE} (the operating system clamps it), an
 * integer is used as given, and a value below 1 means the JDK default.
 */
public class ListenBacklogTest {

    private String saved;

    @Before
    public void setUp() {
        saved = System.getProperty(ListenBacklog.PROPERTY);
        System.clearProperty(ListenBacklog.PROPERTY);
    }

    @After
    public void tearDown() {
        ListenBacklogProbe.restore(saved);
    }

    @Test
    public void defaultRequestsTheMaximum() {
        Assert.assertEquals(Integer.MAX_VALUE, ListenBacklog.get());
    }

    @Test
    public void propertyOverridesTheDefault() {
        System.setProperty(ListenBacklog.PROPERTY, "4");
        Assert.assertEquals(4, ListenBacklog.get());
        System.setProperty(ListenBacklog.PROPERTY, "1");
        Assert.assertEquals(1, ListenBacklog.get());
        System.setProperty(ListenBacklog.PROPERTY, "0x100");
        Assert.assertEquals(256, ListenBacklog.get());
    }

    @Test
    public void propertyIsReadOnEachCall() {
        System.setProperty(ListenBacklog.PROPERTY, "7");
        Assert.assertEquals(7, ListenBacklog.get());
        System.clearProperty(ListenBacklog.PROPERTY);
        Assert.assertEquals(Integer.MAX_VALUE, ListenBacklog.get());
    }

    @Test
    public void belowOneMeansTheJdkDefault() {
        for (String v : new String[] { "0", "-1", "-2147483648" }) {
            System.setProperty(ListenBacklog.PROPERTY, v);
            Assert.assertEquals(v, 0, ListenBacklog.get());
        }
    }

    @Test
    public void notAnIntegerFallsBackToTheDefault() {
        for (String v : new String[] { "", "many", "1.5", "99999999999" }) {
            System.setProperty(ListenBacklog.PROPERTY, v);
            Assert.assertEquals(v, Integer.MAX_VALUE, ListenBacklog.get());
        }
    }
}
