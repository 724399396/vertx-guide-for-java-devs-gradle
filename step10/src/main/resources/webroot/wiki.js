'use strict';

function generateUUID() {
  var d = new Date().getTime();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    var r = (d + Math.random() * 16) % 16 | 0;
    d = Math.floor(d / 16);
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

angular.module("wikiApp", [])
  .controller("WikiController", ["$scope", "$http", "$timeout", function ($scope, $http, $timeout) {
    var DEFAULT_PAGENAME = "Example page";
    var DEFAULT_MARKDOWN = "# Example page\n\nSome text_here_.\m";

    $scope.newPage = function () {
      $scope.pageId = undefined;
      $scope.pageName = DEFAULT_PAGENAME;
      $scope.pageMarkdown = DEFAULT_MARKDOWN;
    };

    $scope.reload = function () {
      $http.get("/api/pages").then(function (response) {
        $scope.pages = response.data.pages;
      });
    };

    $scope.pageExists = function () {
      return $scope.pageId !== undefined;
    };

    $scope.load = function (id) {
      $http.get("/api/pages/" + id).then(function (response) {
        var page = response.data.page;
        $scope.pageId = page.id;
        $scope.pageName = page.name;
        $scope.pageMarkdown = page.markdown;
        $scope.updateRendering(page.html);
      })
    };

    $scope.updateRendering = function (html) {
      document.getElementById("rendering").innerHTML = html;
    }

    $scope.save = function () {
      if ($scope.pageId === undefined) {
        var payload = {
          "name": $scope.pageName,
          "markdown": $scope.pageMarkdown
        };
        $http.post("/api/pages", payload).then(function (ok) {
          $scope.reload();
          $scope.success("Page created");
          var guessMaxId = _.maxBy($scope.pages, function (page) {
            return page.id;
          });
          $scope.load(guessMaxId.id || 0);
        }, function (err) {
          $scope.error(err.data.error)
        });
      } else {
        var payload = {
          "client": clientUuid,
          "markdown": $scope.pageMarkdown
        };
        $http.put("/api/pages/" + $scope.pageId, payload).then(function (ok) {
          $scope.success("Page saved");
        }, function (err) {
          $scope.error(err.data.error)
        });
      }
    };

    $scope.delete = function () {
      $http.delete("/api/pages/" + $scope.pageId).then(function (ok) {
        $scope.reload();
        $scope.newPage();
        $scope.success("Page deleted");
      }, function (err) {
        $scope.error(err.data.error);
      });
    };

    $scope.success = function (message) {
      $scope.alertMessage = message;
      var alert = document.getElementById("alertMessage");
      alert.classList.add("alert-success");
      alert.classList.remove("invisible");
      $timeout(function () {
        alert.classList.add("invisible");
        alert.classList.remove("alert-success");
      }, 3000);
    };

    $scope.error = function (message) {
      $scope.alertMessage = message;
      var alert = document.getElementById("alertMessage");
      alert.classList.add("alert-danger");
      alert.classList.remove("invisible");
      $timeout(function () {
        alert.classList.add("invisible");
        alert.classList.remove("alert-danger");
      }, 5000);
    };

    $scope.reload();
    $scope.newPage();

    var markdownRenderingPromise = null;
    $scope.$watch("pageMarkdown", function (text) {
      if (markdownRenderingPromise !== null) {
        $timeout.cancel(markdownRenderingPromise);
      }
      markdownRenderingPromise = $timeout(function () {
        markdownRenderingPromise = null;
        $http.post("/app/markdown", text).then(function (response) {
          $scope.updateRendering(response.data);
        });
      }, 300);
    });

    var eb = new EventBus(window.location.protocol + "//" + window.location.host + "/eventbus");
    // eb.send("app.markdown", text, function (err, reply) {
    //   if (err === null) {
    //     $scope.$apply(function () {
    //       $scope.updateRendering(reply.body);
    //     });
    //   } else {
    //     console.warn("Error rendering Markdown content: " + JSON.stringify(err));
    //   }
    // })

    var clientUuid = generateUUID();
    eb.onopen = function () {
      eb.registerHandler("page.saved", function (error, message) {
        if (message.body
          && $scope.pageId === message.body.id
          && clientUuid !== message.body.client) {
          $scope.$apply(function () {
            $scope.pageModified = true;
          });
        }
      });
    };
  }]);
