# Methods

Methods are defined in the __methods__ entry of a resource's
__resource-model__.

Only methods that are known to yada can appear in a resource's
definition. Each method corresponds to a type that extends the
`yada.methods.Method` protocol. This design also makes it possible to
add new methods to yada, as required.

The responsibility of each method type is to encode the semantics of
the corresponding method as it is defined in HTTP standards, such as
responding with the correct HTTP status codes (most other web
frameworks delegate this responsibility to developers).

Some methods can be implemented entirely by yada itself (HEAD,
OPTIONS, TRACE etc.). Most methods, however, delegate to some function
or functions declared in the method's declaration in the
__resource-model__.

## Method semantics, by method

Each HTTP method has defined semantics. Often these semantics are
defined in the HTTP standards, other RFCs or, in the case of custom
methods, by you.

These semantics are important because they allow other web agents,
such as browsers and proxies, to inter-operate with your site.

Below is an explanation of the semantics for every method yada
currently supports and the your responsibilities should you choose to
provide the method for a resource.

## GET

Specify a function in __:response__ that will be called during the GET
method processing.

If the resource exists, the single-arity function will be called with the request
context as its only argument.

It should return the response's body, which should satisfy
`yada.body.MessageBody` determining how exactly the response's body
will be returned.

## PUT

[coming soon]

## POST

[coming soon]

## DELETE

[coming soon]

## HEAD

[coming soon]

## OPTIONS

[coming soon]

## TRACE

[coming soon]

## PATCH

[coming soon]

## Handling all methods

If you want to handle all request methods, or a complex set of them,
you can specify the special keyword `:*` in the methods section of
your resource model.

```clojure
{:methods
  {:*
    {:response (fn [ctx] …)}}}
```

## Custom methods

Custom methods can be added by defining new types that extend the
`yada.methods.Method` protocol.

## BREW

BREW is an example of a custom method you might want to create,
especially if you are building a networked coffee maker compliant with
RFC.

```clojure
(require '[yada.methods Method])

(deftype BrewMethod [])

(extend-protocol Method
  BrewMethod
  (keyword-binding [_] :brew)
  (safe? [_] false)
  (idempotent? [_] false)
  (request [this ctx] …))
```
