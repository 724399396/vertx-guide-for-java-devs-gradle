package io.vertx.wiki.http;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava.ext.unit.Async;
import io.vertx.rxjava.ext.unit.TestContext;
import io.vertx.wiki.database.WikiDatabaseVerticle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.vertx.wiki.DatabaseConstants.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE;
import static io.vertx.wiki.DatabaseConstants.CONFIG_WIKIDB_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class ApiTest {
  private WebClient webClient;
  private String jwtTokenHeaderValue;

  @BeforeEach
  public void prepare(Vertx vertx, VertxTestContext testContext) {
    JsonObject dbConf = new JsonObject()
      .put(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new AuthInitializerVerticle(),
      new DeploymentOptions().setConfig(dbConf), testContext.succeeding(authV -> {
          vertx.deployVerticle(new WikiDatabaseVerticle(),
            new DeploymentOptions().setConfig(dbConf), testContext.succeeding(dbV -> {
              vertx.deployVerticle(new HttpServerVerticle(), testContext.succeeding(httpV -> {
                webClient = WebClient.create(vertx, new WebClientOptions()
                  .setDefaultHost("localhost")
                  .setDefaultPort(8080)
                  .setSsl(true)
                  .setTrustOptions(new JksOptions().setPath("server-keystore.jks").setPassword("secret")));
                testContext.completeNow();
              }));
            }));
        }
      ));

  }

  @Test
  public void play_with_api(VertxTestContext testContext) {

    Future<String> tokenRequest = Future.future();
    webClient.get("/api/token")
      .putHeader("login", "foo")
      .putHeader("password", "bar")
      .as(BodyCodec.string())
      .send(ar -> {
        if (ar.succeeded()) {
          tokenRequest.complete(ar.result().body());
        } else {
          testContext.failNow(ar.cause());
        }
      });

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A Page");

    Future<JsonObject> postRequest = Future.future();
    tokenRequest.compose(token -> {
      jwtTokenHeaderValue = "Bearer " + token;
      webClient.post("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(page, ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> postResponse = ar.result();
            postRequest.complete(postResponse.body());
          } else {
            testContext.failNow(ar.cause());
          }
        });
    }, postRequest);

    Future<JsonObject> getRequest = Future.future();
    postRequest.compose(h -> {
      webClient.get("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> getResponse = ar.result();
            getRequest.complete(getResponse.body());
          } else {
            testContext.failNow(ar.cause());
          }
        });
    }, getRequest);

    Future<JsonObject> putRequest = Future.future();
    getRequest.compose(response -> {
      JsonArray array = response.getJsonArray("pages");
      testContext.verify(() -> {
        assertEquals(1, array.size());
        assertEquals(0, array.getJsonObject(0).getInteger("id").intValue());
      });
      webClient.put("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(new JsonObject()
          .put("id", 0)
          .put("markdown", "Ho Yeah!"), ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> putResponse = ar.result();
            putRequest.complete(putResponse.body());
          } else {
            testContext.failNow(ar.cause());
          }
        });
    }, putRequest);

    Future<JsonObject> deleteRequest = Future.future();
    putRequest.compose(response -> {
      testContext.verify(() -> {
        assertTrue(response.getBoolean("success"));
      });
      webClient.delete("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> delResponse = ar.result();
            deleteRequest.complete(delResponse.body());
          } else {
            testContext.failNow(ar.cause());
          }
        });
    }, deleteRequest);

    deleteRequest.compose(response -> {
      testContext.verify(() -> {
        assertTrue(response.getBoolean("success"));
      });
      testContext.completeNow();
    }, Future.failedFuture("Oh?"));
  }
}
