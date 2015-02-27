(ns yada.util
  (:import (java.text SimpleDateFormat)
           (java.util GregorianCalendar TimeZone)))

(defn create-date-format
  []
  (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z"))

(defn format-http-date [d]
  (when d
    (let [c (GregorianCalendar/getInstance)
          format (create-date-format)]
      (.setTimeZone c (TimeZone/getTimeZone "GMT"))
      (.setTime c d)
      (.setCalendar format c)
      (.format format (.getTime c)))))

(defn parse-http-date [s]
  (when s
    (.parse (create-date-format) s)))
