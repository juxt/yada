;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "When bidi is used as a routing library, yada provides some
  support functions for more seamless integration and combined
  functionality"}
  yada.bidi
  (:refer-clojure :exclude [partial])
  (:require
   [yada.core :refer (yada invoke-with-initial-context k-bidi-match-context)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler context succeed)]
   [bidi.ring :refer (Ring request)]))

(def ^{:doc "This key is used to inject partial yada resource-map
  entries into bidi's matching-context, which is a map that is built up
  during bidi's matching process."}
  k-resource-map :yada/resource-map)

;; Define a resource which can act as a handler in a bidi
(defrecord Resource [state options]
  Matched
  (resolve-handler [this m]
    ;; Succeed, returning this, because this satisfies Ring (below), so
    ;; can be called by the handler created by bidi's make-handler
    ;; function.
    (merge m {:handler this}))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))

  ;; For testing, it can be useful to invoke this with a request, just
  ;; as if it were a normal Ring handler function.
  clojure.lang.IFn
  (invoke [this req]
    ((yada state options) req))

  Ring
  (request [_ req match-context]
    (when-let [path-info (:path-info req)]
      (throw (ex-info "path-info already set on request" {:path-info path-info})))
    (let [handler (yada state (merge (get match-context k-resource-map) options))]
      (handler (if (not-empty (:remainder match-context))
                 (assoc req :path-info (:remainder match-context))
                 req)))))

(defn resource
  ([state]
   (resource state {}))
  ([state {:as options}]
   (-> (->Resource state options)
       ;; Inherit metadata, exploited for swagger spec gen
       (with-meta (meta options)))))

(defn partial
  "Contextually bind a set of resource-map entries to the match
  context. This allows policies (e.g. security entries) to be specified
  at a bidi route context which are merged with the final resource
  map. Where there is a merge clash, the inner-most (lower) context
  wins."
  [m routes]
  (context
   (fn [ctx]
     (merge-with merge ctx {k-resource-map m}))
   routes))
