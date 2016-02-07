# Resources

Resources are defined by __resource models__, which are just data, and
yada's defining feature.

Resource models should be defined ahead-of-time wherever possible,
before the server starts listening for HTTP requests. Doing so will
expose the resource model to tooling. However, you are free to create
them dynamically on every request if necessary.

Once complete, resource models are used to create request handlers,
which serve HTTP requests targeting the resource.

## Writing resource models

In Clojure, resource models are usually defined as map literals, but
naturally they can be derived programmatically from other sources.

Internally, resource models are defined by a strict schema, which
ensures that they are properly specified before they can be turned
into request handlers.

```clojure
(require '[yada.yada :refer [resource]])

(def my-web-resource
  (resource
    {:id …
     :description …
     :summary …
     :parameters {…}
     :produces {…}
     :consumes {…}
     :authentication {…}
     :cors {…}
     :properties {…}
     :methods {…}
     :custom/other {…}}))
```

To create a resource from a map, call the `yada.resource` function
with the map as the single argument. Since the `yada.resource`
function checks the map conforms to the resource-model schema, it is
recommended to build your map fully _before_ calling the
`yada.resource` function, rather than building the resource and then
modifying it further. The latter is possible, but you may risk
creating a model that is no longer valid.

## Data abbreviations

Parts of the canonical resource model structure can be quite
verbose. To make the job of authoring resource models easier a variety
of literal short-hand forms are available. Short-forms are
automatically coerced to their canonical equivalents, prior to
building the request handler.

For example:

```clojure
{:produces "text/html"}
```

is automatically coerced to this _canonical_ form:

```clojure
{:produces [{:media-type "text/html"}]}
```

## Common examples

There are numerous other short-hands. If in doubt, learn the canonical
form and use that until you discover the short-hand for it. You can
experiment with the `yada.resource` function ahead of time in the
REPL. If you do something that isn't possible, the schema validation
errors should help you figure out why.

[insert table of common coercions here]

## Resource types

A __resource type__ is a Clojure types or record that can be
automatically coerced into a __resource model__. These types satisfy
the `yada.protocols.ResourceCoercion` protocol, and any existing type
or record may be extended to do so, using Clojure's `extend-protocol`
macro.

```clojure
(extend-type datomic.api.Database
  yada.protocols/ResourceCoercion
  (as-resource [_]
    (resource
      {:properties
        {:last-modified …}
       :methods
        {:get …}}})))
```

The `as-resource` function must return a resource (by calling
`yada.resource`, not just a map).

## Summary

The resource model is yada's central concept. The following chapters
describe the various aspects of the resource-model in more detail.
