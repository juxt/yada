;; Copyright © 2014-2017, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.methods :as m]
   [yada.resource :refer [resource as-resource ResourceCoercion]]
   yada.resources.string-resource))

(defn string-atom-resource [*a]
  (let [*last-modified (atom (to-date (now)))
        val (as-resource @*a)
        *version (atom @*a)]
    (add-watch
     *a :last-modified
     (fn [_ _ _ s]
       (reset! *last-modified (to-date (now)))
       (reset! *version s)))
    (resource
     (merge
      (when-let [produces (:produces val)]
        {:produces produces})
      {:properties (fn [ctx] {:last-modified @*last-modified
                              :version @*version})
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
                                     (reset! *a body)
                                     ;; Return a nil
                                     nil))}
                 :delete {:description "Reset the atom to nil, such that this resource has no representation"
                          :summary "Reset the atom to nil"
                          :response (fn [ctx] (reset! *a nil))}}}))))

(extend-protocol ResourceCoercion
  clojure.lang.Atom
  (as-resource [*a]
    (let [v @*a]
      (cond
        (string? v) (string-atom-resource *a)
        :otherwise (throw (ex-info (format "Unsupported value type: %s" (type v)) {}))))))
