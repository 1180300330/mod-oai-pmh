package org.folio.rest.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxTestContext;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.fail;

class OkapiMockServer {

  private static final Logger logger = Logger.getLogger(OkapiMockServer.class);

  static final String EXISTING_IDENTIFIER = "existing-identifier";
  static final String NON_EXISTING_IDENTIFIER = "non-existing-identifier";
  static final String INVALID_IDENTIFIER = "non-existing-identifier";
  static final String ERROR_IDENTIFIER = "please-return-error";

  // Dates
  static final String NO_RECORDS_DATE = "2011-11-11T11:11:11Z";
  static final String ERROR_DATE = "2010-10-10T10:10:10Z";
  static final String RECORD_STORAGE_INTERNAL_SERVER_ERROR_DATE = "2001-01-01T01:01:01Z";
  static final String DATE_FOR_FOUR_INSTANCES_BUT_ONE_WITHOT_RECORD = "2000-01-02T03:04:05Z";
  static final String THREE_INSTANCES_DATE = "2018-12-12T12:12:12Z";

  // Instance UUID
  private static final String NOT_FOUND_RECORD_INSTANCE_ID = "04489a01-f3cd-4f9e-9be4-d9c198703f45";
  private static final String INTERNAL_SERVER_ERROR_INSTANCE_ID = "6b4ae089-e1ee-431f-af83-e1133f8e3da0";

  // Paths to json files
  private static final String INSTANCES_0 = "/instance-storage/instances/instances_0.json";
  private static final String INSTANCES_1 = "/instance-storage/instances/instances_1.json";
  private static final String INSTANCES_2 = "/instance-storage/instances/instances_2.json";
  private static final String INSTANCES_3 = "/instance-storage/instances/instances_3.json";
  private static final String INSTANCES_4 = "/instance-storage/instances/instances_4.json";
  private static final String INSTANCES_10 = "/instance-storage/instances/instances_10.json";


  private final int port;
  private final Vertx vertx;

  OkapiMockServer(Vertx vertx, int port) {
    this.port = port;
    this.vertx = vertx;
  }

  private Router defineRoutes() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.GET, "/instance-storage/instances")
          .handler(this::handleInstancesInventoryStorageResponse);
    router.route(HttpMethod.GET, "/instance-storage/instances/:instanceId/source-record/marc-json")
          .handler(this::handleMarcJsonInventoryStorageResponse);
    return router;
  }

  private void handleMarcJsonInventoryStorageResponse(RoutingContext ctx) {
    String instanceId = ctx.request().getParam("instanceId");
    if (instanceId.equalsIgnoreCase(INTERNAL_SERVER_ERROR_INSTANCE_ID)) {
      failureResponse(ctx, 500, "Internal Server Error");
    } else if (instanceId.equalsIgnoreCase(NOT_FOUND_RECORD_INSTANCE_ID)) {
      failureResponse(ctx, 404, "Record not found");
    } else {
      successResponse(ctx, getJsonObjectFromFile(String.format("/instance-storage/instances/marc-%s.json", instanceId)));
    }
  }

  public void start(VertxTestContext context) {
    HttpServer server = vertx.createHttpServer();

    server.requestHandler(defineRoutes()::accept).listen(port, context.succeeding(result -> {
      logger.info("The server has started");
      context.completeNow();
    }));
  }

  private void handleInstancesInventoryStorageResponse(RoutingContext ctx) {
    String query = ctx.request().getParam("query");
    if (query != null)
    {
      if (query.endsWith("id==" + EXISTING_IDENTIFIER)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_2));
      } else if (query.endsWith("id==" + NON_EXISTING_IDENTIFIER)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_0));
      } else if (query.contains(NO_RECORDS_DATE)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_0));
      } else if (query.endsWith("id==" + ERROR_IDENTIFIER)) {
        failureResponse(ctx, 500, "Internal Server Error");
      } else if (query.contains(ERROR_DATE)) {
        failureResponse(ctx, 500, "Internal Server Error");
      } else if (query.contains(RECORD_STORAGE_INTERNAL_SERVER_ERROR_DATE)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_2));
      } else if (query.contains(THREE_INSTANCES_DATE)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_3));
      } else if (query.contains(DATE_FOR_FOUR_INSTANCES_BUT_ONE_WITHOT_RECORD)) {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_4));
      } else {
        successResponse(ctx, getJsonObjectFromFile(INSTANCES_10));
      }
      logger.info("Mock returns http status code: " + ctx.response().getStatusCode());
    } else {
      throw new UnsupportedOperationException();
    }
  }

  private void successResponse(RoutingContext ctx, String body) {
    ctx.response()
       .setStatusCode(200)
       .putHeader(HttpHeaders.CONTENT_TYPE, "text/json")
       .end(body);
  }

  private void failureResponse(RoutingContext ctx, int code, String body) {
    ctx.response()
       .setStatusCode(code)
       .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
       .end(body);
  }

  /**
   * Creates {@link JsonObject} from the json file
   * @param path path to json file to read
   * @return json as string from the json file
   */
  private String getJsonObjectFromFile(String path) {
    try {
      File file = new File(OkapiMockServer.class.getResource(path).getFile());
      byte[] encoded = Files.readAllBytes(Paths.get(file.getPath()));
      return new String(encoded, StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.error("Unexpected error", e);
      fail(e.getMessage());
    }
    return null;
  }
}
