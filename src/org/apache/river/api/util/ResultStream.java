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

package org.apache.river.api.util;

import java.io.IOException;

/**
 * This interface is similar to an Enumerator, it is designed to return
 * results incrementally in loops, however unlike an Enumerator, there is no
 * check first operation as implementors must return a null value after
 * the backing data source has been exhausted. So this terminates like a stream
 * by returning a null value.
 * 
 * @author Peter Firmstone
 */
public interface ResultStream<T> {
    /**
     * Get next T, call from a loop until T is null;
     * @return T unless end of stream in which case null is returned.
     */
    public T get() throws IOException;
    /**
     * Close the result stream, this allows the implementer to close any
     * resources prior to deleting reference.
     * 
     * At an arbitrary future date, this interface will extend Closeable in Java 8.
     */
    public void close() throws IOException;
}
