import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by yangzai on 5/9/16.
 */

public class HttpServerVerticle extends AbstractVerticle {
    private static final String API_HOST = "api.lifeup.com.sg";
    private static final String API_VERSION_PATH = "/v1";
    private static final int API_PORT = 443;
    private static final int PORT = ((Supplier<Integer>)() -> {
        try { return Integer.parseInt(System.getenv("PORT")); }
        catch (Exception e) { return 8080; }
    }).get();

    private HttpClient client;

    @Override
    public void start() throws Exception {
        client = vertx.createHttpClient(new HttpClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setDefaultHost(API_HOST)
                .setDefaultPort(API_PORT)
        );

        Router router = Router.router(vertx);
        //note: set body handler 1st before before other routes
        router.route().handler(BodyHandler.create());

        Router eNetsRouter = Router.router(vertx);
        router.mountSubRouter("/enet", eNetsRouter);

        //POST /v1/enet/enet_login
        eNetsRouter.post("/enet_login").handler(rc -> { //routing context
            HttpServerResponse res = rc.response();

            Map<String, List<String>> parameters =
                    new QueryStringDecoder(rc.getBodyAsString(), false).parameters();

            if (!parameters.containsKey("enet_username")) {
                res.end("enet_username is required.");
                return;
            }

            if (!parameters.containsKey("enet_pwd")) {
                res.end("enet_pwd is required.");
                return;
            }

            //get parameters and re-encode
            QueryStringEncoder qse = new QueryStringEncoder("");
            qse.addParam("username", parameters.get("enet_username").get(0));
            qse.addParam("password", parameters.get("enet_pwd").get(0));

            String queryString;
            try { queryString = qse.toUri().getQuery(); }
            catch (URISyntaxException e) { queryString = ""; }

            //API request
            HttpClientRequest clientReq = client.post(API_VERSION_PATH + "/user/login", clientRes -> {
                int statusCode = clientRes.statusCode();

                if (statusCode < 200 || statusCode >= 300) {
                    String statusMessage = clientRes.statusMessage();
                    res.setStatusCode(statusCode).setStatusMessage(statusMessage).end();
                    throw new UncheckedIOException( //goes to exceptionHandler
                            new IOException("Backend: " + statusCode + ' ' + statusMessage)
                    );
                }

                clientRes.bodyHandler(body -> {
                    String token = body.toJsonObject()
                            .getJsonObject("User", new JsonObject())
                            .getString("token");

                    res.putHeader("content-type", "application/json");
                    res.end(new JsonObject().put("token", token).encode());
                });
            });

            clientReq.exceptionHandler(err -> {
                if (!res.ended()) res.setStatusCode(500).end();
                if (err instanceof UncheckedIOException)
                    err = err.getCause();
                System.err.println("Client Request Exception: " +
                        clientReq.method() + ' ' + clientReq.path());
                err.printStackTrace();
            }).putHeader("content-type", "application/x-www-form-urlencoded")
                    .end(queryString);
        });

        vertx.createHttpServer().requestHandler(router::accept).listen(PORT);
    }

    @Override
    public void stop() throws Exception {
        if (client != null) client.close();
    }
}
