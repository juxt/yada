;; Copyright © 2016, JUXT LTD.

(ns yada.dev.upload
  (:require
   [byte-streams :as bs]
   [bidi.bidi :refer [RouteProvider tag]]
   [bidi.vhosts :refer [uri-for redirect]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [schema.core :as s]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   [yada.consume :refer [save-to-file]]
   [yada.yada :refer [handler resource]]))

(defn build-routes []
  (try
    ["/upload"
     [
      ["" (redirect ::index)]
      ["/" (redirect ::index)]
      ["/post"
       (resource
        {:id ::index
         :methods
         {:post
          {:consumes "application/octet-stream"
           :consumer (fn [ctx _ body-stream]
                       (let [f (java.io.File/createTempFile "yada" ".tmp" (io/file "/tmp"))]
                         (infof "Saving to file: %s" f)
                         (save-to-file
                          ctx body-stream
                          f)))
           :response (fn [ctx] (format "Thank you, saved upload content to file: %s\n" (:file ctx)))}}})]]]

    (catch clojure.lang.ExceptionInfo e
      (errorf e (format "Errors: %s" (pr-str (ex-data e))))
      (errorf e "Getting exception on upload examples routes")
      [true (handler (str e))])
    
    (catch Throwable e
      (errorf e "Getting exception on upload examples routes")
      [true (handler (str e))])))

(s/defrecord UploadExamples []
  RouteProvider
  (routes [_] (build-routes)))

(defn new-upload-examples []
  (map->UploadExamples {}))

