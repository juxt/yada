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
   [modular.bidi :refer [path-for]]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]]
   [yada.media-type :as mt]
   [yada.dev.markdown-resource :refer (new-markdown-resource)]
   [yada.resources.file-resource :refer [new-directory-resource]]
   )
  (:import [modular.bidi Router]))

(s/defrecord Talks [*router :- (co-dep Router)
                    config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
          (try
            ["" [["/talks/" (s/with-fn-validation
                              (yada
                               (new-directory-resource
                                (io/file "talks")
                                {:custom-suffices
                                 {"md" {:representations [{:media-type #{"text/html" "text/plain;q=0.9"}}]
                                        :reader (fn [f rep]
                                                  (cond
                                                    (= (mt/media-type (:media-type rep)) "text/html")
                                                    (str (md-to-html-string (slurp f)) \newline)
                                                    :otherwise f)
                                                  )}
                                  "org" {:representations [{:media-type "text/plain"}]}}

                                 :index-files ["README.md"]
                                 })
                               {:id ::index}))]
                 ["/hello" (-> "Hello World!" yada)]
                 ["/hello-meta" (-> "Hello World!" yada yada)]
                 ["/hello-atom-meta" (-> "Hello World!" atom yada yada)]
                 ;; just a joke...
                 ["/hello-meta-meta" (-> "Hello World!" yada yada yada)]

                 ;; Let's do this properly, and bind a file resource somehow
                 ["/test.md" (yada (new-markdown-resource "# Heading\nHello"))]
                 ]]
            (catch clojure.lang.ExceptionInfo e
              (errorf e "Problem with routes %s" (ex-data e))))))

(defn new-talks [config]
  (-> (map->Talks {:config config})
      (co-using [:router])))
