;; Copyright © 2014-2017, JUXT LTD.

(ns yada.parameters-test
  (:require
   [byte-streams :as bs]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [schema.core :as s]
   [yada.context :as ctx]
   [yada.handler :refer [handler]]
   [yada.resource :refer [resource]]))

(s/defschema Transaction
  {:payee String
   :description String
   :amount Double})

;; Testing <<parameters>> examples in user-manual

(deftest parameters-test
  (let [res (resource
             {:parameters {:path {:account Long}}
              :methods
              {:get {:produces "application/edn"
                     :parameters {:query {:since String}}
                     :response (fn [ctx] {:message "Parameters"
                                          :account (ctx/path-parameter ctx :account)
                                          :since (ctx/query-parameter ctx :since)})}
               :post {:parameters {:body Transaction}
                      :consumes "application/edn"
                      :produces "application/edn"
                      :response (fn [ctx] {:body (:body ctx)})}}})
        h (handler res)]

    (is res)

    (is (= {:message "Parameters" :account 1234 :since "tuesday"}
           (edn/read-string (bs/to-string (:body @(h (assoc (mock/request :get "/accounts/1234/transactions?since=tuesday")
                                                            :route-params {:account "1234"})))))))

    (is (= 400
           (:status @(h (assoc (mock/request :get "/accounts/1234/transactions")
                               :route-params {:account "1234"})))))

    (is (= 400 (:status @(h (let [body (pr-str {:payee "me" :description "monies"})]
                              (assoc-in (mock/request :post "/accounts/1234/transactions" body)
                                        [:headers "content-type"] "application/edn"))))))

    (is (= 200 (:status @(h (let [body (pr-str {:payee "me" :description "monies" :amount 100.0})]
                              (-> (mock/request :post "/accounts/1234/transactions" body)
                                  (assoc :route-params {:account "1234"})
                                  (assoc-in [:headers "content-type"] "application/edn")))))))

    (is (= {:body {:payee "me", :description "monies", :amount 100.0}}
           (edn/read-string (bs/to-string (:body @(h (let [body (pr-str {:payee "me" :description "monies" :amount 100.0})]
                                                       (merge (-> (mock/request :post "/accounts/1234/transactions" body)
                                                                  (assoc :route-params {:account "1234"}))
                                                              {:headers {"content-type" "application/edn"
                                                                         "content-length" (pr-str (count (.getBytes body)))}}))))))))))
