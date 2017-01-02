;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   [bidi.bidi :as bidi]
   yada.aleph
   yada.async
   yada.bidi
   yada.context
   [yada.handler :refer [interceptor-chain error-interceptor-chain]]
   [yada.interceptors :as i]
   yada.json
   yada.json-html
   yada.multipart
   yada.redirect
   yada.resources.atom-resource
   yada.resources.collection-resource
   yada.resources.exception-resource
   yada.resources.file-resource
   yada.resources.string-resource
   yada.resources.url-resource
   yada.resources.sse
   [yada.security :as sec]
   yada.swagger
   [yada.swagger-parameters :as swgparams]
   yada.test
   yada.transit
   yada.util
   yada.wrappers
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.aleph listener server]
 [yada.context content-type charset language uri-info url-for path-for href-for scheme-for]
 [yada.handler handler yada]
 [yada.swagger swaggered]
 [yada.redirect redirect]
 [yada.resource resource as-resource]
 [yada.test request-for response-for]
 [yada.util get-host-origin]
 [yada.resources.file-resource safe-relative-path safe-relative-file]
 [yada.wrappers routes])


(def default-interceptor-chain
  [i/available?
   i/known-method?
   i/uri-too-long?
   i/TRACE
   i/method-allowed?
   swgparams/parse-parameters
   i/capture-proxy-headers
   sec/authenticate
   i/get-properties
   sec/authorize
   i/process-content-encoding
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
   sec/security-headers
   i/create-response
   i/logging
   i/return
   ])

(defmethod interceptor-chain nil [options]
  default-interceptor-chain)

(def default-error-interceptor-chain
  [sec/access-control-headers
   i/create-response
   i/logging
   i/return])

(defmethod error-interceptor-chain nil [options]
  default-error-interceptor-chain)
