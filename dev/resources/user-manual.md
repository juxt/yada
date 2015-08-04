# The yada manual

Welcome to the yada manual!

This manual corresponds with version {{yada.version}}.

### Table of Contents

<toc drop="0"/>

### Audience

This manual is the authoritative documentation to yada. As such, it is
intended for a wide audience of developers, from beginners to experts.

### Get involved

If you are a more experienced Clojure or REST developer and would like
to get involved in influencing yada's future, please join our
[yada-discuss](https://groups.google.com/forum/#!forum/yada-discuss)
discussion group. Regardless of your experience, everyone is more than
welcome to join the list. List members will do their best to answer any
questions you might have.

### Spot an error?

If you spot a typo, misspelling, grammar problem, confusing text, or
anything you feel you could improve, please go-ahead and
[edit the source](https://github.com/juxt/yada/edit/master/dev/resources/user-manual.md). If
your contribution is accepted you will have our eternal gratitude, help
future readers and be forever acknowledged in the yada documentation as
a contributor!

[Back to index](/)

## Forward

State is everywhere. The world is moving and we need to keep up. We need our computers to do the same, keeping up-to-date with the latest information, trends, stock-prices, news and weather updates, and other important 'stuff'.

The web is primarily a means to move state around. You have some state
here, and you want it over there. Or it's over there, but you want it
over here.

For two decades or more, the pre-dominant model for web programming has ignored state, instead requiring developers to work at the level of the HTTP protocol itself.

For example, in Java...

```java
public void handleRequest(HttpServletRequest request,
                          HttpServletResponse response)
{
    response.setStatus(200);
}
```

or in Clojure

```clojure
(fn [request] {:status 200})
```

This programming model puts the HTTP request and response at centre
stage. The concept of state is missing entirely - the resource is seen
merely as an _operation_ (or set of operations) available for remote
invocation.

For years, the same RPC-centered approach has been copied by web
frameworks in most other languages, old and new (Python, Ruby, Go,
Clojure...). It has survived because it is so flexible, as most
low-level programming models are.

But there are significant downsides to this model too. HTTP is a big
specification, and it is unreasonable to expect developers to have the
time to implement all the relevant pieces of it. What's more, many
developers tend to implement much the same code over and over again, for
each and every 'operation' they write.

A notable variation on this programming model can be found in Erlang's
WebMachine and Clojure's Liberator. To a degree, these libraries ease
the burden on the developer by orchestrating the steps required to build
a response to a web request. However, developers are still required to
understand the state transition diagram underlying this orchestration if
they are to successfully exploit these libraries to the maximum
extent. Fundamentally, the programming model is the same: the developer
is still writing code with a view to forming a response at the protocol
level.

While this model has served as well in the past, there are increasingly
important reasons why we need an upgrade. Rather than mere playthings,
HTTP-based APIs are becoming critical components in virtually every
organisation. With supporting infrastructure such as proxies, API
gateways and monitoring, there has never been a greater need to improve
compatibility through better conformance with HTTP standards. Yet many
APIs today at best ignore, and worst violate many parts of the HTTP
standard.

It is time for a fresh approach. We need our libraries to do more work
for us. For this to happen, we need to move from the _de-facto_
'operational' view of web 'services' to a strong _data-oriented_
approach, focussing on what a web _resource_ is really about: _state_.

## Introduction

### What is yada?

yada is a Clojure library that lets you expose state to the web over
HTTP. But many libraries and 'web framworks' let you do that. What makes
yada different is the _programming model_ it offers developers, one that
is based on state rather than the HTTP protocol itself.

This approach has a number of advantages. Many things you would expect
to have to code yourself and taken care of automatically, leaving you
time to focus on other aspects of your application. And you end up with
far less networking code to write and maintain.

yada is built on a fully asynchronous foundation, allowing you to
exploit the asynchronous features of modern web servers, to achieve
greater scaleability for Clojure-powered your websites and APIs.

Above all, yada is data-centric, letting you specify your web resources
as data. This has some compelling advantages, such as being able to
transform that data into other formats, such as
[Swagger](http://swagger.io) specifications for API documentation.

### What yada is not

yada is not a fully-fledged web framework. It does not offer URI routing
and link formation, nor does it offer templating. It does, however,
integrate seamlessly with its sibling library
[bidi](https://github.com/juxt/bidi (for URI routing and formation) and
other routing libraries. It can integrated with the many template
libraries available for Clojure and Java, so you can build your own
web-framework from yada and other libraries.

### A tutorial: Hello World!

Let's introduce yada properly by writing some code. Let's start with some state, a string: `Hello World!`. We'll be able to give an overview of many of yada's features using this simple example.

We pass the string as a single argument to yada's `resource` function, and yada returns a web _resource_.

```clojure
(require '[yada.yada :as yada])

(yada/resource "Hello World!\n")
```

This web resource can be used as a Ring handler. By combining this handler with a web-server, we can start the service.

Here's how you can start the service using
[Aleph](https://github.com/ztellman/aleph). (Note that Aleph is the only
web server that yada currently supports).

```clojure
(require '[yada.yada :as yada]
         '[aleph.http :refer [start-server]])

(start-server
  (yada/resource "Hello World!\n")
  {:port 3000})
```

Once we have bound this handler to the path `/hello`, we're able to make the following HTTP request :-

```nohighlight
curl -i {{prefix}}/hello
```

and receive a response like this :-

```http
HTTP/1.1 200 OK
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: {{now.date}}
Last-Modified: {{hello.date}}
ETag: 8ddd8be4b179a529afa5f2ffae4b9858
Content-Type: text/plain;charset=utf-8
Vary: accept-charset
Content-Length: 13

Hello World!
```

Let's examine this response in detail.

The status code is `200 OK`. We didn't have to set it explicitly in
code, yada inferred the status from the request and the resource.

The first three response headers are added by our webserver.

```http
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: {{now.date}}
```

Next we have another date and a string known as the _entity tag_.

```http
Last-Modified: {{hello.date}}
ETag: 8ddd8be4b179a529afa5f2ffae4b9858
```

The __Last-Modified__ header shows when the string `Hello World!` was
created. As Java strings are immutable, yada is able to deduce that the
string's creation date is also the last time it could have been
modified. The same goes for the entity tag. Both are used in cacheing
and conflict detection, which will be described later.

Next we have a header telling us the media-type of the string's representation.

```http
Content-Type: text/plain;charset=utf-8
```

yada is able to determine that the media-type is text, but
without more clues it defaults to `text/plain`.

```http
Vary: accept-charset
```

Since the Java platform can encode a string in other charsets, yada uses the _Vary_ header to signal to the user-agent (and caches) that the body could change if a request contained an _Accept-Charset_ header.

Next we are given the length of the body, in bytes.

```http
Content-Length: 13
```

Finally we see our response body.

```nohighlight
Hello World!
```

#### Hello Swagger!

Now we have a web resource, let's build an API!

First, we need to choose a URI router. Let's choose [bidi](https://github.com/juxt/bidi), because it allows us to specify our _routes as data_. Since yada allows us to specify our _resources as data_, we can combine both to form a single data structure to describe our URI.

Having the API specified as a data structure means we can easily derive a Swagger spec.

First, let's require the support we need in the `ns` declaration.

```clojure
(require '[aleph.http :refer [start-server]]
         '[bidi.ring :refer [make-handler]]
         '[yada.yada :as yada])
```

Now for our resource.

```clojure
(def hello
  (yada/resource "Hello World!\n"))
```

Now let's create a route structure housing this resource. This is our API.

```clojure
(def api
  ["/hello-api"
      (yada/swaggered
        {:info {:title "Hello World!"
                :version "1.0"
                :description "Demonstrating yada + swagger"}
                :basePath "/hello-api"}
        ["/hello" hello])]
```

This is an API we can serialize into a data structure, store to disk,
generate and derive new data structures from.

Finally, let's use bidi to create a Ring handler from our API definition
and start the web server.

```clojure
(start-server
  (bidi/make-handler api)
  {:port 3000})
```

The `yada/swaggered` wrapper provides a Swagger specification, in JSON, derived from its arguments. This specification can be used to drive a [Swagger UI]({{prefix}}/swagger-ui/index.html?url=/hello-api/swagger.json).

![Swagger](static/img/hello-swagger.png)

But we're getting ahead of ourselves here. Let's delve a bit deeper in our `Hello World!` resource.

#### A conditional request

In HTTP, a conditional request is one where a user-agent (like a
browser) can ask a server for the state of the resource but only if a
particular condition holds. A common condition is whether the resource
has been modified since a particular date, usually because the
user-agent already has a copy of the resource's state which it can use
if possible. If the resource hasn't been modified since this date, the
server can tell the user-agent that there is no new version of the
state.

We can test this by setting the __If-Modified-Since__ header in the
request.

```nohighlight
curl -i {{prefix}}/hello -H "If-Modified-Since: {{hello.date}}"
```

The server responds with

```http
HTTP/1.1 304 Not Modified
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Tue, 21 Jul 2015 20:17:51 GMT
Content-Length: 0
```

### Mutation

Let's try to overwrite the string by using a `PUT`.

```nohighlight
curl -i {{prefix}}/hello -X PUT -d "Hello Dolly!"
```

The response is as follows (we'll omit the Aleph contributed headers from now on).

```http
HTTP/1.1 405 Method Not Allowed
Allow: GET, HEAD, OPTIONS
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: {{now.date}}
Content-Length: 0
```

The response status is `405 Method Not Allowed`, telling us that our
request was unacceptable. There is also a __Allow__ header, telling us
which methods are allowed. One of these methods is OPTIONS. Let's try
this.

```nohighlight
curl -i {{prefix}}/hello -X OPTIONS
```

```http
HTTP/1.1 200 OK
Last-Modified: Thu, 23 Jul 2015 14:21:21 GMT
Allow: GET, HEAD, OPTIONS
Content-Type: text/plain;charset=utf-8
Vary: accept-charset
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Thu, 23 Jul 2015 14:22:12 GMT
Content-Length: 0
```

An `OPTIONS` response contains an __Allow__ header which tells us that `PUT` isn't possible.

We can't mutate our Java string, but we can put it into a Clojure
reference, swapping in different Java strings.

To demonstrate this, yada contains support for atoms (but you would usually employ a durable implementation).

```clojure
(yada/resource (atom "Hello World!"))
```

We can now make another `OPTIONS` request to see whether `PUT` is available.

```nohighlight
curl -i {{prefix}}/hello-atom -X OPTIONS
```

```http
HTTP/1.1 200 OK
Allow: GET, DELETE, HEAD, POST, OPTIONS, PUT
Vary:
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Thu, 23 Jul 2015 14:33:16 GMT
Content-Length: 0
```

It is! So let's try it.

```nohighlight
curl -i {{prefix}}/hello -X PUT -d "Hello Dolly!"
```

And now let's see if we've managed to change the state of the resource.

```http
HTTP/1.1 200 OK
Last-Modified: Thu, 23 Jul 2015 14:38:20 GMT
Content-Type: application/edn
Vary: accept-charset
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Thu, 23 Jul 2015 14:38:23 GMT
Content-Length: 14

Hello Dolly!
```

As long as someone else hasn't sneaked in a different state between your `PUT` and `GET`, and the server hasn't been restarted, you should see the new state of the resource is "Hello Dolly!".

Before reverting our code back to the original, without the atom, let's see the Swagger UI again.

![Swagger](static/img/mutable-hello-swagger.png)

We now have a few more methods. [See for your self]({{prefix}}/swagger-ui/index.html?url=/mutable-hello-api/swagger.json).

#### A HEAD request

There was one more method indicated by the __Allow__ header of our `OPTIONS` request, which was `HEAD`. Let's try this now.

```nohighlight
curl -i {{prefix}}/hello -X HEAD
```

```http
HTTP/1.1 200 OK
Last-Modified: Thu, 23 Jul 2015 14:41:20 GMT
Content-Type: text/plain;charset=utf-8
Vary: accept-charset
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Thu, 23 Jul 2015 14:42:26 GMT
Content-Length: 0
```

The response does not have a body, but tells us the headers we would get
if we were to try a `GET` request.

For more details about HREAD queries, see [insert reference here].

<!--
(Note that unlike Ring's implementation of `HEAD`
in `ring.middleware.head/wrap-head`, yada's implementation does not cause a
response body to be generated and then truncated. This means that HEAD requests in yada are fast and inexpensive.)
-->

#### Parameters

Often, a resource's state will not be constant, but depend in some way on the request itself. Let's say we want to pass a parameter to the resource, via a query parameter.

First, let's call name our query parameter `p`. Since the state is
sensitive to the request, we specify a function rather than a value. The
function takes a single parameter called the _request context_, denoted
by the symbol `ctx`.

```clojure
(yada/resource
  (fn [ctx] (format "Hello %s!\n" (get-in ctx [:parameters :p])))
  :parameters {:get {:query {:p String}}})
```

Parameters are declared using additional key-value arguments after the first argument. They are declared on a per-method, per-type basis.

If the request correctly contains the parameter, it is available in the
request context, via the __:parameters__ key.

Let's see this in action

```nohighlight
curl -i {{prefix}}/hello-parameters?p=Ken
```

```http
HTTP/1.1 200 OK
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Mon, 27 Jul 2015 16:31:59 GMT
Content-Length: 7

Hi Ken
```

As well as query parameters, yada supports path parameters, request
headers, form parameters and whole request bodies. It can also coerce
parameters to a range of types. For more details, see
[insert reference here].

#### Content negotiation

Let's suppose we wanted to provide our greeting in both (simplified)
Chinese and English.

We add an option indicating the language codes of the two languages we are going to support. We can then

```clojure
(yada/resource
  (fn [ctx]
    (case (yada/language ctx)
      "zh-ch" "你好世界!\n"
      "en" "Hello World!\n"))
  :language ["zh-ch" "en"])
```

Let's test this by providing a request header which indicates a
preference for simplified Chinese

```nohighlight
curl -i {{prefix}}/hello-languages -H "Accept-Language: zh-CH"
```

We should get the following response

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=utf-8
Vary: accept-language
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Mon, 27 Jul 2015 17:36:42 GMT
Content-Length: 14

你好世界!
```

There is a lot more to content negotiation than this simple example can
show. It is covered in depth in subsequent chapters.

### How yada compares

It is often easier to understand a technology in relation to another.

> "I know Ring, how does yada compare to that?"

As a developer, Ring provides you with the raw HTTP request. The rest is up to you. You've got to figure out how to take that data and turn it into a response. There are some support functions, called Ring middleware, which can help in common tasks. However, it's up to you which Ring middleware to use, and in which order to apply it. You have figure this out for every service you write.

Generally speaking, a web service written with Ring starts at zero
functionality and builds up. A web service written with yada starts you
off at full HTTP functionality.

That said, there do exist projects such as ring-defaults that compose
together a tower of Ring middleware which provides a good foundation of
HTTP and other functionality. The primary problem with applying Ring
middleware in this way is that the tower needs to be executed by the
request thread, which precludes the option to run middleware
asynchronously.

### Summary

This simple example demonstrated how a rich and functional HTTP resource
was created with a tiny amount of code. And yet, none of the behaviour
we have seen is hardcoded or contrived. Much of the behavior was
inferred from the types of the first argument given to the
`yada/resource` properties, in this case, the Java string. And yada
includes support for many other basic types (atoms, Clojure collections,
URLs, files, directories…).

But the real power of yada comes when you define your own resource
types, as we shall discover in subsequent chapters. But first, let's see
how to install and integrate yada in your web app.

## Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "{{yada.version}}"]
[aleph "0.4.0"]
```

If you want to use yada to create a web API, this is all you need to
do. But you can also clone the yada repository with `git`.

```nohighlight
git clone https://github.com/juxt/yada
```

You can then 'run' yada on your local machine to provide off-line access the documentation and demos.

```nohighlight
cd yada
lein run

```

(`lein` is available from [http://leiningen.org](http://leiningen.org))

## Async

![Swagger](static/img/hello-async.png)

## Methods

## Parameters

## Representations

If you think of a web resource in MVC terms, representations are the
different views of a resource. Representations are the means by which a
resource's state can be tranferred to different parts of the web.

The actual content of a representation is determined by the outcome of a
process known as _content negotiation_.

Content negotation is an important feature of HTTP, allowing clients and
servers to agree on how a resource can be represented to best meet the
availability, compatibility and preferences of both parties.

There are 2 types of
[content neogiation]({{prefix}}/static/spec/rfc7231.html#section-3.4)
described in HTTP.

### Proactive negotiation

The first is termed _proactive negotiation_ where the server determined
the type of representation from requirements sent in the request
headers.

There are 4 aspects to the representation that can be negotiated

* Content-type - the media type of the content
* Charset - if the content is textual, the character set of the text
* Encoding - usually how the content is compressed
* Language - the natural language of content

Recall our `Hello World!` example. Let's extend this by specifying 2 sets of possible representation.

```clojure
(yada/resource
  (fn [ctx]
    (case (get-in ctx [:response :representation :language])
            "zh-ch" "你好世界!\n"
            "en" "Hello World!\n"))
  :representations [{:content-type "text/plain"
                     :language #{"en" "zh-ch"}
                     :charset "UTF-8"}
                    {:content-type "text/plain"
                     :language "zh-ch"
                     :charset "Shift_JIS;q=0.9"}])
```


```nohighlight
curl -i http://localhost:8090/hello-languages -H "Accept-Charset: Shift_JIS" -H
"Accept: text/plain" -H "Accept-Language: zh-CH"
```

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=shift_jis
Vary: accept-charset, accept-language, accept
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Mon, 27 Jul 2015 18:38:01 GMT
Content-Length: 9

?�D���E!
```

### Reactive negotiation

The second type of negotiation is termed _reactive negotation_ where the
agent chooses from a list of representations provided by the server.


## Resources

Different types of resources are added to yada by defining types or records.

Let's delve a little deeper into how the _Hello World!_ example works.

Here is the actual code that tells yada about Java strings (comments removed).

```clojure
(defrecord StringResource [s last-modified]
  Resource
  (methods [this] #{:get :options})
  (parameters [_] nil)
  (exists? [this ctx] true)
  (last-modified [this _] last-modified)

  ResourceRepresentations
  (representations [_]
    [{:content-type #{"text/plain"}
      :charset platform-charsets}])

  Get
  (get* [this ctx] s))

(extend-protocol ResourceCoercion
  String
  (make-resource [s]
    (->StringResource s (to-date (now)))))
```

Recall the _Hello World!_ example.

```clojure
(yada/resource "Hello World!")
```

yada calls `make-resource` on the argument. This declaration causes a new instance of the `StringResource` record to be created.

```clojure
(extend-protocol ResourceCoercion
  String
  (make-resource [s]
  (->StringResource s (to-date (now)))))
```

The original string (`Hello World!`) and the current date is captured
and provided to the `StringResource` record.

The `StringResource` resource satisfies the `ResourceRepresentations`
protocol, which means it can specify which types of representation it is
able to generate. The `representations` function must return a list of
_representation declarations_, which declare all the possible
combinations of media-type, charset, encoding and language. In this
case, we just have one representation declaration which specifies
`text/plain` and the charsets available (all those supported on the Java
platform we are on).

### Extending yada

There are numerous types already built into yada, but you can also add
your own. You can also add your own custom methods.
