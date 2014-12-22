(ns user)

;; This is an old trick from Pedestal. When system.clj doesn't compile,
;; it can prevent the REPL from starting, which makes debugging very
;; difficult. This extra step ensures the REPL starts, no matter what.

(defn dev
  []
  (require 'dev)
  (in-ns 'dev))


(defn go
  []
  (println "Don't you mean (dev) ?"))
