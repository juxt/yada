;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [schema.utils :refer [error?]]
   [yada.security :as sec]
   [yada.handler :refer [new-handler] :as handler]
   [yada.interceptors :as i]
   [yada.methods :as methods]
   [yada.protocols :as p]
   [yada.schema :as ys]))

;; TODO: Move these final remnants to yada.handler and finally delete this ns!

(def default-interceptor-chain
  [i/available?
   i/known-method?
   i/uri-too-long?
   i/TRACE
   i/method-allowed?
   i/parse-parameters
   sec/verify ; step 1
   i/get-properties ; step 2
   sec/authorize ; steps 3,4 and 5
   i/process-request-body
   i/check-modification-time
   i/select-representation
   ;; if-match and if-none-match computes the etag of the selected
   ;; representations, so needs to be run after select-representation
   ;; - TODO: Specify dependencies as metadata so we can validate any
   ;; given interceptor chain
   i/if-match
   i/if-none-match
   i/invoke-method
   i/get-new-properties
   i/compute-etag
   sec/access-control-headers
   i/create-response])

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
