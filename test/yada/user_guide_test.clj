(ns yada.user-guide-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :refer [yada]]
   [yada.test.util :refer [given]]
   [ring.mock.request :as mock]
   yada.string-resource))

(deftest hello-world-test
  (testing "hello-world"
    (let [resource "Hello World!"
          handler (yada resource)
          request (mock/request :get "/")
          response @(handler request)]

      (given response
        :status := 200
        :headers :> {"content-length" (count "Hello World!")}
        :body :? string?)

      ))  )
