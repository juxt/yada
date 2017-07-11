;; Copyright © 2014-2017, JUXT LTD.

(ns yada.stack-trace-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(deftest disable-stack-traces-test
  (let [response
        (yada/response-for
         {:methods {:get {:produces "text/html" :response (fn [ctx] nil)}}}
         :get
         "/"
         {:headers {"accept" "text/html"}})]
    (is (re-find #"clojure.lang.ExceptionInfo" (:body response))))

  (let [response
        (yada/response-for
         {:methods {:get {:produces "text/html" :response (fn [ctx] nil)}}
          :show-stack-traces? false
          }
         :get
         "/"
         {:headers {"accept" "text/html"}})]
    (is (not (re-find #"clojure.lang.ExceptionInfo" (:body response))))))
