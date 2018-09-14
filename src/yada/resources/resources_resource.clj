;; Copyright © 2014-2018, JUXT LTD.

(ns yada.resources.resources-resource
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.util.mime-type :refer [ext-mime-type]]
   [yada.resource :refer [as-resource resource]]))

(defn new-resources-resource
  [root-path]
  (letfn [(file-of-resource [^java.net.URL res]
            (case (.getProtocol res)
              "jar" (let [^java.net.JarURLConnection conn (.openConnection res)
                          jar-file-url ^java.net.URL (.getJarFileURL conn)]
                      (.getFile jar-file-url))
              "file" (.getFile res)))]
    (resource
     {:path-info? true
      :properties
      (fn [ctx]
        (if-let [res (io/resource (str root-path (-> ctx :request :path-info)))]
          {:last-modified (some-> res file-of-resource io/file (.lastModified))}
          {}))
      :methods
      {:get
       {:produces
        (fn [ctx]
          (let [path (-> ctx :request :path-info)]
            (let [[mime-type typ _]
                  (re-matches
                   #"(.*)/(.*)"
                   (or (ext-mime-type path) "text/plain"))]
              (merge
               {:media-type mime-type}
               (when (= typ "text") {:charset "UTF-8"})))))
        :response
        (fn [ctx]
          (when-let [res (io/resource (str root-path (-> ctx :request :path-info)))]
            (.openStream res)))}}})))
