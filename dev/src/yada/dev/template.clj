;; Copyright © 2015, JUXT LTD.

(ns yada.dev.template
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [clj-time.core :refer [now]]
   [ring.util.mime-type :refer (ext-mime-type)]
   [yada.protocols :as p]
   [clojure.java.io :as io]
   [modular.template :refer (render-template)]
   [stencil.core :as stencil]
   [stencil.loader :as loader]
   [yada.methods :as m]
   [yada.util :refer [as-file]]))

(defrecord TemplateResource [template-name model *template]
  Lifecycle
  (start [component] (assoc component :*template (atom nil)))
  (stop [component] component)

  p/ResourceProperties
  (resource-properties
    [_]
    (let [resource (loader/find-file template-name)]
      {:allowed-methods #{:get}
       :representations [{:media-type (or (ext-mime-type template-name)
                                          "application/octet-stream"
                                          )}]
       ::resource resource
       ::file (as-file resource)}))
  (resource-properties
    [_ ctx])

  m/Get
  (GET [_ ctx]
    (let [props (:resource-properties ctx)
          template @*template]
      (when (or (nil? template)
                (when-let [f (::file props)]
                  (when-let [t (.getMillis (:date template))]
                    (< t (.lastModified f)))))

        (reset! *template
                {:date (now)
                 :parsed-template (when-let [res (::resource props)] (stencil.parser/parse (slurp res)))}))
      (when-let [pt (:parsed-template @*template)]
        (stencil/render pt (if (fn? model) (model ctx) model))
        ;; Returning nil causes a 404
        ))))

(defn new-template-resource [template model]
  (->TemplateResource template model (atom nil)))



(.lastModified (io/file "README.md"))
