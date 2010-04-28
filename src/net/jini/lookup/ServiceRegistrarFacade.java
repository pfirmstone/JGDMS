/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.lookup;

import java.rmi.MarshalledObject;
import java.rmi.RemoteException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.event.EventRegistration;
import net.jini.core.event.RemoteEventListener;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceRegistration;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.StreamServiceRegistrar;
import net.jini.discovery.Facade;
import net.jini.io.Convert;
import net.jini.io.MarshalledInstance;

/**
 *
 * @author Peter Firmstone.
 * @since 2.2.0
 */
public class ServiceRegistrarFacade implements ServiceRegistrar, Facade<PortableServiceRegistrar>{
    
    private final PortableServiceRegistrar sr; //Never allow a facade referent.
    private final boolean isStreamingServiceRegistrar;
    
    public ServiceRegistrarFacade(PortableServiceRegistrar psr){
        while (psr instanceof Facade){ //always ensure we have uncovered any facades.
            @SuppressWarnings("unchecked")
                Facade<PortableServiceRegistrar> f = (Facade<PortableServiceRegistrar>) psr;
                psr = f.reveal();
        }
        sr = psr;       
        if (psr instanceof StreamServiceRegistrar){
            isStreamingServiceRegistrar = true;
        } else {
            isStreamingServiceRegistrar = false;
        }
    }

    public EventRegistration notify(ServiceTemplate tmpl, int transitions, 
            RemoteEventListener listener, MarshalledObject handback, 
            long leaseDuration) throws RemoteException {
        if ( isStreamingServiceRegistrar ) {
            Convert convert = Convert.getInstance();
            @SuppressWarnings("unchecked")
            MarshalledInstance hback = convert.toMarshalledInstance(handback);
            StreamServiceRegistrar ssr = (StreamServiceRegistrar) sr;
            return ssr.notify(hback, tmpl, transitions, listener, leaseDuration);
        }
        throw new UnsupportedOperationException("PortableServiceRegistrar " +
                "doesn't implement this method");
    }

    public Class[] getEntryClasses(ServiceTemplate tmpl) throws RemoteException {
        return sr.getEntryClasses(tmpl);
    }

    public Object[] getFieldValues(ServiceTemplate tmpl, int setIndex, String field) throws NoSuchFieldException, RemoteException {
        return sr.getFieldValues(tmpl, setIndex, field);
    }

    public String[] getGroups() throws RemoteException {
        return sr.getGroups();
    }

    public LookupLocator getLocator() throws RemoteException {
        return sr.getLocator();
    }

    public ServiceID getServiceID() {
        return sr.getServiceID();
    }

    public Class[] getServiceTypes(ServiceTemplate tmpl, String prefix) throws RemoteException {
        return sr.getServiceTypes(tmpl, prefix);
    }

    public Object lookup(ServiceTemplate tmpl) throws RemoteException {
        return sr.lookup(tmpl);
    }

    public ServiceMatches lookup(ServiceTemplate tmpl, int maxMatches) throws RemoteException {
        return sr.lookup(tmpl, maxMatches);
    }

    public ServiceRegistration register(ServiceItem item, long leaseDuration) throws RemoteException {
        return sr.register(item, leaseDuration);
    }
    
    @Override
    public boolean equals(Object obj){
        if (obj == this) return true;
        while (obj instanceof Facade){
            Facade f = (Facade) obj;
            obj = f.reveal();
        }
        if (obj instanceof PortableServiceRegistrar){
            PortableServiceRegistrar psr = (PortableServiceRegistrar) obj;
            if (sr.equals(psr)) return true;
        }
        return false;
    }
    
    @Override
    public int hashCode(){
        return sr.hashCode();
    }

    public PortableServiceRegistrar reveal() {
        return sr;
    }

}
