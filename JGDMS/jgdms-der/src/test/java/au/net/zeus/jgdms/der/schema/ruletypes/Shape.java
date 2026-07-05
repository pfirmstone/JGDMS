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

package au.net.zeus.jgdms.der.schema.ruletypes;

import org.apache.river.api.io.AtomicSerial;

/**
 * A polymorphic {@code @AtomicSerial} slot interface for the bounded-wildcard edge case
 * (memo §3.2 / E7): a field {@code Set<? extends Shape>} must resolve to {@code set:@AtomicSerial}
 * (the upper bound {@code Shape}) and stay fully typed -- NOT {@code Any} -- because concrete
 * subtypes self-identify at the value level via their schema digest.
 */
@AtomicSerial
public interface Shape {
    double area();
}
