/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.io;

/**
 * Converter Class for CDC to convert between MarshalledInstance and
 * net.jini.io.MarshalledObject.
 * 
 * @see MarshalledInstance
 * @see MarshalledObject
 * @author Peter Firmstone
 */
public class Converter {
    @SuppressWarnings("unchecked")
    public static net.jini.io.MarshalledInstance toMarshalledInstance(
            net.jini.io.MarshalledObject mo){
        return new MarshalledInstance(mo);
    }
    
    public static net.jini.io.MarshalledObject toJiniMarshalledObject(
            net.jini.io.MarshalledInstance instance){
        return instance.asMarshalledObject();
    }
}
