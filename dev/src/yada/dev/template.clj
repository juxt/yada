;; Copyright © 2015, JUXT LTD.

(ns yada.dev.template
  (:require
   [clj-time.core :refer [now]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [modular.template :refer [render-template]]
   [ring.util.mime-type :refer (ext-mime-type)]
   [schema.core :as s]
   [stencil.core :as stencil]
   [stencil.loader :as loader]
   [yada.charset :as charset]
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.resource :refer [new-custom-resource]]
   [yada.util :refer [as-file]]))

(s/defrecord TemplateResource
    [template-name :- String
     model :- (s/either
               {s/Keyword s/Any}
               (s/pred delay?)
               (s/=> {s/Keyword s/Any} s/Any))
     *template]
  p/Properties
  (properties
   [_]
   (let [resource (loader/find-file template-name)]
     {:allowed-methods #{:get}
      ;; We can work out last-modified, iff model is not a function
      ;; then last-modified date is now
      }))
  (properties
   [_ ctx]
   ;; TODO: What about a delay?
   (when-not (fn? model)
     {:last-modified (max (-> ctx :properties ::file .lastModified)
                          (-> ctx :properties ::create-time))})))

(defn new-template-resource
  "Create a yada resource that uses Stencil to server the template. The
  model can be a value, delayed value or a 1-arity function taking the
  yada request context. Stencil functions are used to locate and parse
  the Mustache template, but Stencil's own cacheing is avoided. The
  resource ensures that changes to the template source ensure that the
  template is reloaded and parsed. Also, last-modified-time is computed
  if possible."
  [template-name model]
  (let [*template (atom nil)
        resource (loader/find-file template-name)
        file (as-file resource)
        create-time (.getMillis (now))]

    (new-custom-resource
     {:methods
      {:get
       {:produces
        [{:media-type (or (ext-mime-type template-name)
                          "application/octet-stream"
                          )
          ;; Since stencil produces strings, we can
          ;; specify the encoding and yada's
          ;; representation logic takes care of the
          ;; encoding.

          ;; TODO: Is this necessary? Doesn't yada
          ;; default to this anyway?
          :charset charset/default-platform-charset}]

        :handler
        (fn [ctx]
          (let [props (:properties ctx)
                template @*template]

            (when (or (nil? template)
                      (when-let [f file]
                        (when-let [t (.getMillis (:date template))]
                          (< t (.lastModified f)))))
              (reset! *template
                      {:date (now)
                       :parsed-template (stencil.parser/parse (slurp resource))}))

            (when-let [pt (:parsed-template @*template)]
              (stencil/render pt (cond (fn? model) (model ctx)
                                       (delay? model) @model
                                       :otherwise model))
              ;; Returning nil causes a 404
              )))}}})))
