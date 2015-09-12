(ns yada.console.db)

(def default-db
  {
   :cards {:resources
           {:title "Resources"
            :supporting-text "Resources that have been accessed, showing recent requests, state changes."
            :background :#455A64
            :actions ["Show"]}

           :errors
           {:title "Errors"
            :supporting-text "Client and server errors that have been detected."
            :background :#607D8B
            :actions ["Show"]}

           :routes
           {:title "Routes"
            :supporting-text "Routes"
            :background :#212121
            :actions ["Show"]}

           :performance
           {:title "Performance"
            :supporting-text "Statistics gathered to show overall performance, with drill-downs to identify performance issues."
            :background :#CDDC39
            :actions ["Summary" "Detailed"]}

           :documentation
           {:title "Documentation"
            :supporting-text "Help and documentation on yada"
            :background :#727272
            :actions ["Show"]}}

   :active-card {:id nil}})
