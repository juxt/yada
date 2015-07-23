;; Copyright © 2015, JUXT LTD.

(ns yada.methods
  (:refer-clojure :exclude [methods])
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [yada.mime :as mime]
   [yada.representation :as rep]
   [yada.resource :as res]
   [yada.service :as service]
   [yada.util :refer (link)])
  (:import
   [yada.response Response]
   [java.io File]))

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
  (idempotent? [_] "Is the method considered safe? Return a boolean")
  (request [_ ctx] "Apply the method to the resource"))

(defn methods
  "Return a map of method instances"
  []
  (into {}
        (map
         (fn [m]
           (let [i (.newInstance m)]
             [(keyword-binding i) i]))
         (extenders Method))))

(deftype Head [])

(extend-protocol Method
  Head
  (keyword-binding [_] :head)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]

    (when (false? (:exists? ctx))
      (d/error-deferred (ex-info "" {:status 404
                                     ::http-response true})))

    ;; HEAD is implemented without delegating to the resource.

    ;; TODO: This means that any headers normally set by the resource's
    ;; request function will not be added here. There may be a need to
    ;; revise this design decision later.

    ;; We don't need to add Content-Length,
    ;; Content-Range, Trailer or Tranfer-Encoding, as
    ;; per rfc7231.html#section-3.3
    ctx))

(deftype Get [])

(defprotocol GetResult
  (interpret-get-result [_ ctx]))

(extend-protocol GetResult
  Response
  (interpret-get-result [response ctx]
    (assoc ctx :response response))
  Object
  (interpret-get-result [o ctx]
    (assoc-in ctx [:response :body] o))
  nil ;; TODO: throw 404?
  (interpret-post-result [_ ctx] ctx))

(extend-protocol Method
  Get
  (keyword-binding [_] :get)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]
    (when (false? (:exists? ctx))
      (d/error-deferred (ex-info "" {:status 404
                                     ::http-response true})))
    (d/chain
     ;; GET request normally returns (possibly deferred) body.
     (res/request (:resource ctx) :get ctx)

     (fn [res]
       (interpret-get-result res ctx))

     (fn [ctx]
       (let [representation (get-in ctx [:response :representation])]

         ;;(assert (not (instance? Response (get-in ctx [:response :body]))))

         ;; representation could be nil, for example, resource could be a java.io.File
         (update-in ctx [:response :body] rep/to-body representation)))

     (fn [ctx]
       (let [content-length (rep/content-length (get-in ctx [:response :body]))]
         (cond-> ctx content-length (assoc-in [:response :content-length] content-length)))))))

(deftype Put [])

(extend-protocol Method
  Put
  (keyword-binding [_] :put)
  (safe? [_] false)
  (idempotent? [_] true)
  (request [_ ctx]
    (let [res (res/request (:resource ctx) :put ctx)]
      (assoc-in ctx [:response :status]
                (cond
                  ;; TODO A 202 may be not what the developer wants!
                  (d/deferred? res) 202
                  (:exists? ctx) 204
                  :otherwise 201)))))

(deftype Post [])

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
  #_java.util.Map
  #_(interpret-post-result [m ctx]
      ;; TODO: Factor out hm (header-merge) so it can be tested independently
      (letfn [(hm [x y]
                (cond
                  (and (nil? x) (nil? y)) nil
                  (or (nil? x) (nil? y)) (or x y)
                  (and (coll? x) (coll? y)) (concat x y)
                  (and (coll? x) (not (coll? y))) (concat x [y])
                  (and (not (coll? x)) (coll? y)) (concat [x] y)
                  :otherwise (throw (ex-info "Unexpected headers case" {:x x :y y}))))]
        (cond-> ctx
          (:status m) (assoc-in [:response :status] (:status m))
          (:headers m) (update-in [:response :headers] #(merge-with hm % (:headers m)))
          (:body m) (assoc-in [:response :body] (:body m)))))
  nil
  (interpret-post-result [_ ctx] ctx))

(extend-protocol Method
  Post
  (keyword-binding [_] :post)
  (safe? [_] false)
  (idempotent? [_] false)
  (request [_ ctx]
    (d/chain
     (res/request (:resource ctx) :post ctx)
     (fn [res]
       (infof "res is %s" res)
       (interpret-post-result res ctx))
     (fn [ctx]
       (-> ctx
           (update-in [:response] (partial merge {:status 200})))))))

(deftype Delete [])

(extend-protocol Method
  Delete
  (keyword-binding [_] :delete)
  (safe? [_] false)
  (idempotent? [_] true)
  (request [_ ctx]
    (d/chain
     (res/request (:resource ctx) :delete ctx)
     (fn [res]
       ;; TODO: Could we support 202 somehow?
       (assoc-in ctx [:response :status] 204)))))

(deftype Options [])

(extend-protocol Method
  Options
  (keyword-binding [_] :options)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [_ ctx]
    ;; TODO: Build in explicit support for CORS pre-flight requests
    (d/chain
     (res/request (:resource ctx) :options ctx)
     (fn [res]
       (update-in ctx [:response] merge res))
     ;; For example, for a resource supporting CORS
     #_(link ctx
       (if-let [origin (service/allow-origin (:resource ctx) ctx)]
         (update-in ctx [:response :headers]
                    merge {"access-control-allow-origin"
                           origin
                           "access-control-allow-methods"
                           (apply str
                                  (interpose ", " ["GET" "POST" "PUT" "DELETE"]))}))))))

(deftype Trace [])

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
  Trace
  (keyword-binding [_] :trace)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [_ ctx]
    (throw
     (ex-info "TRACE"
              (merge
               {:yada.core/http-response true}
               (let [body (-> ctx
                              :request
                              ;; A client MUST NOT generate header fields in a TRACE request containing sensitive
                              ;; data that might be disclosed by the response. For example, it would be foolish for
                              ;; a user agent to send stored user credentials [RFC7235] or cookies [RFC6265] in a
                              ;; TRACE request.
                              (update-in [:headers] dissoc "authorization" "cookie")
                              print-request
                              with-out-str
                              ;; only "7bit", "8bit", or "binary" are permitted (RFC 7230 8.3.1)
                              (to-encoding "utf8"))]

                 {:status 200
                  :headers {"content-type" "message/http;charset=utf8"
                            ;; TODO: Whoops! http://mark.koli.ch/remember-kids-an-http-content-length-is-the-number-of-bytes-not-the-number-of-characters
                            "content-length" (.length body)}
                  :body body
                  ::http-response true}))))))
