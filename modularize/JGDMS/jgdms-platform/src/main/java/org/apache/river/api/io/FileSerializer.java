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

package org.apache.river.api.io;

import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType = File.class)
@AtomicSerial
class FileSerializer implements Serializable{
    
    URI path;
    transient File file;
    public FileSerializer(File file){
	this.file = file;
	path = file.toURI();
    }
    
    public FileSerializer(URI uri){
	path = uri;
	file = new File(uri);
    }
    
    public FileSerializer(GetArg arg) throws IOException{
	this(arg.get("path", null, URI.class));
    }
    
    Object readResolve() throws ObjectStreamException {
	if (file != null) return file;
	return new File(path);
    }
    
    @Override
    public boolean equals(Object o){
	if (o == this) return true;
	if (!(o instanceof FileSerializer)) return false;
	FileSerializer that = (FileSerializer) o;
	return file.equals(that.file);
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 41 * hash + Objects.hashCode(this.file);
	return hash;
    }
    
}
