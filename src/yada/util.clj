(ns yada.util
  (:import (java.text SimpleDateFormat)
           (java.util GregorianCalendar TimeZone)))

#_(defn format-http-date [d]
     (let [c (GregorianCalendar/getInstance)
           format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z")]
       (.setTimeZone c (TimeZone/getTimeZone "GMT"))
       (.setTime c d)
       (.setCalendar format c)
       (.format format (.getTime c))))
