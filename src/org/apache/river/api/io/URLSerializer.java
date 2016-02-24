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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 *
 * @author peter
 */
@Serializer(replaceObType=URL.class)
@AtomicSerial
class URLSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * By defining serial persistent fields, we don't need to use transient fields.
     * All fields can be final and this object becomes immutable.
     */
    private static final ObjectStreamField[] serialPersistentFields = 
	{
	    new ObjectStreamField("urlExternalForm", String.class)
	};
    
    private final String urlExternalForm;
    private final /*transient*/ URL url;
    
    URLSerializer(GetArg arg) throws IOException {
	this(new URL(null, arg.get("urlExternalForm", null, String.class)));
    }
    
    URLSerializer(URL url){
	this(url, url.toString());
    }
    
    URLSerializer(URL url, String urlExternalForm){
	this.url = url;
	this.urlExternalForm = urlExternalForm;
    }
    
    /*
     * We're not interested in the various forms of URL, we just don't want to
     * serialize it more than once if we don't have to.
     * URL.equals or hashCode is not consulted because doing so may cause a
     * network call.
     */
    @Override
    public boolean equals(Object obj){
	if (this == obj) return true;
	if (!(obj instanceof URLSerializer)) return false;
	return (urlExternalForm.equals(((URLSerializer) obj).urlExternalForm));
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 13 * hash + Objects.hashCode(this.urlExternalForm);
	return hash;
    }
    
    Object readResolve() throws ObjectStreamException, MalformedURLException {
	if (url != null) return url;
	return new URL(null, urlExternalForm);
    }
   
    /**
     * @serialData 
     * @param out
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
	ObjectOutputStream.PutField pf = out.putFields();
	pf.put("urlExternalForm", urlExternalForm);
	out.writeFields();
    }
    
}
