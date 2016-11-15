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
package net.jini.lookup.entry;

import java.beans.ConstructorProperties;
import java.io.Serializable;
import net.jini.core.entry.Entry;

/**
 * A JavaBeans(TM) component that encapsulates a Comment object.
 * 
 * @author Sun Microsystems, Inc.
 *
 * @see Comment
 */
public class CommentBean implements EntryBean, Serializable {
    private static final long serialVersionUID = 5272583409036504625L;

    /**
     * The Entry object associated with this JavaBeans component.
     *
     * @serial
     */
    protected Comment assoc;

    /**
     * Construct a new JavaBeans component, linked to a new empty 
     * Comment object.
     */
    public CommentBean() {
	assoc = new Comment();
    }
    
    @ConstructorProperties({"comment"})
    public CommentBean(String comment){
	assoc = new Comment(comment);
    }

    /**
     * Make a link to an Entry object.
     *
     * @param e the Entry object to link to
     * @exception java.lang.ClassCastException the Entry is not of the
     * correct type for this JavaBeans component
     */
    public void makeLink(Entry e) {
	assoc = (Comment) e;
    }

    /**
     * Return the Entry linked to by this JavaBeans component.
     */
    public Entry followLink() {
	return assoc;
    }

    /**
     * Return the value of the comment field in the Comment object linked to
     * by this JavaBeans component.
     *
     * @return a <code>String</code> representing the comment value
     * @see #setComment
     */
    public String getComment() {
	return assoc.comment;
    }

    /**
     * Set the value of the comment field in the Comment object linked to by
     * this JavaBeans component.
     *
     * @param x  a <code>String</code> specifying the comment value
     * @see #getComment
     */
    public void setComment(String x) {
	assoc.comment = x;
    }
}
