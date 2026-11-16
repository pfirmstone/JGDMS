/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.cel.testsupport;

/**
 * Builds a message with a {@link StringBuilder}, because the house rule is no {@code +} string concatenation in
 * Java (operator, 2026-10-03). Every part is appended in order, so the text is what a {@code +} chain over the
 * same parts would give, with {@code null} as "null".
 */
public final class Msg {

    private Msg() {}

    public static String msg(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) sb.append(part);
        return sb.toString();
    }
}
