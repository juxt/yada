(ns yada.examples-test
  (:require [yada.dev.examples :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [yada.test.util :refer [given]]))

(deftest HelloWorld-test
  (let [ex (->HelloWorld)
        h (make-handler ex)
        req (request ex)
        path (str "/" (get-path ex))]
    (given @(h (mock/request (:method req) path))
      :status := 200
      :body := "Hello World!")))


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
