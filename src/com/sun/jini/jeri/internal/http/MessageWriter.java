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

package com.sun.jini.jeri.internal.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Class for writing HTTP messages.  Each instance writes a single HTTP
 * message.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class MessageWriter {
    
    private static final int CHUNK_SIZE = 512;

    /* state values */
    private static final int START   = 0;
    private static final int HEADER  = 1;
    private static final int CONTENT = 2;
    private static final int DONE    = 3;
    
    private final OutputStream out;
    private final OutputStream cout;
    private int state = START;
    private Header header;

    /**
     * Creates new writer on top of given output stream.
     */
    MessageWriter(OutputStream out, boolean chunked) {
	this.out = out;
	cout = chunked ? (OutputStream) new ChunkedOutputStream()
		       : (OutputStream) new ByteArrayOutputStream();
    }
    
    /**
     * Writes HTTP message start line.
     */
    void writeStartLine(StartLine line) throws IOException {
	updateState(START, HEADER);
	line.write(out);
    }
    
    /**
     * "Writes" HTTP message header (the header may not actually be written
     * until after the message content length is known).  The caller should
     * avoid using the passed header after invoking this method.
     */
    void writeHeader(Header header) throws IOException {
	updateState(HEADER, CONTENT);
	if (cout instanceof ChunkedOutputStream) {
	    header.setField("Transfer-Encoding", "chunked");
	    header.setField("Content-Length", null);
	    header.write(out);
	} else {
	    this.header = header;
	}
    }
    
    /**
     * Writes message content.
     */
    void writeContent(byte[] b, int off, int len) throws IOException {
	updateState(CONTENT, CONTENT);
	cout.write(b, off, len);
    }
    
    /**
     * Writes message trailer (if not using chunked output, merges trailer with
     * header before writing), completing message output.  Flushes underlying
     * output stream once trailer has been written.
     */
    void writeTrailer(Header trailer) throws IOException {
	updateState(CONTENT, DONE);
	cout.close();
	if (cout instanceof ChunkedOutputStream) {
	    if (trailer != null) {
		trailer.write(out);
	    } else {
		writeLine(out, "");
	    }
	} else {
	    ByteArrayOutputStream bout = (ByteArrayOutputStream) cout;
	    header.merge(trailer);
	    header.setField("Content-Length", Integer.toString(bout.size()));
	    header.setField("Transfer-Encoding", null);
	    header.write(out);
	    bout.writeTo(out);
	}
	out.flush();
    }
    
    /**
     * Flushes written data to underlying output stream.  Throws
     * IllegalStateException if called after message has been fully written.
     */
    void flush() throws IOException {
	if (state == DONE) {
	    throw new IllegalStateException();
	}
	if (state == CONTENT) {
	    cout.flush();
	}
	out.flush();
    }

    /**
     * Writes line to given output stream in ASCII, terminated by HTTP
     * end-of-line sequence "\r\n".
     */
    static void writeLine(OutputStream out, String line) throws IOException {
	// REMIND: use Charset?
	line += "\r\n";
	int len = line.length();
	for (int i = 0; i < len; i++) {
	    out.write(line.charAt(i));
	}
    }

    private void updateState(int oldState, int newState) {
	if (state != oldState) {
	    throw new IllegalStateException();
	}
	state = newState;
    }

    /**
     * Output stream for writing chunked transfer-coded content.
     */
    private class ChunkedOutputStream extends OutputStream {

	private final byte[] buf = new byte[CHUNK_SIZE];
	private int pos = 0;

	ChunkedOutputStream() {}
	
	public void write(int val) throws IOException {
	    write(new byte[]{ (byte) val }, 0, 1);
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
	    while (len > 0) {
		int avail = buf.length - pos;
		if (avail > 0) {
		    int ncopy = Math.min(len, avail);
		    System.arraycopy(b, off, buf, pos, ncopy);
		    pos += ncopy;
		    off += ncopy;
		    len -= ncopy;
		} else {
		    flush();
		}
	    }
	}
	
	public void flush() throws IOException {
	    if (pos > 0) {
		writeLine(out, Integer.toString(pos, 16));
		out.write(buf, 0, pos);
		writeLine(out, "");
		pos = 0;
	    }
	}

	public void close() throws IOException {
	    flush();
	    writeLine(out, "0");
	}
    }
}
