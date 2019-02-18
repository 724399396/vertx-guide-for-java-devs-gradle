package io.vertx.wiki.database.reactivex;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.wiki.database.WikiDatabaseServiceVertxEBProxy;

import java.util.List;

public class WikiDatabaseService {
  private WikiDatabaseServiceVertxEBProxy wikiDatabaseServiceVertxEBProxy;

  public WikiDatabaseService(WikiDatabaseServiceVertxEBProxy wikiDatabaseServiceVertxEBProxy) {
    this.wikiDatabaseServiceVertxEBProxy = wikiDatabaseServiceVertxEBProxy;
  }

  public Single<JsonArray> rxFetchAllPages() {
    Future<JsonArray> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.fetchAllPages(future);
    return SingleHelper.toSingle(future::setHandler);
  }

  public Single<JsonObject> rxFetchPage(String name) {
    Future<JsonObject> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.fetchPage(name, future.completer());
    return SingleHelper.toSingle(future::setHandler);
  }

  public Completable rxCreatePage(String title, String markdown) {
    Future<Void> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.createPage(title, markdown, future.completer());
    return CompletableHelper.toCompletable(future::setHandler);
  }

  public Completable rxSavePage(int id, String markdown) {
    Future<Void> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.savePage(id, markdown, future.completer());
    return CompletableHelper.toCompletable(future::setHandler);
  }

  public Completable rxDeletePage(int id) {
    Future<Void> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.deletePage(id, future.completer());
    return CompletableHelper.toCompletable(future::setHandler);
  }

  public Single<List<JsonObject>> rxFetchAllPagesData() {
    Future<List<JsonObject>> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.fetchAllPagesData(future.completer());
    return SingleHelper.toSingle(future::setHandler);
  }

  public Single<JsonObject> rxFetchPageById(int id) {
    Future<JsonObject> future = Future.future();
    wikiDatabaseServiceVertxEBProxy.fetchPageById(id, future.completer());
    return SingleHelper.toSingle(future::setHandler);
  }
}
