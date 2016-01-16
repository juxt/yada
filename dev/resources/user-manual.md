# The yada manual

Welcome to the yada manual!

This manual corresponds with version 1.1.0-20160114.191215-6

### Table of Contents

<toc drop="0"/>

### Audience

This manual is the authoritative documentation to yada and as such, it is
intended for a wide audience of developers, from beginners to experts.

### Get involved

If you are a more experienced Clojure or REST developer and would like
to get involved in influencing yada's future, please join our
[yada-discuss](https://groups.google.com/forum/#!forum/yada-discuss)
discussion group. Regardless of your experience, everyone is more than
welcome to join the list. List members will do their best to answer any
questions you might have.

### Document conventions

Terms that have specific meaning are introduced in
_italics_. Terminology specific to yada is in **bold**.

### Spot an error?

If you spot a typo, misspelling, grammar problem, confusing text, or
anything you feel you could improve, please go-ahead and
[edit the source](https://github.com/juxt/yada/edit/master/dev/resources/user-manual.md). If
your contribution is accepted you will have our eternal gratitude,
help future readers and be forever acknowledged in the yada
documentation as a contributor!

[Back to index](/)

## Introduction

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

### Resources

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

### Handlers

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

### Why?

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

## Example 1: Hello World!

Let's introduce yada properly by writing some code. Let's start with
some state, a string: `Hello World!`. We'll be able to give an
overview of many of yada's features using this simple example. For
brevity, we'll be using Clojure.

```clojure
(require '[yada.yada :refer [yada]])

(yada "Hello World!\n")
```

First we require the `yada` function, which Clojure needs to know
where it comes from.

We give it our string and it returns a __handler__.

(Just a minute!  We just said that the argument to give to `yada` was
a __resource__. Well, that's true, but yada has some built-in code
that implicitly coerces the string into a resource. Don't worry about
that for now, we'll discuss it more later).

You can see the result of this at
[http://localhost:8090/hello](http://localhost:8090/hello).

By combining this handler with a web-server, we can start the service.

Here's how you can start the service using
[Aleph](https://github.com/ztellman/aleph).

```clojure
(require '[yada.yada :refer [yada]]
         '[aleph.http :refer [start-server]])

(start-server
  (yada "Hello World!\n")
  {:port 3000})
```

(Note that you are free to choose any yada -compatible web server, as
long as it's Aleph! Joking aside, as more web servers support
end-to-end asynchronicity with back-pressure, all the way up to the
application, then yada will support those. Currently Aleph is the only
web server we know that offers this).

Once we have bound this handler to the path `/hello`, we're able to make
the following HTTP request:

```nohighlight
curl -i http://localhost:3000/hello
```

and receive a response like this:

```http
HTTP/1.1 200 OK
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Sun, 09 Aug 2015 07:32:23 GMT
Last-Modified: Sun, 09 Aug 2015 07:25:10 GMT
ETag: 1462348343
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
Date: Sun, 09 Aug 2015 07:32:23 GMT
```

Next we have another date and a string known as the _entity tag_.

```http
Last-Modified: Sun, 09 Aug 2015 07:25:10 GMT
ETag: 1462348343
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
         '[bidi.ring :refer [make-handler] :as bidi]
         '[yada.yada :refer [yada] :as yada])
```

Now for our resource.

```clojure
(def hello
  (yada "Hello World!\n"))
```

Now let's create a route structure housing this resource. This is our API.

```clojure
(def api
  ["/hello-swagger"
      (yada/swaggered
        {:info {:title "Hello World!"
                :version "1.0"
                :description "Demonstrating yada + swagger"}
                :basePath "/hello-swagger"}
        ["/hello" hello])])
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

The `yada/swaggered` wrapper provides a Swagger specification, in JSON, derived from its arguments. This specification can be used to drive a [Swagger UI](http://localhost:8090/swagger-ui/index.html?url=/hello-swagger/swagger.json).

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
curl -i http://localhost:8090/hello -H "If-Modified-Since: Sun, 09 Aug 2015 07:25:10 GMT"
```

The server responds with

```http
HTTP/1.1 304 Not Modified
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Tue, 21 Jul 2015 20:17:51 GMT
Content-Length: 0
```

#### Mutation

Let's try to overwrite the string by using a `PUT`.

```nohighlight
curl -i http://localhost:8090/hello -X PUT -d "Hello Dolly!"
```

The response is as follows (we'll omit the Aleph contributed headers from now on).

```http
HTTP/1.1 405 Method Not Allowed
Allow: GET, HEAD, OPTIONS
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Sun, 09 Aug 2015 07:32:23 GMT
Content-Length: 0
```

The response status is `405 Method Not Allowed`, telling us that our
request was unacceptable. There is also an __Allow__ header, telling us
which methods are allowed. One of these methods is OPTIONS, which we
could have used to check whether PUT was available without actually
attempting it.

```nohighlight
curl -i http://localhost:8090/hello -X OPTIONS
```

```http
HTTP/1.1 200 OK
Allow: GET, HEAD, OPTIONS
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Thu, 23 Jul 2015 14:22:12 GMT
Content-Length: 0
```

Both the `PUT` and the `OPTIONS` response contain an __Allow__ header
which tells us that `PUT` isn't possible. This makes sense, because we
can't mutate a Java string.

We could, however, wrap the Java string with a Clojure reference which
could be changed to point at different Java strings.

To demonstrate this, let's use a Clojure atom instead, adding the new
resource with the identifier `http://localhost:8090/hello-atom`.

```clojure
(yada (atom "Hello World!"))
```

We can now make another `OPTIONS` request to see whether `PUT` is
available, before trying it.

```nohighlight
curl -i http://localhost:8090/hello-atom -X OPTIONS
```

```http
HTTP/1.1 200 OK
Allow: GET, DELETE, HEAD, POST, OPTIONS, PUT
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Sun, 09 Aug 2015 07:56:20 GMT
Content-Length: 0
```

It is! So let's try it.

```nohighlight
curl -i http://localhost:8090/hello-atom -X PUT -d "Hello Dolly!"
```

And now let's see if we've managed to change the state of the resource.

```nohighlight
curl -i http://localhost:8090/hello-atom
```

```http
HTTP/1.1 200 OK
Last-Modified: Thu, 23 Jul 2015 14:38:20 GMT
Content-Type: application/edn
Vary: accept-charset
ETag: 1462348343
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Sun, 09 Aug 2015 08:00:58 GMT
Content-Length: 14

Hello Dolly!
```

As long as someone else hasn't sneaked in a different state between your
`PUT` and subsequent `GET`, you should see the new state of the resource
is "Hello Dolly!".

But what if someone _did_ manage to `PUT` their change ahead of yours?
Their version would now be overwritten. That might not be what you
wanted. To ensure we don't override someone's change, we could have
set the __If-Match__ header using the __ETag__ value.

Let's test this now, using the ETag value we got before we sent our
`PUT` request.

```nohighlight
curl -i http://localhost:8090/hello -X PUT -H "If-Match: 1462348343" -d "Hello Dolly!"
```

[fill out]

Before reverting our code back to the original, without the atom, let's see the Swagger UI again.

![Swagger](static/img/mutable-hello-swagger.png)

We now have a few more methods. [See for yourself](http://localhost:8090/swagger-ui/index.html?url=/hello-atom-swagger/swagger.json).

#### A HEAD request

There was one more method indicated by the __Allow__ header of our `OPTIONS` request, which was `HEAD`. Let's try this now.

```nohighlight
curl -i http://localhost:8090/hello -X HEAD
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

The response does not have a body, but tells us the headers we would
get if we were to try a `GET` request.

For more details about HEAD queries, see [insert reference here].

#### Parameters

Often, a resource's state or behavior will depend on parameters in the
request. Let's say we want to pass a parameter to the resource, via a
query parameter.

To show this, we'll write some real code:

```clojure
(require '[yada.yada :refer [yada resource]])

(defn say-hello [ctx]
  (str "Hello " (get-in ctx [:parameters :query :p]) "!\n"))

(def hello-parameters-resource
  (resource
    {:methods
      {:get
        {:parameters {:query {:p String}}
         :produces "text/plain"
         :response say-hello}}}))

(def handler (yada hello-parameters-resource))
```

This declares a resource with a GET method, which responds with a
plain-text message formed from the query parameter.

Let's see this in action: [http://localhost:8090/hello-parameters?p=Ken](http://localhost:8090/hello-parameters?p=Ken)

```nohighlight
curl -i http://localhost:8090/hello-parameters?p=Ken
```

```http
HTTP/1.1 200 OK
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: Mon, 27 Jul 2015 16:31:59 GMT
Content-Length: 7

Hello Ken!
```

As well as query parameters, yada supports path parameters, request
headers, form data, cookies and request bodies. It can also coerce
parameters to a range of types. For more details, see the
[Parameters](#Parameters) chapter.

#### Content negotiation

Let's suppose we wanted to provide our greeting in both (simplified)
Chinese and English. Again, we can declare these two languages in the
__resource-model__.

We add an option indicating the language codes of the two languages we
are going to support. We can then

```clojure
(require '[yada.yada :as yada :refer [yada resource]])

(defn say-hello [ctx]
  (case (yada/language ctx)
    "zh-ch" "你好世界!\n"
    "en" "Hello World!\n"))

(def hello-languages-resource
  (resource
    {:methods
      {:get
        {:produces {:media-type "text/plain"
                    :language #{"zh-ch" "en"}}
         :response say-hello}}}))

(def handler (yada hello-languages-resource))
```

Let's test this by providing a request header which indicates a
preference for simplified Chinese

```nohighlight
curl -i http://localhost:8090/hello-languages -H "Accept-Language: zh-CH"
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

There's a lot more to content negotiation than this simple example can
show. It is covered in depth in subsequent chapters.

#### Summary

This simple example demonstrated how a rich and functional HTTP resource
was created with a tiny amount of code. And yet, none of the behaviour
we have seen is hardcoded or contrived. Much of the behavior was
inferred from the types of the first argument given to the
`yada` function, in this case, the Java string. And yada
includes support for many other basic types (atoms, Clojure collections,
files, directories, Java resources…).

But the real power of yada comes when you define your own resource
models and types, as we shall discover in subsequent chapters. But
first, let's see how to install and integrate yada in your web app.

## Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "1.1.0-SNAPSHOT"]

[aleph "0.4.1-beta3"]
[bidi "1.25.0"]
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

## Resources

Resources are defined by __resource models__, which are just data, and
yada's defining feature.

Resource models should be defined ahead-of-time wherever possible,
before the server starts listening for HTTP requests. Doing so will
expose the resource model to tooling. However, you are free to create
them dynamically on every request if necessary.

Once complete, resource models are used to create request handlers,
which serve HTTP requests targeting the resource.

### Writing resource models

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

#### Data abbreviations

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

#### Common examples

There are numerous other short-hands. If in doubt, learn the canonical
form and use that until you discover the short-hand for it. You can
experiment with the `yada.resource` function ahead of time in the
REPL. If you do something that isn't possible, the schema validation
errors should help you figure out why.

[insert table of common coercions here]

### Resource types

A __resource type__ is a Clojure types or record that can be
automatically coerced into a __resource model__. These types satisfy
the `yada.protocols.ResourceCoercion` protocol, and any existing type
or record may be extended to do so, using Clojure's `extend-protocol`
macro.

```clojure
(extend-protocol datomic.api.Database
  yada.protocols.ResourceCoercion
  (as-resource
    (resource
      {:properties
        {:last-modified …}
       :methods
        {:get …}}})))
```

The `as-resource` function must return a resource (by calling
`yada.resource`, not just a map).

### Summary

The resource model is yada's central concept. The following chapters
describe the various aspects of the resource-model in more detail.

## Parameters

Web requests can contain parameters that can influence the response
and yada can capture these. This is especially useful when you are
writing APIs.

There are different types of parameters, which you can mix-and-match.

- Query parameters (part of the request URI's query-string)
- Path parameters (embedded in the request URI's path)
- Request headers
- Form data
- Request bodies
- Cookies

There are many benefits to declaring the parameters in the resource
model.

- yada will check they exist, and return 400 (Malformed Request)
  errors on requests that don't provide the ones you need for your
  logic
- yada will coerce them to the types you want, so you can avoid
  writing loads of type-conversion logic in your code
- yada and other tools can process your declarations independently of
  your request-processing code, e.g. to generate API documentation

For example, let's imagine a URI to access the transactions
of a fictitious bank account.

```nohighlight
https://bigbank.com/accounts/1234/transactions?since=tuesday
```

There could be 2 parameters here. The first, `1234`, is contained in the
path `/accounts/1234/transactions`. We call this a __path parameter__.

The second, `tuesday`, is embedded in the URI's query-string (after
the `?` symbol). We call this a __query parameter__.

You can declare these parameters in the __resource model__.

```clojure
{:parameters {:path {:entry Long}}
 :methods {:get {:parameters {:query {:since String}}}
           :post {:parameters {:body …}}}
```

Parameters can be specified at _resource-level_ or at
_method-level_. Path parameters are usually declared at the
resource-level because they form part of the URI that is independent
of the request's method. In contrast, query parameters usually apply
to GET requests, so it's common to define this parameter at the
_method-level_, and it's only visible if the method we declare it with
matches the request method.

We declare parameter values using the syntax of
[Prismatic](https://prismatic.com)'s
](https://github.com/prismatic/schema) library. This allows us
to get quite sophisticated in how we define parameters.

```clojure
(require [schema.core :refer (defschema)]

(defschema Transaction
  {:payee String
   :description String
   :amount Double}

{:parameters {:path {:entry Long}}
 :methods {:get {:parameters {:query {:since String}}}
           :post {:parameters {:body Transaction}}}
```

### Capturing multi-value parameters

Occasionally, you may have multiple values associated with a given
parameter. Query strings and HTML form data both allow for the same
parameter to be specified multiple times.

```
/search?accno=1234&accno=1235
```

To capture all values in a vector, declare your parameter type as a
vector type,

```clojure
{:parameters {:query {:accno [Long]}}}}
```

### Capturing large request bodies

Sometimes, request bodies are very large or even unlimited. To ensure
you don't run out of memory receiving this request data, you can
specify more suitable containers, such as files, database blobs,
Amazon S3 buckets or your own extensions.

All data produced and received from yada is handled efficiently and
asynchronously, ensuring that even with very large data streams your
service continues to work.

```clojure
{:parameters {:form {:video java.io.File}}}
```

## Properties

Properties tell us about the current state of a resource, such as
whether the resource exists, or when the resource was last
modified. Properties allow us to determine whether the user agent's
cache of a resource's state is up-to-date.

Sometimes all a resource's properties are constant and can be known
when the resource is defined. More likely the resource's properties
have to be determined by some logic, and often this logic involves
I/O.

Also, if the resource has declared parameters, it can be that
resource's properties depend in some way on these parameters. For
example, the properties of account A may well be different from the
properties of account B.

A resource's properties may also depend on who is making the
request. Your bank account details should only exist if you're the one
accessing them. If I tried to access your bank account details, you'd
want the service to behave differently.

For this reason, a resource's __properties__ declaration in the
__resource-model__ points to a single-arity function that is called by
yada after the request's parameters have been parsed and the
credentials of a caller have been established.

In many cases, it will be necessary to query a database, internal
web-service or equivalent operation involving I/O.

If you use a __properties__ function, anything you return will be
placed in the __:properties__ entry of the __request-context__. Since
the request-context is available when the full response body is
created, you may choose to return the entire state of the resource, in
addition to its properties. This may be sensible if it helps avoid a
second trip to the database.

## Methods

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

### GET

Specify a function in __:response__ that will be called during the GET
method processing.

If the resource exists, the single-arity function will be called with the request
context as its only argument.

It should return the response's body, which should satisfy
`yada.body.MessageBody` determining how exactly the response's body
will be returned.

### PUT

[coming soon]

### POST

[coming soon]

### DELETE

[coming soon]

### HEAD

[coming soon]

### OPTIONS

[coming soon]

### TRACE

[coming soon]

### PATCH

[coming soon]

### Custom methods

Custom methods can be added by defining new types that extend the
`yada.methods.Method` protocol.

### BREW

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

## Representations

Resources have state, but when this state needs to be transferred from
one host to another, we use one of a number of formats to represent
it. We call these formats _representations_.

A given resource may have a large number of actual or possible
representations.

Representation may differ in a number of respects, including:

- the media-type ('file format').
- if textual, the character set used to encode it into octets
- the (human) language used (if textual)
- whether and how the content is compressed

Whenever a user-agent requests the state from a resource, a particular
representation is chosen, either by the server (proactive) or client
(reactive). The process of choosing which representation is the most
suitable is known as
[_content negotiation_](/spec/rfc7231#section-3.4).

### Producing content

Content negotiation is an important feature of HTTP, allowing clients
and servers to agree on how a resource can be represented to best meet
the availability, compatibility and preferences of both parties. It is
a key factor in the survival of services over time, since both new and
legacy media-types can be supported concurrently. (It is also the
mechanism by which new versions of media-types can be introduced, even
media-types that define hypermedia interactions, more on this later.)

#### Proactive negotiation

There are 2 types of content negotiation. The first is termed
_proactive negotiation_ where the server determines the type of
representation from requirements sent in the request headers. These
are the headers beginning with `Accept`.

For any resource, the available representations that can be produced
by a resource, and those that it consumers, are declared in the
__resource model__. Every resource that allows a GET method should
declare at least one representation that it is able to produce.

Let's start with a simple web-page example.

```clojure
{:produces "text/html"}
```

This is a short-hand for writing [...]

[missing text here]

```clojure
{:produces [{:media-type "text/plain"
             :language #{"en" "zh-ch"}
             :charset "UTF-8"}
            {:media-type "text/plain"
             :language "zh-ch"
             :charset "Shift_JIS;q=0.9"}]
             ```

[ todo - languages ]


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

#### Reactive negotiation

The second type of negotiation is termed _reactive negotiation_ where the
agent chooses from a list of representations provided by the server.

(Currently, yada does not yet support reactive negotiation but it is
definitely on the road-map.)

#### The Vary response header

[coming soon]

### Consuming content

[coming soon]

## Security

With security, it is important to understand the concepts, processes
and standards in detail. While yada can help with good security
defaults and attempts to make security configuration easier, it is
still important that you are familiar with security concepts, so read
this chapter carefully.

### Security is part of the resource, not the route

In yada, resources are self-contained and are individually protected
from unauthorized access. We agree with the HTTP standards authors
when we consider security to be integral to the definition of the
resource itself, and not an extra to be bolted on afterwards. Nor
should it be complected with routing. The process of looking up a
resource from a URI is independent of how that resource should behave.

This needs to be emphasized because other web frameworks and libraries
couple security to the router. While this may be convenient at first,
it brings in additional complexity and tightly couples the URI to
security concerns. HTTP considers the URI's path to be opaque, and it
should be decoupled from the semantics of the resource it identifies.

Building security into each resource yields other benefits, such as
making it easier to test the resource as a unit in isolation of other
resources and the router.

As in all other areas, yada aims for 100% compliance with core HTTP
standards when it comes to security, notably
[RFC 7235](https://tools.ietf.org/html/rfc7235). Also, since HTTP APIs
are nowadays used to facilitate transactional integration between
systems via the user's browser, it is critically important that yada
fully supports applications that want to open up services to other
applications, across origins, as standardised by
[CORS](http://www.w3.org/TR/cors/).

### Authentication

Authentication is the process of establishing the identity of a
person, with reasonable confidence that the person is not an impostor.

In yada, authentication happens after the request parameters have been
processed, so if necessary they can be used to establish the identity
of the user. However, it is important to remember that authentication
happens before the resource's properties have been loaded, as it has
nothing to do with the actual resource. Thus, if the user is not
genuine, we might well save a wasted trip to the resource's
data-store.

Resources may exist inside a _protection space_ determined by one or
more _realms_. Each resource declares the realm (or realms) it is
protected by, as part of the __:authentication__ entry of its
__resource-model__. Each realm determines the _authentication scheme_
governing how requests are authenticated.

yada supports numerous authentication schemes, include custom ones you
can provide yourself.

Each scheme has a verifier, depending on the scheme this is usually a
function. The verifier is used to check that the credentials in the
request are authentic.

If credentials are found and verified as authentic, the verifier
should return a non-nil (truthy) result containing the credentials
that have been established, such as the username, email address and
user's roles.

If no credentials are found, the verifier should return nil.

If no credentials are found by any of the schemes, a 401 response is
returned containing a WWW-Authenticate header.

#### Basic authentication

Here is an example of a resource which uses Basic authentication
described in [RFC 2617](https://www.ietf.org/rfc/rfc2617.txt)

```clojure
{:access-control
  {:realm "accounts"
   :scheme "Basic"
   :verify (fn [[user password]] …}}
```

There are 3 entries here. The first specifies the _realm_, which is
mandatory in Basic Authentication because the browser needs it to tell
the user.

The second declares we are using Basic authentication.

The last entry is the verify function. In Basic Authentication, the
verify function takes a single argument which is a vector of two
entries: the username and password.

If the user/password pair correctly identifies an authentic user, your
function should return credentials.

```clojure
(fn [[user password]]
  …
  {:email "bob@acme.com"
   :role #{:admin}})
```

If the password is wrong, you may choose to return either an empty map
or nil. If you return an empty map (a truthy value) and the resource
requires credentials that aren't in the map, a 403 Forbidden response
will be returned. However, if you return nil, this will be treated as
no credentials being sent and a 401 Unauthorized response will be
returned.

From a UX perspective there is a difference. If the user-agent is a
browser, returning nil will mean that the password dialog will
reappear for every failed login attempt. If you return truthy, it will
show the 403 Forbidden response.

You may choose to limit the number of times a failed 'login' attempt
is tolerated by setting a cookie or other means.

#### Digest authentication

[coming soon]

#### Cookie authentication

Basic Authentication has a number of perceived and real weaknesses,
including the 'log out' problem and the lack of control that a website
has over the fields presented to a human.  Therefore, the vast
majority of websites prefer to use a custom login form generated in
HTML.

In yada, a login 'form' is a resource that lets you exchange one set
of credentials for another. The set of credentials you give, via a
form, is verified and if they correspond to a valid user, you get a
cookie that certifies you've already been authenticated. It is this
certification that will be checked on every request.

But first, we need to construct this resource that will verify form
data and, if valid, return a cookie.

A good method to use is POST, since web browsers support that and it
avoids passwords appearing in URIs.

From reasons of cohesion, it's right that the same resource that knows
which fields are relevant gets to reveal this information on a GET
request. If the content-type is HTML, this can look just like a login
form.



We now protect resources by declaring an authentication scheme that
verifies the request's cookie.

```clojure
{:access-control
  {:realm "accounts"
   :cookie "Token"
   :verify (fn [ctx] …}}
```

#### Bearer authentication (OAuth2)

[coming soon]

#### Multifactor authentication

[coming soon]

### Authorization

Authorization is the process of allowing a user access to a
resource. This may require knowledge about the user only (for example,
in
[Role-based access control](https://en.wikipedia.org/wiki/Role-based_access_control)). Authorization
may also depend on properties of the resource identified by the HTTP
request's URI (as part of an
[Attribute-based access control](https://en.wikipedia.org/wiki/Attribute-based_access_control)
authorization scheme).

In either case, we assume that the user has already been
authenticated, and we are confident that their credentials are
genuine.

In yada, authorization happens _after_ the resource's properties has
been loaded, because it may be necessary to check some aspect of the
resource itself as part of the authorization process.

Any method can be protected by declaring a role or set of roles in its model.

```clojure
{:methods {:post :accounts/create-transaction}}
```

If multiple roles are involved, they can be composed inside vectors
using simple predicate logic.

```clojure
{:methods {:post [:or [:and :accounts/user
                            :accounts/create-transaction]
                      :superuser}}
```

Only the simple boolean 'operators' of `:and`, `:or` and `:not` are
allowed in this authorization scheme. This keeps the role definitions
declarative and easy to extract and process by other tooling.

Of course, authentication information is available in the request
context when a method is invoked, so any method may apply its own
custom authorization logic as necessary. However, yada encourages
developers to adopt a declarative approach to resources wherever
possible, to maximise the integration opportunities with other
libraries and tools.

### Realms

yada supports multiple realms.

### Cross-Origin Resource Sharing (CORS)

[coming soon]

## Routing

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

### Declaring your website or API as a bidi/yada tree

A bidi routing model is a hierarchical pattern-matching tree. The tree
is made up of pairs, which tie a pattern to a (resource) handler (or a
further group of pairs, recursively).

Both bidi's route models and and yada's resource models are recursive
data structures and can be composed together.

The end result might be a large and deeply nested tree, but one that
can be manipulated, stored, serialized, distributed and otherwise
processed as a single dataset.

```
(require [yada.yada :refer [resource]])

["/shop/"
 [
  ["electronics.html"
    (resource
      {:methods
        {:get
          {:response
            (fn [ctx] (hiccup/html …))}}})]


]

```

A yada handler (created by yada's `yada` function) and a yada resource
(created by yada's `resource` constructor function) extends bidi's
`Matched` protocol are both able to participant in the pattern
matching of an incoming request's URI.

For a more thorough introduction to bidi, see
[https://github.com/juxt/bidi/README.md](https://github.com/juxt/bidi/README.md).

### Declaring policies across multiple resources

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

## Example 2: Phonebook

We have covered a lot of ground so far. Let's consolidate our knowledge by building a simple application, using all the concepts we've learned so far.

We'll build a simple phonebook application. Here is a the brief:

### Phonebook requirements

Create a simple HTTP service to represent a phone book.

Acceptance criteria.
 - List all entries in the phone book.
 - Create a new entry to the phone book.
 - Remove an existing entry in the phone book.
 - Update an existing entry in the phone book.
 - Search for entries in the phone book by surname.

A phone book entry must contain the following details:
 - Surname
 - Firstname
 - Phone number
 - Address (optional)

### The database

Create a new namespace called `phonebook.db`.

```clojure
(ns phonebook.db)
```

We'll create a database constructor, and some functions to access its contents.

This constructor creates a map with two entries, both refs. We could use an atom, but refs offer more flexibility.

```clojure
(defn create-db [entries]
  {:phonebook (ref entries)
   :next-entry (ref (inc (apply max (keys entries))))})
```

Now let's add some code to add an entry.

```clojure
(defn add-entry [db entry]
  (dosync
   (let [nextval @(:next-entry db)]
     (alter (:phonebook db) conj [nextval entry])
     (alter (:next-entry db) inc)
     nextval)))
```

### Creating new phonebook entries

For this requirement, we are going to support the POST method.

Let's add the following entry to the static properties of the `IndexResource`.

```clojure
{:parameters {:post {:form {:surname String
                           :firstname String
                           :phone [String]}}}
…
}
```

This declaration tells yada what parameters we are expecting in the POST method. In return, yada will do the following:

1. Validate the request, ensuring that all the mandatory parameters have been provided
1. Coerce the parameters to the desired types (if possible).
1. Return a 400 response (if not).
1. Parse the request body.
1. Help prevent XSS scripting attacks, by ensuring that no unexpected parameters are allowed to pass through. We should still be careful of String parameters though.

## Swagger

[coming soon]

See
[IBM's Watson Developer Cloud](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/apis/)
for a sophistated Swagger example.

## Async

Under normal circumstances, with Clojure running on a JVM, each request can be processed by a separate thread.

However, sometimes the production of the response body involves making requests
to data-sources and other activities which may be _I/O-bound_. This means the thread handling the request has to block, waiting on the data to arrive from the I/O system.

For heavily loaded or high-throughput web APIs, this is an inefficient
use of resources. Today, this problem is addressed by asynchronous I/O
programming modesl. The request thread is able to make a request for
data via I/O, and then is free to carry out further work (such as
processing another web request). When the data requested arrives on the
I/O channel, a potentially different thread carries on processing the
original request.

As a developer, yada gives you fine-grained control over when to use a
synchronous programming model and when to use an asynchronous one.

#### Deferred values

A deferred value is simply a value that may not yet be known. Examples
include Clojure's futures, delays and promises. Deferred values are
built into yada — for further details, see Zach Tellman's
[manifold](https://github.com/ztellman/manifold) library.

In almost all cases, it is possible to return a _deferred value_ from
any of the functions that make up our resource record or handler options.

For example, suppose our resource retrieves its state from another internal web API. This would be a common pattern with µ-services. Let's assume you are using an HTTP client library that is asynchronous, and requires you provide a _callback_ function that will be called when the HTTP response is ready.

On sending the request, we could create a promise which is given to the callback function and returned to yada as the return value of our function.

Some time later the callback function will be called, and its implementation will _deliver_ the promise with the response value. That will cause the continuation of processing of the original request. Note that _at no time is any thread blocked by I/O_.

![Async](static/img/hello-async.png)

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

## Example 3: Search engine

## Server Sent Events

### Introduction

**Server Sent Events** (SSE) is a part of the HTML5 generation of
specifications that describes a capability for delivering events,
asynchronously, from a server to a browser or other user-agent, over a
long-lived connection.

SSE conceptually similar to _web sockets_. However, a key difference
is that SSE is layered upon HTTP and thus inherits the protocol's
support for proxying, authorization, cookies and is integrated with
Cross-Origin Resource Sharing (CORS).

In contrast, _web sockets_ are raw TCP sockets that share nothing with
HTTP except for the ability for a user agent to use the HTTP protocol
to initiate a web socket connection. After that, everything is up to
agreements between the client and server.

Since yada is designed to support HTTP, it does not provide anything
extra to support _web sockets_ beyond that which is provided by the
web server.

### SSE with yada

To create Server Sent Event streams with yada, return a stream of data
from a response.

For example, a stream of data could be a core.async channel. It is
important that you set the representation to be `text/event-stream`,
so that a client recognises this as a Server Sent Event stream and
keeps the connection open.

```clojure
(require '[clojure.core.async :refer [chan]])

{:methods {:get {:produces "text/event-stream"
                 :response (chan)}}}
```

It is, however, highly unusual to want to provide a channel of data to
a single client. Typically, what is required is that each client gets
a copy of every message in the channel. This can be achieved easily by
multiplexing the channel with `clojure.core.async/mult`, which yada
will recognise and tap on your behalf.

```clojure
(require '[clojure.core.async :refer [mult]])

(let [mlt (mult channel)]
  {:methods {:get {:produces "text/event-stream"
                   :response mlt}}})
```

Of course, you can `tap` the `mult` yourself in your own logic and
provide the tapping channel directly to yada, which will 'do the right
thing' depending on what you provide.

## Example 4: Chat server

[coming soon]

## Handling request bodies

[coming soon]

## Example 5: Selfie uploader

[coming soon]

## Handlers

[coming soon]

## The Request Context

[coming soon]

## Interceptors

The interceptor chain, established on the creation of a resource. A
resource's interceptor chain can be modified from the defaults.

### available?
### known-method?
### uri-too-long?
### TRACE
### method-allowed?
### malformed?
### check-modification-time
### select-representation
### if-match
### if-none-match
### invoke-method
### get-new-properties
### compute-etag
### access-control-headers
### create-response

## Sub-resources

Usually, it is better to declare as much as possible about a resource
prior to directing requests to it. If you do this, your resources will
expose more information and there will be more opportunities to
utilize this information in various ways (perhaps serendipitous
ones). But sometimes it just isn't possible to know everything about a
resource, up front, prior to the request.

A classic example is serving a changing directory of files. Each file
might be a separate resource, identified by a unique URI and have
different possible representations. Unless the directory's contents
are immutable, you should only determine the number and nature of
files contained therein upon the arrival of a request. For this
reason, yada has __sub-resources__. Sub-resources are resources that
are created just-in-time when a request arrives.

### Declaring sub-resources

Any __resource__ can declare that it manages sub-resources by
declaring a __:sub-resource___ entry in its __resource-model__. The
value of a sub-resource is a single-arity function, taking the
__request-context__, that returns a new __resource__, from which a
temporary handler is constructed to serve just the incoming request.

Sub-resources are recursive. A __resource__ that is returned from a
__sub-resource__ function can itself declare that it provides its own
sub-resources, _ad-infinitum_.

### Path info

When routing, it is common for resources that provide sub-resources to
match a set of URIs all starting with a common prefix, and extract the
rest of the path from the request's `:path-info` entry. This is
achieved by declaring a __:path-info?__ entry in the
__resource-model__ set to true.

```clojure
(resource
  {:path-info? true
   :sub-resource
   (fn [ctx]
     (let [path-info (get-in ctx [:request :path-info])]
       (resource {…})))})
```

(For a good example of sub-resources, readers are encouraged to examine
the code for `yada.resources.file-resource` to see how yada serves the
contents of directories.)

## Example 6: File server

[coming soon]



## Reference

### Resource model schema

This is defined in `yada.schema`.

### Handler schema

[coming soon]

### Request context schema

[coming soon]

### Protocols

yada defines a number of protocols. Existing Clojure types and records
can be extended with these protocols to adapt them to use with yada.

### `yada.protocols.ResourceCoercion`

[coming soon]

### `yada.methods.Method`

Every HTTP method is implemented as a type which extends the
yada.methods.Method protocols. This way, new HTTP methods can be
added. Each type must implement the correct semantics for the method,
although yada comes with a number of built-in methods for each of the
most common HTTP methods.

Many method types define their own protocols so that resources can also
help determine the behaviour. For example, the built-in `GetMethod` type
uses a `Get` protocol to communicate with resources. The exact semantics
of this additional protocol depend on the HTTP method semantics being
implemented. In the `Get` example, the resource type is asked to return
its state, from which the representation for the response is produced.

### `yada.body.MessageBody`

Message bodies are formed from data provided by the resource, according
to the representation being requested (or having been negotiated). This
removes a lot of the formatting responsibility from the resources, and
this facility can be extended via this protocol for new message body
types.

### Built-in types

There are numerous types already built into yada, but you can also add
your own. You can also add your own custom methods.

#### Files

The `yada.resources.file-resource.FileResource` exposes a single file in the file-system.

The record has a number of fields.

<table class="table">
<thead>
<tr>
<th>Field</th>
<th>Type</th>
<th>Required?</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>`file`</td>
<td>`java.io.File`</td>
<td>yes</td>
<td>The file in the file-system</td>
</tr>
<tr>
<td>`reader`</td>
<td>Map</td>
<td>no</td>
<td>A file reader function that takes the file and selected representation, returning the body</td>
</tr>
<tr>
<td>`representations`</td>
<td>A collection of maps</td>
<td>no</td>
<td>The available representation combinations</td>
</tr>
</tbody>
</table>

The purpose of specifying the `reader` field is to apply a
transformation to a file's content prior to forming the message
payload.

For instance, you might decide to transform a file of markdown text
content into HTML. The reader function takes two arguments: the file and
the selected representation containing the media-type.

```clojure
(fn [file rep]
  (if (= (-> rep :media-type :name) "text/html")
    (-> file slurp markdown->html)
    ;; Return unprocessed
    file))
```

The reader function can return anything that can be normally returned in
the body payload, including strings, files, maps and sequences.

The `representation` field indicates the types of representation the
file can support. Unless you are specifying a custom `reader` function,
there will usually only be one such representation. If this field isn't
specified, the file suffix is used to guess the available representation
metadata. For example, a file with a `.png` suffix will be assumed to
have a media-type of `image/png`.

#### Directories

The `yada.resources.file-resource.DirectoryResource` record exposes a directory in a filesystem as a collection of read-only web resources.

The record has a number of fields.

<table class="table">
<thead>
<tr>
<th>Field</th>
<th>Type</th>
<th>Required?</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>`dir`</td>
<td>`java.io.File`</td>
<td>yes</td>
<td>The directory to serve</td>
</tr>
<tr>
<td>`custom-suffices`</td>
<td>Map</td>
<td>no</td>
<td>A map relating file suffices to field values of the corresponding FileResource </td>
</tr>
<tr>
<td>`index-files`</td>
<td>Vector of Strings</td>
<td>no</td>
<td>A vector of strings considered to be suitable to represent the index</td>
</tr>
</tbody>
</table>

A directory resource not only represents the directory on the
file-system, but each file resource underneath it.

The `custom-suffices` field allows you to specify fields for the
FileResource records serving files in the directory, on the basis of the
file suffix.

For example, files ending in `.md` may be served with a FileResource with a reader that can convert the file content to another format, such as `text/html`.

```clojure
(yada.resources.file-resource/map->DirectoryResource
  {:dir (clojure.java.io "talks")
   :custom-suffices {"md" {:representations [{:media-type "text/html"}]
                           :reader markdown-reader}}})
```

## Glossary
