;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "When bidi is used as a routing library, yada provides some
  support functions for more seamless integration and combined
  functionality"}
  yada.bidi
  (:refer-clojure :exclude [partial])
  (:require
   [clojure.tools.logging :refer :all]
   [yada.core :refer (yada invoke-with-initial-context k-bidi-match-context)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler context succeed)]
   [bidi.ring :refer (Ring request)]))

(def ^{:doc "This key is used to inject partial yada service options
  into bidi's matching-context, which is a map that is built up during
  bidi's matching process."}  k-options :yada/options)

;; Define a resource which can act as a handler in a bidi

;; A bidi endpoint that captures a path remainder as path-info
(defrecord ResourceBranchEndpoint [resource options]
  Matched
  (resolve-handler [this m]
    ;; Succeed with this as the handler, because this satisfies Ring (below), so
    ;; can be called by the handler created by bidi's make-handler
    ;; function.
    (merge m {:handler this}))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))

  ;; For testing, it can be useful to invoke this with a request, just
  ;; as if it were a normal Ring handler function.
  clojure.lang.IFn
  (invoke [this req]
    ((yada resource options) req))

  Ring
  (request [_ req match-context]
    (let [handler (yada resource (merge (get match-context k-options) options))]
      (handler (let [rem (:remainder match-context)]
                 (if (and (seq rem) (.startsWith rem "/"))
                   (do
                     (when-let [path-info (:path-info req)]
                       (throw (ex-info "path-info already set on request" {:path-info path-info})))
                     (assoc req :path-info (:remainder match-context)))
                   req))))))

(defn resource-branch
  ([res]
   (resource-branch res {}))
  ([res service]
   (->ResourceBranchEndpoint res service)))

(defrecord ResourceLeafEndpoint [resource options]
  Matched
  (resolve-handler [this m]
    ;; Succeed with this has the handler, because this satisfies Ring (below), so
    ;; can be called by the handler created by bidi's make-handler
    ;; function.
    (succeed this m))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))

  ;; For testing, it can be useful to invoke this with a request, just
  ;; as if it were a normal Ring handler function.
  clojure.lang.IFn
  (invoke [this req]
    ((yada resource options) req))

  Ring
  (request [_ req match-context]
    (let [handler (yada resource (merge (get match-context k-options) options))]
      (handler req))))

(defn resource-leaf
  ([res]
   (resource-leaf res {}))
  ([res service]
   (->ResourceLeafEndpoint res service)))

(defn partial
  "Contextually bind a set of resource options to the match
  context. This allows policies (e.g. security entries) to be specified
  at a bidi route context which are merged with the final resource
  map. Where there is a merge clash, the inner-most (lower) context
  wins."
  [m routes]
  (context
   (fn [ctx]
     (merge-with merge ctx {k-options m}))
   routes))
