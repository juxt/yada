;; Copyright © 2015, JUXT LTD.

(ns yada.authorization
  (:require
   [manifold.deferred :as d]
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
  (allowed? [this ctx roles]
    (case (first this)
      :and (every? #(allowed? % ctx roles) (rest this))
      :or (some #(allowed? % ctx roles) (rest this))
      :not (not (allowed? (second this) ctx roles))))

  APersistentMap
  (allowed? [this ctx roles]
    (let [{:keys [role]} this]
      (contains? (set roles) role)))

  Object
  (allowed? [this ctx roles]
    (contains? (set roles) this)))

(defmulti validate "Return ctx if authorized, otherwise return nil"
  (fn [ctx credentials authorization]
    (:scheme authorization)))

(defmethod validate nil [ctx credentials authorization]
  (when-let [methods (:methods authorization)]
    (let [pred (get-in authorization [:methods (:method ctx)])]
      (when (allowed? pred ctx (set (:roles credentials)))
        ctx))))
