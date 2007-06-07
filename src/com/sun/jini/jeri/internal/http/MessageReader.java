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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.StringTokenizer;

/**
 * Class for reading HTTP messages.  Each instance reads a single HTTP message.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class MessageReader {
    
    /* state values */
    private static final int START   = 0;
    private static final int HEADER  = 1;
    private static final int CONTENT = 2;
    private static final int DONE    = 3;
    
    private static final byte[] sink = new byte[256];

    private final InputStream in;
    private final boolean noContent;
    private int state = START;
    private StartLine sline;
    private InputStream cin;

    /**
     * Creates new reader on top of given input stream.  If noContent is true,
     * incoming message is assumed to be bodiless (e.g., a HEAD response).
     */
    MessageReader(InputStream in, boolean noContent) {
	this.in = in;
	this.noContent = noContent;
    }
    
    /**
     * Reads in HTTP message start line.
     */
    StartLine readStartLine() throws IOException {
	updateState(START, HEADER);
	sline = new StartLine(in);
	return sline;
    }
    
    /**
     * Reads in HTTP message header.
     */
    Header readHeader() throws IOException {
	updateState(HEADER, CONTENT);
	Header header = new Header(in);

	if (!noContent && contentIndicated(sline, header)) {
	    String clen;
	    if (header.containsValue("Transfer-Encoding", "chunked", true)) {
		cin = new ChunkedInputStream();
	    } else if ((clen = header.getField("Content-Length")) != null) {
		int len;
		try {
		    len = Integer.parseInt(clen);
		} catch (Exception ex) {
		    throw new HttpParseException("invalid content length");
		}
		if (len < 0) {
		    throw new HttpParseException("invalid content length");
		}
		cin = new BoundedInputStream(len);
	    } else if (sline.isRequest) {
		throw new HttpParseException("request length undeclared");
	    } else {
		cin = in;
	    }
	} else {
	    cin = new BoundedInputStream(0);
	}

	return header;
    }
    
    /**
     * Reads message content.
     */
    int readContent(byte[] b, int off, int len) throws IOException {
	updateState(CONTENT, CONTENT);
	return cin.read(b, off, len);
    }
    
    /**
     * Returns count of available message content.
     */
    int availableContent() throws IOException {
	updateState(CONTENT, CONTENT);
	return cin.available();
    }
    
    /**
     * Reads in message trailer after consuming any unread content data.
     * Returns null if message doesn't have a trailer.
     */
    Header readTrailer() throws IOException {
	updateState(CONTENT, CONTENT);
	while (cin.read(sink) != -1) {
	}

	updateState(CONTENT, DONE);
	if (cin instanceof ChunkedInputStream) {
	    Header trailer = new Header(in);
	    return (trailer.size() > 0) ? trailer : null;
	} else {
	    return null;
	}
    }

    /**
     * Reads and returns next line from stream, or null if at end of stream.
     * Expects ASCII lines terminated by the HTTP end-of-line sequence "\r\n".
     */
    static String readLine(InputStream in) throws IOException {
	// REMIND: use Charset?
	int c = in.read();
	if (c == -1) {
	    return null;
	}
	StringBuffer sbuf = new StringBuffer();
	do {
	    if (c == '\r') {
		if ((c = in.read()) == '\n') {
		    break;
		}
		sbuf.append('\r');
	    } else {
		sbuf.append((char) c);
		c = in.read();
	    }
	} while (c != -1);
	return sbuf.toString();
    }

    private void updateState(int oldState, int newState) {
	if (state != oldState) {
	    throw new IllegalStateException();
	}
	state = newState;
    }
    
    /**
     * Returns true if given start line and header indicate a content body.
     */
    private static boolean contentIndicated(StartLine sline, Header header) {
	if (!sline.isRequest && 
	    ((sline.status / 100) == 1 ||
	     sline.status == HttpURLConnection.HTTP_NO_CONTENT ||
	     sline.status == HttpURLConnection.HTTP_NOT_MODIFIED))
	{
	    return false;
	}
	
	if (header.getField("Transfer-Encoding") != null ||
	    header.getField("Content-Length") != null)
	{
	    return true;
	}

	return !sline.isRequest;
    }
    
    /**
     * Input stream for reading bounded content data.
     */
    private class BoundedInputStream extends InputStream {
	
	private int bound;
	
	BoundedInputStream(int bound) {
	    this.bound = bound;
	}
	
	public int read() throws IOException {
	    byte[] b = new byte[1];
	    return (read(b, 0, 1) != -1) ? b[0] & 0xFF : -1;
	}
	
	public int read(byte[] b, int off, int len) throws IOException {
	    if (bound == 0) {
		return -1;
	    } else {
		int n = in.read(b, off, Math.min(bound, len));
		if (n != -1) {
		    bound -= n;
		}
		return n;
	    }
	}
	
	public int available() throws IOException {
	    return Math.min(bound, in.available());
	}
    }
    
    /**
     * Input stream for reading chunked content data.
     */
    private class ChunkedInputStream extends InputStream {

	private byte[] buf;
	private int pos = 0;
	private int lim = 0;
	
	ChunkedInputStream() {}

	public int read() throws IOException {
	    byte[] b = new byte[1];
	    return (read(b, 0, 1) != -1) ? b[0] & 0xFF : -1;
	}

	public int read(byte[] b, int off, int len) throws IOException {
	    while (pos >= lim) {
		refill();
	    }
	    if (pos < 0) {
		return -1;
	    }
	    int n = Math.min(lim - pos, len);
	    System.arraycopy(buf, pos, b, off, n);
	    pos += n;
	    return n;
	}
	
	public int available() throws IOException {
	    while (pos >= lim) {
		refill();
	    }
	    return (pos >= 0) ? (lim - pos) : 0;
	}

	private void refill() throws IOException {
	    int newlim = 0;
	    try {
		String line = readLine(in);
		StringTokenizer tok = new StringTokenizer(line, " ;\t");
		newlim = Integer.parseInt(tok.nextToken(), 16);
	    } catch (Exception ex) {
		throw new HttpParseException("error parsing chunk length");
	    }
	    
	    if (newlim < 0) {
		throw new HttpParseException("illegal chunk length");
	    } else if (newlim == 0) {
		pos = -1;
	    } else {
		// REMIND: place upper limit on chunk length?
		if (buf == null || newlim > buf.length) {
		    buf = new byte[newlim];
		}
		for (int i = 0; i < newlim; ) {
		    int n = in.read(buf, i, newlim - i);
		    if (n < 0) {
			throw new EOFException("incomplete chunk");
		    }
		    i += n;
		}
		String blank = readLine(in);
		if (blank == null || blank.length() > 0) {
		    throw new HttpParseException("illegal chunk tail");
		}
		pos = 0;
		lim = newlim;
	    }
	}
    }
}
