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

package au.net.zeus.jgdms.der.schema;

import au.net.zeus.jgdms.der.DerException;
import net.jini.io.MarshalledInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The per-class memoisation of {@link SchemaGenerator#generateChain} (the performance fix): the
 * schema chain -- reflection over {@code serialForm()} plus SHA-256 digests -- is computed once per
 * class, not per marshalled object. (Byte-for-byte correctness of the cached chains is covered by the
 * full round-trip suite running with the cache active.)
 */
public class SchemaGeneratorCacheTest {

    @Test
    public void chainIsMemoisedPerClass() throws Exception {
        SchemaChain.Result a = SchemaGenerator.generateChain(MarshalledInstance.class);
        SchemaChain.Result b = SchemaGenerator.generateChain(MarshalledInstance.class);
        assertSame(a, b, "generateChain must return the same memoised SchemaChain.Result per class");
    }

    @Test
    public void failureIsDeterministicAndCached() {
        // String is not @AtomicSerial -> a deterministic failure, cached and re-thrown fresh each call.
        DerException first  = assertThrows(DerException.class, () -> SchemaGenerator.generateChain(String.class));
        DerException second = assertThrows(DerException.class, () -> SchemaGenerator.generateChain(String.class));
        assertEquals(first.getMessage(), second.getMessage(), "cached failure must be consistent");
    }
}
