;; Copyright © 2015, JUXT LTD.

(ns yada.dev.error-example
  (:require
   [com.stuartsierra.component :refer [Lifecycle]]
   [bidi.bidi :refer [RouteProvider tag]]
   [ring.mock.request :refer [request]]
   [yada.swagger :refer [swaggered]]
   [yada.resources.journal-browser :refer [new-journal-browser-resources]]
   [yada.yada :as yada]))

(defn hello-error [journal]
  (yada/yada
   (fn [ctx] (throw (ex-info "TODO: 123" {:foo :bar})))
   {:allowed-methods #{:get}
    :representations [{:media-type "text/html"}]
    :error-handler identity
    :journal journal
    :journal-browser-path "/journal/"}))

(defrecord ErrorExample [journal error-resource]
  Lifecycle
  (start [component]
    (let [journal (atom {})
          error-resource (hello-error journal)]
      (dotimes [n 10]
        @(error-resource (request :get "/")))
      (assoc component :journal journal :error-resource error-resource)))

  (stop [component] component)

  RouteProvider
  (routes [_]
    [""
     [
      ["/error" error-resource]
      ["/journal/" (new-journal-browser-resources :journal journal)]]]))

(defn new-error-example [& {:as opts}]
  (map->ErrorExample opts))
