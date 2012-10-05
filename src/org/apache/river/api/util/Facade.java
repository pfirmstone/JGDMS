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

/**
 * @since 2.2.0
 */
public interface Facade<T> {
    public T reveal();
    /**
     * Equals must be implemented to check against another Facade by utilising
     * reveal, so the underlaying parameter objects can be compared.
     * It must not return true if comparing the Facade against a
     * non-Facade object, otherwise the equals contract would not be symmetrical
     * and would violate the equals contract.
     * 
     * A Facade must not be allowed to contain another Facade implementation as
     * this too would voilate the equals contract.  The constructor should throw
     * an IllegalArgumentException.
     * 
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o);
    /**
     * The hashcode from T.hashCode();
     * @return hashCode for underlying parameter T.
     */
    @Override
    public int hashCode();
}
