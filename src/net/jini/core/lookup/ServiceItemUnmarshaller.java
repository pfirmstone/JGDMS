/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.core.lookup;

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
public class ServiceItemUnmarshaller implements ResultStream<ServiceItem> {
    ResultStream<ServiceItem> input;
    
    public ServiceItemUnmarshaller(ResultStream<ServiceItem> rs){
        input = rs;
    }

    public ServiceItem get() {
        for(ServiceItem item = input.get(); item != null; 
                item = input.get()) {
            if (item instanceof MarshalledServiceItem){
                MarshalledServiceItem msi = (MarshalledServiceItem) item;
                ServiceItem it = new ServiceItem(msi.serviceID, msi.getService(),
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
