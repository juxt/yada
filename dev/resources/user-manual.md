# The yada manual

Welcome to the yada manual!

This manual corresponds with version 1.1.0-20151228.110457-4

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

yada is a library that lets you develop and deploy web resources that
are fully compliant with, and thereby taking full advantage of, HTTP
specifications.

Let's start then by defining a _web resource_. A web resource is
identified and located by a URI. It responds to requests it receives
according to the request's method. Usually it produces or consumes
state.

In yada, resources are defined by a **resource model**, backed by a
map.

Here's an example of how a particular **resource model** might be
written in Clojure:-

```clojure
{:properties {…}
 :methods {:get {:response (fn [ctx] "Hello World!")}
           :put {…}
           :brew {…}}
 …
}
```

You can use Java or any JVM language to create these **resource
models**. One benefit of using Clojure is that it offers a large
number of ways to be generate, derive and transform maps. This gives
you the maximum flexibility in how your web resources are developed.

A resource can be created from a map using yada's `resource` function,
which just validates the given map, coercing any parameters as
necessary, and wrapping in a record indicating a **resource**.

yada's eponymous function `yada` takes a single parameter (the
resource) and returns a **handler**. This is both a _function_
that can be used to create responses from Ring requests, and a
_data-model_ that can be further modified (if desired).

```clojure
(require '[yada.yada :refer [yada resource]])

(yada (resource {…}))
```

Finally, yada is built on a fully asynchronous core, bringing high
performance and scalability to your websites and APIs.

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
a **resource model** (a _map_). Well, that's true, but yada has some
built-in code that transforms the string into a resource-model. Don't
worry about that for now, we'll discuss it more later).

You can see the result of this at
[http://localhost:8090/hello](http://localhost:8090/hello).

By combining this handler with a web-server, we can start the service.

Here's how you can start the service using
[Aleph](https://github.com/ztellman/aleph).

(Note that you are free to choose any yada -compatible web server, as
long as it's Aleph! Joking aside, as more web servers support
end-to-end asynchronicity with back-pressure, all the way up to the
application, then yada will support those. Currently Aleph is the only
web server we know that offers this).

```clojure
(require '[yada.yada :refer [yada]]
         '[aleph.http :refer [start-server]])

(start-server
  (yada "Hello World!\n")
  {:port 3000})
```

Alternatively, you can following along by referencing and
experimenting with the code in `dev/src/yada/dev/hello.clj`. See the
[installation](#Installation) chapter.

Once we have bound this handler to the path `/hello`, we're able to make
the following HTTP request :-

```nohighlight
curl -i http://localhost:3000/hello
```

and receive a response like this :-

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

To show this, we'll write some real code :-

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

There is a lot more to content negotiation than this simple example can
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
types, as we shall discover in subsequent chapters. But first, let's see
how to install and integrate yada in your web app.

## Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "1.1.0-SNAPSHOT"]
[aleph "0.4.1-beta3"]
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

[coming soon]

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

There are numerous benefits to declaring the parameters you use.

- yada will check they exist, and return 400 (Malformed Request) errors on
requests that don't provide the ones you need for your logic
- yada will coerce them to the types you want, so you can avoid writing loads of
type-conversion logic in your code
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

## Representations

Resources have state, but when this state needs to be transferred from
one host to another, we use one of a number of formats to represent
it. We call these formats _representations_.

A given resource may have a large number of actual or possible
representations.

Representation may differ in a number of respects, including :-

- the media-type ('file format').
- if textual, the character set used to encode it into octets
- the (human) language used (if textual)
- whether and how the content is compressed

Whenever a user-agent requests the state from a resource, a particular
representation is chosen, either by the server (proactive) or client
(reactive). The process of choosing which representation is the most
suitable is known as
[_content negotiation_](/spec/rfc7231#section-3.4).

Content negotiation is an important feature of HTTP, allowing clients
and servers to agree on how a resource can be represented to best meet
the availability, compatibility and preferences of both parties. It is
a key factor in the survival of services over time, since both new and
legacy formats can be supported concurrently.

### Proactive negotiation

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

This is a short-hand for writing.


```clojure
{:produces [{:media-type "text/plain"
             :language #{"en" "zh-ch"}
             :charset "UTF-8"}
            {:media-type "text/plain"
             :language "zh-ch"
             :charset "Shift_JIS;q=0.9"}]

(yada
  (fn [ctx]
    (case (get-in ctx [:response :representation :language])
            "zh-ch" "你好世界!\n"
            "en" "Hello World!\n"))
  :representations )
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

The second type of negotiation is termed _reactive negotiation_ where the
agent chooses from a list of representations provided by the server.

## Properties

[coming soon]

## Methods

[coming soon]

## Security

As in all other areas, yada aims for 100% compliance with core HTTP
standards when it comes to security, notably
[RFC 7235](https://tools.ietf.org/html/rfc7235). Also, since HTTP APIs
are nowadays used to facilitate transactional integration between
systems via the user's browser, it is critically important that yada
fully supports applications that want to open up services to other
applications, across origins, as standardised by
[CORS](http://www.w3.org/TR/cors/).

With security, it is important to understand the concepts, processes
and standards in detail. While yada can help with good security
defaults and attempts to make security configuration easier, it is
still important that you are familiar with security concepts, so read
this chapter carefully.

In yada, authentication and authorization are broken into 2 separate
stages. We'll deal with these stages in turn, before discussing other
security features.

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
can provide yourself. Here is an example of a resource which uses Basic
authentication described in [RFC 2617](https://www.ietf.org/rfc/rfc2617.txt)

```clojure
{:authentication
  {:realms
    {"my-realm"
     {:schemes
      [{:scheme "Basic"
        :authenticator (fn [user password] …}]}}}}
```

Each scheme has an authenticator, usually a function (depending on the
scheme). If the authenticator returns truthy, the request is deemed to
be authentic and the return value is retained in the request
context. Therefore, the return value should contain user profile
information and any role(s) that might be used in the later
authorization stage, in the special :role entry.

```clojure
(fn [user password]
  …
  {:email "bob@acme.com"
   :role #{:admin}})
```

If the authenticator returns nil, then this does not mean the request
is not authorized. It may be that access to the particular resource is
still possible without authenticated credentials. However, the
response will contain information about the authentication schemes in
place, via the WWW-Authenticate header, as per RFC 7235.

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
{:methods {:post {:response (fn [ctx] …)
                  :role :admin}}}
```

Of course, authentication information is available in the request
context when a method is invoked, so any method may apply its own
custom authorization logic as necessary. However, yada encourages
developers to adopt a declarative approach to resources wherever
possible, to maximise the integration opportunities with other
libraries and tools.

### Cross-Origin Resource Sharing (CORS)

[coming soon]

## Routing

Since the `yada` function returns a Ring-compatible handler, it is compatible with any Clojure URI router.

However, yada is designed to work seamlessly with its sister library
[bidi](https://github.com/juxt/bidi), and unless you have a preference
otherwise, bidi is recommended.

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

### A quick introduction to bidi

[coming soon]

### Adding policies

Since we are using a data-structure for your routes, we can walk the tree to
find yada resources which, as data, can be augmented according to any
policies we may have for a particular sub-tree of routes.

Let's demonstrate this with an example. Here is a data structure
compatible with the bidi routing library.

```clojure
(require '[yada.walk :refer [basic-auth]])

["/api"
   {"/status" (yada "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (basic-auth
                  "Protected" (fn [ctx]
                                (or
                                 (when-let [auth (:authentication ctx)]
                                   (= ((juxt :user :password) auth)
                                      ["alice" "password"]))
                                 :not-authorized))
                  {"/a" (yada "Secret area A")
                   "/b" (yada "Secret area B")})}]
```

The 2 routes, `/api/protected/a` and `/api/protected/b` are wrapped with
`basic-auth`. This function simply walks the tree and augments each yada
resource it finds with some additional handler options, effectively
securing both with HTTP Basic Authentication.

If we examine the source code in the `yada.walk` namespace we see that
there is no clever trickery involved, merely a `clojure.walk/postwalk`
of the data structure below it to update the resource leaves with the
given security policy.

It is easy to envisage a number of useful functions that could be
written to transform a sub-tree of routes in this way to provide many
kinds of additional functionality. This is the advantage that choosing a
data-centric approach gives you. When both your routes and resources are
data, they are amenable to programmatic transformation, making our
future options virtually limitless.

## Example 2: Phonebook

We have covered a lot of ground so far. Let's consolidate our knowledge by building a simple application, using all the concepts we've learned so far.

We'll build a simple phonebook application. Here is a the brief :-

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
to data-sources and other activities which may be _I/O-bound_. This means the thread handling the request has to block, waiting on the data to arrive from the IO system.

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

It's _really_ easy to create Server Sent Event streams with yada. All
you need to do is return a response that embodies a stream of data.

One such example is a channel, provided by Clojure's core.async
library.

```clojure
{:methods {:get {:produces "text/event-stream"
                 :response (clojure.core.async/chan)}}}
```

It is, however, highly unusual to want to provide a channel of data to
a single client. Typically, what is required is that each client gets
a copy of every message in the channel. This can be achieved easily by
multiplexing the channel with `clojure.core.async/mult`. By providing
a core.async Mult as a response body, yada can tap the mult.

```clojure
(let [mlt (clojure.core.async/mult channel)]
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

## The request context

[coming soon]

## Interceptors

The interceptor chain, established on the creation of a handler, is a vector.

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

## Subresources

[coming soon]

## Example 6: File server

[coming soon]



## Reference

### Resource model schema

[coming soon]

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
