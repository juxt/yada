;; Copyright © 201^, JUXT LTD.

(ns yada.wrappers
  "Augment routing models"
  (:require
   [clojure.walk :refer [postwalk]]
   [yada.resource :refer [resource]]))

(defn map->resource [m]
  (cond-> m (map? m) resource))

(defn routes
  "Process a route structure, interpretting any 'naked' maps in the
  Matched position as resource-models and converting them into
  resources. A pre-processing function can be provided that will be
  called on each naked map prior to the map being turned into a
  resource (with consequent schema checking). A post-processing
  function can also be provided that will post-process the actual
  resource records, after the schema check."
  [routes & [{:keys [pre post]
              :or {pre identity post identity}}]]
  (postwalk (comp post map->resource pre) routes))

