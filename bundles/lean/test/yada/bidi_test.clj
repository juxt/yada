;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [byte-streams :as b]
   [bidi.vhosts :refer [vhosts-model make-handler]]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :refer [uri-info resource]]
   yada.bidi))

(deftest uri-info-test
  (let [h
        (make-handler
         (vhosts-model
          [{:scheme :http :host "localhost"}
           ["/" [["foo"
                  (resource
                   {:id :foo
                    :methods
                    {:get
                     {:produces "text/plain"
                      :response (fn [ctx]
                                  (str (:href (uri-info ctx :bar))
                                       " "
                                       (:href (uri-info ctx :foobarzip))))}}})]
                 ["foo/bar/zip"
                  (resource
                   {:id :foobarzip
                    :methods {:get {:produces "text/plain"
                                    :response (fn [ctx]
                                                (str (:href (uri-info ctx :bar))
                                                     " "
                                                     (:href (uri-info ctx :foo))))}}
                    })]
                 ["bar" (resource {:id :bar
                                   :methods {}})]
                 ["bar/foo" :barfoo]
                 ]]]))]
    (is (= "bar foo/bar/zip" (some-> (request :get "/foo") h deref :body b/to-string)))
    (is (= "../../bar ../../foo" (some-> (request :get "/foo/bar/zip") h deref :body b/to-string)))))
