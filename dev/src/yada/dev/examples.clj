;; Copyright © 2015, JUXT LTD.

(ns yada.dev.examples
  (:require
   [bidi.bidi :refer (RouteProvider path-for alts tag)]
   [bidi.ring :refer (redirect)]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.stuartsierra.component :refer (using Lifecycle)]
   [hiccup.core :refer (html h) :rename {h escape-html}]
   [markdown.core :as markdown]
   [modular.component.co-dependency :refer (co-using)]
   [ring.middleware.params :refer (wrap-params)]
   [ring.mock.request :refer (request) :rename {request mock-request}]
   [ring.util.time :refer (format-date)]
   [schema.core :as s]
   [yada.yada :refer (yada)]
   [clojure.core.async :refer (go go-loop timeout <! >! chan)])
  (:import
   [java.util Date Calendar]))

(defn basename [r]
  (last (string/split (.getName (type r)) #"\.")))

(defrecord Chapter [title intro-text])

(defn chapter? [h] (instance? Chapter h))

(defprotocol Example
  (resource [_] "Return handler")
  (options [_] "Return resource options")
  (make-handler [_] "Create handler")
  (request [_] "Return the example request that should be sent to handler")
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
  (resource [_] "Hello World!")
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord DynamicHelloWorld []
  Example
  (resource [_] '{:body (fn [ctx] "Hello World!")})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord AsyncHelloWorld []
  Example
  (resource [_] '{:body (fn [ctx]
                              (future (Thread/sleep 1000)
                                      "Hello World!"))})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Parameters

(defrecord PathParameterUndeclared []
  Example
  (resource [_]
    '{:body (fn [ctx]
              (str "Account number is "
                   (-> ctx :request :route-params :account)))})
  (options [_] '{})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account 1234])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(def common-params
        {:get {:path {:account Long}
               :query {:since String}}
         :post {:path {:account Long}
                :body {:payee String
                       :description String
                       :amount Double}}})

(defrecord ParameterDeclaredPathQueryWithGet []
  Example
  (resource [_]
    {
     :body '(fn [ctx]
              (let [{:keys [since account]} (:parameters ctx)]
                (format "List transactions since %s from account number %s"
                          since account)))})
  (options [_] {:parameters common-params})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account 1234])
  (request [_] {:method :get})
  (query-string [_] "since=tuesday")
  (expected-response [_] {:status 200}))

(defrecord ParameterDeclaredPathQueryWithPost []
  Example
  (resource [_]
    {:parameters
     common-params
     :post! '(fn [ctx]
               (format "Thank you for posting %s" (-> ctx :parameters :body)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account 1234])
  (request [_] {:method :post
                :headers {"Content-Type" "application/json"}
                :data {:payee "The gas board"
                       :description "Gas for last year"
                       :amount 20.99}})
  (expected-response [_] {:status 200}))

(defrecord FormParameter []
  Example
  (resource [_]
    {:parameters
     {:post {:form {:email String}}}
     :post! '(fn [ctx] (format "Saving email: %s" (-> ctx :parameters :email)))
     })
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :post
                :headers {"Content-Type" "application/x-www-form-urlencoded;charset=US-ASCII"}
                :data "email=alice%40example.org"})
  (expected-response [_] {:status 200}))

(defrecord HeaderParameter []
  Example
  (resource [_]
    {:parameters
     {:get {:header {:x-tag String}}}
     :body '(fn [ctx] (format "x-tag is %s" (-> ctx :parameters :x-tag)))
     })
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get
                :headers {"X-Tag" "foobar"}})
  (expected-response [_] {:status 200}))

#_(defrecord PathParameterRequired []
  Example
  (resource [_]
    '{:parameters
      {:path {:account schema.core/Num}}
      :body (fn [ctx] (str "Account number is " (-> ctx :parameters :account)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord PathParameterCoerced []
  Example
  (resource [_]
    '{:params
      {:account {:in :path :type Long}
       :account-type {:in :path :type schema.core/Keyword}}
      :body (fn [ctx] (format "Type of account parameter is %s, account type is %s" (-> ctx :params :account type) (-> ctx :params :account-type)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" :account-type "/" :account])
  (path-args [_] [:account 1234 :account-type "savings"])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord PathParameterCoercedError []
  Example
  (resource [_]
    '{:params
      {:account {:in :path :type Long :required true}}
      :body (fn [ctx] (format "Account is %s" (-> ctx :params :account)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" :account])
  (path-args [_] [:account "wrong"])
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameter []
  Example
  (resource [_]
    '{:body (fn [ctx]
              (str "Showing transaction in month "
                   (-> ctx :request :query-params (get "month"))))})
  (make-handler [ex] (-> (yada (eval (resource ex)))
                         (wrap-params)))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord QueryParameterDeclared []
  Example
  (resource [_]
    '{:parameters
      {:get {:query {:month s/Str}}}
      :body (fn [ctx]
              (str "Showing transactions in month "
                   (-> ctx :parameters :month)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

#_(defrecord QueryParameterRequired []
  Example
  (resource [_]
    '{:parameters
      {:month {:in :query :required true}
       :order {:in :query :required true}}
      :body (fn [ctx]
              (format "Showing transactions in month %s ordered by %s"
                      (-> ctx :params :month)
                      (-> ctx :params :order)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameterNotRequired []
  Example
  (resource [_]
    '{:params
      {:month {:in :query :required true}
       :order {:in :query}}
      ;; TODO: When we try to return the map, we get this instead: Caused by: java.lang.IllegalArgumentException: No method in multimethod 'render-map' for dispatch value: null
      :body (fn [ctx] (str "Parameters: " (-> ctx :params)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (query-string [_] "month=2014-09")
  (request [_] {:method :get})
  (expected-response [_] {:status 400}))

#_(defrecord QueryParameterCoerced []
  Example
  (resource [_]
    '{:params
      {:month {:in :query :type schema.core/Inst}
       :order {:in :query :type schema.core/Keyword}}
      :body (fn [ctx]
              (format "Month type is %s, ordered by %s"
                      (-> ctx :params :month type)
                      (-> ctx :params :order)))})
  (make-handler [ex] (yada (eval (resource ex))))
  (query-string [_] "month=2014-09&order=most-recent")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Misc

(defrecord CustomStatus []
  Example
  (resource [_] '{:status 418
                      :headers {"content-type" "text/plain;charset=utf-8"}
                      :body "I'm a teapot!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 418}))

(defrecord CustomHeader []
  Example
  (resource [_] '{:status (fn [ctx] 418)
                      :headers {"content-type" "text/plain;charset=utf-8"
                                "x-blend" "dahjeeling"}
                      :body "I'm a teapot, here's my custom header"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 418}))

;; TODO Async body options (lots more options than just this one)

(comment
  ;; client's content type already indicates content, e.g. application/json
  ;; which is already accessible via a delay (or equiv.)
  {:post! (fn [ctx]
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
  (resource [_] simple-body-map)
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/html"}})
  (expected-response [_] {:status 200}))

(defrecord BodyContentTypeNegotiation2 []
  Example
  (resource [_] simple-body-map)
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

(defrecord ProducesContentTypeNegotiationJson []
  Example
  (resource-map [_] '{:state (fn [ctx] {:foo "bar"})
                     :produces #{"application/json" "application/edn"}})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get
                :headers {"Accept" "application/json"}})
  (expected-response [_] {:status 200}))

(defrecord ProducesContentTypeNegotiationEdn []
  Example
  (resource-map [_] '{:state (fn [ctx] {:foo "bar"})
                     :produces #{"application/json" "application/edn"}})
  (make-handler [ex] (yada (eval (resource-map ex))))
  (request [_] {:method :get
                :headers {"Accept" "application/edn"}})
  (expected-response [_] {:status 200}))

;; Resource metadata (conditional requests)

;; State

#_(defrecord StateFile []
  Example
  (resource [_] '{:state "README.md"})
  (make-handler [ex] (yada (eval (resource ex))))
  #_(path [r] [(basename r) "/" [long :account]])
  #_(path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord State []
  Example
  (resource [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord StateWithBody []
  Example
  (resource [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))
                      :body {"text/plain" (fn [ctx] (format "Your balance is ฿%s " (-> ctx :state :balance)))}
                      })
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord StateWithFile []
  Example
  (resource [_] '{:state (io/file "README.md")  ; TODO Try with README.me, seemed to cause a hang last time
                      :methods [:put]})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r)])
  (request [_] {:method :put})
  (expected-response [_] {:status 200}))

;; Conditional GETs

(defrecord LastModifiedHeader [start-time]
  Example
  (resource [_] {:body "Hello World!"
                     :resource {:last-modified start-time}
                     })
  (make-handler [ex] (yada (resource ex)))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord LastModifiedHeaderAsLong [start-time]
  Example
  (resource [_] {:body "Hello World!"
                     :resource {:last-modified (.getTime start-time)}
                     })
  (make-handler [ex] (yada (resource ex)))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord LastModifiedHeaderAsDeferred [start-time]
  Example
  (resource [_]
    (let [s start-time]
      `{:body "Hello World!"
        :resource {:last-modified (fn [ctx#] (delay (.getTime ~s)))}
        }))
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord IfModifiedSince [start-time]
  Example
  (resource [_] {:body "Hello World!"
                     :resource {:last-modified start-time}
                     })
  (make-handler [ex] (yada (resource ex)))
  (request [_] {:method :get
                :headers {"If-Modified-Since" (format-date (new java.util.Date (+ (.getTime start-time) (* 60 1000))))}})
  (expected-response [_] {:status 304}))

;; POSTS

#_(defrecord PostNewResource []
  Example
  (resource [_] '{})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; Conditional Requests

(defrecord PutResourceMatchedEtag []
  Example
  (resource [_] '{:resource {:etag "58614618"}
                      :put true})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :put
                :headers {"If-Match" "58614618"}})
  (expected-response [_] {:status 204})
  (http-spec [_] ["7232" "3.1"]))

(defrecord PutResourceUnmatchedEtag []
  Example
  (resource [_] '{:resource {:etag "58614618"}
                      :put true})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :put
                :headers {"If-Match" "c668ab6b"}})
  (expected-response [_] {:status 412})
  (http-spec [_] ["7232" "3.1"]))

;; Misc

(defrecord ServiceUnavailable []
  Example
  (resource [_] '{:service-available? false})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableAsync []
  Example
  (resource [_] '{:service-available? (fn [ctx] (future (Thread/sleep 500) false))})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503}))

(defrecord ServiceUnavailableRetryAfter []
  Example
  (resource [_] '{:service-available? 120})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter2 []
  Example
  (resource [_] '{:service-available? (constantly 120)})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter3 []
  Example
  (resource [_]
    '{:service-available? (fn [ctx] (future (Thread/sleep 500) 120))})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord DisallowedPost []
  Example
  (resource [_] '{:body "Hello World!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :post})
  (expected-response [_] {:status 405}))

(defrecord DisallowedGet []
  Example
  (resource [_] '{:methods #{:put :post}})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 405}))

(defrecord DisallowedPut []
  Example
  (resource [_] '{:body "Hello World!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :put})
  (expected-response [_] {:status 405}))

(defrecord DisallowedDelete []
  Example
  (resource [_] '{:methods #{:get :post}})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :delete})
  (expected-response [_] {:status 405}))

(def ^:dynamic *state* nil)

(defrecord PostCounter []
  Example
  (resource [_]
    '{:post
      (let [counter (:*post-counter yada.dev.examples/*state*)]
        (fn [ctx]
          (assoc-in ctx
                    [:response :headers "X-Counter"]
                    (swap! counter inc))))})
  (make-handler [ex] (yada (binding [*state* ex] (eval (resource ex)))))
  (request [_] {:method :post})
  (expected-response [_] {:status 200}))

(defrecord AccessForbiddenToAll []
  Example
  (resource [_]
    '{:authorization false
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 403}))

(defrecord AccessForbiddenToSomeRequests []
  Example
  (resource [_]
    '{:parameters {:get {:query {:secret String}}}
      :authorization (fn [ctx] (= (-> ctx :parameters :secret) "oak"))
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r)])
  (query-string [_] "secret=ash")
  (request [_] {:method :get})
  (expected-response [_] {:status 403}))

(defrecord AccessAllowedToOtherRequests []
  Example
  (resource [_]
    '{:parameters {:get {:query {:secret String}}}
      :authorization (fn [ctx] (= (-> ctx :parameters :secret) "oak"))
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (path [r] [(basename r)])
  (query-string [_] "secret=oak")
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord NotAuthorized []
  Example
  (resource [_]
    '{:authorization (fn [ctx] :not-authorized)
      :body "Secret message!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 401}))

(defrecord BasicAccessAuthentication []
  Example
  (resource [_]
    '{:security {:type :basic :realm "Gondor"}
      :authorization
      (fn [ctx]
        (or
         (when-let [auth (:authentication ctx)]
           (= ((juxt :user :password) auth)
              ["Denethor" "palantir"]))
         :not-authorized))
      :body "All is lost. Yours, Sauron (Servant of Morgoth, yada yada yada)"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 401}))

(defrecord CorsAll []
  Example
  (resource [_]
    '{:allow-origin true
      :body "Hello everyone!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord CorsCheckOrigin []
  Example
  (resource [_]
    '{:allow-origin
      (fn [ctx]
        (println (get-in ctx [:request :headers "origin"]))
        (get-in ctx [:request :headers "origin"]))
      :body "Hello friend!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (different-origin? [_] true))

(defrecord CorsPreflight []
  Example
  (resource [_]
    '{:allow-origin
      (fn [ctx]
        (println (get-in ctx [:request :headers "origin"]))
        (get-in ctx [:request :headers "origin"]))
      :put (fn [_] "Resource changed!")
      :body "Hello friend!"})
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :put})
  (expected-response [_] {:status 204})
  (different-origin? [_] true))

(defrecord ServerSentEvents []
  Example
  (resource [_]
    '{:body {"text/event-stream" ["Event 1" "Event 2" "Event 3"]}
      })
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ServerSentEventsWithCoreAsyncChannel []
  Example
  (resource [_]
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
  (make-handler [ex] (yada (eval (resource ex))))
  (request [_] {:method :get})
  (expected-response [_] {:status 200})
  (test-function [_] "tryItEvents"))

(defrecord ServerSentEventsDefaultContentType []
  Example
  (resource [_]
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
  (make-handler [ex] (yada (eval (resource ex))))
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

(defn get-request [r]
  (try (request r) (catch AbstractMethodError e)))

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
