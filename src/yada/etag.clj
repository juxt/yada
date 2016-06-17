(ns yada.etag
  (:require
   [yada.util :refer [md5-hash]]))

(defprotocol ETag
  "The version function returns material that becomes the ETag response
  header. This is left open for extension. The ETag must differ between
  representations, so the representation is given in order to be used in
  the algorithm. Must always return a string (to aid comparison with the
  strings the client will present on If-Match, If-None-Match."
  (to-etag [_ rep]))

(extend-protocol ETag
  Object
  (to-etag [o rep]
    (md5-hash (str (hash {:value o :representation rep}))))
  nil
  (to-etag [o rep] nil))
