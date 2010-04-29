/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.core.lookup;

import net.jini.core.entry.Entry;

/**
 * MarshalledServiceItem extends ServiceItem and can be used anywhere a 
 * ServiceItem can.  A MarshalledServiceItem implementation instance 
 * contains the marshalled form of a Service and it's Entry's,
 * the corresponding superclass ServiceItem however contains null values
 * for the service and can exclude any Entry's. 
 * 
 * The ServiceID shall be in unmarshalled form always in the ServiceItem super class.
 * 
 * Since the ServiceItem.service is null, use of this class in existing software
 * will not return the service, however it will not break that software as
 * ServiceItem's contract is to set service or Entry's to null when they cannot
 * be unmarshalled.
 * 
 * ServiceItem's toString() method will return a different result for
 * MarshalledServiceItem instances.
 * 
 * If required, a new ServiceItem that is fully unmarshalled 
 * can be constructed from this class's methods and ServiceID.
 * 
 * @author Peter Firmstone.
 */
public abstract class MarshalledServiceItem extends ServiceItem{
    private static final long SerialVersionUID = 1L;
    protected MarshalledServiceItem(ServiceID id, Entry[] unmarshalledEntries){
        super(id, (Object) null, unmarshalledEntries);
    }
    /**
     * Unmarshall the service proxy. 
     * @return the service proxy, null if class not found.
     */
    public abstract Object getService();
    /**
     * Unmarshall the Entry's
     * @return array of Entry's, null entry in array for any class not found.
     */
    public abstract Entry[] getEntries();
}
