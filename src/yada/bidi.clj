;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "When bidi is used as a routing library, yada provides some
  support functions for more seamless integration and combined
  functionality"}
  yada.bidi
  (:refer-clojure :exclude [partial])
  (:require
   [yada.core :refer (yada* invoke-with-initial-context k-bidi-match-context)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler Compilable succeed)]
   [bidi.ring :refer (Ring request)]))

(def ^{:doc "This key is used to inject partial yada resource-map
  entries into bidi's matching-context, which is a map that is built up
  during bidi's matching process."}
  k-resource-map :yada/resource-map)

;; Acts as a wrapper
(defrecord PartialResourceMap [resource-map route]
  Matched
  (resolve-handler [_ m]
    (resolve-handler route (update-in m [k-resource-map] merge resource-map)))
  (unresolve-handler [_ m]
    (unresolve-handler route m))
  Compilable
  (compile-matched [this] (throw (ex-info "TODO: Compilation is not
  compatible with partial resource-map, until compilation supports
  propagation of a context down to delegates" {}))))

(defn partial
  "Wrap a group of bidi routes such that partial yada resource-map
  entries are injected into bidi's match-context. These are retrieved by
  the target Resource, if matched."
  [m route]
  (->PartialResourceMap m route))

;; bidi supports route compilation, where performance is critical, which
;; yada hooks into.

;; However, please note: Any partial resource-map information is not
;; able to be communicated, so will be missing and the potential change
;; to the resource's behaviour as a consequence means that yada/partials
;; cannot currently be combined with bidi/routes compilation. This can
;; be solved but only by adding a compilation context to bidi's
;; compilation process. But such a change to bidi/Compilable will cause
;; incompatibility with any bidi users that have created their own
;; records which satisfy it. Therefore this involves a new major release
;; of bidi. Since this namespace is currently of experimental status, I
;; don't want to change bidi to support it until the design in this
;; namespace stabilizes.

(defrecord CompiledResource [handler]
  Matched
  (resolve-handler [this m]
    (succeed this m))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))
  Ring
  (request [_ req match-context]
    (invoke-with-initial-context
     handler req {k-bidi-match-context match-context}))
  Compilable
  (compile-matched [this] this))

(defrecord Resource [resource-map]
  Matched
  (resolve-handler [_ m]
    (succeed (yada* (merge (get m k-resource-map) resource-map)) m))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))
  Compilable
  (compile-matched [m]
    (->CompiledResource (yada* resource-map))))

(defn resource* [{:as resource-map}]
  (->Resource resource-map))

(defn resource [& args]
  (if (keyword? (first args))
    (resource* (into {} (map vec (partition 2 args))))
    (resource* (first args))))
