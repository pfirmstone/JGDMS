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

package org.apache.river.jeri.internal.mux;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.jini.jeri.InboundRequest;
import net.jini.jeri.RequestDispatcher;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An IOException from a write to the channel takes the connection down and
 * fails the writes that wait, whatever its message.  The message of one, "A
 * non-blocking socket operation could not be completed immediately", was once
 * swallowed (JDK bug 4854354, fixed in Java 5), which also hid a real error.
 */
public class SocketChannelWriteFailureTest {

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @After
    public void tearDown() {
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
    }

    private SocketChannel[] pair() throws Exception {
        Path dir = Files.createTempDirectory("muxwrite");
        Path path = dir.resolve("s");
        toClose.add(() -> {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        });
        ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        toClose.add(listener);
        listener.bind(UnixDomainSocketAddress.of(path));
        SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX);
        client.connect(listener.getLocalAddress());
        SocketChannel server = listener.accept();
        toClose.add(client);
        toClose.add(server);
        return new SocketChannel[] { client, server };
    }

    private void failsTheConnection(String message) throws Exception {
        SocketChannel[] owner = pair();
        SocketChannel[] io = pair();
        MuxServer mux = new MuxServer(owner[1], new RequestDispatcher() {
            public void dispatch(InboundRequest request) { }
        });
        toClose.add(() -> mux.shutdown("test over"));
        SocketChannelConnectionIO connectionIO = new SocketChannelConnectionIO(mux, io[0]);
        connectionIO.writer = (bufs, off, len) -> {
            throw new IOException(message);
        };
        IOFuture future = connectionIO.futureSend(
            ByteBuffer.wrap(new byte[8]), ByteBuffer.wrap(new byte[100]));
        long end = System.nanoTime() + 10_000_000_000L;
        while (!mux.muxDown && System.nanoTime() < end) {
            Thread.sleep(10);
        }
        assertTrue("the connection must go down: " + message, mux.muxDown);
        assertTrue("and say why: " + mux.muxDownMessage,
                   mux.muxDownMessage.contains(message));
        try {
            future.waitUntilDone();
            fail("a write that failed must fail the future");
        } catch (IOException expected) {
        }
    }

    @Test(timeout = 60000)
    public void anIoExceptionFailsTheConnectionAndWhatWaits() throws Exception {
        failsTheConnection("Broken pipe");
    }

    @Test(timeout = 60000)
    public void theMessageOfBug4854354IsNotSwallowed() throws Exception {
        failsTheConnection(
            "A non-blocking socket operation could not be completed immediately");
    }
}
