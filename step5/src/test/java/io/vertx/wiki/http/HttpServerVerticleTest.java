package io.vertx.wiki.http;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class HttpServerVerticleTest {
  @Test
  public void start_http_server(Vertx vertx, VertxTestContext testContext) {
    vertx.createHttpServer().requestHandler(req ->
      req.response().putHeader("Content-Type", "text/plain").end("Ok"))
      .listen(8080, testContext.succeeding(server -> {
          WebClient webClient = WebClient.create(vertx);
          webClient.get(8080, "localhost", "/").send(ar -> testContext.verify(() -> {
            if (ar.succeeded()) {
              HttpResponse<Buffer> response = ar.result();
              assertTrue(response.headers().contains("Content-Type"));
              assertEquals("text/plain", response.getHeader("Content-Type"));
              assertEquals("Ok", response.bodyAsString());
              webClient.close();
              testContext.completeNow();
            } else {
              testContext.failNow(ar.cause());
            }
          }));
        }
      ));
  }
}
