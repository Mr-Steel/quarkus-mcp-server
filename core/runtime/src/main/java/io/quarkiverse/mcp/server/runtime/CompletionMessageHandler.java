package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public abstract class CompletionMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(CompletionMessageHandler.class);

    private final ResponseHandlers responseHandlers;

    protected CompletionMessageHandler(ResponseHandlers responseHandlers) {
        this.responseHandlers = responseHandlers;
    }

    protected abstract Future<CompletionResponse> execute(String key, ArgumentProviders argProviders,
            SecuritySupport securitySupport) throws McpException;

    void complete(JsonObject message, Object id, JsonObject ref, JsonObject argument, Sender sender,
            McpConnection connection,
            SecuritySupport securitySupport) {
        String referenceName = ref.getString("name");
        String argumentName = argument.getString("name");

        LOG.debugf("Complete %s for argument %s [id: %s]", referenceName, argumentName, id);

        String key = referenceName + "_" + argumentName;

        ArgumentProviders argProviders = new ArgumentProviders(
                Map.of(argumentName, argument.getString("value")), connection, id, null, sender,
                Messages.getProgressToken(message), responseHandlers);

        try {
            Future<CompletionResponse> fu = execute(key, argProviders, securitySupport);
            fu.onComplete(new Handler<AsyncResult<CompletionResponse>>() {
                @Override
                public void handle(AsyncResult<CompletionResponse> ar) {
                    if (ar.succeeded()) {
                        CompletionResponse completionResponse = ar.result();
                        JsonObject result = new JsonObject();
                        JsonObject completion = new JsonObject()
                                .put("values", completionResponse.values());
                        if (completionResponse.total() != null) {
                            completion.put("total", completionResponse.total());
                        }
                        if (completionResponse.hasMore() != null) {
                            completion.put("hasMore", completionResponse.hasMore());
                        }
                        result.put("completion", completion);
                        sender.sendResult(id, result);
                    } else {
                        handleFailure(id, sender, connection, ar.cause(), LOG, "Unable to complete %s", referenceName);
                    }
                }
            });
        } catch (McpException e) {
            sender.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
