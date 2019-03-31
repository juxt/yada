;; Copyright © 2014-2018, JUXT LTD.

(ns yada.resources.resources-resource
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.util.mime-type :refer [ext-mime-type]]
   [yada.resource :refer [as-resource resource]]))

(defn new-resources-resource
  [root-path]
  (letfn [(last-modified [^java.net.URL res]
            (with-open [conn (.openConnection res)]
              (let [lm (.getLastModified conn)]
                (when-not (zero? lm) lm))))]
    (resource
     {:path-info? true
      :properties
      (fn [ctx]
        (if-let [lm (some-> (io/resource (str root-path
                                              (-> ctx :request :path-info)))
                            (last-modified))]
          {:last-modified lm}
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
