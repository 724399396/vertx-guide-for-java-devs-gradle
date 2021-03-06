package io.vertx.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.auth.jdbc.JDBCAuth;
import io.vertx.reactivex.ext.auth.jwt.JWTAuth;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.wiki.database.reactivex.WikiDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;

import static io.vertx.wiki.DatabaseConstants.*;

// end::rx-imports[]

@SuppressWarnings("Duplicates")
public class HttpServerVerticle extends AbstractVerticle {
  public static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private FreeMarkerTemplateEngine templateEngine;
  private WikiDatabaseService dbService;
  private WebClient webClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
    dbService = io.vertx.wiki.database.WikiDatabaseService.createProxy(vertx.getDelegate(), wikiDbQueue);
    webClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setUserAgent("vert-x3"));
    JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKIDB_JDBC_URL))
      .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
      .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE)));
    JDBCAuth auth = JDBCAuth.create(vertx, dbClient);

    HttpServer server = vertx.createHttpServer(
      new HttpServerOptions()
        .setSsl(true)
        .setKeyStoreOptions(new JksOptions()
          .setPath("server-keystore.jks")
          .setPassword("secret"))
    );
    Router router = Router.router(vertx);
    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(UserSessionHandler.create(auth));
    AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login");
    router.route("/").handler(authHandler);
    router.route("/wiki/*").handler(authHandler);
    router.route("/action/*").handler(authHandler);

    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post("/action/save").handler(this::pageUpdateHandler);
    router.post("/action/create").handler(this::pageCreateHandler);
    router.post("/action/delete").handler(this::pageDeletionHandler);
    router.get("/action/backup").handler(this::backupHandler);
    router.get("/login").handler(this::loginHandler);
    router.post("/login-auth").handler(FormLoginHandler.create(auth));
    router.get("/logout").handler(context -> {
      context.clearUser();
      context.response()
        .setStatusCode(302)
        .putHeader("Location", "/")
        .end();
    });

    Router apiRouter = Router.router(vertx);

    JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .setKeyStore(new KeyStoreOptions()
        .setPath("keystore.jceks")
        .setType("jceks")
        .setPassword("secret")));
    apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/api/token"));

    apiRouter.get("/pages").handler(this::apiRoot);
    apiRouter.get("/pages/:id").handler(this::apiGetPage);
    apiRouter.post().handler(BodyHandler.create());
    apiRouter.post("/pages").handler(this::apiCreatePage);
    apiRouter.put().handler(BodyHandler.create());
    apiRouter.put("/pages/:id").handler(this::apiUpdatePage);
    apiRouter.delete("/pages/:id").handler(this::apiDeletePage);
    apiRouter.get("/token").handler(context -> {
      JsonObject creds = new JsonObject()
        .put("username", context.request().getHeader("login"))
        .put("password", context.request().getHeader("password"));
      auth.rxAuthenticate(creds).flatMap(user -> {
        Single<Boolean> create = user.rxIsAuthorized("create");
        Single<Boolean> delete = user.rxIsAuthorized("delete");
        Single<Boolean> update = user.rxIsAuthorized("update");

        return Single.zip(create, delete, update, (canCreate, canDelete, canUpdate) -> jwtAuth.generateToken(
          new JsonObject()
            .put("username", context.request().getHeader("login"))
            .put("canCreate", canCreate)
            .put("canDelete", canDelete)
            .put("canUpdate", canUpdate),
          new JWTOptions()
            .setSubject("Wiki API")
            .setIssuer("Vert.x")));
      }).subscribe(
        token -> context.response().putHeader("Content-Type", "text/plain").end(token),
        t -> context.fail(401));
    });
    router.mountSubRouter("/api", apiRouter);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);

    server
      .requestHandler(router)
      .listen(portNumber, ar -> {
        if (ar.succeeded()) {
          LOGGER.info("HTTP server running on port 8080");
          startFuture.complete();
        } else {
          LOGGER.error("Could not start a HTTP server", ar.cause());
          startFuture.fail(ar.cause());
        }
      });

  }

  private void indexHandler(RoutingContext context) {
    context.user().rxIsAuthorized("create")
      .flatMap(canCreatePage -> {
        context.put("canCreatePage", canCreatePage);
        return dbService.rxFetchAllPages();
      }).flatMap(result -> {
      context.put("title", "Wiki home");
      context.put("pages", result.getList());
      context.put("username", context.user().principal().getString("username"));
      return templateEngine.rxRender(context.data(), "templates/index.ftl");
    }).subscribe(markup -> {
      context.response().putHeader("Content-Type", "text/html");
      context.response().end(markup);
    });
  }

  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  private void pageRenderingHandler(RoutingContext context) {
    User user = context.user();
    user.rxIsAuthorized("update")
      .flatMap(canSavePage -> {
        context.put("canSavePage", canSavePage);
        return user.rxIsAuthorized("delete");
      }).flatMap(canDeletePage -> {
      context.put("canDeletePage", canDeletePage);
      String requestedPage = context.request().getParam("page");
      context.put("title", requestedPage);
      return dbService.rxFetchPage(requestedPage);
    }).flatMap(payLoad -> {

      boolean found = payLoad.getBoolean("found");
      String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
      context.put("id", payLoad.getInteger("id", -1));
      context.put("newPage", found ? "no" : "yes");
      context.put("rawContent", rawContent);
      context.put("content", Processor.process(rawContent));
      context.put("timestamp", new Date().toString());
      context.put("username", user.principal().getString("username"));
      return templateEngine.rxRender(context.data(), "templates/page.ftl");
    }).subscribe(
      markup -> {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(markup);
      },
      context::fail
    );
  }

  private void pageUpdateHandler(RoutingContext context) {
    String title = context.request().getParam("title");

    boolean pageCreation = "yes".equals(context.request().getParam("newPage"));
    String markdown = context.request().getParam("markdown");
    checkAuthorised(context, pageCreation ? "create" : "update")
      .andThen(pageCreation ? dbService.rxCreatePage(title, markdown) : dbService.rxSavePage(Integer.valueOf(context.request().getParam("id")), markdown))
      .subscribe(() -> {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      }, t -> onError(context, t));
  }

  private Completable checkAuthorised(RoutingContext context, String authority) {
    return context.user().rxIsAuthorized(authority)
      .flatMapCompletable(authorized -> authorized ? Completable.complete() :
        Completable.error(new UnauthorizedThrowable(authority)));
  }

  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private void pageDeletionHandler(RoutingContext context) {
    checkAuthorised(context, "delete")
      .andThen(dbService.rxDeletePage(Integer.parseInt(context.request().getParam("id"))))
      .subscribe(() -> {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      }, t -> onError(context, t));
  }

  private void backupHandler(RoutingContext context) {
    checkAuthorised(context, "role:writer")
      .andThen(dbService.rxFetchAllPagesData())
      .map(pages -> {
        JsonArray filesObject = new JsonArray();
        JsonObject payload = new JsonObject()
          .put("files", filesObject)
          .put("language", "plaintext")
          .put("title", "vertx-wiki-backup")
          .put("public", true);
        pages.forEach(page -> {
          JsonObject fileObject = new JsonObject();
          fileObject.put("name", page.getString("NAME"));
          fileObject.put("content", page.getString("CONTENT"));
          filesObject.add(fileObject);
        });
        return payload;
      }).flatMap(body ->
      webClient.post(443, "snippets.glot.io", "/snippets")
        .putHeader("Content-Type", "application/json")
        .as(BodyCodec.jsonObject())
        .rxSendJsonObject(body))
      .subscribe(response -> {
        if (response.statusCode() == 200) {
          String url = "https://glot.io/snippets/" + response.body().getString("id");
          context.put("backup_gist_url", url);
          indexHandler(context);
        } else {
          StringBuilder message = new StringBuilder()
            .append("Could not backup the wiki:")
            .append(response.statusMessage());
          JsonObject body = response.body();
          if (body != null) {
            message.append(System.getProperty("line.separator"))
              .append(body.encodePrettily());
          }
          LOGGER.error(message.toString());
          context.fail(502);
        }
      }, t -> onError(context, t));
  }

  private void apiRoot(RoutingContext context) {
    dbService.rxFetchAllPagesData()
      .flatMapPublisher(Flowable::fromIterable)
      .map(obj -> new JsonObject()
        .put("id", obj.getInteger("ID"))
        .put("name", obj.getString("NAME")))
      .collect(JsonArray::new, JsonArray::add)
      .subscribe(pages -> apiResponse(context, 200, "pages", pages), t -> apiFailure(context, t));
  }

  private void apiGetPage(RoutingContext context) {
    int id = Integer.valueOf(context.request().getParam("id"));
    dbService.rxFetchPageById(id)
      .subscribe(dbObject -> {
        if (dbObject.getBoolean("found")) {
          JsonObject payload = new JsonObject()
            .put("name", dbObject.getString("name"))
            .put("id", dbObject.getInteger("id"))
            .put("markdown", dbObject.getString("content"))
            .put("html", Processor.process(dbObject.getString("content")));
          apiResponse(context, 200, "page", payload);
        } else {
          apiFailure(context, 404, "There is no page with ID " + id);
        }
      }, t -> apiFailure(context, t));
  }

  private void apiCreatePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canCreate", false)) {
      JsonObject page = context.getBodyAsJson();
      if (!validateJsonPageDocument(context, page, "name", "markdown")) {
        return;
      }
      dbService.rxCreatePage(page.getString("name"), page.getString("markdown"))
        .subscribe(() -> apiResponse(context, 201, null, null), t -> apiFailure(context, t));
    } else {
      context.fail(401);
    }

  }

  private void apiUpdatePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canUpdate", false)) {
      int id = Integer.valueOf(context.request().getParam("id"));
      JsonObject page = context.getBodyAsJson();
      if (!validateJsonPageDocument(context, page, "markdown")) {
        return;
      }
      dbService.rxSavePage(id, page.getString("markdown"))
        .subscribe(() -> apiResponse(context, 200, null, null),
          t -> apiFailure(context, t));
    } else {
      context.fail(401);
    }

  }

  private void apiDeletePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canDelete", false)) {
      int id = Integer.valueOf(context.request().getParam("id"));
      dbService.rxDeletePage(id)
        .subscribe(() -> apiResponse(context, 200, null, null), t -> apiFailure(context, t));
    } else {
      context.fail(401);
    }
  }

  private boolean validateJsonPageDocument(RoutingContext context, JsonObject page, String... expectedKeys) {
    if (!Arrays.stream(expectedKeys).allMatch(page::containsKey)) {
      LOGGER.error("Bad page creation JSON payload: " + page.encodePrettily() + " from " + context.request().remoteAddress());
      context.response().setStatusCode(400);
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(new JsonObject()
        .put("success", false)
        .put("error", "Bad request payload").encode());
      return false;
    }
    return true;
  }

  private void loginHandler(RoutingContext context) {
    context.put("title", "Login");
    templateEngine.rxRender(context.data(), "templates/login.ftl")
      .subscribe(markup -> {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(markup);
      }, context::fail);
  }

  private void apiResponse(RoutingContext context, int statusCode, String jsonField, Object jsonData) {
    context.response().setStatusCode(statusCode);
    context.response().putHeader("Content-Type", "application/json");
    JsonObject wrapper = new JsonObject().put("success", true);
    if (jsonField != null && jsonData != null) {
      wrapper.put(jsonField, jsonData);
    }
    context.response().end(wrapper.encode());
  }

  private void apiFailure(RoutingContext context, Throwable t) {
    apiFailure(context, 500, t.getMessage());
  }

  private void apiFailure(RoutingContext context, int statusCode, String error) {
    context.response().setStatusCode(statusCode);
    context.response().putHeader("Content-Type", "application/json");
    context.response().end(new JsonObject()
      .put("success", false)
      .put("error", error).encode());
  }

  private void onError(RoutingContext context, Throwable t) {
    if (t instanceof HttpServerVerticle.UnauthorizedThrowable) {
      context.fail(403);
    } else {
      context.fail(t);
    }
  }

  private static final class UnauthorizedThrowable extends Throwable {
    UnauthorizedThrowable(String message) {
      super(message, null, false, false);
    }
  }
}
