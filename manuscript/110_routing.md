# Routing

Since the `yada` function returns a Ring-compatible handler, it is
compatible with any Clojure URI router.

However, yada is designed to work especially well with its sister
library [bidi](https://github.com/juxt/bidi), and unless you have a
strong reason to use an alternative routing library, you should stay
with the default.

While yada is concerned with semantics of how a resource responds to
requests, bidi is concerned with the identification of these
resources. In the web, identification of resources is a first-class
concept. Each resource on the web is uniquely identified with a Uniform
Resource Identifier (URI).

No resource is an island, and it is common that resource representations
need to embed references to other resources. This is true both for
ad-hoc web applications and 'hypermedia APIs', where the client
traverses the application via a series of hyperlinks.

These days, hyperlinks are so critical to the reliable operation of
systems that it is no longer satisfactory to rely on ad-hoc means of
constructing these URIs, they must be generated from the same tree of
data that defines the API route structure.

## Declaring your website or API as a bidi/yada tree

A bidi routing model is a hierarchical pattern-matching tree. The tree
is made up of pairs, which tie a pattern to a (resource) handler (or a
further group of pairs, recursively).

Both bidi's route models and and yada's resource models are recursive
data structures and can be composed together.

The end result might be a large and deeply nested tree, but one that
can be manipulated, stored, serialized, distributed and otherwise
processed as a single dataset.

```
(require
  [yada.yada :refer [resource]]
  [hiccup.core :refer [html]]
  [clojure.java.io :refer [file])

;; Our store's API
["/store/"
 [ ; Vector containing our store's routes (bidi)
  ["index.html"
   (resource ; (everything under this is now yada)
    {:summary "A list of the products we sell"
     :methods
     {:get
      {:response (file "index.html")
       :produces "text/html"}}})]
  ["cart"
   (resource
    {:summary "Our visitor's shopping cart"
    :methods
    {:get
     {:response (fn [ctx] …)
      :produces #{"text/html" "application/json"}}
     :post
     {:response (fn [ctx] …)
      :produces #{"text/html" "application/json"}}}}]
  …
 ]]
```

A yada handler (created by yada's `yada` function) and a yada resource
(created by yada's `resource` constructor function) extends bidi's
`Matched` protocol are both able to participant in the pattern
matching of an incoming request's URI.

For a more thorough introduction to bidi, see
[https://github.com/juxt/bidi/README.md](https://github.com/juxt/bidi/README.md).

## Declaring policies across multiple resources

Many web frameworks allow you to set a particular behavioral policy
(such as security) across a set of resources by specifying it within
the routing mechanism.

In our view, this is wrong, for many reasons. A URI is purely a
identifier for a resource. A resource's identifier might change, but
such a change should not cause the resource to bahave differently.

In the phraseology offered by Rich Hickey in his famous Simple Not
Easy talk, we should not _complect_ a resource's identification with
its operation. Neither should we _complect_ a protection space with a
URI 'space'. Doing so reduces adds an unnecessary constraint to the
already difficult problem of naming things (URIs) which adding an
unnecessary constraint to the ring-facing of resources into protection
spaces. For this reason, yada and bidi are kept apart as separate
libraries.

Some web frameworks can be excused for offering a pragmatic way
of reducing duplication in specification, but this really ought not to
be necessary for Clojure programmers which have powerful alternatives.

What are these alternatives? How can we avoid typing the same
declarations over and over in every resource?

One option is to create a function that can augment a set of base
resource models with policies. That function can then be mapped over a
number of resources.

A variation of this option is to use Clojure's built-in tree-walking
functions such as `clojure.walk/postwalk`. If you specify your entire
API has a single bidi/yada tree it is easy to specify each policy as a
transformation from one version of the tree to another. What's more,
you will be able to check, debug and automate testing on the end
result prior to handling actual requests.
