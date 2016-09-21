;; Copyright © 2015, JUXT LTD.

(ns yada.boolean
  (:refer-clojure :exclude [boolean?])
  (:require
   [schema.core :as s]))

(defn boolean? [x]
  (contains? #{true false} x))

(declare BooleanExpression)

(s/defschema NotExpression
  [(s/one (s/eq :not) "not") (s/one (s/recursive #'BooleanExpression) "not expr")])

(s/defschema AndExpression
  [(s/one (s/eq :and) "and") (s/recursive #'BooleanExpression)])

(s/defschema OrExpression
  [(s/one (s/eq :or) "or") (s/recursive #'BooleanExpression)])

(s/defschema CompositeExpression
  (s/conditional
   (comp (partial = :not) first) NotExpression
   (comp (partial = :and) first) AndExpression
   (comp (partial = :or) first) OrExpression))

(s/defschema BooleanExpression
  (s/conditional
   boolean? s/Bool
   vector? CompositeExpression
   :else s/Any))
