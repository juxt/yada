(ns yada.resources.webjar-resource
  (:import [org.webjars WebJarAssetLocator])
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [yada.resource :refer [resource as-resource]]
    [yada.resources.classpath-resource :refer [new-classpath-resource]]
    [yada.test :as test]))

(def ^:private webjars-pattern
  #"META-INF/resources/webjars/([^/]+)/([^/]+)/(.*)")

(defn- asset-path [resource]
  (let [[_ name version path] (re-matches webjars-pattern resource)]
    path))

(defn- asset-map [^WebJarAssetLocator locator webjar]
  (->> (.listAssets locator (str webjar "/"))
       (map (juxt asset-path identity))
       (into {})))

(defn new-webjar-resource
  "Create a new webjar resource that resolves requests with
   path info in the specified webjar name in the active classpath.

   Optionally takes an options map with the following keys:

   * index-files - a collection of files to try if the path info
                   ends with /

   Example:

      (new-webjar-resource \"swagger-ui\")

   would resolve index.html to META-INF/resources/webjars/swagger-ui//index.html inside
   the active classpath (e.g. of the JAR that serves the resource).

   If used with bidi, the following route

     [\"\" (yada (new-webjar-resource
                \"swagger-ui\" {:index-files [\"index.html\"]}))]

   can be used to serve all files for the swagger-ui webjar and fall back to
   index.html for URL paths like / or foo/."
  ([webjar]
   (new-webjar-resource webjar nil))
  ([webjar {:keys [index-files]}]
   (resource
     {:path-info?   true
      :methods      {}
      :sub-resource (let [assets (asset-map (WebJarAssetLocator.) webjar)]
                      (fn [ctx]
                        (let [path-info (-> ctx :request :path-info)
                              path (str/replace path-info #"^/" "")
                              files (if (= (last path-info) \/)
                                      (map #(get assets (str path %)) index-files)
                                      (list (get assets path)))
                              res (first (sequence (comp (drop-while nil?)
                                                         (map io/resource))
                                                   files))]
                          (as-resource res))))})))

(defn webjars-route-pair
  "Creates webjar resources for all webjars on the active classpath.

  An options map can be provided and will be passed on to the
  new-webjar-resource call for every webjar.

  Each webjar will be put on a path corresponding to its name. For
  example, a webjar called \"bootstrap\" will be accessible from the
  \"/bootstrap/\" path."
  ([] (webjars-route-pair nil))
  ([options]
   (let [webjars (->> (WebJarAssetLocator.)
                      .getWebJars
                      keys
                      (map (juxt identity #(new-webjar-resource % options))))]
     ["" webjars])))
