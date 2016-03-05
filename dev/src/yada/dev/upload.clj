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
          {:response (fn [ctx] (format "hi, file is %s\n" (:file ctx)))
           :consumes "application/octet-stream"
           :consumer (fn [ctx _ body-stream]
                       (let [tmpfile
                             (java.io.File/createTempFile "yada" ".tmp" (io/file "/tmp"))
                             fos (new java.io.FileOutputStream tmpfile false)
                             fc (.getChannel fos)
                             ]

                         (infof "got a body-stream, streaming to %s" tmpfile)
                         
                         (d/chain
                          (stream/reduce (fn [ctx buf]
                                           (let [buf (bs/to-byte-buffer buf)]
                                             (infof "buf is type %s" (type buf))
                                             (.write fc buf))
                                           ctx)
                                         ctx
                                         body-stream)
                          (fn [ctx]
                            (.close fc)
                            (assoc-in ctx [:file] tmpfile)
                            )
                          )
                         )
                       )}}})]]]

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

