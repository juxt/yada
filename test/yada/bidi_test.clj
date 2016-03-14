;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [byte-streams :as b]
   [clojure.string :as str]
   [bidi.vhosts :refer [vhosts-model make-handler]]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.context :refer [relativize]]
   [yada.yada :refer [uri-for resource]]))

(deftest relativize-test
  (are [source dest href] (= href (relativize source dest))
    ;; TODO: Test to itself
    "" "" nil
    "/abc/cd/d.html" "/abc/" "../"
    "/abc/foo.html" "/abc/bar.html" "bar.html"
    "/abc/foo/a.html" "/abc/bar/b.html" "../bar/b.html"
    "/abc/foo/a/b" "/abc/bar/b" "../../bar/b"
    "/abc.html" "/abc.html" "abc.html"
    "/a/abc.html" "/a/abc.html" "abc.html"

    "/a/" "/a/abc.html" "abc.html"
    "/a" "/a/abc.html" "a/abc.html"

    "/a/abc.html" "/a/" ""
    ))

(deftest uri-for-test
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
                                  (str (:href (uri-for ctx :bar))
                                       " "
                                       (:href (uri-for ctx :foobarzip))))}}})]
                 ["foo/bar/zip"
                  (resource
                   {:id :foobarzip
                    :methods {:get {:produces "text/plain"
                                    :response (fn [ctx]
                                                (str (:href (uri-for ctx :bar))
                                                     " "
                                                     (:href (uri-for ctx :foo))))}}
                    })]
                 ["bar" (resource {:id :bar
                                   :methods {}})]
                 ["bar/foo" :barfoo]
                 ]]]))]
    (is (= "bar foo/bar/zip" (b/to-string (:body (deref (h (request :get "/foo")))))))
    (is (= "../../bar ../../foo" (b/to-string (:body (deref (h (request :get "/foo/bar/zip")))))))))
