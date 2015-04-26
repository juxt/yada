(ns yada.coerce
  (:require
   [schema.coerce :refer (string-coercion-matcher)]
   [clj-time.coerce :as time]
   [schema.core :as s]))

(def +date-coercions+
  {s/Inst (comp time/to-date time/from-string)})

(defn coercion-matcher [schema]
  (or (string-coercion-matcher schema)
      (+date-coercions+ schema)))
