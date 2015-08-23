;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require
   [schema.core :as s]
   [yada.protocols :as p]))

(s/defn allowed-methods :- (s/either [s/Keyword] #{s/Keyword})
  [r]
  (p/allowed-methods r))

(s/defn all-allowed-methods :- (s/either [s/Keyword] #{s/Keyword})
  [r]
  (p/all-allowed-methods r))
