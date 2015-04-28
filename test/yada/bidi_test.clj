;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :refer (yada) :as yada]
   [bidi.bidi :as bidi :refer (Matched Compilable compile-route succeed)]
   [bidi.ring :refer (make-handler Ring)]
   [ring.mock.request :refer (request)]))

;; This section tests uncompiled routes

(def security
  {:security {:type :basic :realm "Protected"}
   :authorization (fn [ctx]
                    (or
                     (when-let [auth (:authentication ctx)]
                       (= ((juxt :user :password) auth)
                          ["alice" "password"]))
                     :not-authorized))})

(def secure (partial yada/partial security))

(def api
  ["/api"
   {"/status" (yada/resource {:body "API working!"})
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure {"/a" (yada/resource :body "Secret area A")
                          "/b" (yada/resource :body "Secret area B")})}])

#_(let [h (-> api make-handler)]
  (h (request :get "/api/protected/a"))
  )

(deftest api-tests
  (let [h (-> api make-handler)
        => (comp (juxt :status :headers :body) deref h)]
    (testing "hello"
      (is (= (=> (request :get "/api/status")) [200 nil "API working!"])))))

;; Allowing for route compilation

;; A Clojure map can be compiled into this
(defrecord YadaResourceMap [resource-map yada-handler]
  Matched
  (resolve-handler [s m] yada-handler)
  (unresolve-handler [s m] (throw (ex-info "TODO" {}))))

;; Compile maps into handlers, via yada
(extend-protocol Compilable
  clojure.lang.APersistentMap
  (compile-pattern [m] m)
  (compile-matched [m] (->YadaResourceMap m (yada m))))

(def api-to-compile
  ["/api"
   [["/index" (yada {:body "Hello World!"})]
    ["/protected" ""]]])

(deftest compiled-api-tests
  (let [h (-> api-to-compile compile-route make-handler)
        => (comp (juxt :status :headers :body) deref h)]

    (testing "yada resource-map is compiled to a handler"
        (is (= (=> (request :get "/api/index")) [200 nil "Hello World!"])))))

;; What are we trying to achieve?

;; 1. Data-first structure defining API service in mostly data (bidi/yada) - can't go full edn because of the need to add functions sometimes

;; 2. Hierarchical definition of API, to include security details, e.g.

#_{:security {:type :basic :realm "Rubric"}
 :authorization (fn [ctx]
                  (or
                   (when-let [auth (:authentication ctx)]
                     (= ((juxt :user :password) auth)
                        ["rubric" "oxford-lexicon-eater"]))
                   :not-authorized))}

;; 3. bidi/yada driven swagger spec. (not swagger-first) - so bidi/yada format drives the generation of the swagger details


;; 4. Allow a String to be used as a constant
#_(extend-protocol Matched
  String
  (resolve-handler [s m] (succeed (yada :body s) m))
  (unresolve-handler [s m] nil))

#_(deftest string-tests
  (let [h (-> api-to-compile compile-route make-handler)
        => (comp (juxt :status :headers :body) deref h)]

    (testing "String compiles to a yada resource-map"
      (is (= (=> (request :get "/api/hello")) [200 nil "hello"])))))
