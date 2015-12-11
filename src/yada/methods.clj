;; Copyright © 2015, JUXT LTD.

(ns yada.methods
  (:refer-clojure :exclude [methods get])
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [yada.body :as body]
   [yada.context :as ctx]
   [yada.protocols :as p]
   [yada.service :as service]
   yada.response)
  (:import
   [yada.response Response]
   [java.io File]))


(defn- zero-content-length
  "Unless status code is 1xx or 204, or method is CONNECT. We don't set
  the content-length in the case of a 304. Ensure status is set
  in [:response :status] prior to call this, and only call if
  response :body is nil. See rfc7230#section-3.3.2 for specification
  details."
  [ctx]
  (let [status (-> ctx :response :status)]
    (assert status)
    (or
     (when (and
            (nil? (get-in ctx [:response :headers "content-length"]))
            (not= status 304)
            (>= status 200)
            (not= (:method ctx) :connect))
       (assoc-in ctx [:response :headers "content-length"] 0))
     ctx)))

;; Allowed methods

;; RFC 7231 - 8.1.3.  Registrations

;; | Method  | Safe | Idempotent | Reference     |
;; +---------+------+------------+---------------+
;; | CONNECT | no   | no         | Section 4.3.6 |
;; | DELETE  | no   | yes        | Section 4.3.5 |
;; | GET     | yes  | yes        | Section 4.3.1 |
;; | HEAD    | yes  | yes        | Section 4.3.2 |
;; | OPTIONS | yes  | yes        | Section 4.3.7 |
;; | POST    | no   | no         | Section 4.3.3 |
;; | PUT     | no   | yes        | Section 4.3.4 |
;; | TRACE   | yes  | yes        | Section 4.3.8 |


(defprotocol Method
  (keyword-binding [_] "Return the keyword this method is for")
  (safe? [_] "Is the method considered safe? Return a boolean")
  (idempotent? [_] "Is the method considered idempotent? Return a boolean")
  (request [_ ctx] "Apply the method to the resource"))

(defn known-methods
  "Return a map of method instances"
  []
  (into {}
        (map
         (fn [m]
           (let [i (.newInstance m)]
             [(keyword-binding i) i]))
         (extenders Method))))

;; --------------------------------------------------------------------------------
(deftype HeadMethod [])

(extend-protocol Method
  HeadMethod
  (keyword-binding [_] :head)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]

    (when-not (ctx/exists? ctx)
      (d/error-deferred (ex-info "" {:status 404})))

    (when-not (get-in ctx [:response :produces])
      (d/error-deferred
       (ex-info "" {:status 406})))

    ;; HEAD is implemented without delegating to the resource.

    ;; TODO: This means that any headers normally set by the resource's
    ;; request function will not be added here. There may be a need to
    ;; revise this design decision later.

    ;; We don't need to add Content-Length,
    ;; Content-Range, Trailer or Tranfer-Encoding, as
    ;; per rfc7231.html#section-3.3

    ;; TODO: Content-Length (of 0) is still being added to HEAD
    ;; responses - is this Aleph or a bug in yada?

    ctx))

;; --------------------------------------------------------------------------------

(defprotocol GetResult
  (interpret-get-result [_ ctx]))

(extend-protocol GetResult
  Response
  (interpret-get-result [response ctx]
    (assoc ctx :response response))
  Object
  (interpret-get-result [o ctx]
    (assoc-in ctx [:response :body] o))
  nil
  (interpret-get-result [_ ctx]
    (d/error-deferred (ex-info "" {:status 404})))
  clojure.lang.Fn
  (interpret-get-result [f ctx]
    (interpret-get-result (f ctx) ctx)))

(deftype GetMethod [])

(extend-protocol Method
  GetMethod
  (keyword-binding [_] :get)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]
    (->
     (d/chain

      (ctx/exists? ctx)

      (fn [exists?]
        (if exists?
          (-> ctx :response :produces)
          (d/error-deferred (ex-info "" {:status 404}))))

      (fn [representation]
        (if representation
          :ok
          (d/error-deferred (ex-info "" {:status 406}))))

      ;; function normally returns a (possibly deferred) body.
      (fn [x]
        (if-let [f (get-in ctx [:handler :methods (:method ctx) :handler])]
          (try
            (f ctx)
            (catch Exception e
              (d/error-deferred e)))
          ;; No handler!
          (d/error-deferred
           (ex-info (format "Resource %s does not provide a handler for :get" (type (:resource ctx)))
                    {:status 500}))))

      (fn [res]
        (interpret-get-result res ctx))

      (fn [ctx]
        (let [representation (get-in ctx [:response :produces])]
          ;; representation could be nil, for example, resource could be a java.io.File
          (update-in ctx [:response :body] body/to-body representation)))

      (fn [ctx]
        (let [content-length (body/content-length (get-in ctx [:response :body]))]
          (cond-> ctx content-length (assoc-in [:response :content-length] content-length)))))

     (d/catch
         (fn [e]
           (if (:status (ex-data e))
             (throw e)
             (throw (ex-info "Error on GET" {:response (:response ctx)
                                             :resource (type (:resource ctx))
                                             :error e}))))))))

;; --------------------------------------------------------------------------------

(deftype PutMethod [])

(extend-protocol Method
  PutMethod
  (keyword-binding [_] :put)
  (safe? [_] false)
  (idempotent? [_] true)
  (request [_ ctx]
    (let [f (get-in ctx [:handler :methods (:method ctx) :handler]
                    (constantly (d/error-deferred
                                 (ex-info (format "Resource %s does not provide a handler for :put" (type (:resource ctx)))
                                          {:status 500}))))]
      (d/chain
       (f ctx)
       (fn [res]
         (assoc-in ctx [:response :status]
                   (cond
                     ;; TODO: A 202 may be not what the user wants!
                     ;; TODO: See RFC7240
                     (d/deferred? res) 202
                     (ctx/exists? ctx) 204
                     :otherwise 201)))))))


;; --------------------------------------------------------------------------------

(defprotocol PostResult
  (interpret-post-result [_ ctx]))

(extend-protocol PostResult
  Response
  (interpret-post-result [response ctx]
    (assoc ctx :response response))
  Boolean
  (interpret-post-result [b ctx]
    (if b ctx (throw (ex-info "Failed to process POST" {}))))
  String
  (interpret-post-result [s ctx]
    (assoc-in ctx [:response :body] s))
  clojure.lang.Fn
  (interpret-post-result [f ctx]
    (interpret-post-result (f ctx) ctx))
  nil
  (interpret-post-result [_ ctx] ctx))

(deftype PostMethod [])

(extend-protocol Method
  PostMethod
  (keyword-binding [_] :post)
  (safe? [_] false)
  (idempotent? [_] false)
  (request [_ ctx]
    (d/chain
     (when-let [f (get-in ctx [:handler :methods (:method ctx) :handler])]
       (f ctx))
     (fn [res]
       (interpret-post-result res ctx))
     (fn [ctx]
       (let [status (get-in ctx [:response :status])]
         (cond-> ctx
           (not status) (assoc-in [:response :status] 200)
           (not (-> ctx :response :body)) zero-content-length))))))

;; --------------------------------------------------------------------------------

(deftype DeleteMethod [])

(extend-protocol Method
  DeleteMethod
  (keyword-binding [_] :delete)
  (safe? [_] false)
  (idempotent? [_] true)
  (request [_ ctx]
    (d/chain
     (if-let [f (get-in ctx [:handler :methods (:method ctx) :handler])]
       (try
         (f ctx)
         (catch Exception e
           (d/error-deferred e)))
       ;; No handler!
       (d/error-deferred
        (ex-info (format "Resource %s does not provide a handler for :get" (type (:resource ctx)))
                 {:status 500})))
     (fn [res]
       ;; TODO: Could we support 202 somehow?
       (assoc-in ctx [:response :status] 204)))))

;; --------------------------------------------------------------------------------

(defprotocol Options
  (OPTIONS [_ ctx]))

(defprotocol OptionsResult
  (interpret-options-result [_ ctx]))

(extend-protocol OptionsResult
  Response
  (interpret-options-result [response ctx]
    (assoc ctx :response response))
  clojure.lang.Fn
  (interpret-options-result [f ctx]
    (interpret-options-result (f ctx) ctx))
  nil
  (interpret-options-result [_ ctx] ctx))

(deftype OptionsMethod [])

(extend-protocol Method
  OptionsMethod
  (keyword-binding [_] :options)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [_ ctx]
    (let [ctx (assoc-in ctx [:response :headers "allow"]
                        (str/join ", " (map (comp (memfn toUpperCase) name) (-> ctx :allowed-methods))))]
      ;; TODO: Build in explicit support for CORS pre-flight requests
      (if (satisfies? Options (:resource ctx))
        (d/chain
         (OPTIONS (:resource ctx) ctx)
         (fn [res]
           (interpret-options-result res ctx)))
        ctx)

      ;; For example, for a resource supporting CORS
      #_(link ctx
          (if-let [origin (service/allow-origin (:resource ctx) ctx)]
            (update-in ctx [:response :headers]
                       merge {"access-control-allow-origin"
                              origin
                              "access-control-allow-methods"
                              (apply str
                                     (interpose ", " ["GET" "POST" "PUT" "DELETE"]))})))
      )))

;; --------------------------------------------------------------------------------

(deftype TraceMethod [])

(defn to-encoding [s encoding]
  (-> s
      (.getBytes)
      (java.io.ByteArrayInputStream.)
      (slurp :encoding encoding)))

(defn to-title-case [s]
  (when s
    (str/replace s #"(\w+)" (comp str/capitalize second))))

(defn print-request
  "Print the request. Used for TRACE."
  [req]
  (letfn [(println [& x]
            (apply print x)
            (print "\r\n"))]
    (println (format "%s %s %s"
                     (str/upper-case (name (:request-method req)))
                     (:uri req)
                     "HTTP/1.1"))
    (doseq [[h v] (:headers req)] (println (format "%s: %s" (to-title-case h) v)))
    (println)
    (when-let [body (:body req)]
      (print (slurp body)))))

(extend-protocol Method
  TraceMethod
  (keyword-binding [_] :trace)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [_ ctx]
    (let [body (-> ctx
                   :request
                   ;; A client MUST NOT generate header fields in a
                   ;; TRACE request containing sensitive data that might
                   ;; be disclosed by the response. For example, it
                   ;; would be foolish for a user agent to send stored
                   ;; user credentials [RFC7235] or cookies [RFC6265] in
                   ;; a TRACE request.
                   (update-in [:headers] dissoc "authorization" "cookie")
                   print-request
                   with-out-str
                   ;; only "7bit", "8bit", or "binary" are permitted (RFC 7230 8.3.1)
                   (to-encoding "utf8"))]

      (assoc ctx :response
             {:status 200
              :headers {"content-type" "message/http;charset=utf8"
                        ;; TODO: Whoops! http://mark.koli.ch/remember-kids-an-http-content-length-is-the-number-of-bytes-not-the-number-of-characters
                        "content-length" (.length body)}
              :body body
              }))))
