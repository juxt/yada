;; Copyright © 2015, JUXT LTD.

(ns yada.dev.template
  (:require
   [clj-time.core :refer [now]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [modular.template :refer [render-template]]
   [ring.util.mime-type :refer (ext-mime-type)]
   [stencil.core :as stencil]
   [stencil.loader :as loader]
   [yada.charset :as charset]
   [yada.methods :as m]
   [yada.protocols :as p]
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
                                          )
                          ;; Since stencil produces strings, we can
                          ;; specify the encoding and yada's
                          ;; representation logic takes care of the
                          ;; encoding.
                          :charset charset/default-platform-charset}]

       ;; We can work out last-modified, iff model is not a function
       ;; then last-modified date is now

       ::create-time (.getMillis (now))
       ::resource resource
       ::file (as-file resource)}))
  (resource-properties
    [_ ctx]
    (when-not (fn? model)
      {:last-modified (max (-> ctx :resource-properties ::file .lastModified)
                           (-> ctx :resource-properties ::create-time))}))

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
        (stencil/render pt (cond (fn? model) (model ctx)
                                 (delay? model) @model
                                 :otherwise model))
        ;; Returning nil causes a 404
        ))))

(defn new-template-resource [template model]
  (->TemplateResource template model (atom nil)))
