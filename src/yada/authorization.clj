;; Copyright © 2015, JUXT LTD.

(ns yada.authorization
  (:require
   [clojure.tools.logging :refer :all])
  (:import [clojure.lang APersistentVector APersistentMap Keyword]))

(defprotocol AuthorizationPredicate
  (allowed? [_ ctx realm] ""))

(extend-protocol AuthorizationPredicate
  Boolean
  (allowed? [this _ _] this)

  nil
  (allowed? [_ _ _] nil)
  
  APersistentVector
  (allowed? [this ctx realm]
    (case (first this)
      :and (every? #(allowed? % ctx realm) (rest this))
      :or (some #(allowed? % ctx realm) (rest this))
      :not (not (allowed? (second this) ctx realm))))

  APersistentMap
  (allowed? [this ctx realm]
    (let [{:keys [role]} this
          credentials (get-in ctx [:authentication realm])]
      (contains? (:roles credentials) role)))

  Keyword
  (allowed? [this ctx realm]
    (let [credentials (get-in ctx [:authentication realm])]
      (contains? (:roles credentials) this))))



