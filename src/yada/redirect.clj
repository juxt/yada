;; Copyright © 2014-2017, JUXT LTD.

(ns yada.redirect
  (:require
   ring.util.response
   [yada.resource :refer [resource]]
   [yada.context :as ctx]
   [yada.methods :refer [idempotent?]]
   [clojure.tools.logging :as log]))

(defn- redirect-resource
  "Opts can include route-params, query-params and vhost"
  ([target]
   (redirect-resource target {}))
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


(defn- redirect-context
  "Add a redirect response to the ctx, with the recommended status to use for the request method"
  ([ctx location-or-target]
   (redirect-context ctx location-or-target {}))
  ([ctx location-or-target opts]
   (let [method (get-in ctx [:request :request-method])
         known-method (get-in ctx [:known-methods method])

         status (ring.util.response/redirect-status-codes
                 (if (and known-method (idempotent? known-method))
                   :found
                   :see-other))

         uri-info (:uri-info ctx)
         location (if (string? location-or-target)
                    location-or-target
                    (if (nil? uri-info)
                      (throw (ex-info "No uri-info in context, cannot do redirect" {}))
                      (:uri (uri-info location-or-target opts))))]

     (if location
       (assoc (:response ctx)
              :status status
              :headers {"location" location})
       (throw (ex-info (format "Redirect to unknown location: %s" location-or-target)
                       {:status 500
                        :target location-or-target}))))))



(defn redirect
  "There are 2 different redirects in yada, one to construct a
  redirecting resource, the other, to be used in a response function
  to augment a yada context. Since we want the function name to be
  'redirect', this proxy function delegates to the correct
  implementation."
  [first-arg & args]
  (if (instance? yada.context.Context first-arg)
    (apply redirect-context first-arg args)
    (apply redirect-resource first-arg args)))
