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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * This class defines the encoding half of character encoders.
 * A character encoder is an algorithm for transforming 8 bit binary
 * data into text (generally 7 bit ASCII or 8 bit ISO-Latin-1 text)
 * for transmission over text channels such as e-mail and network news.
 * 
 * The character encoders have been structured around a central theme
 * that, in general, the encoded text has the form:
 *
 * <pre>
 *	[Buffer Prefix]
 *	[Line Prefix][encoded data atoms][Line Suffix]
 *	[Buffer Suffix]
 * </pre>
 *
 * In the CharacterEncoder and CharacterDecoder classes, one complete
 * chunk of data is referred to as a <i>buffer</i>. Encoded buffers 
 * are all text, and decoded buffers (sometimes just referred to as 
 * buffers) are binary octets.
 *
 * To create a custom encoder, you must, at a minimum,  override three
 * abstract methods in this class.
 * <DL>
 * <DD>bytesPerAtom which tells the encoder how many bytes to 
 * send to encodeAtom
 * <DD>encodeAtom which encodes the bytes sent to it as text.
 * <DD>bytesPerLine which tells the encoder the maximum number of
 * bytes per line.
 * </DL>
 *
 * Several useful encoders have already been written and are 
 * referenced in the See Also list below.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
public abstract class CharacterEncoder {

    /** Stream that understands "printing" */
    protected PrintStream pStream;

    /** Return the number of bytes per atom of encoding */
    abstract protected int bytesPerAtom();

    /** Return the number of bytes that can be encoded per line */
    abstract protected int bytesPerLine();

    /**
     * Encode the prefix for the entire buffer. By default is simply
     * opens the PrintStream for use by the other functions.
     */
    protected void encodeBufferPrefix(OutputStream aStream) throws IOException {
	pStream = new PrintStream(aStream);
    }

    /**
     * Encode the suffix for the entire buffer.
     */
    protected void encodeBufferSuffix(OutputStream aStream) throws IOException {
    }

    /**
     * Encode the prefix that starts every output line.
     */
    protected void encodeLinePrefix(OutputStream aStream, int aLength)
    throws IOException {
    }

    /**
     * Encode the suffix that ends every output line. By default
     * this method just prints a <newline> into the output stream.
     */
    protected void encodeLineSuffix(OutputStream aStream) throws IOException {
	pStream.println();
    }

    /** Encode one "atom" of information into characters. */
    abstract protected void encodeAtom(OutputStream aStream, byte someBytes[],
		int anOffset, int aLength) throws IOException;

    /**
     * This method works around the bizarre semantics of BufferedInputStream's
     * read method.
     */
    protected int readFully(InputStream in, byte buffer[]) 
	throws java.io.IOException {
	for (int i = 0; i < buffer.length; i++) {
	    int q = in.read();
	    if (q == -1)
		return i;
	    buffer[i] = (byte)q;
	}
	return buffer.length;
    }

    /**
     * Encode bytes from the input stream, and write them as text characters
     * to the output stream. This method will run until it exhausts the
     * input stream, but does not print the line suffix for a final
     * line that is shorter than bytesPerLine().
     */
    public void encode(InputStream inStream, OutputStream outStream) 
	throws IOException {
	int	j;
	int	numBytes;
	byte	tmpbuffer[] = new byte[bytesPerLine()];

	encodeBufferPrefix(outStream);
	
	while (true) {
	    numBytes = readFully(inStream, tmpbuffer);
	    if (numBytes == 0) {
		break;
	    }
	    encodeLinePrefix(outStream, numBytes);
	    for (j = 0; j < numBytes; j += bytesPerAtom()) {

		if ((j + bytesPerAtom()) <= numBytes) {
		    encodeAtom(outStream, tmpbuffer, j, bytesPerAtom());
		} else {
		    encodeAtom(outStream, tmpbuffer, j, (numBytes)- j);
		}
	    }
	    if (numBytes < bytesPerLine()) {
		break;
	    } else {
		encodeLineSuffix(outStream);
	    }
	}
	encodeBufferSuffix(outStream);
    }

    /**
     * Encode the buffer in <i>aBuffer</i> and write the encoded
     * result to the OutputStream <i>aStream</i>.
     */
    public void encode(byte aBuffer[], OutputStream aStream) 
    throws IOException {
	ByteArrayInputStream inStream = new ByteArrayInputStream(aBuffer);
	encode(inStream, aStream);
    }

    /**
     * A 'streamless' version of encode that simply takes a buffer of
     * bytes and returns a string containing the encoded buffer.
     */
    public String encode(byte aBuffer[]) {
	ByteArrayOutputStream	outStream = new ByteArrayOutputStream();
	ByteArrayInputStream	inStream = new ByteArrayInputStream(aBuffer);
	String retVal = null;
	try {
	    encode(inStream, outStream);
	    // explicit ascii->unicode conversion
	    retVal = outStream.toString("8859_1");
	} catch (Exception IOException) {
	    // This should never happen.
	    throw new Error("CharacterEncoder::encodeBuffer internal error");
	}
	return (retVal);
    }

    /**
     * Encode bytes from the input stream, and write them as text characters
     * to the output stream. This method will run until it exhausts the
     * input stream. It differs from encode in that it will add the
     * line at the end of a final line that is shorter than bytesPerLine().
     */
    public void encodeBuffer(InputStream inStream, OutputStream outStream) 
	throws IOException {
	int	j;
	int	numBytes;
	byte	tmpbuffer[] = new byte[bytesPerLine()];

	encodeBufferPrefix(outStream);
	
	while (true) {
	    numBytes = readFully(inStream, tmpbuffer);
	    if (numBytes == 0) {
		break;
	    }
	    encodeLinePrefix(outStream, numBytes);
	    for (j = 0; j < numBytes; j += bytesPerAtom()) {
		if ((j + bytesPerAtom()) <= numBytes) {
		    encodeAtom(outStream, tmpbuffer, j, bytesPerAtom());
		} else {
		    encodeAtom(outStream, tmpbuffer, j, (numBytes)- j);
		}
	    }
	    encodeLineSuffix(outStream);
	    if (numBytes < bytesPerLine()) {
		break;	
	    }
	}
	encodeBufferSuffix(outStream);
    }

    /**
     * Encode the buffer in <i>aBuffer</i> and write the encoded
     * result to the OutputStream <i>aStream</i>.
     */
    public void encodeBuffer(byte aBuffer[], OutputStream aStream) 
    throws IOException {
	ByteArrayInputStream inStream = new ByteArrayInputStream(aBuffer);
	encodeBuffer(inStream, aStream);
    }

    /**
     * A 'streamless' version of encode that simply takes a buffer of
     * bytes and returns a string containing the encoded buffer.
     */
    public String encodeBuffer(byte aBuffer[]) {
	ByteArrayOutputStream	outStream = new ByteArrayOutputStream();
	ByteArrayInputStream	inStream = new ByteArrayInputStream(aBuffer);
	try {
	    encodeBuffer(inStream, outStream);
	} catch (Exception IOException) {
	    // This should never happen.
	    throw new Error("CharacterEncoder::encodeBuffer internal error");
	}
	return (outStream.toString());
    }

}
