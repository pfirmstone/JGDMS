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
package org.apache.river.test.impl.outrigger.matching;

// All other imports
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * RandomList class implements random access methods for obtaining
 * list items.
 * Note that this class is intentionally left package private.
 */
class RandomList extends LinkedList {

    /**
     * Constructor is not explicitly necessary in this case, but
     * used for completeness.
     */
    RandomList() {
        super();
    }

    /**
     * Generates a random integer from 0 to <code>size()</code>-1.
     * The <code>size</code> argument is assumed to be the length
     * of a collection and the returned value is uniformly distributed
     * int in the range of (0, size-1).
     */
    static int getRandomIndex(int size) {

        // Generate a random integer from 0 to size()-1
        return (int) ((java.lang.Math.random() * (size - 1)) + 0.5);
    }

    /**
     * Returns and removes a randomly selected object from the list.
     */
    Object removeRandomItem() {

        // Defer removal to super.remove()
        return remove(getRandomIndex(size()));
    }

    /**
     * Returns a randomly selected object from the list.
     */
    Object getRandomItem() {

        // Defer selection to super.get()
        return get(getRandomIndex(size()));
    }

    /**
     * Unit test driver for standalone testing.
     */
    public static void main(String[] args) {

        // Create empty list
        RandomList rl = new RandomList();
        int i = 0;

        // Fill list with Integer objects
        for (i = 0; i < 10; ++i) {
            rl.add(new Integer(i));
        }

        // Traverse list in iterator order
        System.out.println("Ordered elements:");
        Iterator it = rl.iterator();

        while (it.hasNext()) {
            System.out.println(it.next());
        }

        // Traverse list in random order
        System.out.println("Random sampling:");

        for (i = 0; i < rl.size(); ++i) {
            System.out.println(rl.getRandomItem());
        }
    }
}
