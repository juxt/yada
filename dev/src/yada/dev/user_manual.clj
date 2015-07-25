;; Copyright © 2015, JUXT LTD.

(ns yada.dev.user-manual
  (:require
   [bidi.bidi :refer (tag RouteProvider alts)]
   [bidi.ring :refer (redirect)]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [clojure.pprint :refer (pprint *print-right-margin*)]
   [clojure.string :as str]
   [clojure.walk :refer (postwalk)]
   [clojure.xml :refer (parse)]
   [com.stuartsierra.component :refer (using Lifecycle)]
   [hiccup.core :refer (h html) :rename {h escape-html}]
   [markdown.core :refer (md-to-html-string)]
   [modular.bidi :refer (path-for)]
   [modular.template :as template :refer (render-template)]
   [modular.component.co-dependency :refer (co-using)]
   [yada.dev.examples :refer (get-title get-resource get-options get-path get-path-args get-query-string get-request expected-response get-test-function external? make-example-handler encode-data)]
   [yada.yada :refer (resource)]
   [yada.mime :as mime]
   [yada.resource :as res]
   [yada.swagger :refer (swaggered)]
   [ring.util.time :as rt]
   yada.resources.string-resource
   yada.resources.atom-resource
   ))

(defn emit-element
  ;; An alternative emit-element that doesn't cause newlines to be
  ;; inserted around punctuation.
  [e]
  (if (instance? String e)
    (print e)
    (do
      (print (str "<" (name (:tag e))))
      (when (:attrs e)
	(doseq [attr (:attrs e)]
	  (print (str " " (name (key attr))
                      "='"
                      (let [v (val attr)] (if (coll? v) (apply str v) v))
                      "'"))))
      (if (:content e)
	(do
	  (print ">")
          (if (instance? String (:content e))
            (print (:content e))
            (doseq [c (:content e)]
              (emit-element c)))
	  (print (str "</" (name (:tag e)) ">")))
	(print "/>")))))

(defn basename [r]
  (last (str/split (.getName (type r)) #"\.")))

(defn enclose [^String s]
  (format "<div>%s</div>" s))

(defn xml-parse [^String s]
  (parse (io/input-stream (.getBytes s))))

(defn get-source []
  (xml-parse (enclose (md-to-html-string
                       (slurp (io/resource "user-manual.md"))))))

(defn title [s]
  (letfn [(lower [x]
            (if (#{"as" "and" "of" "for"}
                 (str/lower-case x)) (str/lower-case x) x))
          (part [x]
            (if (Character/isDigit (char (first x)))
              (format "(part %s)" x)
              x
              )
            )]
    (->> (re-seq #"[A-Z1-9][a-z]*" s)
         (map lower)
         (map part)
         (str/join " "))))

(defn chapter [c]
  (str/replace (apply str c) #"\s+" ""))

(defn ->meth
  [m]
  (str/upper-case (name m)))

(defn example-instance [user-manual example]
  (when-let [v (find-var (symbol "yada.dev.examples" (str "map->" (namespace-munge example))))]
    (v user-manual)))

(defn post-process-example [user-manual ex xml {:keys [prefix ext-prefix]}]
  (when xml
    (let [url (str
               (when (external? ex) ext-prefix)
               (apply path-for @(:*router user-manual) (keyword (basename ex)) (get-path-args ex))
               (when-let [qs (get-query-string ex)] (str "?" qs)))
          {:keys [method headers data] :as req} (get-request ex)
          ]

      (postwalk
       (fn [{:keys [tag attrs content] :as el}]
         (cond
           (= tag :handler)
           {:tag :div
            :content [{:tag :pre
                       :content [{:tag :code
                                  :attrs {:class "clojure"}
                                  :content [(escape-html
                                             (str/trim
                                              (with-out-str
                                                (binding [*print-right-margin* 80]
                                                  (let [res (get-resource ex)
                                                        opts (get-options ex)]
                                                    (pprint
                                                     (cond
                                                       (and res opts)
                                                       `(yada ~res ~opts)
                                                       res `(yada ~res)
                                                       opts `(yada ~opts)
                                                       )
                                                     ))))))]}]}]}

           (= tag :request)
           {:tag :div
            :content [{:tag :pre
                       :content [{:tag :code
                                  :attrs {:class "http"}
                                  :content [(str (->meth method) (format " %s HTTP/1.1" url)
                                                 (apply str (for [[k v] headers] (format "\n%s: %s" k v))))
                                            (str (when data
                                                   (str "\n\n" (encode-data data (get headers "Content-Type")) )))]}]}]}

           (= tag :response)
           {:tag :div
            :attrs {:id (format "response-%s" (basename ex))}
            :content [{:tag :p
                       :content [{:tag :button
                                  :attrs {:class "btn btn-primary"
                                          :type "button"
                                          :onClick (format "%s(\"%s\",\"%s\",\"%s\",%s,%s)"
                                                           (or (get-test-function ex) "tryIt")
                                                           (->meth method)
                                                           url
                                                           (basename ex)
                                                           (json/encode headers)
                                                           (when data (encode-data data (get headers "Content-Type"))))}
                                  :content ["Try it"]}
                                 " "
                                 {:tag :button
                                  :attrs {:class "btn"
                                          :type "button"
                                          :onClick (format "clearIt(\"%s\")"
                                                           (basename ex))}

                                  :content ["Reset"]}
                                 ]}
                      {:tag :table
                       :attrs {:class "table"}
                       :content [{:tag :tbody
                                  :content [{:tag :tr
                                             :content [{:tag :td :content ["Status"]}
                                                       {:tag :td :attrs {:class "status"} :content [""]}]}
                                            {:tag :tr
                                             :content [{:tag :td :content ["Headers"]}
                                                       {:tag :td :attrs {:class "headers"} :content [""]}]}
                                            {:tag :tr
                                             :content [{:tag :td :content ["Body"]}
                                                       {:tag :td :content [{:tag :textarea
                                                                            :attrs {:class "body"}
                                                                            :content [""]}]}]}]}]}]}

           (= tag :curl)
           {:tag :div
            :content
            [{:tag :pre
              :content
              [{:tag :code
                :attrs {:class "http"}
                :content
                [(format "curl -i %s%s%s"
                         (apply str (map #(str % " ") (for [[k v] headers] (format "-H '%s: %s'" k v))))
                         (if-not (external? ex) prefix "")
                         url)
                 ]}]}]}

           ;; Raise divs in paragraphs.
           (and (= tag :p) (= (count content) 1) (= (:tag (first content)) :div))
           (first content)

           :otherwise el))
       xml))))

(defn extract-chapters [xml]
  (let [xf (comp (filter #(= (:tag %) :h2)) (mapcat :content))]
    (map str (sequence xf (xml-seq xml)))))

(defn link [r]
  (last (str/split (.getName (type r)) #"\.")))

(defn toc [xml dropno]
  {:tag :ol
   :attrs nil
   :content (vec
             (for [ch (drop dropno (extract-chapters xml))]
               {:tag :li
                :attrs nil
                :content [{:tag :a
                           :attrs {:href (str "#" (chapter ch))}
                           :content [ch]}]}))})

(defn post-process-doc [user-manual xml examples config]
  (postwalk
   (fn [{:keys [tag attrs content] :as el}]
     (cond
       (= tag :h2)
       ;; Add an HTML anchor to each chapter, for hrefs in
       ;; table-of-contents and elsewhere
       {:tag :div
        :attrs {:class "chapter"}
        :content [{:tag :a :attrs {:name (chapter content)} :content []} el]}

       (= tag :example)
       ;; Render the example box
       (let [exname (:ref attrs)]
         (if-let [ex (get examples exname)]
           {:tag :div
            :attrs {:class "example"}
            :example ex ; store the example
            :content
            (concat
             [{:tag :a :attrs {:name (str "example-" exname)} :content []}
              {:tag :h3 :content [(get-title ex)]}]
             (remove nil? [(post-process-example
                            user-manual
                            ex
                            (some-> (format "examples/%s.md" exname)
                                    io/resource slurp md-to-html-string enclose xml-parse)
                            config)]))}
           {:tag :p :content [(str "MISSING EXAMPLE: " exname)]}))

       (= tag :include)
       ;; Include some content
       {:tag :div
        :attrs {:class (:type attrs)}
        :content [{:tag :a :attrs {:name (:ref attrs)} :content []}
                  (some-> (format "includes/%s.md" (:ref attrs))
                          io/resource slurp md-to-html-string enclose xml-parse)]}

       (= tag :toc)
       (toc xml (Integer/parseInt (:drop attrs)))

       (and (= tag :p) (= (count content) 1) (= (:tag (first content)) :div))
       ;; Raise divs in paragraphs.
       (first content)

       (= tag :code)
       (update-in el [:content] (fn [x] (map (fn [y] (if (string? y) (str/trim y) y)) x)))

       :otherwise el))
   xml))

(defn extract-examples [user-manual xml]
  (let [xf (comp (filter #(= (:tag %) :example)) (map :attrs) (map :ref))]
    (map (juxt identity (partial example-instance user-manual)) (sequence xf (xml-seq xml)))))

(defn post-process-body
  "Some whitespace reduction"
  [s replacements]
  (-> s
      (str/replace #"\{\{now.date\}\}" (:now-date replacements))
      (str/replace #"\{\{hello.date\}\}" (:hello-date replacements))
      (str/replace #"\{\{hello.date.after\}\}" (:hello-date-after replacements))
      (str/replace #"\{\{prefix\}\}" (:prefix replacements))
      (str/replace #"\{\{(.+)\}\}" #(System/getProperty (last %)))
      (str/replace #"<p>\s*</p>" "")
      (str/replace #"(yada)(?![-/])" "<span class='yada'>yada</span>")
      ))

(defn body [{:keys [*router templater] :as user-manual} doc replacements]
  (render-template
   templater
   "templates/page.html.mustache"
   {:content
    (-> (with-out-str (emit-element doc))
        (post-process-body replacements)
        )
    :scripts ["/static/js/examples.js"]}))

(defn tests [{:keys [*router templater]} examples]
  (render-template
   templater
   "templates/page.html.mustache"
   {:content
    (let [header [:button.btn.btn-primary {:onClick "testAll()"} "Repeat tests"]]
      (html
       [:body
        [:div#intro
         (md-to-html-string (slurp (io/resource "tests.md")))]
        header
        [:table.table
         [:thead
          [:tr
           [:th "#"]
           [:th "Title"]
           [:th "Expected response"]
           [:th "Status"]
           [:th "Response"]
           [:th "Result"]
           ]]
         [:tbody
          (map-indexed
           (fn [ix [exname ex]]
             (let [url
                   (str
                    (apply path-for @*router (keyword (basename ex)) (get-path-args ex))
                    (when-let [qs (get-query-string ex)] (str "?" qs)))

                   {:keys [method headers data]} (get-request ex)]
               [:tr {:id (str "test-" (link ex))}
                [:td (inc ix)]
                [:td [:a {:href (format "%s#example-%s"
                                        (path-for @*router ::user-manual)
                                        (link ex))}
                      exname]]
                [:td (:status (try (expected-response ex) (catch AbstractMethodError e)))]
                [:td.status ""]
                [:td
                 [:div.headers ""]
                 [:textarea.body ""]]

                [:td.result ""]
                [:td [:button.btn.test
                      {:onClick (format
                                 "testIt('%s','%s','%s',%s,%s,%s)"
                                 (->meth method)
                                 url
                                 (link ex)
                                 (json/encode headers)
                                 (when data (encode-data data (get headers "Content-Type")))
                                 (json/encode (or (try (expected-response ex)
                                                       (catch AbstractMethodError e))
                                                  {:status 200}))
                                 )} "Run"]]]))
           examples)]]]))
    :scripts ["/static/js/tests.js"]}))



(defrecord UserManual [*router templater prefix ext-prefix]
  Lifecycle
  (start [component]
    (infof "Starting user-manual")
    (assert prefix)
    (let [xbody (get-source)
          component (assoc
                     component
                     :start-time (java.util.Date.)
                     :*post-counter (atom 0)
                     :xbody xbody)]
      (assoc component
             :examples (extract-examples component xbody))))
  (stop [component] component)

  RouteProvider
  (routes [component]
    (let [xbody (:xbody component)
          examples (:examples component)
          hello (resource "Hello World!\n")
          hello-atom (resource (atom "Hello World!\n"))
          hello-date (-> hello :resource (res/last-modified nil))
          hello-date-after (java.util.Date/from (.plusMillis (.toInstant hello-date) 2000))]

      ["/"
       [["hello-api" (swaggered {:info {:title "Hello World!"
                                        :version "0.0.1"
                                        :description "Demonstrating yada + swagger"}
                                 :basePath "/hello-api"}
                                ["/hello" hello])]

        ["petstore-simple.json" (resource (json/decode (slurp (io/file "dev/resources/petstore/petstore-simple.json")))
                                          :representations [{:content-type #{"application/json"
                                                                             "text/html;q=0.9"
                                                                             "application/edn;q=0.8"}
                                                             :charset #{"UTF-8"}}])]

        ["hello" hello]
        ["hello-atom" hello-atom]
        ["user-manual"
         [[".html"
           (->
            (let [config {:prefix prefix :ext-prefix ext-prefix}]

              ;; The problem now is that yada knows neither this string's
              ;; content-type (nor its charset), so can't produce the
              ;; correct Content-Type for the response. So we must specify it.

              ;; Given that we are providing a function (which is only
              ;; called relatively late, in the 'fetch' phase of the
              ;; resource/request life-cycle, we must provide the
              ;; negotiation data explicitly as an option.

              ;; NB: The reason we are using a function here, rather than a
              ;; 'static' string, is to ensure template expansion happens
              ;; outside the component's start phase, so that the *router
              ;; is bound, which means we can use path-for to generate
              ;; hrefs.

              ;; Using a function as a resource (to fetch state) is
              ;; expensive in this case - the string is generated from the
              ;; template on every request, even on conditional
              ;; requests. A better implementation is needed in this
              ;; case. But it works OK for the "Hello World!" example. But
              ;; perhaps the use of 'fetch functions' is a placeholder for
              ;; a better design.
              (->
               (resource (fn [ctx]
                           (body component
                                 (post-process-doc component xbody (into {} examples) config)
                                 (assoc config
                                        :hello-date (rt/format-date hello-date)
                                        :hello-date-after (rt/format-date hello-date-after)
                                        :now-date (rt/format-date (java.util.Date.)))))
                         :representations [{:content-type #{"text/html"} :charset #{"utf-8"}}]
                         :last-modified (io/file "dev/resources/user-manual.md"))
               (tag ::user-manual))))]

          ["examples/"
           (vec
            (for [[_ ex] examples]
              [(get-path ex) (->
                              (make-example-handler ex)
                              (tag
                               (keyword (basename ex))))]))]
          ["tests.html"
           (-> (resource (fn [ctx] (tests component examples))
                     :representations [{:content-type #{"text/html"}
                                        :charset #{"utf-8"}}])
               (tag ::tests))]]]]])))

(defmethod clojure.core/print-method UserManual
  [o ^java.io.Writer writer]
  (.write writer "<usermanual>"))

(defn new-user-manual [& {:as opts}]
  (-> (->> opts
           (merge {})
           map->UserManual)
      (using [:templater])
      (co-using [:router])))
