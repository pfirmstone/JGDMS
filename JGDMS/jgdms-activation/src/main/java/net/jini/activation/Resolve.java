/*
 * Copyright 2018 peter.
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
package net.jini.activation;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * For serializable object graphs that have circular references with Objects that implement
 * readResolve, where objects containing the circular reference needs to
 * resolve the actual object during de-serialization to prevent ClassCastExceptions.
 * 
 * @author peter
 */
public interface Resolve extends Serializable {
    
    public Object readResolve() throws InvalidObjectException;
}
