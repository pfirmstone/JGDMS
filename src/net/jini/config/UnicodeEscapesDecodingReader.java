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

package net.jini.config;

import java.io.IOException;
import java.io.Reader;

/**
 * A Reader that converts Unicode escape sequences, throwing IOException for
 * malformed escapes.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
final class UnicodeEscapesDecodingReader extends Reader {

    /** Marker for when peekc is empty. */
    private static final int NO_CHAR = Integer.MIN_VALUE;

    /** The source of characters to translate. */
    private Reader reader;

    /**
     * A peeked character -- the character after a backslash if it wasn't 'u'
     * -- or NO_CHAR if no peek was done.
     */
    private int peekc = NO_CHAR;

    /** Buffer to hold the Unicode value being parsed. */
    private final char[] code = new char[4];

    /** Creates an instance of this class. */
    UnicodeEscapesDecodingReader(Reader reader) {
	if (reader == null) {
	    throw new NullPointerException("reader is null");
	}
	this.reader = reader;
    }

    public synchronized int read() throws IOException {
	if (reader == null) {
	    throw new IOException("stream is closed");
	}
	return readInternal();
    }

    public synchronized int read(char[] cbuf, int off, int len)
	throws IOException
    {
	if (reader == null) {
	    throw new IOException("stream is closed");
	} else if (off < 0 || off > cbuf.length ||
		   len < 0 || off + len > cbuf.length ||
		   off + len < 0)
	{
	    throw new IndexOutOfBoundsException();
	}
	for (int nchars = 0; nchars < len; nchars++) {
	    int c = readInternal();
	    if (c < 0) {
		return (nchars == 0) ? -1 : nchars;
	    }
	    cbuf[off + nchars] = (char) c;
	}
	return len;
    }

    /** Implements read() without checking if this reader is closed. */
    private int readInternal() throws IOException {
	int c;
	if (peekc == NO_CHAR) {
	    c = reader.read();
	} else {
	    c = peekc;
	    peekc = NO_CHAR;
	    if (c == '\\') {	/* an escaped slash */
		return c;
	    }
	}
	if (c != '\\') {	/* not a Unicode escape */
	    return c;
	}
	c = reader.read();
	if (c != 'u') {		/* not a Unicode escape */
	    peekc = c;
	    return '\\';
	}
	do {
	    c = reader.read();
	} while (c == 'u');
	int nchars = 0;
	if (c >= 0) {
	    code[nchars++] = (char) c;
	    while (nchars < 4) {
		int n = reader.read(code, nchars, 4 - nchars);
		if (n < 0) {
		    break;
		}
		nchars += n;
	    }
	}
	String s = new String(code, 0, nchars);
	if (nchars == 4) {
	    try {
		int i = Integer.parseInt(s, 16);
		if (i >= 0) {
		    return (char) i;
		}
	    } catch (NumberFormatException e) {
	    }
	}
	throw new IOException("illegal Unicode escape: \\u" + s);
    }

    public synchronized void close() throws IOException {
	if (reader != null) {
	    reader.close();
	    reader = null;
	}
    }
}
