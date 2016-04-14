(ns yada.tempfile
  (:require
   ;; [taoensso.timbre :refer [debug info warn error]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [manifold.stream :as stream]
   [byte-streams :as bs]
   [yada.yada :refer [resource]]
   [yada.request-body :as req-body]
   [yada.multipart :as mp]
   [bidi.ring :as bidi]
   [aleph.http :as http])
  (:import [java.io File]))

(defrecord TempfilePartial [part fieldname filename content-type f out]
  mp/Partial
  (continue [this piece]
    (info "continue TempfilePartial"
          {:fieldname fieldname
           :filename filename
           :content-type content-type
           :tempfile f
           :piece piece})
    (.write out (:bytes piece))
    this)
  (complete [this state piece]
    (info "complete TempfilePartial"
          {:fieldname fieldname
           :filename filename
           :content-type content-type
           :tempfile f
           :piece piece})
    (.write out (:bytes piece))
    (.close out)
    (update state
            :parts
            (fnil conj [])
            (-> part
                (assoc :tempfile-partial this)))))

(defn create-tempfile-partial
  [{:keys [headers bytes body-offset] :as piece}]
  (let [cd (get headers "content-disposition")
        [_ fieldname] (some->> cd (re-find #"name=\"([^\"]+)\""))
        [_ filename] (some->> cd (re-find #"filename=\"([^\"]+)\""))
        [_ pre suff] (some->> filename (re-find #"(.+)\.(.*)"))
        ct (get headers "content-type")
        f (File/createTempFile (or pre "file") (when suff (str "." suff)))
        out (io/output-stream f)]
    (info "create-tempfile-partial"
          {:fieldname fieldname
           :filename filename
           :content-type ct
           :tempfile f
           :piece piece})
    (.write out
            bytes
            body-offset
            (- (alength bytes) body-offset))
    (->TempfilePartial (-> piece
                           (dissoc :bytes)
                           (assoc :type :part))
                       fieldname
                       filename
                       ct
                       f
                       out)))

(defrecord TempfilePartConsumer []
    mp/PartConsumer
    (consume-part [_ state part]
      (info "consume-part" state part)
      (update state :parts (fnil conj []) (mp/map->DefaultPart part)))
    (start-partial [_ piece]
      (info "start-partial" piece)
      (create-tempfile-partial piece))
    (part-coercion-matcher [_]
      (info "part-coercion-matcher")
      ;; Coerce a DefaultPart into the following keys
      {String (fn [^yada.multipart.DefaultPart part]
                (let [offset (get part :body-offset 0)]
                  (String. (:bytes part) offset (- (count (:bytes part)) offset))))
       yada.tempfile.TempfilePartial :tempfile-partial}))

(defn tempfile-resource
  []
  (resource
   {:methods {:post {:consumes #{"multipart/form-data"}
                     :consumer (fn [ctx content-type body]
                                 (req-body/process-request-body
                                  (assoc-in ctx
                                            [:options :part-consumer]
                                            (->TempfilePartConsumer))
                                  (stream/map
                                   bs/to-byte-array
                                   (bs/to-byte-buffers body))
                                  (:name content-type)))
                     :produces #{"application/json"}
                     :response (fn [ctx] (json/generate-string :ok))}}
    :access-control {:allow-origin #{"*"}
                     :allow-headers ["Authorization" "Content-Type"]
                     :allow-methods #{:post :get}}}))

(defn make-handler
  [app]
  (bidi/make-handler ["/" (tempfile-resource)]))

(defn start-server
  [port]
  (http/start-server (make-handler {}) {:port port}))
