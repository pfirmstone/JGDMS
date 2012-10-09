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

package com.sun.jini.jeri.internal.mux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

//import org.testng.Assert;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
//import org.testng.annotations.Test;


/**
 * Experiment with the behavior of MuxClient's handshake when a "server" never responds.
 * @version 1.0, May 4, 2011 12:30:55 PM
 * 
 */
public class MuxStartTimeoutTest {
    @Test
    public void test() throws IOException, InterruptedException {
        System.out.println("Test MuxClient handshake when server non responsive");
        // make fake input and output streams.
        OutputStream os = new ByteArrayOutputStream();
        InputStream is = new InputStream() {
            @Override
            public synchronized int read() throws IOException {
                try {
                    // block indefinitely
                    while (true)
                        wait();
                } catch (InterruptedException e) {
                    return 0;
                }
            }
        };

        final AtomicBoolean finished = new AtomicBoolean(false);
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        final AtomicBoolean failed = new AtomicBoolean(false);
        final MuxClient muxClient = new MuxClient(os, is);
        try {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        muxClient.start();
                        succeeded.set(true);
                    } catch (IOException e) {
                        failed.set(true);
                    }
                    finished.set(true);
                }
            });
            t.start();
            t.join(20000);
            assertTrue(finished.get());
            assertFalse(succeeded.get());
            assertTrue(failed.get());
            if (!t.isInterrupted())
                t.interrupt();
        } finally {
            muxClient.shutdown("end of test");
        }
    }
}
