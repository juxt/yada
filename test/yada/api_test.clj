;; Copyright © 2015, JUXT LTD.

(ns yada.api-test
  (:require
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

(defn get-api []
  (-> *system* :api))

#_(defn get-op-response [api req & {:as opts}]
  (let [h (yada opts)
        {rp :route-params} (match-route api (:uri req))]
    (let [res (h (assoc req :route-params rp))]
      (if (d/deferrable? res) @res res))))

(deftest api-test
  (let [h (make-handler (routes (:api *system*)))
        req (mock/request :get "/api/swagger.json")
        response @(h req)]
    (given response
      :status := 200
      :headers :> {"content-type" "application/json"}
;;      [:body json/decode] := ""
      )
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
