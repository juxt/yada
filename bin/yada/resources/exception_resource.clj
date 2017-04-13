;; Copyright © 2016, JUXT LTD.

(ns yada.resources.exception-resource
  (:require
   [clojure.tools.logging :refer :all]
   [hiccup.core :refer [html]]
   [yada.body :as body]
   [yada.context :refer [content-type]]
   [yada.resource :refer [resource ResourceCoercion]]))

(extend-protocol ResourceCoercion
  Exception
  (as-resource [e]
    (resource
     {:produces #{"text/html" "text/plain;q=0.9"}
      :response
      (fn [ctx]
        (let [rep (get-in ctx [:response :produces])]
          (-> (:response ctx)
              (assoc :status 500)
              (assoc :body
                     (case (content-type ctx)
                       "text/html"
                       (-> (body/render-error 500 e rep ctx)
                           (body/to-body rep)))))))})))
