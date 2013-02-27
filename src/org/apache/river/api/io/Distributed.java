/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.river.api.io;

import java.io.Serializable;

/**
 * Distributed objects are immutable value objects with final fields that may
 * be freely replicated.
 * Distributed objects are not serialized, instead they are only created using a 
 * public constructor, public static factory method or builder object.
 * <p>
 * Distributed objects are free to evolve and may have completely different 
 * classes or be completely unequal after distribution to separate nodes, 
 * but must always share a common public interface or superclass for referential
 * purposes.
 * <p>
 * Distributed objects have no version, instead SerialFactory contains all 
 * information required to distribute and recreate any Distributed Object.
 * <p>
 * Distributed object all have a common serial form, defined by SerialFactory.
 * <p>
 * Distributed objects are value objects in domain driven design.
 * <p>
 * Remote objects are entity or service objects in domain driven design context.
 * <p>
 * Although final is not enforced, all fields must be final and safe
 * construction must be honored, distributed objects will be exposed to multiple
 * threads on multiple nodes, without synchronization or transactions.
 * <p>
 * Do not use Distributed if you don't intend to honor this contract, use
 * Serializable instead.
 * 
 * @author Peter Firmstone.
 */
public interface Distributed {
    SerialFactory substitute();
}
