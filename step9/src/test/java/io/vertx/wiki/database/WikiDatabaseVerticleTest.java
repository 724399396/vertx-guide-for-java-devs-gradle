package io.vertx.wiki.database;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.vertx.wiki.DatabaseConstants.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE;
import static io.vertx.wiki.DatabaseConstants.CONFIG_WIKIDB_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class WikiDatabaseVerticleTest {
  private io.vertx.wiki.database.reactivex.WikiDatabaseService service;

  @BeforeEach
  public void prepare(Vertx vertx, VertxTestContext vertxTestContext) {
    JsonObject conf = new JsonObject()
      .put(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf), vertxTestContext.succeeding(
      id -> {
        service = io.vertx.wiki.database.WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE);
        vertxTestContext.completeNow();
      }
    ));
  }

  @Test
  public void crud_operations(VertxTestContext testContext) {
    service.rxCreatePage("Test", "Some content")
      .andThen(service.rxFetchPage("Test"))
      .flatMap(json1 -> {
        testContext.verify(() -> {
          assertTrue(json1.getBoolean("found"));
          assertTrue(json1.containsKey("id"));
          assertEquals("Some content", json1.getString("rawContent"));
        });

        return service.rxSavePage(json1.getInteger("id"), "Yo!")
          .andThen(service.rxFetchAllPages())
          .flatMap(array1 -> {
            testContext.verify(() -> {
              assertEquals(1, array1.size());
            });
            return service.rxFetchPage("Test")
              .flatMap(json2 -> {
                testContext.verify(() -> {
                  assertEquals("Yo!", json2.getString("rawContent"));
                });
                return service.rxDeletePage(json1.getInteger("id"))
                  .andThen(service.rxFetchAllPages());
              });
          });
      }).subscribe(array2 -> {
      assertTrue(array2.isEmpty());
      testContext.completeNow();
    }, testContext::failNow);
  }

  @Test
  public void test_fetchAllPagesData(VertxTestContext testContext) {
    service.rxCreatePage("A", "abc")
      .andThen(service.rxCreatePage("B", "123"))
      .andThen(service.rxFetchAllPagesData())
      .subscribe(data -> {
        testContext.verify(() -> {
          assertEquals(2, data.size());

          JsonObject a = data.get(0);
          assertEquals("A", a.getString("NAME"));
          assertEquals("abc", a.getString("CONTENT"));

          JsonObject b = data.get(1);
          assertEquals("B", b.getString("NAME"));
          assertEquals("123", b.getString("CONTENT"));
          testContext.completeNow();
        });
      }, testContext::failNow);
  }
}
