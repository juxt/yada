;; Copyright © 2015, JUXT LTD.

(ns yada.dev.markdown-resource
  (:require
   [clj-time.core :refer [now]]
   [schema.core :as s]
   [markdown.core :refer (md-to-html-string)]
   [yada.methods :refer [Get]]
   [yada.protocols :as p]))

(s/defrecord MarkdownResource [content :- String]
  p/Properties
  (properties
   [_]
   (let [html (md-to-html-string content)]
     {:allowed-methods #{:get}
      :representations [{:media-type #{"text/html"}}]
      :last-modified (.getMillis (now))
      :version content
      ::html (md-to-html-string content)}))
  (properties
   [_ ctx] {})
  Get
  (GET [_ ctx] (-> ctx :properties ::html)))

(defn new-markdown-resource [content]
  (->MarkdownResource content))
