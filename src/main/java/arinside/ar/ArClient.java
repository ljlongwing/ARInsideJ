package arinside.ar;

import arinside.config.AppConfig;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ARServerUser;

/**
 * Wraps the AR System Java API connection - the Java-API equivalent of CARInside::Init/Terminate
 * (ARInside.cpp) plus the ARControlStruct setup that used to live in ARApi.h. Collapses the
 * C++'s core/+lists/ insideId-flyweight layer per the port plan: the Java API already returns
 * real objects (Form/ActiveLink/Filter/...), so callers just hold onto them directly instead of
 * going through a CARSchema(insideId)-style wrapper backed by a CARSchemaList cache.
 */
public final class ArClient implements AutoCloseable {
    private final ARServerUser server;

    private ArClient(ARServerUser server) {
        this.server = server;
    }

    public static ArClient connect(AppConfig cfg) throws ARException {
        ARServerUser server = new ARServerUser();
        server.setServer(cfg.serverName);
        server.setUser(cfg.userName);
        server.setPassword(cfg.password);
        if (cfg.tcpPort != 0) server.setPort(cfg.tcpPort);

        System.out.println("Logging in to " + cfg.serverName + " as " + cfg.userName + " ...");
        server.login();
        System.out.println("Login OK.");

        // Java port of the C++'s RPC program number handling (-r / RPCPort, see README) - the C++
        // passes it into ARSetServerPort's rpc param before connecting. The Java API's equivalent
        // is ARServerUser.usePrivateRpcQueue(int), which needs an active session (throws
        // ARException, unlike the setters above) - called right after login rather than before.
        // rpcPort=0 (the default/unset case) means "use the fast or list server queue", matching
        // useDefaultRpcQueue's semantics, so it's only called when explicitly configured.
        if (cfg.rpcPort != 0) {
            server.usePrivateRpcQueue(cfg.rpcPort);
        }

        // Java port of CARInside::SetupOverlaySupport (ARSetSessionConfiguration(AR_SESS_CONTROL_PROP_API_OVERLAYGROUP,
        // AR_OVERLAY_CLIENT_MODE_FULL) in the C++) - setOverlayFlag/setOverlayObjType are inherited from
        // com.bmc.arsys.apitransport.ApiUserContextBase, not declared on ARServerUser itself, which is why an
        // ARServerUser-only search missed them initially.
        server.setOverlayFlag(true);

        return new ArClient(server);
    }

    public ARServerUser raw() { return server; }

    @Override
    public void close() {
        server.logout();
    }
}
