package emu.grasscutter.server.http.api;

import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;

/** Read-only status endpoints, for uptime monitors and server-list widgets. */
public final class ApiHandler implements Router {
    public static final int ERROR_RET_CODE = -1;
    public static final int SUCCESS_RET_CODE = 0;

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/api/help", ServerStatusHandler::listRoutes);
        javalin.get("/api/status", ServerStatusHandler::serverStatus);
        // The old path, kept so existing server-list tools keep working.
        javalin.get("/status/server", ServerStatusHandler::serverStatusLegacy);
    }
}
