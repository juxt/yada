;; Copyright © 2015, JUXT LTD.

(ns yada.dev.talks
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer (html)]
   [modular.bidi :refer [path-for]]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]]
   [yada.dev.markdown-resource :refer (new-markdown-resource)]
   )
  (:import [modular.bidi Router]))

(s/defrecord Talks [*router :- (co-dep Router)
                    config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
          (try
            ["" [["/talks/" (yada (io/file "talks") {:id ::index})]
                 ["/hello" (-> "Hello World!" yada)]
                 ["/hello-meta" (-> "Hello World!" yada yada)]
                 ["/hello-atom-meta" (-> "Hello World!" atom yada yada)]
                 ;; just a joke...
                 ["/hello-meta-meta" (-> "Hello World!" yada yada yada)]
                 ["/test.org" (yada (new-markdown-resource "# Heading\nHello"))]
                 ]]
            (catch clojure.lang.ExceptionInfo e
              (errorf e "Problem with routes %s" (ex-data e))))))

(defn new-talks [config]
  (-> (map->Talks {:config config})
      (co-using [:router])))
