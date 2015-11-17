;; Copyright © 2015, JUXT LTD.

(ns selfie.www
  (:require
   [bidi.bidi :refer [path-for]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [yada.protocols :as p]
   [yada.yada :as yada])
  (:import [manifold.stream.core IEventSource]))
