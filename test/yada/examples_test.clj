(ns yada.examples-test
  (:require [yada.dev.examples :refer :all]
            [byte-streams :as bs]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [bidi.bidi :refer (path-for)]
            [bidi.ring :as br]
            [yada.test.util :refer [given]]))

(defn ex-handler [ex]
  (let [h (make-example-handler ex)
        route [(get-path ex) h]
        path (let [qs (get-query-string ex)]
               (cond-> (apply path-for route h (get-path-args ex))
                 qs (str "?" qs)))

        bh (br/make-handler route)
        req (get-request ex)
        req (merge
             (mock/request (:method req) path)
             (dissoc req :method)
             (when-let [data (:data req)]
               {:body (java.io.ByteArrayInputStream. (.getBytes (encode-data data (get-in req [:headers "Content-Type"]))))})
             (when-let [headers (:headers req)]
               {:headers (reduce-kv (fn [acc k v] (assoc acc (.toLowerCase k) v)) {} headers)}
               ))]
    {:handler bh
     :path path
     :request req}))

(deftest HelloWorld-test
  (let [{:keys [handler request path]}
        (ex-handler (->HelloWorld))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := "Hello World!")))

(deftest DynamicHelloWorld-test
  (let [{:keys [handler request path]}
        (ex-handler (->DynamicHelloWorld))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := "Hello World!")))

(deftest AsyncHelloWorld-test
  (let [{:keys [handler request path]}
        (ex-handler (->AsyncHelloWorld))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := "Hello World!")))

(deftest PathParameterUndeclared-test
  (let [{:keys [handler request path]}
        (ex-handler (->PathParameterUndeclared))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := "Account number is 1234")))

(deftest ParameterDeclaredPathQueryWithGet-test
  (let [{:keys [handler request path]}
        (ex-handler (->ParameterDeclaredPathQueryWithGet))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := (format "List transactions since %s from account number %s" "tuesday" 1234))))

(deftest ParameterDeclaredPathQueryWithPost-test
  (let [{:keys [handler request path]}
        (ex-handler (->ParameterDeclaredPathQueryWithPost))]
    (given @(handler request)
      :status := 200
      [:body #(bs/convert % String)] := "Description is 'Gas for last year'")))

;; Get all examples

#_(let [examples (->> (for [v (->> 'yada.dev.examples
                                 find-ns
                                 ns-publics
                                 (filter (comp (partial re-matches #"map->.*") name first))
                                 )]
                      [(first v) ((second v) {})])
                    (filter (comp (partial satisfies? Example) second)))]
  (try
    (for [[nm ex] examples
          :when (not (#{'map->ServerSentEventsWithCoreAsyncChannel
                        'map->ServerSentEventsDefaultContentType
                        'map->StateWithFile
                        'map->LastModifiedHeaderAsLong} nm))]
      (try
        [(get-path ex) (make-handler ex)]
        (catch Exception _
          (throw (ex-info "" {:nm nm :example ex})))))
    ))
