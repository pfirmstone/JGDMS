/*
 * Copyright 2017 peter.
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
package org.apache.river.landlord;

/**
 * This is a convenience abstract LeasedResource that creates a 
 * new LeasedResource instead of modifying the original.
 *
 * @author peter
 * @since 3.1
 */
public abstract class AbstractLeasedResource implements LeasedResource, Cloneable {
    
    @Override
    public AbstractLeasedResource clone() {
	try {
	    return (AbstractLeasedResource) super.clone();	   
	} catch (CloneNotSupportedException e){
	    throw new AssertionError("This should never happen", e);
	}
    }
    
    /**
     * Returns a copy of the lease with a new expiration time.
     * 
     * @param expiration The new expiration time in milliseconds
     *                      since the beginning of the epoch
     * @return copy with new expiration time.
     */
    public AbstractLeasedResource renew(long expiration){
	AbstractLeasedResource renewed = clone();
	renewed.setExpiration(expiration);
	return renewed;
    }
    
}
