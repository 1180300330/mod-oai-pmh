package org.folio.oaipmh.helpers;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.client.ConfigurationsClient;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.folio.oaipmh.Constants.OKAPI_TENANT;
import static org.folio.oaipmh.Constants.OKAPI_TOKEN;
import static org.folio.oaipmh.Constants.OKAPI_URL;

public class RepositoryConfigurationHelper {

  private static final Logger logger = LoggerFactory.getLogger(RepositoryConfigurationHelper.class);

  private static final Pattern HOST_PORT_PATTERN = Pattern.compile("https?://([^:/]+)(?::?(\\d+)?)");
  private static final int DEFAULT_PORT = 9130;
  private static final String QUERY = "module==OAI-PMH";

  /**
   * Retrieve configuration for mod-oai-pmh from mod-configuration and puts these properties into context.
   * @param okapiHeaders
   * @param ctx the context
   * @return empty CompletableFuture
   */
  public CompletableFuture<Void> getConfiguration(Map<String, String> okapiHeaders,
                                                        Context ctx) {

    String okapiURL = okapiHeaders.get(OKAPI_URL);
    String tenant = okapiHeaders.get(OKAPI_TENANT);
    String token = okapiHeaders.get(OKAPI_TOKEN);
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);

    Matcher matcher = HOST_PORT_PATTERN.matcher(okapiURL);
    if (!matcher.find()) {
      future.complete(null);
      return future;
    }

    String host = matcher.group(1);
    String port = matcher.group(2);
    ConfigurationsClient configurationsClient = new ConfigurationsClient(host,
      StringUtils.isNotBlank(port) ? Integer.valueOf(port) : DEFAULT_PORT, tenant, token);



    try {
      configurationsClient.getEntries(QUERY, 0, 7, null, null, response ->
        response.bodyHandler(body -> {

          if (response.statusCode() != 200) {
            logger.error(String.format("Expected status code 200, got '%s' :%s",
              response.statusCode(), body.toString()));
            future.complete(null);
            return;
          }

          JsonObject entries = body.toJsonObject();
          final  JsonObject config = new JsonObject();
          entries.getJsonArray("configs").stream()
            .forEach(o ->
              config.put(((JsonObject) o).getString("code"),
                ((JsonObject) o).getString("value")));
          ctx.config().mergeIn(config);
          future.complete(null);
        })
      );
    } catch (UnsupportedEncodingException e) {
      logger.error(e.getMessage());
      future.complete(null);
    }
    return future;
  }

  public static String getProperty(String name, Context ctx) {
    return ctx.config().getString(name, System.getProperty(name));
  }

}