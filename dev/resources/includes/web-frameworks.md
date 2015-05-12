yada resource-maps define the behaviour of a RESTful _resource_, and are strictly independent of the identifiers (URIs) used to address them. Therefore, it cannot be said that yada is a _web framework_. Web frameworks usually integrate a particular method for interpretting URIs and dispatching such requests to the relevant resource logic.

In the Clojure tradition, most libraries keep to doing 'one thing well' and are composeable, such that applications can be built from a set of libraries rather than on a single all-encompassing framework.

That said, one of the cornerstones of REST is the use of hyperlinks to
weave a web between resources (see
[hypermedia as the engine of application state](https://en.wikipedia.com/Hateoas)). Therefore
it cannot be accurately claimed that yada, on its own, is a complete
RESTful framework. However, when combined with a library that manages
URIs, the combination can form a powerful solution for building RESTful
services.
