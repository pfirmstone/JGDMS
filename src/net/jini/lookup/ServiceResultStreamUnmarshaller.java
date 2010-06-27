/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.lookup;

import org.apache.river.api.util.ResultStream;
import net.jini.core.lookup.*;

/**
 * Add this to the ResultStream filter chain
 * {@link StreamServiceRegistrar#lookup(ServiceTemplate, Class[], int)}
 * to unmarshall any MarshalledServiceItem's in the stream, prior to 
 * proxy verification, or applying constraints.
 * 
 * @author Peter Firmstone.
 * @see MarshalledServiceItem.
 * @see StreamServiceRegistrar
 */
public class ServiceResultStreamUnmarshaller implements ResultStream<ServiceItem> {
    ResultStream<ServiceItem> input;
    
    public ServiceResultStreamUnmarshaller(ResultStream<ServiceItem> rs){
        input = rs;
    }

    public ServiceItem get() {
        for(ServiceItem item = input.get(); item != null; 
                item = input.get()) {
            if (item instanceof MarshalledServiceItem){
                MarshalledServiceItem msi = (MarshalledServiceItem) item;
                ServiceItem it = new ServiceItem(msi.serviceID, msi.getService(null),
                        msi.getEntries());
                item = it;
            }
            return item;
        }//end item loop
        return null; // Our stream terminated item was null;
    }

    public void close() {
        input.close();
    }

}
