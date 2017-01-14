;; Copyright © 2014-2017, JUXT LTD.

(ns yada.bidi
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring :as br]
   [bidi.vhosts :as bv]
   [yada.methods :as methods]
   [yada.handler :refer [new-handler HandlerCoercion allowed-methods interceptor-chain error-interceptor-chain handle-request]]
   [yada.resource])
  (:import
   [bidi.vhosts VHostsModel]
   [clojure.lang PersistentVector]
   [yada.handler Handler]
   [yada.resource Resource]))

(extend-type Resource
  bidi/Matched
  (resolve-handler [resource m]
    (if (:path-info? resource)
      (assoc m :handler resource)
      (bidi/succeed resource m)))

  (unresolve-handler [resource m]
    (when
        (or (= resource (:handler m))
            (when-let [id (:id resource)] (= id (:handler m))))
        ""))

  bidi/RouteSeq
  (gather [this context]
    [(bidi/map->Route
      (merge
       (assoc context :handler this)
       (when-let [id (:id this)] {:tag id})))])

  br/Ring
  (request [resource req match-context]
    (br/request (new-handler
                 (merge
                  {:id (get resource :id (java.util.UUID/randomUUID))
                   :resource resource
                   :allowed-methods (allowed-methods resource)
                   :known-methods (methods/known-methods)
                   :interceptor-chain (or (:interceptor-chain resource) (interceptor-chain match-context))
                   :error-interceptor-chain (or (:error-interceptor-chain resource) (error-interceptor-chain match-context))}))
                req match-context)))

(extend-type Handler

  bidi/Matched
  (resolve-handler [this m]
    ;; If we represent a collection of resources, let's match and retain
    ;; the remainder which we place into the request as :path-info (see
    ;; below).
    (if (-> this :resource :path-info?)
      (assoc m :handler this)
      (bidi/succeed this m)))

  (unresolve-handler [this m]
    (when
        (or (= this (:handler m))
            (when-let [id (:id this)] (= id (:handler m))))
        ""))

  bidi/RouteSeq
  (gather [this context]
    [(bidi/map->Route
      (merge
       (assoc context :handler this)
       (when-let [id (some-> this :resource :id)] {:tag id})))])

  br/Ring
  (request [this req match-context]
    (handle-request
     this
     (if (and (-> this :resource :path-info?)
              (not-empty (:remainder match-context)))
       (assoc req :path-info (:remainder match-context))
       req)
     match-context)))

(extend-protocol HandlerCoercion
  PersistentVector
  (as-handler [route]
    (br/make-handler route))
  VHostsModel
  (as-handler [model] (bv/make-handler model)))
