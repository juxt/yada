;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require
   [schema.core :as s]
   yada.charset
   yada.media-type
   [yada.protocols :as p])
  (:import [yada.charset CharsetMap]
           [yada.media_type MediaTypeMap]))

(s/defschema MediaTypeSchema
  (s/either String MediaTypeMap))

(s/defschema CharsetSchema
  (s/either String CharsetMap))

(s/defn resource-properties
  :- {(s/optional-key :allowed-methods)
      (s/either [s/Keyword] #{s/Keyword})

      (s/optional-key :representations)
      [{(s/optional-key :media-type) (s/either MediaTypeSchema #{MediaTypeSchema})
        (s/optional-key :charset) (s/either CharsetSchema #{CharsetSchema})
        (s/optional-key :encoding) (s/either String #{String})
        (s/optional-key :language) (s/either String #{String})}]}
  [r]
  (p/resource-properties r))

;; The reason we can't have multiple arities is that s/defn has a
;; limitation that 'all arities must share the same output schema'.
(s/defn resource-properties-on-request
  :- {:exists? s/Bool
      (s/optional-key :last-modified) java.util.Date
      (s/optional-key :version) s/Any}
  [r ctx]
  (merge
   {:exists? true}
   (p/resource-properties r ctx)))
