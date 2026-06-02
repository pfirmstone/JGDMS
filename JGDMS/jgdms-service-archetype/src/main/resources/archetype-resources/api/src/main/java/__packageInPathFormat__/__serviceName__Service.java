package ${package};

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote service interface for ${serviceName}Service.
 *
 * <p>This is the contract between the client and the server.  Add your
 * service methods here; they will be forwarded through the JGDMS smart proxy
 * to the server-side implementation.
 *
 * <p>All methods must declare {@link RemoteException} so that JERI can
 * propagate transport errors to the caller.
 *
 * @see ${package}.proxy.${serviceName}ServiceProxy
 * @since 1.0
 */
public interface ${serviceName}Service extends Remote {

    /**
     * Example service method — replace with your own business logic.
     *
     * @param input the input string; must not be {@code null}
     * @return the result string; never {@code null}
     * @throws RemoteException if a transport error occurs
     */
    String process(String input) throws RemoteException;
}
