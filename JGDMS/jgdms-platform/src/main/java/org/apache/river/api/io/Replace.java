/*
 * Copyright 2021 The Apache Software Foundation.
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
package org.apache.river.api.io;

import java.io.ObjectStreamException;

/**
 *
 * @author peter
 */
public interface Replace {
    
    /**
     * A writeReplace method to allow a class to nominate a replacement object to be written to the stream
     * <a href = https://docs.oracle.com/en/java/javase/14/docs/specs/serialization/output.html#the-writereplace-method>
     * Section 2.5, "The writeReplace Method"</a>
     * @return
     * @throws ObjectStreamException 
     */
    Object writeReplace() throws ObjectStreamException;
    
}
