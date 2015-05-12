Since bidi's route-structure and yada's resource-maps are normal Clojure data structures, it is relatively straight-forward to transform the data into a Swagger 2.0 specification. This is exactly what the `Swagger` record does, and publishes the generated specification for use by tools such as _swagger-ui_.

If you have clone the yada repository you can see the code in `src/yada/swagger.clj`.
