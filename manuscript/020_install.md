# Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "1.1.14"]

[aleph "0.4.1"]
[bidi "2.0.0"]
```

## Setting up a development environment

The best way to learn is to experiment with yada, either on its own or by using it to build some service you have in mind. Either way you'll want to set up a quick development environment.

## The Easy Way: Clone edge

The quickest and perhaps easiest way to get started is to clone the yada branch of JUXT's __edge__ repository. This is a continuously improving development environment representing our firm's best advice for building Clojure projects.

    git clone git@github.com/juxt/edge

__edge__ is opinionated and combines a number of practices that we've found useful on many projects at JUXT.

If you're new to Clojure, we recommend learning yada with edge. Not only is it packed full of examples for you to explore, the project can provide a solid foundation for your own projects with an architecture that is proven to scale as your project grows.

## The Simple Way: Construct your own

If you prefer to build your own development environment you'll need a few pointers in order to integrate with yada.

### Serving resources

To serve the resources you define with yada you'll need to choose a port and start a web server. Currently, yada provides its own built-in web server ([Aleph](https://github.com/ztellman/aleph)), but in the future other web servers will be supported. (For now, please be reassured that Aleph will support many thousands of concurrent connections without breaking a sweat. Aleph is built on Netty, a very capable platform used by Google, Apple, Facebook and many others to support extreme workloads.)

To start a web-server, just require `server` from `yada.yada` and call it with some resources and some configuration that includes the port number.

    (require '[yada.yada :refer [server handler routes resource]])

    (def svr
      (server
        (routes
          ["/"
            [
              ["hello" (as-resource "Hello World!")]
              ["test" {:produces "text/plain"
                       :response "This is a test!"}]]])
        {:port 3000}))

The `routes` function expects a [bidi](https://github.com/juxt/bidi) route structure but interprets Clojure maps to be yada resource models.

The server can be shutdown by calling `.close`

    (.close svr)

### Creating resources

Resources can be created in the following ways:

- From a resource model, calling `resource` with a map argument
- Using `as-resource` on an existing type known to yada
- A normal Ring handler, or one created from a resource with yada's `handler` function.

### Routing to resources

A yada handler created by yada's `handler` function can be used anywhere a Ring-compatible function is expected. This way, you can use any routing library you wish, including Compojure.

However, yada and bidi _are_ designed to work together, and there are a number of features that are enabled when they are used together.

## REPL and Testing

The `response-for` function is great for testing the Ring response that would be returned from a yada type or handler.

    (require '[yada.yada :refer [response-for]])

    (response-for "Hello World!")
