;; Copyright © 2014-2017, JUXT LTD.

(ns yada.resources.jar-resource
  (:require
   [clojure.java.io :as io]
   [ring.util.mime-type :refer (ext-mime-type)]
   [yada.resource :refer [resource as-resource]])
  (:import [java.util.jar JarFile]))

(defn new-jar-resource [prefix]
  (resource
   {:path-info? true
    :methods {}
    :sub-resource
    (fn [ctx]
      (let [path-info (-> ctx :request :path-info)
            path (str prefix path-info)
            res (io/resource path)]

        (cond
          (= (.getProtocol res) "jar")
          (let [[_ jarfile _] (re-matches #"file:(.*)!(.*)" (.getPath res))
                jarfile (new JarFile jarfile)]

            (let [je (.getEntry jarfile path)]
              (if (.isDirectory je)
                (resource
                 {:methods
                  {:get
                   {:produces "text/html"
                    :response
                    [:div
                     [:h1 "Resources"]
                     [:dl
                      [:dt "Jar location"]
                      [:dd [:tt (.getName jarfile)]]
                      [:dt "Path"]
                      [:dd [:tt path]]]
                     [:table
                      [:tbody
                       (let [entries
                             (sort-by (memfn getName)
                                      (for [entry (enumeration-seq (.entries jarfile))
                                            :let [n (.getName entry)]
                                            :when (and (.startsWith n path)
                                                       (> (count n) (count path)))]
                                        entry))]
                         (for [i entries
                               :let [p (subs (.getName i) (count path))]]
                           [:tr
                            [:td [:a {:href p} p]]]))]]]}}})
                (as-resource res)
                )))
          :otherwise (as-resource "Protocol not supported"))))}))
