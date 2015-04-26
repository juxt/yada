;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require
   [hiccup.core :refer (html)]
   [cheshire.core :as json]
   [manifold.stream :refer (->source transform)])
  (:import
   (yada.protocols ReadPortWrapper)
   (clojure.core.async.impl.channels ManyToManyChannel)
   (manifold.stream.async CoreAsyncSource)))

(defprotocol Content
  (content [_ content-type] "Get resource's content, given the content-type")
  (content-type-default [_] "Return the default content type of the object if not specified explicitly"))

(defmulti render-map (fn [state content-type] content-type))
(defmulti render-seq (fn [state content-type] content-type))

(extend-protocol Content
  java.util.Map
  (content [state content-type] (render-map state content-type))
  (content-type-default [_] "application/json")
  clojure.lang.Sequential
  (content [state content-type] (render-seq state content-type))
  (content-type-default [_] "application/json")
  String
  (content [state _] state)
  (content-type-default [_] "text/plain")
  CoreAsyncSource
  (content [state content-type] (render-seq state content-type))
  (content-type-default [_] "text/event-stream")
  #_Object
  ;; Default is to return object unmarshalled
  #_(content [state _] state)
  #_(content-type-default [_] nil)
  nil
  (content [_ content-type] nil)
  (content-type-default [_] nil))

(defmethod render-map "application/json"
  [m _]
  (json/encode m))

(defmethod render-seq "application/json"
  [s _]
  (json/encode s))

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
