;; Copyright © 2015, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :as m]
   [yada.resource :refer [resource]]
   yada.resources.string-resource))

(defn string-atom-resource [*a]
  (let [*last-modified (atom (to-date (now)))
        val (p/as-resource @*a)]
    (add-watch
     *a :last-modified
     (fn [_ _ _ _]
       (reset! *last-modified (to-date (now)))))
    (resource
     (merge
      (when-let [produces (:produces val)]
        {:produces produces})
      {:properties (fn [ctx] {:last-modified @*last-modified})
       :methods {:get (merge (get-in val [:methods :get])
                             {:description "Derefrence to get the value of the atom"
                              :summary "Get the atom's value"
                              :response (fn [ctx] @*a)})
                 :put {:description "Reset the atom to the value parameter of the form"
                       :summary "Reset the atom to a given value"
                       :parameters {:form {:value String}}
                       :consumes "application/x-www-form-urlencoded"
                       :response (fn [ctx]
                                   ;; We can't PUT a nil, because nils mean
                                   ;; no representation and yield 404s on
                                   ;; GET, hence this when guard
                                   (when-let [body (get-in ctx [:parameters :form :value])]
                                     (reset! *a body)))}
                 :delete {:description "Reset the atom to nil, such that this resource has no representation"
                          :summary "Reset the atom to nil"
                          :response (fn [ctx] (reset! *a nil))}}}))))

(extend-protocol p/ResourceCoercion
  clojure.lang.Atom
  (as-resource [*a]
    (let [v @*a]
      (cond
        (string? v) (string-atom-resource *a)
        :otherwise (throw (ex-info (format "Unsupported value type: %s" (type v)) {}))))))


