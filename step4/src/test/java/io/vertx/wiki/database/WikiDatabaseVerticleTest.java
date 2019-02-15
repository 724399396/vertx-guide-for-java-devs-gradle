package io.vertx.wiki.database;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class WikiDatabaseVerticleTest {
  private WikiDatabaseService service;

  @BeforeEach
  public void prepare(Vertx vertx, VertxTestContext vertxTestContext) {
    JsonObject conf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf), vertxTestContext.succeeding(
      id -> {
        service = WikiDatabaseService.createProxy(vertx, WikiDatabaseVerticle.CONFIG_WIKIDB_QUEUE);
        vertxTestContext.completeNow();
      }
    ));
  }

  @Test
  public void crud_operations(VertxTestContext testContext) {
    service.createPage("Test", "Some content", testContext.succeeding(v1 -> {
      service.fetchPage("Test", testContext.succeeding(json1 -> testContext.verify(() -> {
        assertTrue(json1.getBoolean("found"));
        assertTrue(json1.containsKey("id"));
        assertEquals("Some content", json1.getString("rawContent"));

        service.savePage(json1.getInteger("id"), "Yo!", testContext.succeeding(v2 -> {
          service.fetchAllPages(testContext.succeeding(array1 -> testContext.verify(() -> {
            assertEquals(1, array1.size());
            service.fetchPage("Test", testContext.succeeding(json2 -> testContext.verify(() -> {
                assertEquals("Yo!", json2.getString("rawContent"));
                service.deletePage(json1.getInteger("id"), v3 -> {
                  service.fetchAllPages(testContext.succeeding(array2 -> {
                    assertTrue(array2.isEmpty());
                    testContext.completeNow();
                  }));
                });
              })
            ));
          })));
        }));
      })));
    }));
  }
}
