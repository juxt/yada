;; Copyright © 2014-2017, JUXT LTD.

(ns dev
  (:require
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.java.io :as io]
   [bidi.bidi :as bidi]
   [com.stuartsierra.component :as component]
   [schema.core :as s]
   [yada.dev.config :refer [config]]
   [yada.dev.system :refer (new-system-map new-dependency-map)]
   ))

(def system nil)

(defn new-dev-system
  "Create a development system"
  []
  (s/with-fn-validation
    (-> (config :dev)
        (new-system-map)
        (component/system-using (new-dependency-map)))))

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (new-dev-system))))

(defn check
  "Check for component validation errors"
  []
  (let [errors
        (->> system
             (reduce-kv
              (fn [acc k v]
                (assoc acc k (s/check (type v) v)))
              {})
             (filter (comp some? second)))]

    (when (seq errors) (into {} errors))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root
   #'system
   component/start-system)
  (when-let [errors (check)]
    (println "Warning, component integrity violated!" errors)))

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
  (clojure.test/run-all-tests #"(yada|phonebook).*test$"))

(defn reset-and-test []
  (reset)
  (time (test-all)))

(defn routes []
  (-> system :docsite-router :routes))

(defn match-route [path & args]
  (apply bidi/match-route (routes) path args))

(defn path-for [target & args]
  (apply bidi/path-for (routes) target args))
