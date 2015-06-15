;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [hiccup.core :refer [html]]
            [manifold.stream :refer [->source transform]]
            [ring.swagger.schema :as rs]
            [ring.util.mime-type :as mime])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]
           [java.io File]
           [java.net URL]
           [manifold.stream.async CoreAsyncSource]))

;; From representation

(defmulti decode-representation (fn [representation content-type & args] content-type))

(defmethod decode-representation "application/json"
  ([representation content-type schema]
   (rs/coerce schema (decode-representation representation content-type) :json))
  ([representation content-type]
   (json/decode representation keyword)))

;; Representation means the representation of state, for the purposes of network communication.

(defprotocol Representation
  (content [_ content-type] "Get representation data, given the content-type")
  (content-type-default [_] "Return the default content type of the object if not specified explicitly")
  (content-length [_] "Return the size of the resource's represenation, if this can possibly be known up-front (return nil if this is unknown)"))

(defmulti render-map (fn [resource content-type] content-type))
(defmulti render-seq (fn [resource content-type] content-type))

;; TODO: what does it mean to have a default content type? Perhaps, this
;; should be a list of content types that the representation can adapt
;; to

(extend-protocol Representation

  java.util.Map
  (content [resource content-type] (render-map resource content-type))
  (content-type-default [_] "application/json")

  clojure.lang.Sequential
  (content [resource content-type] (render-seq resource content-type))
  (content-type-default [_] "application/json")

  String
  (content [resource _] resource)
  (content-type-default [_] "text/plain")
  (content-length [s] (.length s))

  CoreAsyncSource
  (content [resource content-type] (render-seq resource content-type))
  (content-type-default [_] "text/event-stream")
  (content-length [_] nil)

  File
  (content [f content-type] f)
  (content-type-default [f] (or (mime/ext-mime-type (.getName f)) "application/octet-stream"))
  (content-length [f] (.length f))

  URL
  (content [url content-type] (.openStream url))
  (content-type-default [f] (or (mime/ext-mime-type (.getPath f)) "application/octet-stream"))

  Object
  (content-type-default [_] nil)

  nil
  (content [_ content-type] nil)
  (content-type-default [_] nil)
  (content-length [_] nil))

(defmethod render-map "application/json"
  [m _]
  (json/encode m))

(defmethod render-map "application/edn"
  [m _]
  (pr-str m))

(defmethod render-seq "application/json"
  [s _]
  (json/encode s))

(defmethod render-seq "application/edn"
  [s _]
  (pr-str s))

(defmethod render-seq "text/event-stream"
  [s _]
  ;; Transduce the body to server-sent-events
  (transform (map (partial format "data: %s\n\n")) (->source s)))

(defmethod render-map "text/html"
  [m _]
  (let [style {:style "border: 1px solid black; border-collapse: collapse"}]
    (html
     [:html
      [:body
       (cond
         (map? (second (first m)))
         (let [ks (keys (second (first m)))
               ]
           [:table
            [:thead
             [:tr
              [:th style "id"]
              (for [k ks] [:th style k])
              ]]
            [:tbody
             (for [[k v] (sort m)]
               [:tr
                [:td style k]
                (for [k ks]
                  [:td style (get v k)])])]])
         :otherwise
         [:table
          [:thead
           [:tr
            [:th "Name"]
            [:th "Value"]
            ]]
          [:tbody
           (for [[k v] m]
             [:tr
              [:td style k]
              [:td style v]
              ])]])
       [:p "This is a default rendering to assist development"]]])))
