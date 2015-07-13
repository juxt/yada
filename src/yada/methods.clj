(ns yada.methods
  (:refer-clojure :exclude [methods])
  (:require
   [clojure.string :as str]
   [manifold.deferred :as d]
   [yada.mime :as mime]
   [yada.representation :as rep]
   [yada.resource :as res]
   [yada.service :as service]
   [yada.util :refer (link)]))

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
  (request [_ ctx] "Call the method"))

(defn methods
  "Return a map of method instances"
  []
  (into {}
        (map
         (fn [m]
           (let [i (.newInstance m)]
             [(keyword-binding i) i]))
         (extenders Method))))

(deftype Get [])

(extend-protocol Method
  Get
  (keyword-binding [_] :get)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]
    (d/chain
     ctx

     ;; Get body
     (fn [ctx]
       (let [content-type (get-in ctx [:response :content-type])]

         (d/chain

          ;; the resource here can still be deferred (TODO: test this)
          (:resource ctx)

          (fn [resource]
            (rep/to-representation
             (res/get-state resource content-type ctx)
             content-type))

          (fn [body]
            (let [content-length (rep/content-length body)]
              (cond-> ctx
                true (assoc-in [:response :body] body)
                content-length (update-in [:response :headers] assoc "content-length" content-length)
                content-type (update-in [:response :headers] assoc "content-type" (mime/media-type->string content-type)))))))))))


(deftype Head [])

(extend-protocol Method
  Head
  (keyword-binding [_] :head)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [this ctx]
    (d/chain
     ctx

     ;; Get body
     (fn [ctx]
       (let [content-type (get-in ctx [:response :content-type])]

         (if content-type
           ;; We don't need to add Content-Length,
           ;; Content-Range, Trailer or Tranfer-Encoding, as
           ;; per rfc7231.html#section-3.3
           (update-in ctx [:response :headers]
                      assoc "content-type" (mime/media-type->string content-type))
           ctx))))))


(deftype Put [])

(extend-protocol Method
  Put
  (keyword-binding [_] :put)
  (safe? [_] false)
  (idempotent? [_] true)
  (request [_ ctx]
    (d/chain
     ctx

     ;; Do the put!
     (fn [ctx]
       (let [exists? (res/exists? (:resource ctx) ctx)]
         (let [res (res/put-state! (:resource ctx) nil nil ctx)]

           (assoc-in ctx [:response :status]
                     (cond
                       ;; TODO A 202 may be not what the developer wants!
                       (d/deferred? res) 202
                       exists? 204
                       :otherwise 201))))))))

(deftype Post [])

(defprotocol PostResult
  (interpret-post-result [_ ctx]))

(extend-protocol PostResult
  Boolean
  (interpret-post-result [b ctx]
    (if b ctx (throw (ex-info "Failed to process POST" {}))))
  String
  (interpret-post-result [s ctx]
    (assoc-in ctx [:response :body] s))
  java.util.Map
  (interpret-post-result [m ctx]
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
     ctx

     (link ctx
       (when-let [etag (get-in ctx [:request :headers "if-match"])]
         (when (not= etag (get-in ctx [:resource :etag]))
           (throw
            (ex-info "Precondition failed"
                     {:status 412
                      ::http-response true})))))

     (fn [ctx]
       (let [result (res/post-state! (:resource ctx) ctx)]

         ;; TODO: what if error?
         (assoc ctx :post-result result)))

     (fn [ctx]
       (interpret-post-result (:post-result ctx) ctx))

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
     ctx
     (fn [ctx]
       (let [res (res/delete-state! (:resource ctx) ctx)]
         (assoc-in ctx [:response :status] (if (d/deferred? res) 202 204)))))))

(deftype Options [])

(extend-protocol Method
  Options
  (keyword-binding [_] :options)
  (safe? [_] true)
  (idempotent? [_] true)
  (request [_ ctx]
    (d/chain
     ctx
     (link ctx
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
    (d/error-deferred
     (ex-info "TRACE"
              (merge
               {::http-response true}
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
                            "content-length" (.length body)}
                  :body body
                  ::http-response true}))))))
