(ns yada.console.routes
  (:require [clojure.set :refer [rename-keys]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [goog.string :as gstring]
            [re-frame.core :as re-frame]
            [yada.xhr :as xhr]))

(def routes
  ["/console/" {"" :cards
                ["card/" [keyword :card-id]] :card}])

(defn- dispatch-route [match]
  (case (:handler match)
    :cards (re-frame/dispatch [:show-all-cards])
    :card (re-frame/dispatch [:card-click (-> match :route-params :card-id)])))

(def history (pushy/pushy dispatch-route (partial bidi/match-route routes)))

(defn init []
  (pushy/start! history))

(defn set-token! [token]
  (pushy/set-token! history token))

(defn path-for [tag & args]
  (apply bidi/path-for routes tag args))
