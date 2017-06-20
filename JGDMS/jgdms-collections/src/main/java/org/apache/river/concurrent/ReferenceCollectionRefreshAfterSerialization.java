/* Copyright (c) 2010-2012 Zeus Project Services Pty Ltd.
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

package org.apache.river.concurrent;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author peter
 */
abstract class ReferenceCollectionRefreshAfterSerialization<T> 
                            extends ReadResolveFixCollectionCircularReferences<T> {

    ReferenceCollectionRefreshAfterSerialization() {
    }

    @Override
    final Collection<T> build() throws InstantiationException, IllegalAccessException, ObjectStreamException {
        Collection<T> result = super.build();
        /* What if the underlying collection is immutable?
         * The ReferenceQueuingFactory is unknown until the ReferenceCollection
         * has been built.
         */
        if (result instanceof ReferenceCollection) {
            ReferenceCollection<T> refCol = (ReferenceCollection<T>) result;
            ReferenceQueuingFactory<T, Referrer<T>> rqf = refCol.getRQF();
            Iterator<Referrer<T>> colIt = getCollection().iterator();
            while (colIt.hasNext()) {
                Referrer<T> ref = colIt.next();
                if (ref == null) {
                    continue;
                }
                if (ref instanceof AbstractReferrerDecorator) {
                    ((AbstractReferrerDecorator<T>) ref).refresh(rqf);
                } else {
                    throw new InvalidObjectException("Referrer's must be an AbstractReferrerWraper for ReferenceCollection");
                }
            }
        }
        return result;
    }
    
}
