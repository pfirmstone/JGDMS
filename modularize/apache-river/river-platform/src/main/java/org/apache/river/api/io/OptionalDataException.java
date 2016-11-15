/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.river.api.io;

import java.io.ObjectStreamException;

/**
 * Has the same semantics as {@link java.io.OptionalDataException}, exists
 * in case we can't de-serialize {@link java.io.OptionalDataException}.
 * 
 * @author PeteFir
 */
public class OptionalDataException extends ObjectStreamException{
    
    public final int length;
    public final boolean eof;
   
    OptionalDataException(int length, boolean eof){
        this.length = length;
        this.eof = eof;
    }
    
    
}
