In this example we want to show how path parameters can be declared with a type and how they are automatically coerced to that type.

We want __account__ to be extracted as a `java.lang.Long`, and __account-type__ to be extracted as a Clojure keyword.

<handler/>

We form the request to contain 2 path parameters, so the path is of the form .../_account-type_/_account_.

<request/>

<response/>
