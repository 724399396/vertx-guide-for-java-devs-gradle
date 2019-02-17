package io.vertx.wiki;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;

@SuppressWarnings("Duplicates")
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    Single<String> dbVerticleDeployment = vertx.rxDeployVerticle(
      "io.vertx.guides.wiki.database.WikiDatabaseVerticle");
    dbVerticleDeployment
      .flatMap(id -> vertx.rxDeployVerticle("io.vertx.wiki.http.HttpServerVerticle",
        new DeploymentOptions().setInstances(2))).flatMap(id -> vertx.rxDeployVerticle("io.vertx.wiki.http.AuthInitializerVerticle"))
      .subscribe(
        id -> startFuture.complete(),
        startFuture::fail
      );
  }
}
