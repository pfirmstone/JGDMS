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

package com.sun.jini.jeri.internal.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * This class encodes a buffer into the classic: "Hexadecimal Dump" format of
 * the past. It is useful for analyzing the contents of binary buffers.
 * The format produced is as follows:
 * <pre>
 * xxxx: 00 11 22 33 44 55 66 77   88 99 aa bb cc dd ee ff ................
 * </pre>
 * Where xxxx is the offset into the buffer in 16 byte chunks, followed
 * by ascii coded hexadecimal bytes followed by the ASCII representation of
 * the bytes or '.' if they are not valid bytes.
 *
 * @author Sun Microsystems, Inc.
 * 
 */

public class HexDumpEncoder extends CharacterEncoder {

    private int offset;
    private int thisLineLength;
    private int currentByte;
    private byte thisLine[] = new byte[16];

    static void hexDigit(PrintStream p, byte x) {
	char c;

	c = (char) ((x >> 4) & 0xf);
	if (c > 9)
	    c = (char) ((c-10) + 'A');
	else
	    c = (char)(c + '0');
	p.write(c);
	c = (char) (x & 0xf);
	if (c > 9)
	    c = (char)((c-10) + 'A');
	else
	    c = (char)(c + '0');
	p.write(c);
    }

    protected int bytesPerAtom() {
	return (1);
    }

    protected int bytesPerLine() {
	return (16);
    }

    protected void encodeBufferPrefix(OutputStream o) throws IOException {
	offset = 0;
	super.encodeBufferPrefix(o);
    }

    protected void encodeLinePrefix(OutputStream o, int len) throws IOException {
	hexDigit(pStream, (byte)((offset >>> 8) & 0xff));
	hexDigit(pStream, (byte)(offset & 0xff));
	pStream.print(": ");
	currentByte = 0;
	thisLineLength = len;
    }
	
    protected void encodeAtom(OutputStream o, byte buf[], int off, int len) throws IOException {
	thisLine[currentByte] = buf[off];
	hexDigit(pStream, buf[off]);
	pStream.print(" ");
	currentByte++;
	if (currentByte == 8)
	    pStream.print("  ");
    }

    protected void encodeLineSuffix(OutputStream o) throws IOException {
	if (thisLineLength < 16) {
	    for (int i = thisLineLength; i < 16; i++) {
		pStream.print("   ");
		if (i == 7)
		    pStream.print("  ");
	    }
	}
	pStream.print(" ");
	for (int i = 0; i < thisLineLength; i++) {
	    if ((thisLine[i] < ' ') || (thisLine[i] > 'z')) {
		pStream.print(".");
	    } else {
		pStream.write(thisLine[i]);
            }
	}
	pStream.println();
	offset += thisLineLength;
    }

}
