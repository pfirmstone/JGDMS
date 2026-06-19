package ${package}.proxy;

import java.rmi.Remote;
import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import net.jini.export.CodebaseAccessor;
import net.jini.lookup.ServiceAttributesAccessor;
import net.jini.lookup.ServiceIDAccessor;
import net.jini.lookup.ServiceProxyAccessor;
import org.apache.river.admin.DestroyAdmin;
import ${package}.${serviceName}Service;

/**
 * Server-side <b>backend</b> remote interface for ${serviceName}Service — the
 * single interface the exported server stub satisfies.
 *
 * <p>This is the analogue of Reggie's {@code Registrar}: one {@link Remote}
 * interface that aggregates the client-facing service contract
 * ({@link ${serviceName}Service}) <em>and</em> every infrastructure capability
 * a JGDMS service stub must expose.  Because it extends {@link Remote}, a JERI
 * exporter ({@code AtomicILFactory}) generates a stub that — being a proxy for
 * {@code ${serviceName}ServiceBackend} — transitively implements all of the
 * aggregated interfaces, including the ones that are not themselves
 * {@code Remote} ({@link Administrable}, {@link JoinAdmin}, {@link DestroyAdmin}).
 * That is what lets {@link au.net.zeus.jgdms.proxy.AbstractSmartProxy} validate
 * the deserialized stub and makes admin operations invocable over the wire.
 *
 * <p><b>Why this exists.</b> A bare {@code interface ${serviceName}Service
 * extends Remote} carries only the service methods; an implementation that
 * separately implements {@code Administrable}/{@code JoinAdmin}/
 * {@code DestroyAdmin} does <em>not</em> expose them on its exported stub,
 * because those interfaces are not reachable through any {@code Remote}
 * interface.  Aggregating them here is the convention that makes the stub
 * carry them.
 *
 * <p>The split mirrors Reggie:
 * <ul>
 *   <li>{@link ${serviceName}Service} — clean, client-facing (the
 *       {@code ServiceRegistrar} role), in the {@code -api} module.</li>
 *   <li>{@code ${serviceName}ServiceBackend} — infrastructure-laden,
 *       server/proxy facing (the {@code Registrar} role), in the downloadable
 *       {@code -dl} jar.</li>
 * </ul>
 *
 * <p>{@code ${serviceName}ServiceImpl} implements this interface; every member
 * other than the {@link ${serviceName}Service} methods is supplied by
 * {@link au.net.zeus.jgdms.service.support.AbstractJiniService}.
 *
 * @see ${serviceName}Service
 * @see au.net.zeus.jgdms.proxy.AbstractSmartProxy
 * @since 1.0
 */
public interface ${serviceName}ServiceBackend
        extends Remote,
                ${serviceName}Service,
                ServiceProxyAccessor,
                ServiceAttributesAccessor,
                ServiceIDAccessor,
                CodebaseAccessor,
                Administrable,
                JoinAdmin,
                DestroyAdmin {
}
