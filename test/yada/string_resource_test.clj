(ns yada.string-resource-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.core :refer [yada]]
   [ring.mock.request :refer [request]]
   [yada.test.util :refer [given]]))


(deftest string-test
  (testing "Producing a Java string implies utf-8 charset"
    ;; Yada should make life easy for developers. If the developer does not
    ;; declare a charset parameter, it is possible to infer one based on the
    ;; resource. For files, this is not possible, since there is no reliable
    ;; or performant way of determining the encoding used for a file's
    ;; content. However, with java.lang.String instances, this isn't the
    ;; case. We know the Java platform stores Strings as Unicode utf-16, and
    ;; that it can output these strings in utf-8.
    (let [resource "Hello World"
          handler (yada resource :produces "text/html")
          request (request :get "/")
          response @(handler request)]
      (given response
        [:headers "content-type"] := "text/html;charset=utf-8"
        )
      )

    ;; TODO: If strings are used, then an explicit charset provided in
    ;; the :produces entry should be honored and used when writing the
    ;; String.
    ))
