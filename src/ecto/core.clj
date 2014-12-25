(ns ecto.core
  (:require
   [manifold.deferred :as d]
   [bidi.bidi :as bidi]
   bidi.swagger
   [clojure.core.match :refer (match)]
   ))

;; API specs. are created like this

;; This is kind of like a bidi route structure

;; But we shouldn't limit ourselves to only that which is declared, because so much more can be generated, like 404s, etc.

;; "It is better to have 100 functions operate on one data structure than 10 functions on 10 data structures." —Alan Perlis

(defrecord Resource [])

(extend-protocol bidi/Matched
  Resource
  (resolve-handler [op m]
    (assoc m ::resource op))
  (unresolve-handler [op m]
    (when (= (:operationId op) (:handler m)) "")))

(defn op-handler [op]
  (fn [req]
    (cond
      :otherwise
      {:status 200 :body "foo"})))

(defn match-route [spec path & args]
  (apply bidi/match-route spec path args))

(defn make-handler
  [op]
  (let [allowed-methods (-> op :ecto.core/resource keys set)]
    (fn [req]
      (cond
        (not (contains? allowed-methods (:request-method req))) {:status 405 :body "Method not allowed."}
        :otherwise {:status 200 :body "foo"}))))

;; handle-method-not-allowed 405 "Method not allowed."



#_(let [req (mock/request :put "/persons")]
  ((op-handler (apply handle-route routes (:uri req) (apply concat req))) req))

;; This is OK
;; ((api-handler api) (mock/request :get "/persons"))

;; This is should yield 405 "Method not allowed."
;; ((api-handler api) (mock/request :get "/persons"))


;; List of interesting things to do


;; There should be a general handler that does the right thing
;; wrt. available methods (Method Not Allowed) and calls out to
;; callbacks accordingly. Perhaps there's no sense in EVERYTHING being
;; overridable, as with Liberator. It should hard-code the things that
;; don't make sense to override, and use hooks for the rest.

;; Resource existence is most important - and not covered by swagger, so it's a key hook.

;; Return deferreds, if necessary, if the computation is length to compute (e.g. for exists? checks)

;; CORS support: build this in, make allow-origin first-class, which headers should be allowed should be a hook (with default)
