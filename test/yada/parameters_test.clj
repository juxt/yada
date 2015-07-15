(ns yada.parameters-test
  (:require
   [byte-streams :as bs]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [yada.yada :refer (yada)]
   [yada.resource :as res]
   [yada.test.util :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [clojure.tools.logging :refer :all]))

(defrecord JustMethods []
  res/Resource
  (methods [this] (keys this))
  (parameters [this]
    (infof "this is %s" (seq this))
    (let [res
          (reduce-kv (fn [acc k v]
                       (infof "associng %s: %s = %s" acc k v)
                       (assoc acc k (:parameters v))) {} this)]
      (infof "res is %s" res)
      res
      ))
  (exists? [_ ctx] true)
  (last-modified [_ ctx] (java.util.Date.))
  (request [this method ctx]
    (when-let [f (get-in this [method :function])]
      (f ctx)))
  res/ResourceRepresentations
  (representations [_] [{:content-type #{"text/plain"}}]))

(defn just-methods [& {:as args}]
  (infof "args is %s" args)
  (map->JustMethods args))

(deftest post-test
  (let [handler (yada (just-methods
                       :post {:function
                              (fn [ctx]
                                (pr-str (:parameters ctx)))
                              :parameters {:form {:foo s/Str}}}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := nil
        ))

    ;; Form post body
    (let [response (handler (mock/request :post "/"
                                          {"foo" "bar"}))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "bar"}))))

(deftest post-test-with-query-params
  (let [handler (yada (just-methods
                       :post {:function
                              (fn [ctx] (pr-str (:parameters ctx)))
                              :parameters {:query {:foo s/Str}
                                          :form {:bar s/Str}}}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/?foo=123"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "123"}))

    ;; Form post body
    (let [response (handler (mock/request :post "/?foo=123"
                                          {"bar" "456"}))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "123" :bar "456"}))))
