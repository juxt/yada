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
   [schema.core :as s]
   [phonebook.system :refer (new-system-map new-dependency-map new-co-dependency-map)]))

(def system nil)

(defn new-dev-system
  "Create a development system"
  []
  (let [s-map (new-system-map)]
    (-> s-map
        (component/system-using (new-dependency-map))
        (co-dependency/system-co-using (new-co-dependency-map)))))

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
   co-dependency/start-system)
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
  (clojure.test/run-all-tests #"yada.*test$"))
