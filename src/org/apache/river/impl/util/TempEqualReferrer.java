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

package org.apache.river.impl.util;

/**
 *
 * @param <T> 
 * @author peter
 */
class TempEqualReferrer<T> extends TempIdentityReferrer<T> {
    private final int hash;
    
    TempEqualReferrer(T t){
        super(t);
        int hash = 7;
        hash = 29 * hash + t.hashCode();
        hash = 29 * hash + t.getClass().hashCode();
        this.hash = hash;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Referrer)) {
            return false;
        }
        Object t2 = ((Referrer) o).get();
        return ( get().equals(t2) );
    }
    
    public int hashCode(){
        Object k = get();
        int hash = 7;
        if (k != null) {
            hash = 29 * hash + k.hashCode();
            hash = 29 * hash + k.getClass().hashCode();
        } else {
            hash = this.hash;
        }
        return hash;
    }
}
