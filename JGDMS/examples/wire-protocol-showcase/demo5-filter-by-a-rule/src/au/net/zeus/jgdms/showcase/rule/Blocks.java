/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.net.zeus.jgdms.showcase.rule;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A trivial length-prefixed container so the producer can hand the reader several
 * byte blocks in one file. This is just framing (a length, then that many bytes) —
 * it carries no class, and the reader depends on it without depending on any record
 * class. It stands in for "the producer sent the reader some bytes over a connection".
 */
final class Blocks {

    private Blocks() {}

    static void writeBlock(DataOutputStream out, byte[] block) throws IOException {
        out.writeInt(block.length);
        out.write(block);
    }

    private static final int MAX_BLOCK_BYTES = 16 * 1024 * 1024; // mirrors the DER default input budget

    static byte[] readBlock(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > MAX_BLOCK_BYTES) {
            throw new IOException("block length out of bounds: " + len);
        }
        byte[] block = new byte[len];
        in.readFully(block);
        return block;
    }
}
