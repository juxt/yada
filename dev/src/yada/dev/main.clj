;; Copyright © 2015, JUXT LTD.

(ns yada.dev.main
  "Main entry point"
  (:require clojure.pprint)
  (:gen-class))

(defn -main [& args]
  ;; We eval so that we don't AOT anything beyond this class
  (eval '(do
           (require 'yada.dev.main)
           (require 'yada.dev.system)
           (require 'com.stuartsierra.component)

           (println "Starting yada website")

           (let [system (->
                         (yada.dev.system/new-production-system)
                         com.stuartsierra.component/start-system)]

             (println "System started")
             (println "Ready...")

             ))))
