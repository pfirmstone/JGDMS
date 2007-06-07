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

package com.sun.jini.phoenix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.Date;

/**
 * PipeWriter plugs together two pairs of input and output streams by
 * providing readers for input streams and writing through to
 * appropriate output streams.  Both output streams are annotated on a
 * per-line basis.
 *
 * @author Sun Microsystems, Inc.
 * 
 * @since 2.0
 */
class PipeWriter implements Runnable {

    /** stream used for buffering lines */
    private final ByteArrayOutputStream bufOut;

    /** count since last separator */
    private int cLast;  
    
    /** current chunk of input being compared to lineSeparator.*/
    private final byte[] currSep;
    
    private final PrintWriter out;
    private final InputStream in;
    
    private final String execString;
    private final Object[] date = new Object[]{ new Date() };
    
    private static final String lineSeparator =
	System.getProperty("line.separator");
    private static final int lineSeparatorLength = lineSeparator.length();
    private static final String format = "{0,date} {0,time}";
    private static final MessageFormat formatter = new MessageFormat(format);

    /**
     * Create a new PipeWriter object. All methods of PipeWriter,
     * except plugTogetherPair, are only accessible to PipeWriter
     * itself.  Synchronization is unnecessary on functions that will
     * only be used internally in PipeWriter.
     *
     * @param in input stream from which pipe input flows
     * @param out output stream to which log messages will be sent
     * @param name name of the process
     * @param tag name of the stream (out or err)
     */
    private PipeWriter (InputStream in,
			OutputStream out,
			String name,
			String tag)
    {

	this.in = in;
	this.out = new PrintWriter(out);
	
	bufOut = new ByteArrayOutputStream();
	currSep = new byte[lineSeparatorLength];
	
	/* set unique pipe/pair annotations */
	execString = ":" + name + ':' + tag + ':';
    }
    
    /**
     * Create a thread to listen and read from input stream, in.  buffer
     * the data that is read until a marker which equals lineSeparator
     * is read.  Once such a string has been discovered; write out an
     * annotation string followed by the buffered data and a line
     * separator.  
     */
    public void run() {
	byte[] buf = new byte[256];
	int count;
	
	try {
	    /* read bytes till there are no more. */
	    while ((count = in.read(buf)) != -1) {
		write(buf, 0, count);
	    }

	    /*  flush internal buffer... may not have ended on a line
             *  separator, we also need a last annotation if 
             *  something was left.
	     */
	    String lastInBuffer = bufOut.toString();
	    bufOut.reset();
	    if (lastInBuffer.length() > 0) {
		out.println (createAnnotation() + lastInBuffer);    
		out.flush();                    // add a line separator
                                                // to make output nicer
	    }

	} catch (IOException e) {
	}
    }
    
    /**
     * Write a subarray of bytes.  Pass each through write byte method.
     */
    private void write(byte b[], int off, int len) throws IOException {

	if (len < 0) {
	    throw new ArrayIndexOutOfBoundsException(len);
	}
	for (int i = 0; i < len; ++ i) {
	    write(b[off + i]);
	}
    }

    /**
     * Write a byte of data to the stream.  If we have not matched a
     * line separator string, then the byte is appended to the internal
     * buffer.  If we have matched a line separator, then the currently
     * buffered line is sent to the output writer with a prepended 
     * annotation string.
     */
    private void write(byte b) throws IOException {
	int i = 0;
	
	/* shift current to the left */
	for (i = 1 ; i < (currSep.length); i ++) {
	    currSep[i-1] = currSep[i];
	}
	currSep[i-1] = b;
	bufOut.write(b);
	
	/* enough characters for a separator? */
	if ( (cLast >= (lineSeparatorLength - 1)) &&
	     (lineSeparator.equals(new String(currSep))) )
	{
	    
	    cLast = 0;
	    
	    /* write prefix through to underlying byte stream */
	    out.print(createAnnotation() + bufOut.toString());
	    out.flush();
	    bufOut.reset();
	    
	    if (out.checkError()) {
		throw new IOException
		    ("PipeWriter: IO Exception when"+
		     " writing to output stream.");
	    }
	    
	} else {
	    cLast++;
	}
    }
    
    /** 
     * Create an annotation string to be printed out after
     * a new line and end of stream.
     */
    private String createAnnotation() {
	
	/* construct prefix for log messages:
	 * date/time stamp...
	 */
	StringBuffer text = new StringBuffer();
	((Date) date[0]).setTime(System.currentTimeMillis());
	formatter.format(date, text, null);
	text.append(execString);
	return text.toString();
    }

    /**
     * Allow plugging together two pipes at a time, to associate
     * output from an exec'ed process.
     *
     * @param name name of the process
     * @param in input stream from which pipe input comes
     * @param out output stream to which log messages will be sent
     * @param in1 input stream from which pipe input comes
     * @param out1 output stream to which log messages will be sent
     */
    static void plugTogetherPair(String name,
				 InputStream in,
				 OutputStream out,
				 InputStream in1,
				 OutputStream out1)
    {
	/* start threads to read output from child process */
	Thread outThread = new Thread(new PipeWriter(in, out, name, "out"),
				      name + "-out");
	outThread.setDaemon(true);
	outThread.start();
	Thread errThread = new Thread(new PipeWriter(in1, out1, name, "err"),
				      name + "-err");
	errThread.setDaemon(true);
	errThread.start();
    }
}
