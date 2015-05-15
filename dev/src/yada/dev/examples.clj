;; Copyright © 2015, JUXT LTD.

(ns yada.dev.examples
  (:require
   [bidi.bidi :refer (RouteProvider path-for alts tag)]
   [bidi.ring :refer (redirect)]
   [schema.core :as s]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cheshire.core :as json]
   [yada.yada :refer (yada format-http-date)]
   [hiccup.core :refer (html h) :rename {h escape-html}]
   [markdown.core :as markdown]
   [com.stuartsierra.component :refer (using Lifecycle)]
   [modular.component.co-dependency :refer (co-using)]
   [ring.middleware.params :refer (wrap-params)]
   [ring.mock.request :refer (request) :rename {request mock-request}]
   [clojure.core.async :refer (go go-loop timeout <! >! chan)]
   )
  (:import
   (java.util Date Calendar)))

(defn basename [r]
  (last (string/split (.getName (type r)) #"\.")))

(defrecord Chapter [title intro-text])

(defn chapter? [h] (instance? Chapter h))

(defprotocol Example
  (resource-map [_] "Return handler")
  (make-handler [_] "Create handler")
  (request [_] "Return request sent to handler")
  (path [_] "Where a resource is mounted")
  (path-args [_] "Any path arguments to use in the URI")
  (query-string [_] "Query string to add to the request")
  (expected-response [_] "What the response should be")
  (test-function [_] "Which JS function to call to test the example")
  (http-spec [_] "Which section of an RFC does this relate to")
  (different-origin? [_] "Whether the example tests against a server of a different origin")
  )

(defn example? [h] (satisfies? Example h))

;; Introduction

(defrecord HelloWorld []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord DynamicHelloWorld []
  Example
  (resource-map [_] '{:body (fn [ctx] "Hello World!")})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord AsyncHelloWorld []
  Example
  (resource-map [_] '{:body (fn [ctx]
                              (future (Thread/sleep 1000)
                                      "Hello World!"))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Parameters

(defrecord PathParameterUndeclared []
  Example
  (resource-map [_]
    '{:body (fn [ctx]
              (str "Account number is "
                   (-> ctx :request :route-params :account)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account 1234])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ParameterDeclaredPathQueryWithGet []
  Example
  (resource-map [_]
    '{:parameters
      {:get {:path {:account Long}
             :query {:since String}}
       :post {:path {:account Long}
              :body {:payee String
                     :description String
                     :amount Double}}}
      :body (fn [ctx]
              (let [{:keys [since account]} (:parameters ctx)]
                (format "List transactions since %s from account number %s"
                        since account)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account 1234])
  (request [_] {:method :get})
  (query-string [_] "since=tuesday")
  (expected-response [_] {:status 200}))

#_(defrecord ServerSentEventsWithCoreAsyncChannel []
  Example
  (resource-map [_]
    '(do
       (require '[clojure.core.async :refer (chan go-loop <! >! timeout close!)])
       (require '[manifold.stream :refer (->source)])
       {:body
        {"text/event-stream"
         (fn [ctx]
           (let [ch (chan)]
             (go-loop [n 10]
               (if (pos? n)
                 (do
                   (>! ch (format "The time on the yada server is now %s, messages after this one: %d" (java.util.Date.) (dec n)))
                   (<! (timeout 1000))
                   (recur (dec n)))
                 (close! ch)))
             ch)
           )}}))
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (test-function [_] "tryItEvents"))



#_(defrecord PathParameterRequired []
  Example
  (resource-map [_]
    '{:parameters
      {:path {:account schema.core/Num}}
      :body (fn [ctx] (str "Account number is " (-> ctx :parameters :account)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord PathParameterCoerced []
  Example
  (resource-map [_]
    '{:params
      {:account {:in :path :type Long}
       :account-type {:in :path :type schema.core/Keyword}}
      :body (fn [ctx] (format "Type of account parameter is %s, account type is %s" (-> ctx :params :account type) (-> ctx :params :account-type)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" :account-type "/" :account])
  (path-args [_] [:account 1234 :account-type "savings"])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord PathParameterCoercedError []
  Example
  (resource-map [_]
    '{:params
      {:account {:in :path :type Long :required true}}
      :body (fn [ctx] (format "Account is %s" (-> ctx :params :account)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account "wrong"])
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameter []
  Example
  (resource-map [_]
    '{:body (fn [ctx]
              (str "Showing transaction in month "
                   (-> ctx :request :query-params (get "month"))))})
  (make-handler [ex] (-> (yada (eval (resource-map ex)))
                         (wrap-params)))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord QueryParameterDeclared []
  Example
  (resource-map [_]
    '{:parameters
      {:get {:query {:month s/Str}}}
      :body (fn [ctx]
              (str "Showing transactions in month "
                   (-> ctx :parameters :month)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord QueryParameterRequired []
  Example
  (resource-map [_]
    '{:parameters
      {:month {:in :query :required true}
       :order {:in :query :required true}}
      :body (fn [ctx]
              (format "Showing transactions in month %s ordered by %s"
                      (-> ctx :params :month)
                      (-> ctx :params :order)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameterNotRequired []
  Example
  (resource-map [_]
    '{:params
      {:month {:in :query :required true}
       :order {:in :query}}
      ;; TODO: When we try to return the map, we get this instead: Caused by: java.lang.IllegalArgumentException: No method in multimethod 'render-map' for dispatch value: null
      :body (fn [ctx] (str "Parameters: " (-> ctx :params)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameterCoerced []
  Example
  (resource-map [_]
    '{:params
      {:month {:in :query :type schema.core/Inst}
       :order {:in :query :type schema.core/Keyword}}
      :body (fn [ctx]
              (format "Month type is %s, ordered by %s"
                      (-> ctx :params :month type)
                      (-> ctx :params :order)))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (query-string [_] "month=2014-09&order=most-recent")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Misc

(defrecord StatusAndHeaders []
  Example
  (resource-map [_] '{:status 280
                      :headers {"content-type" "text/plain;charset=utf-8"
                                "x-extra" "foo"}
                      :body "Look, headers ^^^"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 280}))

;; TODO Async body options (lots more options than just this one)

(comment
  ;; client's content type already indicates content, e.g. application/json
  ;; which is already accessible via a delay (or equiv.)
  {:post (fn [ctx]
           ;; this is just like a normal Ring handler.

           ;; the main benefit is that state is available via deref,
           ;; the content-type is already handled

           (let [entity @(get-in ctx [:request :entity])]
             ;; create entity in database
             ;; construct new uri, redirect to it

             ;; the http diag says on POST :-
             ;; if POST, if redirect 303,

             ;; just use ring's redirect-after-post, using a location
             ;; header, constructed with bidi create user 741238, then
             ;; redirect
             (redirect (path-for routes :user 741238))

             ;; otherwise, if new resource? reply with 201
             (created (path-for routes :user 741238))
             => {:status 201 :headers {:location (path-for routes :user 741238)}}
             ;; otherwise return a 204, or a 200 if there's a body

             ;; you can always return a 300 too, if multiple representations
             )

           )})

;; Conneg

(def simple-body-map
  '{:body {"text/html" (fn [ctx] "<h1>Hello World!</h1>")
           "text/plain" (fn [ctx] "Hello World!")}} )

(defrecord BodyContentTypeNegotiation []
  Example
  (resource-map [_] simple-body-map)
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/html"}})
  (expected-response [_] {:status 200}))

(defrecord BodyContentTypeNegotiation2 []
  Example
  (resource-map [_] simple-body-map)
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; Resource metadata (conditional requests)

(defrecord ResourceExists []
  Example
  (resource-map [_] '{:resource true})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceFunction []
  Example
  (resource-map [_] '{:resource (fn [req] true)})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceExistsAsync []
  Example
  (resource-map [_] '{:resource (fn [req] (future (Thread/sleep 500) true))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceDoesNotExist []
  Example
  (resource-map [_] '{:resource false})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 404}))

(defrecord ResourceDoesNotExistAsync []
  Example
  (resource-map [_] '{:resource (fn [opts] (future (Thread/sleep 500) false))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 404}))

;; Resource State


;; TODO: Add other parameter types (header, formData and body)
;;
;; > Possible values are "query", "header", "path", "formData" or "body".
;; -- https://github.com/swagger-api/swagger-spec/blob/master/versions/2.0.md#parameterObject

(defrecord ResourceState []
  Example
  (resource-map [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceStateWithBody []
  Example
  (resource-map [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))
                      :body {"text/plain" (fn [ctx] (format "Your balance is ฿%s " (-> ctx :resource :state :balance)))}
                      })
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceStateTopLevel []
  Example
  (resource-map [_] '{:state (fn [ctx] {:accno (-> ctx :request :route-params :account)})})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Conditional GETs

(defrecord LastModifiedHeader [start-time]
  Example
  (resource-map [_] {:body "Hello World!"
                     :resource {:last-modified start-time}
                     })
  (make-handler [ex] (yada (resource-map ex)))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord LastModifiedHeaderAsLong [start-time]
  Example
  (resource-map [_] {:body "Hello World!"
                     :resource {:last-modified (.getTime start-time)}
                     })
  (make-handler [ex] (yada (resource-map ex)))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord LastModifiedHeaderAsDeferred [start-time]
  Example
  (resource-map [_]
    (let [s start-time]
      `{:body "Hello World!"
        :resource {:last-modified (fn [ctx#] (delay (.getTime ~s)))}
        }))
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord IfModifiedSince [start-time]
  Example
  (resource-map [_] {:body "Hello World!"
                     :resource {:last-modified start-time}
                     })
  (make-handler [ex] (yada (resource-map ex)))
  (request [_] {:method :get
                :headers {"If-Modified-Since" (format-http-date (new java.util.Date (+ (.getTime start-time) (* 60 1000))))}})
  (expected-response [_] {:status 304}))

;; POSTS

#_(defrecord PostNewResource []
  Example
  (resource-map [_] '{})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; Conditional Requests

(defrecord PutResourceMatchedEtag []
  Example
  (resource-map [_] '{:resource {:etag "58614618"}
                      :put true})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :put
                :headers {"If-Match" "58614618"}})
  (expected-response [_] {:status 204})
  (http-spec [_] ["7232" "3.1"]))

(defrecord PutResourceUnmatchedEtag []
  Example
  (resource-map [_] '{:resource {:etag "58614618"}
                      :put true})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :put
                :headers {"If-Match" "c668ab6b"}})
  (expected-response [_] {:status 412})
  (http-spec [_] ["7232" "3.1"]))

;; Misc

(defrecord ServiceUnavailable []
  Example
  (resource-map [_] '{:service-available? false})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableAsync []
  Example
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) false)})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503}))

(defrecord ServiceUnavailableRetryAfter []
  Example
  (resource-map [_] '{:service-available? 120})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter2 []
  Example
  (resource-map [_] '{:service-available? (constantly 120)})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter3 []
  Example
  (resource-map [_]
    '{:service-available? #(future (Thread/sleep 500) 120)})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord DisallowedPost []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :post})
  (expected-response [_] {:status 405}))

(defrecord DisallowedGet []
  Example
  (resource-map [_] '{:post true})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 405}))

(defrecord DisallowedPut []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :put})
  (expected-response [_] {:status 405}))

(defrecord DisallowedDelete []
  Example
  (resource-map [_] '{:post true})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :delete})
  (expected-response [_] {:status 405}))

(def ^:dynamic *state* nil)

(defrecord PostCounter []
  Example
  (resource-map [_]
    '{:post
      (let [counter (:*post-counter yada.dev.examples/*state*)]
        (fn [ctx]
          (assoc-in ctx
                    [:response :headers "X-Counter"]
                    (swap! counter inc))))})
  (make-handler [ex] (yada (binding [*state* ex] (eval (resource-map ex)))))
  (request [_] {:method :post})
  (expected-response [_] {:status 200}))

(defrecord AccessForbiddenToAll []
  Example
  (resource-map [_]
    '{:authorization false
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 403}))

(defrecord AccessForbiddenToSomeRequests []
  Example
  (resource-map [_]
    '{:parameters {:get {:query {:secret String}}}
      :authorization (fn [ctx] (= (-> ctx :parameters :secret) "oak"))
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r)])
  (query-string [_] "secret=ash")
  (request [_] {:method :get})
  (expected-response [_] {:status 403}))

(defrecord AccessAllowedToOtherRequests []
  Example
  (resource-map [_]
    '{:parameters {:get {:query {:secret String}}}
      :authorization (fn [ctx] (= (-> ctx :parameters :secret) "oak"))
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (path [r] [(basename r)])
  (query-string [_] "secret=oak")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord NotAuthorized []
  Example
  (resource-map [_]
    '{:authorization (fn [ctx] :not-authorized)
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 401}))

(defrecord BasicAccessAuthentication []
  Example
  (resource-map [_]
    '{:security {:type :basic :realm "Gondor"}
      :authorization
      (fn [ctx]
        (or
         (when-let [auth (:authentication ctx)]
           (= ((juxt :user :password) auth)
              ["Denethor" "palantir"]))
         :not-authorized))
      :body "All is lost. Yours, Sauron (Servant of Morgoth, yada yada yada)"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 401}))

(defrecord CorsAll []
  Example
  (resource-map [_]
    '{:allow-origin true
      :body "Hello everyone!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord CorsCheckOrigin []
  Example
  (resource-map [_]
    '{:allow-origin
      (fn [ctx]
        (println (get-in ctx [:request :headers "origin"]))
        (get-in ctx [:request :headers "origin"]))
      :body "Hello friend!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (different-origin? [_] true))

(defrecord CorsPreflight []
  Example
  (resource-map [_]
    '{:allow-origin
      (fn [ctx]
        (println (get-in ctx [:request :headers "origin"]))
        (get-in ctx [:request :headers "origin"]))
      :put (fn [_] "Resource changed!")
      :body "Hello friend!"})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :put})
  (expected-response [_] {:status 204})
  (different-origin? [_] true))

(defrecord ServerSentEvents []
  Example
  (resource-map [_]
    '{:body {"text/event-stream" ["Event 1" "Event 2" "Event 3"]}
      })
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ServerSentEventsWithCoreAsyncChannel []
  Example
  (resource-map [_]
    '(do
       (require '[clojure.core.async :refer (chan go-loop <! >! timeout close!)])
       (require '[manifold.stream :refer (->source)])
       {:body
        {"text/event-stream"
         (fn [ctx]
           (let [ch (chan)]
             (go-loop [n 10]
               (if (pos? n)
                 (do
                   (>! ch (format "The time on the yada server is now %s, messages after this one: %d" (java.util.Date.) (dec n)))
                   (<! (timeout 1000))
                   (recur (dec n)))
                 (close! ch)))
             ch)
           )}}))
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (test-function [_] "tryItEvents"))

(defrecord ServerSentEventsDefaultContentType []
  Example
  (resource-map [_]
    '(do
       (require '[clojure.core.async :refer (chan go-loop <! >! timeout close!)])
       (require '[manifold.stream :refer (->source)])
       {:state
        (fn [ctx]
          (let [ch (chan)]
            (go-loop [n 10]
              (if (pos? n)
                (do
                  (>! ch (format "The time on the yada server is now %s, messages after this one: %d" (java.util.Date.) (dec n)))
                  (<! (timeout 1000))
                  (recur (dec n)))
                (close! ch)))
            ch)
          )}))
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (test-function [_] "tryItEvents"))

(defn title [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn get-path [r]
  (or
   (try (path r) (catch AbstractMethodError e))
   (last (string/split (.getName (type r)) #"\."))))

(defn get-path-args [r]
  (or
   (try (path-args r) (catch AbstractMethodError e))
   []))

(defn get-query-string [r]
  (try (query-string r) (catch AbstractMethodError e)))

(defn get-test-function [ex]
  (try (test-function ex) (catch AbstractMethodError e)))

(defn description [r]
  (when-let [s (io/resource (str "examples/pre/" (title r) ".md"))]
    (markdown/md-to-html-string (slurp s))))

(defn post-description [r]
  (when-let [s (io/resource (str "examples/post/" (title r) ".md"))]
    (markdown/md-to-html-string (slurp s))))

(defn external? [r]
  (try (different-origin? r) (catch AbstractMethodError e false)))
