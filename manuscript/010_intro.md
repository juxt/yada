# Introduction

yada is a web library written in Clojure that lets you create websites and web APIs.

It is sufficiently quick-and-easy for quick prototype work but scales up when you need it to, to feature-rich secure services that can handle the most demanding workloads, while remaining faithful to the HTTP standards.

## Say 'Hello!' to yada!

Perhaps the best thing about yada is that the basics are easy to learn.

The obligatory Hello World! example is `(handler "Hello World!")`, which responds with a message.

Perhaps you might want to serve a file? That's `(handler (new java.io.File "index.html"))`.

Now you know how to serve a file, what about that directory full of static resources called `public`? That's `(handler (new java.io.File "public"))`.

Maybe you've got some resources on the classpath? `(handler (clojure.java.io/resource "resources/"))`.

What about `(handler nil)`? Without knowing, can you guess what that might do? (That's right, it produces a `404 Not Found` response).

What about a quick dice generator? `(handler #(inc (rand-int 6)))`. Notice we use a function here, rather than a constant value.

How about streaming those dice rolls as 'Server Sent Events'? Put those dice rolls on a channel, and return it with yada.

All these examples demonstrate the use of Clojure types that are converted on-the-fly into yada resources, and you can create your own types too.

## Resources

Most web libraries use functions to describe the functionality of a web resource. However, in yada, resources are described by a data structure. We call this data structure a __resource-model__.

This has some benefits. While functions are opaque, data is open to inspection. Data structures are easy to generate, transform and query - tasks that Clojure is particularly good at.

Here's an example of a __resource-model__ in Clojure:

```clojure
{:properties {…}
 :methods {:get {:response (fn [ctx] "Hello World!")}
           :put {…}
           :brew {…}}
 …
}
```

You create a __resource__ from a __resource-model__ with yada's `resource` function. This resource is a Clojure record (a Java object) that wraps the raw __resource-model__, having validated against a comprehensive schema first. Since records behave like maps, resources and resource-models are approximately the same thing.

## Handlers

yada's `handler` takes a single parameter (the resource) and returns a __handler__.

```clojure
(require '[yada.yada :refer [handler resource]])

(handler (resource {…}))
```

A handler can be called like a function, with a single argument representing an HTTP __request__. It returns a value representing the corresponding HTTP response. (If you are familiar with Ring, this is the Ring handler, but not one you have to write yourself!)

When given the HTTP __request__, the handler first creates a __request-context__ and populates it with various values, such as the request and the __resource-model__ that corresponds to the request's URI.

The handler then threads the __request-context__ through a chain of functions, called the __interceptor-chain__. This 'chain' is just a list of functions specified in the __resource-model__ that has been carefully crafted to generate a response that complies fully with HTTP standards. However, as with anything in the resource-model, you can choose to modify it to your exact requirements if necessary.

As an aside, the functions making up the interceptor-chain are not necessarily executed in a single thread but rather an asynchronous event-driven implementation enabled by a third-party library called manifold.

To use yada to create real responses to real HTTP requests, you need to add yada to a web-server, such as Aleph or Immutant. The web server takes care of the networking and messages of HTTP (RFC 7230), while yada focuses on the semantics and content (starting with RFC 7231). To write real applications, you also need a router that understands URIs, and yada has some features that are enabled when used with bidi, although there is nothing to stop you using yada with other routing libraries.

## yada is a library, not a framework

However, yada is _not_ a 'web application framework' because it is not concerned with how your application stores or computes state. It is solely concerned with exchanging that state with other agents on the web, over HTTP.

To build a full application serving web-clients you would need to add some application logic, somewhere to store your application's state and a way to retrieve it. Probably, you'll also need a templating library if you want to generate complex HTML, because yada doesn't care about that either.

## Why?

But why might you want to use yada rather than implement your own Ring handler in Clojure?

We took a normal Ring handler, injected it with a mix of radioactive isotopes stolen from the [same secret Soviet atomic research project as ØMQ](http://zguide.zeromq.org/page:all) and bombarded it with near-identical 1950-era cosmic rays, while injecting into it a potent cocktail of powerful steroids. As a result, yada's handlers are much more than your average Ring handler, and the next chapter should give you a glimpse of what such a handler is capable of.
