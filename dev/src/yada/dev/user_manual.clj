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
   [hiccup.page :refer (html5)]
   [markdown.core :refer (md-to-html-string)]
   [modular.bidi :refer (path-for)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [yada.yada :as yada :refer [yada]]
   [yada.swagger :refer [swaggered]]
   [yada.dev.template :refer [new-template-resource]]
   [ring.util.time :as rt]
   [schema.core :as s]
   yada.resources.string-resource
   yada.resources.atom-resource)
  (:import [modular.bidi Router]))

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

(defn post-process-doc [user-manual xml]
  (postwalk
   (fn [{:keys [tag attrs content] :as el}]
     (cond
       (= tag :h2)
       ;; Add an HTML anchor to each chapter, for hrefs in
       ;; table-of-contents and elsewhere
       {:tag :div
        :attrs {:class "chapter"}
        :content [{:tag :a :attrs {:name (chapter content)} :content []} el]}

       (= tag :h3)
       {:tag :div
        :attrs {:class "chapter"}
        :content [{:tag :a :attrs (if-not (re-matches #".*yada.*" (chapter content))
                                    {:name (chapter content)} {}) :content []} el]}

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

(defn post-process-body
  "Some whitespace reduction"
  [s replacements]
  (-> s
      (str/replace #"(yada)(?![-/\.])" "<span class='yada'>yada</span>")
      (str/replace "http://localhost:8090" (:prefix replacements))
      ))

(defn body [{:keys [*router] :as user-manual} doc replacements]
  {:homeref (path-for @*router ::yada.dev.docsite/index)
   :content
   (-> (with-out-str (emit-element doc))
       (post-process-body replacements)
       )
   :scripts []})

(s/defrecord UserManual [*router :- (co-dep Router)
                         prefix :- s/Str
                         start-time :- java.util.Date
                         *post-counter
                         xbody]
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
      component))
  (stop [component] component)

  RouteProvider
  (routes [component]
    (let [xbody (:xbody component)]
      ["/"
       [

        #_["petstore-simple.json" (yada/resource (json/decode (slurp (io/file "dev/resources/petstore/petstore-simple.json")))
                                                 {:representations [{:media-type #{"application/json"
                                                                                   "text/html;q=0.9"
                                                                                   "application/edn;q=0.8"}
                                                                     :charset #{"UTF-8"}}]})]

        ["user-manual"
         [[".html"
           (->
            (let [replacements {:prefix prefix}]

              ;; The problem now is that yada knows neither this string's
              ;; content-type (nor its charset), so can't produce the
              ;; correct Content-Type for the response. So we must specify it.

              ;; Given that we are providing a function (which is only
              ;; called relatively late, in the 'fetch' phase of the
              ;; resource/request life-cycle, we must provide the
              ;; negotiation data explicitly as an option.

              ;; NB: The reason we are using a delay here, rather than a
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
              (yada (-> (new-template-resource
                         "templates/page.html"
                         (delay (body component
                                      (post-process-doc component xbody)
                                      replacements)))
                        (assoc :id ::user-manual)))))]]]]])))

(defmethod clojure.core/print-method UserManual
  [o ^java.io.Writer writer]
  (.write writer "<usermanual>"))

(defn new-user-manual [& {:as opts}]
  (map->UserManual opts))
