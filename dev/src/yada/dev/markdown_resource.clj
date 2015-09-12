;; Copyright © 2015, JUXT LTD.

(ns yada.dev.markdown-resource
  (:require
   [clj-time.core :refer [now]]
   [schema.core :as s]
   [yada.protocols :as p]))

(s/defrecord MarkdownResource [content :- String]
  p/ResourceProperties
  (resource-properties
   [_]
   {:allowed-methods #{:get}
    :representations [{:media-type #{"text/html"}}]
    ::create-time (.getMillis (now))})
  (resource-properties
   [_ ctx]
   {:last-modified (-> ctx :resource-properties ::create-time)}))
