;; Copyright © 2015, JUXT LTD.

(ns yada.api-test
  (:require
   [clojure.edn :as edn]
   [com.stuartsierra.component :refer (system-using system-map)]
   [bidi.bidi :refer (match-route routes)]
   [bidi.ring :refer (make-handler)]
   [clojure.test :refer :all]
   [yada.yada :refer (yada) :as yada]
   [yada.test.util :refer (given)]
   [manifold.deferred :as d]
   [modular.test :refer (with-system-fixture *system*)]
   [cheshire.core :as json]
   [ring.mock.request :as mock]
   [yada.dev.user-api :refer (new-verbose-user-api)]
   [yada.dev.database :refer (new-database)]
   yada.resources.collection-resource
   ))

(defn new-system
  "Define a minimal system which is just enough for the tests in this
  namespace to run"
  []
  (system-using
   (system-map
    :api (new-verbose-user-api))
    {}))

(use-fixtures :each (with-system-fixture new-system))

(deftest api-test
  (let [handler (make-handler (routes (:api *system*)))]
    (testing "swagger.json"
      (let [response @(handler (mock/request :get "/api/swagger.json"))]

        (given response
          :status := 200
          :headers :> {"content-type" "application/json"})

        (given (-> response :body json/decode)
          "swagger" := "2.0"
          ["info" "title"] := "User API"
          ["info" "version"] := "0.0.1"
          ["info" "description"] := "Example user API"
          "produces" := ["application/json"]
          "consumes" := ["application/json"]
          "basePath" := "/api"

          ["paths" "/users" "get" "summary"] := "Get users"
          ["paths" "/users" "get" "description"] := "Get a list of all known users"

          ["paths" "/users/{username}" "get" "parameters"] :? vector?
          ["paths" "/users/{username}" "get" "parameters" first] :? map?
          ["paths" "/users/{username}" "get" "parameters" first "in"] := "path"
          ["paths" "/users/{username}" "get" "parameters" first "name"] := "username"
          ["paths" "/users/{username}" "get" "parameters" first "description"] := ""
          ["paths" "/users/{username}" "get" "parameters" first "required"] := true
          ["paths" "/users/{username}" "get" "parameters" first "type"] := "string"
          ["paths" "/users/{username}" "get" "responses" "default" "description"] := ""

          ["paths" "/users/{username}/posts" "post"] :? map?
          ["paths" "/users/{username}/posts" "post" "summary"] := "Create a new post"
          ["paths" "/users/{username}/posts" "post" "responses"] :? map?
          ["paths" "/users/{username}/posts" "post" "responses" "default"] :? map?
          ["paths" "/users/{username}/posts" "post" "responses" "default" "description"] := ""

          "definitions" := {})))

    (testing "/users"
      ;; TODO
      )

    ;; TODO Reinstate when we know what functions in place of resources are for
    #_(testing "/users/{username}"
      (let [response @(handler (mock/request :get "/api/users/bob"))]

        (given response
          :status := 200
          :headers :> {"content-type" "application/edn"}
          [:body edn/read-string :user :name] := "Bob"))

      (let [response @(handler (mock/request :get "/api/users/zippo"))]
        (given response
          :status := 404)))))
