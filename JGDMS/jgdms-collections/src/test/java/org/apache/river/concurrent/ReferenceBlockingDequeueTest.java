/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.concurrent;

import org.junit.Test;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 *
 * @author Dan Rollo
 */
public class ReferenceBlockingDequeueTest {
    @Test
    public void testEqualsNotOverridden() {
        final BlockingDeque<Referrer<String>> deque = new LinkedBlockingDeque<Referrer<String>>(1);
        final ReferenceBlockingDeque<String> item1 = new ReferenceBlockingDeque<String>(deque, Ref.STRONG, false, 0);
        final ReferenceBlockingDeque<String> item2 = new ReferenceBlockingDeque<String>(deque, Ref.STRONG, false, 0);
        assertThat(item1, not(equalTo(item2)));
    }
}
