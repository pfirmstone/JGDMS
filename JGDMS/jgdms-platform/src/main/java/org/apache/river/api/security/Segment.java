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

package org.apache.river.api.security;

import org.apache.river.api.security.PolicyUtils.ExpansionFailedException;
import org.apache.river.impl.Messages;

import java.util.Properties;

/**
 * Segments form a chain of String parts which represent a framework for
 * building complex String's from Substrings.  Segments are chained at 
 * creation time, the last Segment in a chain communicates back up the chain
 * using the #next() method call.
 * 
 * The first segment in a chain must be checked each string build creation loop,
 * until it is complete, at which point the loop should be terminated by the caller.  
 * The last segment in a chain must have the next() method
 * called at the end of the loop, this will propagate back up the chain, ensuring
 * every possible unique string combination is produced, before the status of
 * the first link becomes complete.
 * 
 * Segments are not thread safe.
 * 
 * Segments are comparable, but shouldn't be compared until after segmentation
 * is completed, so initially should be stored in an unsorted Collection.
 * 
 * @author Peter Firmstone.
 * @since 3.0.0
 */
class Segment {
    private Segment previous;
    private Segment [] divisions;
    private String original;
//    private int length = 0;
    int counter = 0;
    Status stat;
    private StringBuilder sb;
    private boolean toggle;
    public Segment(String s, Segment preceed){
        previous = preceed;
        stat = Status.STRING;
        divisions = null;
        original = s;
        sb = null;
        toggle = false;
    }

    @Override
    public String toString(){
        return original;
    }

    public Status status(){
        return stat;
    }
    
    public boolean hasNext(){
        if (allStringState()) return true;
        if ( stat.equals(Status.COMPLETE) || stat.equals(Status.STRING)) {
            return previous == null ? false : previous.hasNext();
        }
        return true;
    }
    
    private boolean allStringState(){
        if (toggle) return false;
        toggle = true;
        if ( ( previous == null || previous.allStringState()) && status().equals(Status.STRING)) return true;
        return false;
    }

    /**
     * Must only be called on the last segment in the Set after sorting.
     */
    public String next(){
        if (sb == null) sb = new StringBuilder(120);
        else sb.delete(0, sb.capacity() -1 );
        append(sb);
        if ( stat.equals(Status.COMPLETE)){
            if (previous != null ){
                previous.next();
            }
        } else if ( stat.equals(Status.MORE)){
            if (divisions[counter].status().equals(Status.MORE)){
                divisions[counter].next();
            } else {
                counter ++;
            }
            if (counter == divisions.length){
                stat = Status.COMPLETE;
            }
        } else if ( stat.equals(Status.STRING)&& previous != null){
                previous.next(); // ensures backward propagation.
        }
        return sb.toString();
    }
    
    private StringBuilder append(StringBuilder sb){
        if (previous != null) previous.append(sb);
        if (divisions != null) divisions[counter].append(sb);
        else sb.append(original);
        return sb;
    }
    
    private int sequenceNumber(){
        int sequence = 1;
        if (previous != null){
            sequence = sequence + previous.sequenceNumber();
        }
        return sequence;
    }

    /**
     * Segments the current String by find Properties between the START_MARK and
     * END_MARK and replacing them with their values, splitting them into separate
     * Strings (that remain encapsulated in the Segment) if regex is non null.
     * @param START_MARK
     * @param END_MARK
     * @param regex
     * @param p
     */
    public void divideAndReplace(String START_MARK, String END_MARK,
            String regex, Properties p) throws ExpansionFailedException{
        if (previous != null) previous.divideAndReplace(START_MARK, END_MARK, regex, p);
        if (divisions != null ){
            int l = divisions.length;
            for (int i = 0; i < l ; i++ ){
                divisions[i].divideAndReplace(START_MARK, END_MARK, regex, p);
            }
            // If divisions exist, then this Segment has already been processed.
            return;
        }
        String orig = original; //original reference is replaced
        Segment prev = previous; //previous reference too.
        final int START_OFFSET = START_MARK.length();
        final int END_OFFSET = END_MARK.length();
        int start = orig.indexOf(START_MARK);
        int end = 0;
        int beginning = 0;
        while (start >= 0) {
            // Get the segment preceeding the key, or between keys.
            Segment seg;
            if ( start > beginning && beginning >= 0){
                seg = new Segment(orig.substring(beginning, start), prev);
                prev = seg;
            }
            end = orig.indexOf(END_MARK, start);
            if (end >= 0) {
                String key = orig.substring(start + START_OFFSET, end);
                String value = p.getProperty(key);
                if (value != null) {
                    seg = new Segment(value, prev);
                    if (regex != null) {
                        seg.split(regex);
                        seg.status(Status.MORE);
                    }
                    prev = seg;
                } else {
                    throw new ExpansionFailedException(Messages.getString("security.14F", key)); //$NON-NLS-1$
                }
            }
            beginning = end + END_OFFSET;
            start = orig.indexOf(START_MARK, beginning);
        }
        // Now there could be a trailing string.
        if (beginning < orig.length()){
            // Use this to represent it.
            previous = prev;
            original = orig.substring(beginning);
        } else if ( beginning == orig.length() && prev != null){
            // Replace the last Segment in the list with this, after
            // making it equal.  The reason for doing so is that a downstream
            // Segment may reference this.
            previous = prev.previous;
            original = prev.original;
            divisions = prev.divisions;
//            length = last.length;
            stat = prev.stat;
            
        }
    }
    
    /**
     * Same as String.split(String regex) except that it stores the result
     * internally.
     */
    private void split(String regex){
        String [] prop = original.split(regex);
        int l = prop.length;
        divisions = new Segment[l];
        for (int i = 0; i < l; i++ ){
            // Divisions are parallel, they don't preceed or link to each other.
            divisions[i] = new Segment(prop[i], null);
        }
    }
    
    private void status(Status status){
        stat = status;
    }
    
    public enum Status {
        STRING, MORE, COMPLETE
    }
}
