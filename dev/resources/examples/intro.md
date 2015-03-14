# Examples

These examples form a learning aid, reference and cookbook for how to achieve common goals when creating web APIs with yada.

Before we begin, we should define a few terms.

## Resource maps

Readers who know Liberator will be familiar with the concept of a
resource map. A resource map defines how a resource handler should
behave.

```clojure
(def resource
  {:allowed-method? #{:get}
   :body "Hello World!"})
```

A Ring handler is a function that takes a request map and returns a
response map. Many Clojure developers write their own Ring handlers from
scratch, but with yada, the Ring handler is created from a resource map
by a yada function. Currently, yada contains a single function
(`yada.core/make-async-handler`) but it is envisaged that more will be
provided, offering tailored functionality.

```clojure
(require 'yada.core)

(def handler
  (yada.core/make-async-handler resource))

(handler {:uri "/foo" :request-method :get})
=>
{:status 200
 :headers {"content-type" "text/html;charset=utf-8"}
 :body "Hello World!"}

```

A resource map is just a normal Clojure map, and as such can be
generated and otherwise composed prior to the formation of a Ring
handler.

## Routing

Resources should be independent from URI routing. However, in REST services, representations should contain hyperlinks to other resources, as URIs.

Due to the importance of creating links to resources that don't break
when the resource is relocated to another URI, it is better to use yada
with a routing library that features support for creating links from
resources. Core yada functionality is supported for us with any routing
library, but there are some extra features available when yada is
combined with [bidi](https://github.com/juxt/bidi).

## Examples

Now follow the examples. Use the menu for quick access to individual examples. To run all the examples together, see the [tests page](tests.html).
