;; Copyright © 2015, JUXT LTD.

(ns yada.dev.markdown-resource
  (:require
   [clj-time.core :refer [now]]
   [schema.core :as s]
   [markdown.core :refer (md-to-html-string)]
   [yada.resource :refer [new-custom-resource]]))

(s/defn new-markdown-resource [content :- String]
  (let [html (md-to-html-string content)]
    (new-custom-resource
     {:produces [{:media-type #{"text/html"}}]
      :properties {:last-modified (.getMillis (now)) :version content}
      :methods {:get {:handler (fn [ctx] content)}}})))
