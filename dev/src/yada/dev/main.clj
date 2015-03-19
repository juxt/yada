;; Copyright © 2015, JUXT LTD.

(ns yada.dev.main
  "Main entry point"
  (:require clojure.pprint)
  (:gen-class))

(defn -main [& args]
  ;; We eval so that we don't AOT anything beyond this class
  (eval '(do
             (require 'yada.dev.main)
             (require 'dev)
             (require 'com.stuartsierra.component)
             (require '[modular.component.co-dependency :as co-dependency])

             (println "Starting yada")

             (let [system (->
                           (dev/new-dev-system)
                           co-dependency/start-system)]

               (println "System started")
               (println "Ready...")

               ))))
