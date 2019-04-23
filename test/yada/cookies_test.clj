(ns yada.cookies-test
  (:require
   [yada.cookies :as cookies]
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(deftest cookie-test
  (let [cookies
        {:session {:name "session"
                   :consumer (fn [ctx cookie v] (assoc ctx :cookie-val v))}
         :tracker {:name "tracker"}}]

    (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (yada/set-cookie :session 123)
                    (yada/set-cookie :tracker "xyz")))}}})]

      (is
       (= {:status 200,
           :headers
           {"set-cookie" ["session=123" "tracker=xyz"]},
           :body nil} (update response :headers select-keys ["set-cookie"]))))

    (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (or (:cookie-val ctx) "none"))}}}
           :get "/" {:headers {"cookie" "session=123"}})]

      (is (= "123" (:body response))))

    (testing "unset cookie"
      (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (yada/unset-cookie :session)))}}})]
        (is
         (= {:status 200,
             :headers
             {"set-cookie" ["session=; Expires=Thu, 01 Jan 1970 00:00:00 +0000"]},
             :body nil} (update response :headers select-keys ["set-cookie"])))))))
