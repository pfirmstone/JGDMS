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

package au.net.zeus.jgdms.der.object.fixtures;

/**
 * A plain, NON-{@code @AtomicSerial} value type that {@link ForeignSerializer}
 * declares as its {@code @Serializer(replaceObType = ProbeValue.class)} target. It
 * stands in for e.g. {@code Throwable} in the decode-admission attack model: a
 * serializer whose {@code replaceObType} is <em>not</em> assignable to a concrete slot
 * type (such as {@code X500Principal}).
 */
public final class ProbeValue {
    public ProbeValue() {
    }
}
