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

package com.sun.jini.qa.harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * An I/O redirection pipe. A daemon thread copies data from an input
 * stream to an output stream. An optional annotator may be provided
 * which will prefix each line of the copied data with a label which
 * can be used to identify the source. An optional filter may be provided
 * which is called to process each byte of the input stream. If no
 * annotator is provided, input is copied to the output stream immediately.
 * If an annotator is supplied, lines are buffered. Therefore, if an
 * annotator is supplied, the input and output streams MUST have the
 * same line separator convention; otherwise it is possible for the
 * end-of-line to never be detected.
 */
class Pipe implements Runnable {

    /** the line separator string */
    private final static String SEPARATOR = "\n";

    /** most recent input bytes for comparison with lineSeparator.*/
    private final byte[] lastBytes = new byte[SEPARATOR.length()];
    
    /** output line buffer */
    private final ByteArrayOutputStream bufOut = new ByteArrayOutputStream();;

    /** the input stream */
    private final InputStream in;

    /** the output PrintStream */
    private volatile PrintStream stream;

    /** the input data filter */
    private final Filter filter;

    /** the output stream annotator */
    private final Annotator annotator;

    /** the thread to process the data */
    private final Thread outThread;
    
    /**
     * Create a new Pipe object and start the thread to handle the data.
     *
     * @param name the name to assign to the thread
     * @param in input stream from which pipe input flows
     * @param stream the stream to which output will be sent
     * @param f the filter for processing input characters
     * @param a the annotator for prepending text to logged lines
     */
    Pipe(String name, 
		InputStream in, 
		PrintStream stream, 
		Filter f, 
		Annotator a) 
    {
	this.in = in;
	this.stream = stream;
	this.filter = (f == null ? new NullFilter() : f);
	this.annotator = a;
	outThread = new Thread(this, name);
	outThread.setDaemon(true);
	//outThread.start();
    }
    
    void start(){
        outThread.start();
    }

    /**
     * Wait until the run method terminates due to reading EOF on input
     *
     * @param timeout max time to wait for the thread to terminate
     */
    void waitTillEmpty(int timeout) {
	try {
	    outThread.join(timeout);
	} catch (InterruptedException ignore) {
	}
    }

    /**
     * Set the output stream.
     * 
     * @param stream the stream
     */
    void setStream(PrintStream stream) {
	this.stream = stream;
    }
    
    /**
     * Read and write data until EOF is detected. Flush any remaining data to
     * the output steam and return, terminating the thread.
     */
    public void run() {
	byte[] buf = new byte[256];
	int count;
	try {
	    /* read bytes till there are no more. */
	    while ((count = in.read(buf)) != -1) {
		write(buf, count);
	    }

	    /*  If annotating, flush internal buffer... may not have ended on a
             *  line separator, we also need a last annotation if 
             *  something was left.
	     */
	    String lastInBuffer = bufOut.toString();
	    bufOut.reset();
	    if (lastInBuffer.length() > 0) {
		if (annotator != null) {
		    stream.print(annotator.getAnnotation());
		}
		stream.println(lastInBuffer);
	    }
	    // Silently ignore exceptions. Child VM's which are killed
	    // can generate uninteresting noise otherwise.
	} catch (Exception e) {
	}
    }
    
    /**
     * For each byte in the give byte array, pass the byte to the
     * filter and then call the <code>write(byte)</code> method to
     * output the filtered bytes.
     *
     * @param b the array of input bytes
     * @param len the number data bytes in the array
     */
    private void write(byte b[], int len) throws IOException {
	if (len < 0) {
	    throw new ArrayIndexOutOfBoundsException(len);
	}
	for (int i = 0; i < len; i++) {
	    byte[] fb = filter.filterInput(b[i]);
	    for (int j = 0; j < fb.length; j++) {
		write(fb[j]);
	    }
	}
    }

    /**
     * If not annotated, write the byte to the stream immediately. Otherwise,
     * write a byte of data to the internal buffer. If we have matched a line
     * separator, then the currently buffered line is sent to the output writer
     * with a prepended annotation string.
     */
    private void write(byte b) throws IOException {

	bufOut.write(b);
	
	// shift previous bytes 'left' and append new byte
	int i = 1;
	while (i < lastBytes.length) {
	    lastBytes[i-1] = lastBytes[i++];
	}
	lastBytes[i-1] = b;
	
	// write buffered line if line separator detected
	if (SEPARATOR.equals(new String(lastBytes))) {
	    
	    String s = bufOut.toString();
	    bufOut.reset();
	    if (annotator != null) {
		stream.print(annotator.getAnnotation());
	    }
	    stream.print(s);
	}
    }
    
    /**
     * A filter for the input stream.
     */
    static interface Filter {

	/**
	 * Filters a byte. The given <code>byte</code> is
	 * processed, and a filtered array is returned. The return value
	 * may be zero length, but must not be null.
	 *
	 * @param b the <code>byte</code> to process in the filter
	 * @return a non-null array of <code>byte</code>s
	 */
	public byte[] filterInput(byte b);
    }

    /**
     * An annotator for the output stream. 
     */
    static interface Annotator {

	/**
	 * Return the annotation. The returned string is prepended
	 * to each line of output.
	 *
	 * @return the output annotation string
	 */
	public String getAnnotation();
    }

    /** A default implementation of the <code>Filter</code> interface */
    private class NullFilter implements Filter {
	public byte[] filterInput(byte b) {
	    return new byte[]{b};
	}
    }
}
