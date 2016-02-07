# Introduction

yada is a library that lets you create stateful entities on the
web. These stateful entities are called _web resources_ and each one
is identified by a unique __URI__. With yada you can create web
resources that are fully compliant with, and thereby take full
advantage of, HTTP standards.

yada strives to be a complete implementation of all of HTTP, which is
a very rare thing indeed. Therefore, it can be used to serve static
content, generate web pages and expose web APIs.

But yada is _not_ a 'web application framework' because it is
not concerned with how your application stores or computes state, it
is solely concerned with exchanging that state with other agents on
the web, over HTTP.

To build a full application serving web-clients you would need to add
some application logic, somewhere to store your application's state
and a way to retrieve it. Probably, you'll also need a templating
library if you want to generate complex HTML, because yada doesn't
care about that either.

yada is implemented in the Clojure programming language and runs on the JVM.

## Resources

In yada, resources are defined by a __resource-model__, which can be
authored in a declarative syntax or generated programmatically.

Here's an example of the declarative syntax of a typical
__resource-model__ in Clojure:

```clojure
{:properties {…}
 :methods {:get {:response (fn [ctx] "Hello World!")}
           :put {…}
           :brew {…}}
 …
}
```

You can also use Java or any JVM language to create these __resource-models__.

In Clojure, you can create a __resource__ from a __resource-model__
with yada's `resource` function. This resource is a Clojure record (a
Java object) that wraps the raw __resource-model__, having validated
against a comprehensive schema first.

## Handlers

yada's eponymous function `yada` takes a single parameter (the
resource) and returns a __handler__.

```clojure
(require '[yada.yada :refer [yada resource]])

(yada (resource {…}))
```

A handler can be called like a function, with a single argument
representing an HTTP __request__. It returns a value representing the
corresponding HTTP response. (If you are familiar with Ring, this is
the Ring handler, but not one you have to write yourself!)

When given the HTTP __request__, the handler first creates a
__request-context__ and populates it with various values, such as the
request and the __resource-model__ that corresponds to the request's
URI.

The handler then threads the __request-context__ through a chain of
functions, called the __interceptor-chain__. This 'chain' is just a
list of functions specified in the __resource-model__ that has been
carefully crafted to generate a response that complies fully with HTTP
standards. However, as with anything in the resource-model, you can
choose to modify it to your exact requirements if necessary.

As an aside, the functions making up the interceptor-chain are not
necessarily executed in a single thread but rather an asynchronous
event-driven implementation enabled by a third-party library called
manifold.

To use yada to create real responses to real HTTP requests, you need
to add yada to a web-server, such as Aleph or Immutant. The web server
takes care of the networking and messages of HTTP (RFC 7230), while
yada focuses on the semantics and content (starting with RFC 7231). To
write real applications, you also need a router that understands URIs,
and yada has some features that are enabled when used with bidi,
although there is nothing to stop you using yada with other routing
libraries.

## Why?

But why might you want to use yada rather than implement your own Ring
handler in Clojure?

We took a normal Ring handler, injected it with a mix of radioactive
isotopes stolen from the
[same secret Soviet atomic research project as ØMQ](http://zguide.zeromq.org/page:all)
and bombarded it with near-identical 1950-era cosmic rays, while
injecting into it a potent cocktail of powerful steroids. As a result,
yada's handlers are much more than your average Ring handler, and the
next chapter should give you a glimpse of what such a handler is
capable of.
