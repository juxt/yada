(ns yada.core
  (:require
   [manifold.deferred :as d]
   [schema.core :as s]
   [yada.protocols :as p]
   [yada.conneg :refer (best-allowed-content-type)]
   ))

;; API specs. are created like this

;; This is kind of like a bidi route structure

;; But we shouldn't limit ourselves to only that which is declared, because so much more can be generated, like 404s, etc.

;; "It is better to have 100 functions operate on one data structure than 10 functions on 10 data structures." —Alan Perlis



;; TODO For authentication, implementation is out-of-band, in Ring
;; middleware or another mechanism for assoc'ing evidence of credentials
;; to the Ring request.

(defmacro nonblocking-exit-when*
  "Short-circuit exit a d/chain with an error if expr evaluates to
  truthy. To avoid blocking the request thread, the callback can return
  a deferred value."
  [callback expr status]
  `(fn [x#]
     (if (and (some? ~callback) ; guard for performance
              ~expr)
       ;; Exit, intended to be caught with a d/catch
       (d/error-deferred (ex-info "" {:status ~status}))
       x#)))

(defmacro nonblocking-exit-when
  [callback expr status]
  `(nonblocking-exit-when* ~callback (deref (d/chain ~expr)) ~status))

(defmacro nonblocking-exit-when-not
  [callback expr status]
  `(nonblocking-exit-when* ~callback (deref (d/chain ~expr not)) ~status))

(defmacro exit-when [expr status]
  `(fn [x#]
     (if ~expr
       (d/error-deferred (ex-info "" {:status ~status}))
       x#)))

(defmacro exit-when-not [expr status]
  `(exit-when (not ~expr) ~status))

(defn check-cacheable [ctx]
  ;; Check the resource metadata and return one of the following responses
  (cond
    false (d/error-deferred (ex-info "Precondition Failed" {:status 412}))
    false (d/error-deferred (ex-info "Not Modified" {:status 304}))
    :otherwise ctx))

(defn make-handler
  [swagger-ops
   {:keys
    [
     service-available?                 ; async-supported
     known-method?
     request-uri-too-long?
     allowed-method?
     find-resource                      ; async-supported

     ;; a function, may return a deferred value. Parameters
     ;; indicate the (negotiated) content type.

     ;; The allowed? callback will contain the entire resource, the callback must
     ;; therefore extract the OAuth2 scopes, or whatever is needed to
     ;; authorize the request.
     allowed?

     ;; model determines a value, possibly deferred, that will be
     ;; converted to the negotiated content-type and returned to the
     ;; client as the entity body.
     model

     body
     ]
    :or {known-method? #{:get :put :post :delete :options :head}
         request-uri-too-long? 4096
         allowed-method? (-> swagger-ops keys set)
         find-resource true
         }}]

  (fn [req]
    (let [method (:request-method req)
          produces (get-in swagger-ops [method :produces])]

      (-> {:magic ::context
           :content-type (delay (throw (ex-info "TODO: Negotiate content-type" {})))}
        (d/chain
         (nonblocking-exit-when-not service-available? (p/service-available? service-available?) 503)
         (exit-when-not (p/known-method? known-method? method) 501)
         (exit-when (p/request-uri-too-long? request-uri-too-long? (:uri req)) 414)
         (exit-when-not (p/allowed-method? allowed-method? method swagger-ops) 405)

         ;; TODO Malformed

         ;; TODO Unauthorized
         ;; TODO Forbidden

         ;; TODO Not implemented (if unknown Content-* header)

         ;; TODO Unsupported media type

         ;; TODO Request entity too large - shouldn't we do this later,
         ;; when we determine we actually need to read the request body?

         ;; TODO OPTIONS

         ;; Content-negotiation - partly done here to throw back to the client any errors
         #(assoc-in % [:response :content-type]
                    (best-allowed-content-type
                     (or (get-in req [:headers "accept"]) "*/*")
                     produces))

         ;; Does the resource exist? Call find-resource, which returns
         ;; the resource's metadata (optionally deferred to prevent
         ;; blocking this thread)
         (fn [ctx]
           (d/chain
            (p/find-resource find-resource {:params (:params req)})
            #(assoc ctx :resource %)))

         (fn [{:keys [resource] :as ctx}]
           (if resource

             ;; 'Exists' flow
             (d/chain
              ctx
              check-cacheable
              (constantly {:status 200}))

             ;; 'Not exists' flow
             (d/chain
              ctx
              (constantly {:status 404}))))

         #_(if-let [resource (p/find-resource find-resource {:params (:params req)})]
             ;; Resource exists - follow the exists chain
             ;; TODO if this returns a deferred, then wrap it in a d/timeout!
             (d/chain
              metadata
              (fn [metadata]
                (or
                 (check-cacheable metadata)
                 (d/chain
                  metadata
                  (fn [metadata]
                    (merge
                     {:metadata metadata
                      :content-type (delay "text/plain")}
                     (when model
                       (d/chain
                        (p/model model {:params (:params req)})
                        (fn [model] {:model model})))))

                  (fn [{:keys [metadata model] :as resource}]
                    (cond
                      ;; TODO Render model as per negotiated content-type
                      body
                      (and model body)
                      {:status 200
                       :body (if body
                               (p/body body {:model (:model resource-metadata)
                                             :content-type "text/plain"})
                               (pr-str (:model resource-metadata)))}

                      :otherwise
                      {:status 404
                       :body "Not found"}
                      ))))))

             ;; Resource does not exist - follow the not-exists chain
             {:status 404}))

        ;; Handle exits
        (d/catch clojure.lang.ExceptionInfo #(ex-data %))


        ))))

;; TODO: pets should return resource-metadata with a (possibly deferred) model

;; handle-method-not-allowed 405 "Method not allowed."

;; This is OK
;; ((api-handler api) (mock/request :get "/persons"))

;; This is should yield 405 "Method not allowed."
;; ((api-handler api) (mock/request :get "/persons"))

;; List of interesting things to do

;; There should be a general handler that does the right thing
;; wrt. available methods (Method Not Allowed) and calls out to
;; callbacks accordingly. Perhaps there's no sense in EVERYTHING being
;; overridable, as with Liberator. It should hard-code the things that
;; don't make sense to override, and use hooks for the rest.

;; Resource existence is most important - and not covered by swagger, so it's a key hook.

;; Return deferreds, if necessary, if the computation is length to compute (e.g. for exists? checks)

;; CORS support: build this in, make allow-origin first-class, which headers should be allowed should be a hook (with default)
