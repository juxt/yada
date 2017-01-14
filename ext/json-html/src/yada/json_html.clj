;; Copyright © 2014-2017, JUXT LTD.

(ns yada.json-html
  (:require
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [json-html.core :as jh]
   [yada.body :refer [to-body render-map]]))

(defmethod render-map "text/html"
  [m representation]
  (-> (html
       [:head [:style (slurp (io/resource "json.human.css"))]]
       (jh/edn->html m))
      (str \newline) ; annoying on the command-line otherwise
      (to-body representation) ; for string encoding
      ))
