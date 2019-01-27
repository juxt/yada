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
                    (yada/new-cookie :session 123)
                    (yada/new-cookie :tracker "xyz")))}}})]

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

      (is (= "123" (:body response))))))
