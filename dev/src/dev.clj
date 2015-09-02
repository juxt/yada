;; Copyright © 2015, JUXT LTD.

(ns dev
  (:require
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [modular.component.co-dependency :as co-dependency]
   [yada.dev.system :refer (config new-system-map new-dependency-map new-co-dependency-map)]
   [modular.maker :refer (make)]
   [bidi.bidi :as bidi]))

(def system nil)

(defn new-dev-system
  "Create a development system"
  []
  (let [config (config :dev)
        s-map (new-system-map config)]
    (-> s-map
        (component/system-using (new-dependency-map))
        (co-dependency/system-co-using (new-co-dependency-map)))))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (new-dev-system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root
   #'system
   co-dependency/start-system))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start)
  :ok)

(defn reset []
  (stop)
  (refresh :after 'dev/go))

(defn test-all []
  (clojure.test/run-all-tests #"yada.*test$"))

(defn routes []
  (-> system :docsite-router :routes))

(defn match-route [path & args]
  (apply bidi/match-route (routes) path args))

(defn path-for [target & args]
  (apply bidi/path-for (routes) target args))
