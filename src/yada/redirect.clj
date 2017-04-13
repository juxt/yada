;; Copyright © 2016, JUXT LTD.

(ns yada.redirect
  (:require
   [yada.resource :refer [resource]]))

(defn redirect
  "Opts can include route-params, query-params and vhost"
  ([target]
   (redirect target {}))
  ([target opts]
   (resource
    {:produces "text/plain"
     :response (fn [ctx]
                 (let [uri-info (:uri-info ctx)]
                   (when (nil? uri-info)
                     (throw (ex-info "No uri-info in context, cannot do redirect" {})))
                   (if-let [uri (:uri (uri-info target opts))]
                     (assoc (:response ctx)
                            :status 302
                            :headers {"location" uri})
                     (throw (ex-info (format "Redirect to unknown location: %s" target)
                                     {:status 500
                                      :target target})))))})))
