(ns yada.state-management-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]
   [yada.test :refer [response-for]]))

;; To produce a cookie from a response function, return the (:response ctx) with cookies applied as per usual.
;; (yada/response ctx {:cookie :session :body "foo" :location "<redirect>"})




(response-for
         (yada/resource
          {:cookies
           {:session
            {:name "SID"
             :path "/"
             :domain "example.com"
             :secure true
             :http-only true
             :consumer (fn [ctx cookie value]
                         (when (= value "31d4d96e407aad42")
                           (assoc ctx :authentication {:user "Alice"})))
             :privacy/privacy #:privacy{:category "functional"}}}

           :methods
           {:get
            {:produces "application/edn"
             :response (fn [ctx]
                         (-> ctx :authentication))}}})

         :get "/" {:headers {"cookie" "SID=31d4d96e407aad42"}})



(deftest cookie-consumer-test
  (let [response
        (response-for
         (yada/resource
          {:cookies
           {:session
            {:name "SID"
             :path "/"
             :domain "example.com"
             :secure true
             :http-only true
             :consumer (fn [ctx cookie value]
                         (when (= value "31d4d96e407aad42")
                           (assoc ctx :authentication {:user "Alice"})))
             :privacy/privacy #:privacy{:category "functional"}}}

           :methods
           {:get
            {:produces "application/edn"
             :response (fn [ctx]
                         (-> ctx :authentication))}}})

         :get "/" {:headers {"cookie" "SID=31d4d96e407aad42"}})]
    (is (pr-str {:user "Alice"}) (= (:body response)))))
