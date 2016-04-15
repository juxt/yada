;; Copyright © 2016, JUXT LTD.

(ns yada.oauth
  (:require
   [aleph.http :as http]
   [buddy.core.hash :as hash]
   [buddy.core.keys :as keys]
   [buddy.sign.jwe :as jwe]
   [buddy.sign.jws :as jws]
   [byte-streams :as b]
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [clj-time.core :as time]
   [clojure.java.io :as io]
   [hiccup.core :refer [html h]]
   [manifold.deferred :as d]
   [ring.util.codec :as codec]
   [ring.util.response :as response]
   [schema.core :as s]
   [yada.body :refer [render-error]]
   [yada.yada :refer [resource uri-for]]
   [yada.security :refer [verify]]))

;; http://ncona.com/2015/02/consuming-a-google-id-token-from-a-server/

(s/defn oauth2-initiate-resource
  "Returns a resource that can be used in a GET or a POST which
  redirects to the OAuth2 authentication server to initiate the
  acquisition of an access-token for the user."
  [opts :- {:type (s/enum :github :google)
            (s/optional-key :id) s/Keyword
            :client-id s/Str
            ;; Where to send the user after authorization, use a keyword here for yada's uri-for function
            :redirect-uri s/Keyword
            :scope s/Str
            :secret s/Str
            :authorization-uri s/Str
            ;; The default target URI to redirect to on successful
            ;; authentication, can be overridden via full URIs passed
            ;; as query or form parameters
            (s/optional-key :target-uri) s/Keyword
            }]
  (let [initiate (fn [ctx
                      {:keys [client-id redirect-uri scope secret authorization-uri target-uri type]}
                      target-uri-override]
                   (merge
                    (:response ctx)
                    (response/redirect
                     (str authorization-uri "?"
                          (codec/form-encode
                           (merge
                            {"client_id" client-id
                             "redirect_uri" (:uri (uri-for ctx redirect-uri))
                             "scope" scope
                             "state" (jwe/encrypt {:target-uri (or target-uri-override
                                                                   (when target-uri (:uri (uri-for ctx target-uri)))
                                                                   "")}
                                                  secret)}
                            (when (= type :google) {"response_type" "code"})))))))]
    (resource
     (merge
      (when-let [id (:id opts)] {:id id})
      {:methods
       {:get {:parameters {:query {(s/optional-key :target-uri) s/Str}}
              :response (fn [ctx] (initiate ctx opts (-> ctx :parameters :query :target-uri)))}
        :post {:parameters {:form {(s/optional-key :target-uri) s/Str}}
               :response (fn [ctx] (initiate ctx opts (-> ctx :parameters :form :target-uri)))}}}))))

(s/defn oauth2-callback-resource-github
  [opts :- {(s/optional-key :id) s/Keyword
            :client-id s/Str
            :client-secret s/Str
            :secret s/Str

            ;; The function that will ultimately call the third-party API for user-details.
            ;; First argument is the access-token
            :access-token-handler (s/=> {s/Any s/Any} s/Str)
            :access-token-url s/Str}]

  (let [{:keys [client-id client-secret secret access-token-handler access-token-url]} opts]
    (assert access-token-handler)
    (resource
     (merge
      (when-let [id (:id opts)]
        {:id id})
      {:methods
       {:get
        {:produces "text/html"
         :parameters {:query {(s/optional-key :code) s/Str
                              (s/optional-key :state) s/Str
                              (s/optional-key :error) s/Str
                              (s/optional-key "error_description") s/Str
                              (s/optional-key "error_uri") s/Str}}
         :response
         (fn [ctx]

           (if-let [error (-> ctx :parameters :query :error)]
             (str "ERROR: " (-> ctx :parameters :query (get "error_description")))

             (let [code (-> ctx :parameters :query :code)
                   state (jwe/decrypt (-> ctx :parameters :query :state) secret)
                   target-uri (:target-uri state)]

               ;; Make API calls to GitHub without blocking the request thread
               (d/chain

                ;; Using the code, try to acquire an access token for API.
                ;; Note we are using an async client HTTP API here (aleph)
                (http/post
                 access-token-url
                 {:accept "application/json"
                  :form-params {"client_id" client-id
                                "client_secret" client-secret
                                "code" code}})

                (fn [response]
                  (if-not (= (:status response) 200)
                    (d/error-deferred (ex-info "Didn't get 200 from access_token call" {}))
                    (json/parse-stream (io/reader (:body response)))))

                (fn [json]
                  (if (get json "error")
                    (d/error-deferred (ex-info "access_token call returned error" json))
                    (get json "access_token")))

                (fn [access-token]
                  (if-not access-token
                    (d/error-deferred (ex-info "No access token in response" {}))
                    (access-token-handler access-token)))

                (fn [data]
                  (if (nil? data)
                    (d/error-deferred (ex-info "Forbidden" {:status 403}))

                    ;; TODO: Refresh tokens
                    (let [expires (time/plus (time/now) (time/minutes 15)) ; TODO parameterize
                          cookie {:value (jwe/encrypt data secret)
                                  :expires expires
                                  :http-only true}]

                      (merge (:response ctx)
                             {:cookies {"session" cookie}} ; TODO parameterize?
                             (response/redirect target-uri)))))))))}}}))))

(s/defn oauth2-callback-resource-google
  [opts :- {(s/optional-key :id) s/Keyword
            :access-token-url s/Str
            :client-id s/Str
            :client-secret s/Str
            :secret s/Str
            :redirect-uri s/Keyword

            ;; The function that will ultimately call the third-party API for user-details.
            ;; First argument is the access-token
            :handler (s/=> {s/Any s/Any} {:access-token s/Str :openid-claims {s/Str s/Str}})
            }]

  (let [{:keys [access-token-url client-id client-secret secret redirect-uri handler]} opts]
    (assert handler)
    (resource
     (merge
      (when-let [id (:id opts)]
        {:id id})
      {:methods
       {:get
        {:produces "text/html"
         :parameters
         {:query
          {:authuser s/Str
           :hd s/Str
           (s/required-key "session_state") s/Str
           :prompt s/Str
           :state s/Str
           :code s/Str}}
         :response
         (fn [ctx]
           (let [state (jwe/decrypt (-> ctx :parameters :query :state) secret)
                 target-uri (:target-uri state)]

             ;; Make API calls to GitHub without blocking the request thread
             (d/chain
              (http/post
               access-token-url
               {:accept "application/json"
                :form-params {"code" (-> ctx :parameters :query :code)
                              "client_id" client-id
                              "client_secret" client-secret
                              "redirect_uri" (:uri (uri-for ctx redirect-uri))
                              "grant_type" "authorization_code"}
                :throw-exceptions false})

              (fn [response]
                (if-not (= (:status response) 200)
                  (d/error-deferred (ex-info "Didn't get 200 from access_token call" {:response (update response :body b/to-string)}))
                  (json/parse-string (b/to-string (:body response)))))

              (fn [json]
                (let [access-token (get json "access_token")
                      id-token (get json "id_token")
                      [header body sig] (map (comp b/to-string codec/base64-decode) (str/split id-token #"\."))
                      ]

                  ;; TODO: Verify sig (but this is deemed safe because of https)

                  {:access-token access-token
                   :openid-claims (json/parse-string body)}))

              handler

              (fn [data]
                  (if (nil? data)
                    (d/error-deferred (ex-info "Forbidden" {:status 403}))

                    ;; TODO: Refresh tokens
                    (let [expires (time/plus (time/now) (time/minutes 15)) ; TODO parameterize
                          cookie {:value (jwe/encrypt data secret)
                                  :expires expires
                                  :http-only true}]

                      (merge (:response ctx)
                             {:cookies {"session" cookie}} ; TODO parameterize?
                             (response/redirect target-uri))))))))}}

       ;; If you don't want this behavior, replace the :responses
       ;; value in the resource with your own.
       :responses {500 {:produces #{"text/html" "text/plain"}
                        :response (fn [ctx]
                                    (let [error (:error ctx)]
                                      (cond
                                        (not (instance? clojure.lang.ExceptionInfo error))
                                        (render-error 500 error (-> ctx :response :produces) ctx)

                                        :otherwise
                                        (render-error 500 error (-> ctx :response :produces) ctx)
                                        #_(str "An error occured:" (pr-str error))
                                        )

                                      ))}}}))))

(defmethod verify :oauth2
  [ctx {:keys [cookie yada.oauth2/secret] :or {cookie "session"} :as scheme}]
  (when-not secret (throw (ex-info "Buddy JWE decryption requires a secret entry in scheme" {:scheme scheme})))
  (try
    (let [auth (some->
                (get-in ctx [:cookies cookie])
                (jwe/decrypt secret))]
      auth)
    (catch clojure.lang.ExceptionInfo e
      (if-not (= (ex-data e)
                 {:type :validation :cause :decryption})
        (throw e)))))


#_(b/to-string (codec/base64-decode "eyJhbGciOiJSUzI1NiIsImtpZCI6IjRkNTAyMDFhOTI5MDk1NjU5NzVkNTRhNjc0NTZjODA2YTc3ODdlNTQifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhdF9oYXNoIjoieV9Hb3N1bFk3QWR3cjd0S3hUZElOZyIsImF1ZCI6Ijg0ODU4MTI0ODMwNy10aDFiaGU0NXEwMGFsa3M5cmljbThtajRramE0bm43di5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsInN1YiI6IjExNjkyMDEyMDYzOTU3NTQ5NjU3NSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJhenAiOiI4NDg1ODEyNDgzMDctdGgxYmhlNDVxMDBhbGtzOXJpY204bWo0a2phNG5uN3YuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJoZCI6Imp1eHQucHJvIiwiZW1haWwiOiJtYWxjb2xtQGp1eHQucHJvIiwiaWF0IjoxNDYxMDg2Mjc4LCJleHAiOjE0NjEwODk4NzgsIm5hbWUiOiJNYWxjb2xtIFNwYXJrcyIsInBpY3R1cmUiOiJodHRwczovL2xoNC5nb29nbGV1c2VyY29udGVudC5jb20vLWtrMDl6SzM1RjRFL0FBQUFBQUFBQUFJL0FBQUFBQUFBQWxZLzltdUxQY1NkX1dNL3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJNYWxjb2xtIiwiZmFtaWx5X25hbWUiOiJTcGFya3MiLCJsb2NhbGUiOiJlbiJ9.FsVXFltEVNbyZmWK4Mi-wAlYuVkBvHo0vqzd70vl2gWAlZ-icyT-iiHu5NSjfpdkOa26i8NMUF99q1o-_SUH3kY2vZ4Wjx9fmd3sYmTiX4cpZmJJEIb8gkMtQbYv_r6nJpcXkiWa0vVDejH1Af25s6nsWOzoQEtGHfERdFyAs4SDlk3cNAUx6kwjL-dAT93LZooe17pgfV-kgEikJiP-MfR8Kk_P8eAs7JcGsNGWcpOCYv3ATl7Qe1dZ4e5itLJDgIZJl54j_hUzgY5EfV6Yc_1kLWMBIOFMUEUdB-ZqYMd6x40Mpkb7K5_tTOfU6JZ16B4f1tnhifD6uAlCsOmQmQ"))
