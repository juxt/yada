(ns yada.console.routes
  (:require [clojure.set :refer [rename-keys]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [re-frame.core :as re-frame]))

(def routes
  ["/console/" {"home" :home
                "about" :about
                ["device/" :id] :device}])

(defn- dispatch-route [match]
  (case (:handler match)
    (:home :about) (let [panel-name (keyword (str (name (:handler match)) "-panel"))]
                     (re-frame/dispatch [:set-active-panel panel-name]))
    :device (re-frame/dispatch [:set-active-panel :device-panel (-> match :route-params :id)])))

(defn app-routes []
  (pushy/start! (pushy/pushy dispatch-route (partial bidi/match-route routes))))

(defn path-for [tag & args]
  (apply bidi/path-for routes tag args))
