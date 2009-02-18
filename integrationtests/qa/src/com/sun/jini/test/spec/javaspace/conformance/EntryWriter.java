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
package com.sun.jini.test.spec.javaspace.conformance;

// net.jini
import net.jini.core.lease.Lease;
import net.jini.core.entry.Entry;
import net.jini.space.JavaSpace;


/**
 * Class which writes sample entry to the space in specified time.
 *
 * @author Mikhail A. Markov
 */
public class EntryWriter extends Thread {

    /** SimpleEntry to be written */
    private SimpleEntry se;

    /** Time in which entry will be written */
    private long timeout;

    /** JavaSpace to which we need to write an entry */
    private JavaSpace space;

    /**
     * Constructor which initialize fields of the class
     *
     * @param se SimpleEntry to be written to the space.
     * @param timeout Time in which entry will be written.
     * @param space JavaSpace to which we need to write an entry.
     */
    public EntryWriter(SimpleEntry se, long timeout, JavaSpace space) {
        this.se = se;
        this.timeout = timeout;
        this.space = space;
    }

    /**
     * Method which will write the entry to the space in specified time.
     */
    public void run() {
        try {
            sleep(timeout);
        } catch (InterruptedException e) {}

        try {
            space.write(se, null, Lease.FOREVER);
        } catch (Exception ex) {}

        try {
            sleep(1000);
        } catch (InterruptedException e) {}
    }
}
