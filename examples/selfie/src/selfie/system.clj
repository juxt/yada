;; Copyright © 2014-2017, JUXT LTD.

(ns selfie.system
  (:require
   [aleph.http :as http]
   [bidi.bidi :as bidi]
   [bidi.ring :refer [make-handler]]
   [clojure.string :as str]
   [com.stuartsierra.component :refer [system-map Lifecycle system-using using]]
   [hiccup.core :refer [html]]
   [selfie.api :refer [new-api-component]]
   [schema.core :as s]
   [yada.yada :as yada]))

(defn describe-routes
  "An example of the kind of thing you can do when your routes are data"
  [api]
  (for [{:keys [path handler]} (bidi/route-seq (bidi/routes api))]
    {:path (apply str path)
     :description (get-in handler [:properties :doc/description])
     :handler handler}))

(defn index-page [api port]
  (yada/yada
   (merge
    (yada/as-resource
     (html
      [:div
       [:h1 "Selfie"]
       (for [{:keys [path description handler]} (describe-routes api)]
         [:div
          [:h2 path]
          [:p description]
          (for [method (:allowed-methods handler)
                :let [meth (str/upper-case (str (name method)))]]
            [:div
             [:h3 meth]
             [:pre (format "curl -i -X %s http://localhost:%d%s" meth port path)]])])]))
    {:produces "text/html"})))

(defn create-routes [api port]
  ["" [["/" (index-page api port)]
       (bidi/routes api)
       [true (yada/yada nil)]]])

(defrecord ServerComponent [api port]
  Lifecycle
  (start [component]
    (let [routes (create-routes api port)]
      (assoc component
             :routes routes
             :server (http/start-server (make-handler routes) {:port port :raw-stream? true}))))
  (stop [component]
    (when-let [server (:server component)]
      (.close server))
    (dissoc component :server)))

(defn new-server-component [config]
  (map->ServerComponent config))

(defn new-system-map [config]
  (system-map
   :api (new-api-component)
   :server (new-server-component config)))

(defn new-dependency-map []
  {:api {}
   :server [:api]})

(defn new-co-dependency-map []
  {:api {:server :server}})

(s/defschema UserPort (s/both s/Int (s/pred #(<= 1024 % 65535))))

(s/defn new-selfie-app [config :- {:port UserPort}]
  (-> (new-system-map config)
      (system-using (new-dependency-map))))

