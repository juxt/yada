;; Copyright © 2015, JUXT LTD.

(ns yada.new-negotiation-test
  (:require
   [clojure.test :refer :all]
   [yada.test.util :refer [given]]))

(defn accept [request server-acceptable]
  (when-let [method ((:method server-acceptable) (:method request))]
    {:method method}
    ))

(defn negotiate [request server-acceptables]
  (some (partial accept request) server-acceptables))

(deftest method-test
  (testing "single option matches"
    (given (negotiate {:method :get} [{:method #{:get :head}}])
      :method := :get))
  (testing "single option not acceptable"
    (given (negotiate {:method :post} [{:method #{:get :head}}])
      :method :? nil?))
  (testing "second option works"
    (given (negotiate {:method :post} [{:method #{:get :head}}{:method #{:post :put}}])
      :method := :post)))

;; New idea for resource conneg
;;(methods [_] [])
;; Return a set of capabilities, each option can contain methods, content-types, charsets (via parameters) and languages. Also, everything except methods can implement q vals

;; Additionally, methods can be given implementations

;; New methods should be added by use of defmethod, not on a per resource basis

;; In the case of a POST, we need to distinguish between 415 (request
;; content-type not supported) and 406 (not acceptable). 415 relates to
;; the incoming content, while 406 says that no possible response
;; content-types in acceptable by the user-agent.

;; TODO: what about charsets on receiving?

[{:method #{:get :head}
  :content-type #{"text/html;q=1.0" "text/html"}
  :charset #{"UTF-8" "Shift_JIS;q=0.2"}
  :encoding #{"compress" "gzip" "deflate"}
  :language #{"jp-JP"}
  }
 {:method #{:get :head}
  :content-type #{"text/html;q=1.0"}
  :language #{"en-GB" "en-US;q=0.9"}
  }
 {:method #{:post}
  :accept #{"application/edn"}          ; the server accepts
  :accept-language #{"en-US"}           ; the server accepts
  :content-type #{"application/edn"}    ; the server produces

  ;; rfc7231.html#section-3.1.3.2
  ;; "Content-Language MAY be applied to any media type -- it is not limited to textual documents."
  :language #{"en-GB" "en-US;q=0.9"}


  }]
