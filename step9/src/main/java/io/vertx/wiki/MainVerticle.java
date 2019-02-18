package io.vertx.wiki;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;

@SuppressWarnings("Duplicates")
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    vertx.rxDeployVerticle("io.vertx.wiki.database.WikiDatabaseVerticle")
      .flatMap(id -> vertx.rxDeployVerticle("io.vertx.wiki.http.HttpServerVerticle",
        new DeploymentOptions().setInstances(2)))
      .subscribe(
        id -> startFuture.complete(),
        startFuture::fail
      );
  }
}
