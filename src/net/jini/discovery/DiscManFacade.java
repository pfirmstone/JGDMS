/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.jini.discovery;

import org.apache.river.api.util.Facade;
import net.jini.lookup.ServiceRegistrarFacade;
import net.jini.core.lookup.PortableServiceRegistrar;
import net.jini.core.lookup.ServiceRegistrar;

/**
 *
 * @param DiscoveryListenerManagement 
 * @author Peter Firmstone
 * @since 2.2.0
 */
@SuppressWarnings("deprecation")
public class DiscManFacade 
        implements DiscoveryManagement, Facade<DiscoveryListenerManagement> {
    private final DiscoveryListenerManagement dlm;
    private final boolean isDiscoveryManagement2;
    @SuppressWarnings("unchecked")
    public DiscManFacade(DiscoveryListenerManagement dm){
        if (dm == null) throw new NullPointerException("DiscoveryManager cannot be null");
        while (dm instanceof Facade){
            Facade f = (Facade) dm;
            dm = (DiscoveryListenerManagement) f.reveal();
        }
        dlm = dm;
        isDiscoveryManagement2 = (dm instanceof DiscoveryManagement2);
    }

    public ServiceRegistrar[] getRegistrars() {
        if (isDiscoveryManagement2){
            PortableServiceRegistrar[] psr = 
                    ((DiscoveryManagement2)dlm).getPRegistrars();
            int l = psr.length;
            ServiceRegistrar[] sr = new ServiceRegistrar[l];
            for (int i = 0; i < l; i++){
                if (psr[i] instanceof ServiceRegistrar){
                    sr[i] = (ServiceRegistrar) psr[i];
                }else {
                    sr[i] = new ServiceRegistrarFacade(psr[i]);
                }
            }
            return sr;
        }
        throw new UnsupportedOperationException("Not supported.");
    }

    public void discard(ServiceRegistrar proxy) {
        if (isDiscoveryManagement2){
            ((DiscoveryManagement2) dlm).discard(proxy);
        }
        throw new UnsupportedOperationException("Not supported.");
    }

    public void addDiscoveryListener(DiscoveryListener listener) {
        dlm.addDiscoveryListener(listener);
    }

    public void removeDiscoveryListener(DiscoveryListener listener) {
        dlm.removeDiscoveryListener(listener);
    }

    public void terminate() {
        dlm.terminate();

    }

    public DiscoveryListenerManagement reveal() {
        return dlm;
    }

}
