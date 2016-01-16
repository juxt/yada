;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [schema.utils :refer [error?]]
   [yada.security :as sec]
   [yada.handler :refer [new-handler default-interceptor-chain] :as handler]
   [yada.interceptors :as i]
   [yada.methods :as methods]
   [yada.protocols :as p]
   [yada.schema :as ys]))

;; TODO: Move these final remnants to yada.handler and finally delete this ns!

(defn yada
  "Create a Ring handler"
  ([resource]

   (when (not (satisfies? p/ResourceCoercion resource))
     (throw (ex-info "The argument to the yada function must be a Resource record or something that can be coerced into one (i.e. a type that satisfies yada.protocols/ResourceCoercion)"
                     {:resource resource})))
   
   ;; It's possible that we're being called with a resource that already has an error
   (when (error? resource)
     (throw (ex-info "yada function is being passed a resource that is an error"
                     {:error (:error resource)})))

   (let [base resource

         ;; Validate the resource structure, with coercion if
         ;; necessary.
         resource (ys/resource-coercer (p/as-resource resource))]

     (when (error? resource)
       (throw (ex-info "Resource does not conform to schema"
                       {:resource (p/as-resource base)
                        :error (:error resource)
                        :schema ys/Resource})))

     (new-handler
      (merge
       {:id (get resource :id (java.util.UUID/randomUUID))
        :base base
        :resource resource
        :allowed-methods (handler/allowed-methods resource)
        :known-methods (methods/known-methods)
        ;; TODO: interceptor chain should be defined in the resource itself
        :interceptor-chain default-interceptor-chain})))))
