(ns yada.dev.swagger
  (:require
   [yada.yada :refer (yada) :as yada]
   [yada.bidi :refer (resource)]
   [bidi.bidi :refer (RouteProvider tag)]
   [bidi.ring :refer (make-handler)]
   [ring.mock.request :refer (request)]
   [yada.swagger :refer (swaggered)]
   [cheshire.core :refer (decode)]))

;; {"swagger":"2.0","info":{"title":"API","version":"0.0.1"},"produces":["application/json","application/x-yaml","application/edn","application/transit+json","application/transit+msgpack"],"consumes":["application/json","application/x-yaml","application/edn","application/transit+json","application/transit+msgpack"],"basePath":"/","paths":{"/api/users":{"post":{"tags":["registration"],"summary":"Register a user","parameters":[{"in":"body","name":"UserRegistrationSchema","description":"","required":true,"schema":{"$ref":"#/definitions/UserRegistrationSchema"}}],"responses":{"default":{"description":""}}},"get":{"tags":["registration"],"summary":"List users","responses":{"default":{"description":""}}}}},"definitions":{"UserRegistrationSchema":{"type":"object","properties":{"email":{"type":"string"},"password":{"type":"string"}},"required":["email","password"]}}}

(defrecord UserApi []
  RouteProvider
  (routes [_]
    ["/api"
     (->
      (swaggered
       {:info {:title "User API"
               :version "0.0.1"
               :description "Example user API"}
        :basePath "/api"}
       {"/users" {"" (resource
                      :body "Hello"
                      :allowed-methods
                      {:post "Register user"
                       :get "List users"})

                  ["/" :username]
                  {"" (resource :state {:user "bob"})
                   "/posts" (resource
                             :state "Posts"
                             :allowed-methods
                             {:get "List posts"
                              :post "Create new post"
                              :put "Update post"
                              :delete "Delete post"}
                             )}}})
      (tag ::user-api))]))

(defn new-user-api []
  (->UserApi))
