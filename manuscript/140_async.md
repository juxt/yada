# Async

Under normal circumstances, with Clojure running on a JVM, each request can be processed by a separate thread.

However, sometimes the production of the response body involves making requests
to data-sources and other activities which may be _I/O-bound_. This means the thread handling the request has to block, waiting on the data to arrive from the I/O system.

For heavily loaded or high-throughput web APIs, this is an inefficient
use of resources. Today, this problem is addressed by asynchronous I/O
programming models. The request thread is able to make a request for
data via I/O, and then is free to carry out further work (such as
processing another web request). When the data requested arrives on the
I/O channel, a potentially different thread carries on processing the
original request.

As a developer, yada gives you fine-grained control over when to use a
synchronous programming model and when to use an asynchronous one.

## Deferred values

A deferred value is simply a value that may not yet be known. Examples
include Clojure's futures, delays and promises. Deferred values are
built into yada — for further details, see Zach Tellman's
[manifold](https://github.com/ztellman/manifold) library.

In almost all cases, it is possible to return a _deferred value_ from
any of the functions that make up our resource record or handler options.

For example, suppose our resource retrieves its state from another internal web API. This would be a common pattern with µ-services. Let's assume you are using an HTTP client library that is asynchronous, and requires you provide a _callback_ function that will be called when the HTTP response is ready.

On sending the request, we could create a promise which is given to the callback function and returned to yada as the return value of our function.

Some time later the callback function will be called, and its implementation will _deliver_ the promise with the response value. That will cause the continuation of processing of the original request. Note that _at no time is any thread blocked by I/O_.

![Async](hello-async.png)

Actually, if we use Aleph or http-kit as our HTTP client the code is
even simpler, since both libraries return promises from their request
functions.

```clojure
(require '[yada.yada :refer [resource]]
         'aleph.http :refer [get])

(resource
  {:methods
    {:get (fn [ctx] (get "http://users.example.org"))}})
```

In a real-world application, the ability to
use an asynchronous model is very useful for techniques to improve
scalability. For example, in a heavily loaded server, I/O operations can
be queued and batched together. Performance may be slightly worse for
each individual request, but the overall throughput of the web server
can be significantly improved.

Normally, exploiting asynchronous operations in handling web requests is
difficult and requires advanced knowledge of asynchronous programming
techniques. In yada, however, it's easy, thanks to manifold.

For a fuller explanation as to why asynchronous programming models are
beneficial, see the [Ratpack](http://ratpack.io) documentation. (Note
that yada provides all the features of Ratpack and more).
