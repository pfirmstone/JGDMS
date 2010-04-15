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
package net.jini.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collection;

class MarshalledInstanceOutputStream extends MarshalOutputStream {

    private ObjectOutputStream locOut;
    /** <code>true</code> if non-<code>null</code> annotations are
     *  written.
     */
    private boolean hadAnnotations;

    public MarshalledInstanceOutputStream(OutputStream objOut, OutputStream locOut, Collection context) throws IOException {
        super(objOut, context);
        this.locOut = new ObjectOutputStream(locOut);
        hadAnnotations = false;
    }

    /**
     * Returns <code>true</code> if any non-<code>null</code> location
     * annotations have been written to this stream.
     */
    public boolean hadAnnotations() {
        return hadAnnotations;
    }

    @Override
    protected void writeAnnotation(String loc) throws IOException {
        hadAnnotations |= (loc != null);
        locOut.writeObject(loc);
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        locOut.flush();
    }
}
