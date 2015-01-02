(ns yada.dev.router
  (:require
   [modular.ring :refer (WebRequestHandler)]
   [modular.bidi :refer (WebService routes request-handlers uri-context)]
   [bidi.bidi :as bidi]
   [bidi.ring :refer (make-handler)]
   [com.stuartsierra.component :refer (Lifecycle)]
   [schema.core :as s]
   bidi.swagger)
  (:import (bidi.swagger SwaggerOperation)))

(defrecord ToHandler [matched handlers]
  bidi/Matched
  (resolve-handler [this m]
    (when-let [{:keys [handler] :as res} (bidi/resolve-handler matched m)]
      (cond
        (keyword? handler)
        (assoc res :handler (get handlers handler))

        (instance? SwaggerOperation handler)
        (throw (ex-info "TODO! Convert SwaggerOperation as handler in yada.dev.router" {}))

        :otherwise res)))
  (unresolve-handler [this m]
    (bidi/unresolve-handler matched m)))

(defn as-request-handler
  "Take a WebService component and return a Ring handler."
  [service]
  (assert (satisfies? WebService service))
  (let [routes (routes service)
        handlers (request-handlers service)]
    (make-handler [(or (uri-context service) "")
                   (->ToHandler [routes] handlers)])))

(defn wrap-capture-component-on-error
  "Wrap handler in a try/catch that will capture the component and
  handler of the error."
  [h & {:keys [component handler]}]
  (when h
    (fn [req]
      (try
        (h req)
        (catch Exception cause
          (throw (ex-info "Failure during request handling"
                          {:component component :handler handler}
                          cause)))))))

(defrecord ComponentAddressable [matched ckey handlers]
  bidi/Matched
  (resolve-handler [this m]
    (when-let [{:keys [handler] :as res} (bidi/resolve-handler matched m)]
      (if (keyword? handler)
        (assoc res
          :handler (cond-> (get-in handlers [ckey handler])
                           ;; This should be based on given settings
                     true (wrap-capture-component-on-error :component ckey :handler handler)))
        res)))

  (unresolve-handler [this m]
    (bidi/unresolve-handler matched m)))

(defrecord Router []
  Lifecycle
  (start [this]
    (let [handlers
          (apply merge
                 (for [[k v] this :when (satisfies? WebService v)]
                   (try
                     {k (request-handlers v)}
                     (catch Throwable e (throw (ex-info "Failed to call request-handlers" {:k k :v v} e))))))]

      (assoc this
             :handlers handlers
             :routes ["" (vec (for [[ckey v] this
                                    :when (satisfies? WebService v)]
                                [(or (uri-context v) "")
                                 (->ComponentAddressable [(routes v)] ckey handlers)]))])
      ))
  (stop [this] this)
  WebService
  (request-handlers [this] (:handlers this))
  (routes [this] (:routes this))
  (uri-context [this] (:uri-context this))
  WebRequestHandler
  (request-handler [this] (as-request-handler this)))

(def new-router-schema {})

(defn new-router [& {:as opts}]
  (->> opts
    (merge {})
    (s/validate new-router-schema)
    map->Router))
