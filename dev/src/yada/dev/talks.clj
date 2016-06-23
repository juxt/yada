;; Copyright © 2015, JUXT LTD.

(ns yada.dev.talks
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer (html)]
   [markdown.core :refer (md-to-html-string)]
   [bidi.vhosts :refer [uri-for]]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]]
   [yada.media-type :as mt]
   [yada.schema :as ys]
   [yada.dev.markdown-resource :refer (new-markdown-resource)]
   [yada.resources.file-resource :refer [new-directory-resource]]
   )
  (:import [modular.bidi Router]))

(s/defrecord Talks [config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
    (try
      ["" [["/talks/"
            (s/with-fn-validation
              (yada
               (merge
                (new-directory-resource
                 (io/file "talks")
                 {:custom-suffices
                  {"md" {:produces (ys/representation-seq-coercer
                                    [{:media-type #{"text/html" "text/plain;q=0.9"}}])
                         :reader (fn [f rep]
                                   (cond
                                     (= (-> rep :media-type :name) "text/html")
                                     (str (md-to-html-string (slurp f)) \newline)
                                     :otherwise f))}
                   "org" {:produces (ys/representation-seq-coercer
                                     [{:media-type #{"text/plain"}}])}}
                  :index-files ["README.md"]})
                {:id ::index})))]
           #_["/hello" (-> "Hello World!\n" yada)]
           #_["/hello-meta" (-> "Hello World!\n" yada yada)]
           #_["/hello-atom-meta" (-> "Hello World!\n" atom yada yada)]
           ;; just a joke...
           #_["/hello-meta-meta" (-> "Hello World!\n" yada yada yada)]

           ;; Let's do this properly, and bind a file resource somehow
           ["/test.md" (yada (new-markdown-resource "# Heading\nHello"))]
           ]]
      (catch clojure.lang.ExceptionInfo e
        (errorf e "Error building routes %s" (ex-data e)))
      (catch Exception e
        (errorf e "Error building routes %s")))))

(defn new-talks [config]
  (map->Talks {:config config}))
