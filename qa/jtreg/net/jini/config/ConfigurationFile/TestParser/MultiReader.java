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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Defines a Reader that treats an input line that begins with '$' as an end
 * of file, provides methods for continuing to read after such an EOF, and
 * ignores other input lines that start with '#'.
 */
class MultiReader extends Reader {

    /** The underlying reader. */
    private final BufferedReader reader;

    /** The current line. */
    private String line;

    /** The position in the current line. */
    private int pos;

    /** Whether a EOF has been reached. */
    private boolean eof;

    /** Whether the real EOF has been reached. */
    private boolean done;

    /**
     * Creates a MultiReader on top of another reader.
     */
    MultiReader(Reader reader) {
	if (reader instanceof BufferedReader) {
	    this.reader = (BufferedReader) reader;
	} else {
	    this.reader = new BufferedReader(reader);
	}
    }

    /* -- Implement Reader -- */

    public void close() throws IOException {
	reader.close();
	line = null;
    }

    public int read() throws IOException {
	getLine();
	if (eof) {
	    return -1;
	} else if (pos < line.length()) {
	    return line.charAt(pos++);
	} else {
	    line = null;
	    return '\n';
	}
    }

    public int read(char[] buf, int off, int len) throws IOException {
	int result = 0;
	for ( ; result < len; result++) {
	    int c = read();
	    if (c < 0) {
		break;
	    }
	    buf[off++] = (char) c;
	}
	return (result == 0) ? -1 : result;
    }   

    /* -- Other methods -- */

    /**
     * Reads a line of text.
     *
     * @return the line of text, without the final newline, or null if EOF has
     *	       been reached.
     */
    public String readLine() throws IOException {
	getLine();
	if (eof) {
	    return null;
	}
	String result = line.substring(pos);
	line = null;
	return result;
    }

    /** Gets the next line or sets eof to true. */
    private void getLine() throws IOException {
	if (eof || line != null) {
	    return;
	}
	while (true) {
	    line = reader.readLine();
	    if (line == null) {
		eof = true;
		done = true;
		break;
	    } else if (line.startsWith("$")) {
		line = null;
		eof = true;
		break;
	    } else if (!line.startsWith("#")) {
		pos = 0;
		break;
	    }
	}	    
    }


    /**
     * Reads to end of file, then clears the end of file mark, allowing this
     * reader to return more characters.
     */
    public void clearEOF() throws IOException {
	while (!eof) {
	    getLine();
	    line = null;
	}
	eof = false;
    }

    /**
     * Reads to the end of file, returning all the characters read as a String.
     */
    public String readToEOF() throws IOException {
	StringBuffer buf = new StringBuffer();
	while (true) {
	    int c = read();
	    if (c < 0) {
		clearEOF();
		return buf.toString();
	    } else {
		buf.append((char) c);
	    }
	}
    }

    /** Returns true if the real end of file has been reached. */
    public boolean atRealEOF() throws IOException {
	getLine();
	return done;
    }
}    
