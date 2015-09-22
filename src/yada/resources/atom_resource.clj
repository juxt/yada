;; Copyright © 2015, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :as m]
   [schema.core :as s]
   yada.resources.string-resource)
  (:import [yada.resources.string_resource StringResource]))

(defrecord StringAtomResource [*a]
  p/Properties
  (properties [_]
    (let [*last-modified (atom (to-date (now)))
          val (p/as-resource @*a)
          valprops (p/properties val)]
      (add-watch *a :last-modified
                 (fn [_ _ _ _]
                   (reset! *last-modified (to-date (now)))))
      {:representations (:representations valprops)
       :parameters {:put {:body String}}
       ::last-modified *last-modified

       }))

  (properties [_ ctx]
    (let [*last-modified (-> ctx :properties ::last-modified)]
      {:last-modified @*last-modified}))

  m/Get
  (GET [_ ctx] @*a)

  m/Put
  (PUT [_ ctx]
    (infof "Put parameters of %s" (get-in ctx [:parameters]))
    (reset! *a (get-in ctx [:parameters :body])))

  m/Delete
  (DELETE [_ ctx] (reset! *a nil))
  )

(extend-protocol p/ResourceCoercion
  clojure.lang.Atom
  (as-resource [*a]
    (let [v @*a]
      (cond
        (string? v) (->StringAtomResource *a)
        :otherwise (throw (ex-info (format "Unsupported value type: %s" (type v)) {}))))))
