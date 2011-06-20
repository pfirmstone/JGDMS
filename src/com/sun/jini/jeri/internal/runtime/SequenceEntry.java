package com.sun.jini.jeri.internal.runtime;

final class SequenceEntry {

    private volatile long sequenceNum;
    private volatile boolean keep;

    SequenceEntry(long sequenceNum) {
        super();
        this.sequenceNum = sequenceNum;
        keep = false;
    }
    
    SequenceEntry(long sequenceNum, boolean strong){
        this.sequenceNum = sequenceNum;
        this.keep = strong;
    }
    
    /**
     * If the passed in sequence number is greater than the current number,
     * it is updated.
     * If 
     * @param seqNum - passed in sequence number.
     * @param strong - strong clean call is kept in the event of an update.
     * @return true if the sequence number is updated.
     */
    boolean update(long seqNum, boolean strong){
        synchronized (this){
            if (seqNum > sequenceNum){
                sequenceNum = seqNum;
                if (strong) keep = true;
                return true;
            }
            return false;
        }
    }
    
    boolean keep(){
        return keep;
    }
}
