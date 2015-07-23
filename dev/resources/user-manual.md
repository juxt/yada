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
low-level programming models are. But there are significant downsides to
this model too. HTTP is a big specification, and it is unreasonable to
expect developers to have the time to implement all the relevant pieces
of it. What's more, many developers tend to implement much the same code
over and over again, for each and every 'operation' they write.

A notable variation on this programming model can be found in Erlang's
WebMachine and Clojure's Liberator. To a degree, these libraries ease
the burden on the developer by orchestrating the steps required to build
a response to a web request. However, developers are still required to
understand the state transition diagram underlying this orchestration if
they are to successfully exploit these libraries to the maximum
extent. Fundamentally, the programming model is the same: the developer
is still writing code with a view to forming a response at the protocol
level.

It is time for a fresh approach, one that replaces the 'operational'
view of web 'services' with a strong _data-oriented_ approach,
emphasising the view of _state_ at the heart of a web _resource_.

## Introduction

### What is yada?

yada is a Clojure library that lets you expose state to the web over HTTP.

With yada, many things you would expect to have to code yourself and
taken care of automatically, leaving you time to focus on other aspects
of your application. And you end up with less networking code to write
and maintain.

It gives you the option to exploit the asynchronous features of modern web servers, to achieve greater scaleability for Clojure-powered your websites and APIs.

Above all, yada is data-centric, letting you specify your web resources
as data. This has some compelling advantages, such as being able to
transform that data into other formats, such as
[Swagger](http://swagger.io) specifications for API documentation.

## A tutorial: Hello World!

Let's introduce yada properly by writing some code. Let's start with some state, a string: `Hello World!`. We'll be able to give an overview of many of yada's features using this simple example.

We pass the string as a single argument to yada's `yada` function, and yada returns a web _resource_.

```clojure
(yada "Hello World!")
```

This web resource can be used as a Ring handler, for example, in a
Compojure route defintion.

```clojure
(use 'compojure.core
     'ring.adapter.jetty)

(run-jetty
  (GET "/hello" []
    (yada "Hello World!")))
```

Once we have bound this handler to the path `/hello`, we're able to make the following HTTP request :-

```nohighlight
curl -i http://{{prefix}}/hello
```

and receive a response like this :-

```http
HTTP/1.1 200 OK
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: {{now.date}}
Last-Modified: {{hello.date}}
Content-Type: text/plain;charset=utf-8
Vary: accept-charset
Content-Length: 13

Hello World!
```

Let's examine this response in detail.

The status code is `200 OK`. We didn't have to set it explicitly in
code, yada inferred the status from the request and the resource.

The first three response headers are added by our webserver, [Aleph](https://github.com/ztellman/aleph).

```http
Server: Aleph/0.4.0
Connection: Keep-Alive
Date: {{now.date}}
```

(Note that currently, __Aleph is the only web server that yada supports__.)

Next we have another date.

```http
Last-Modified: {{hello.date}}
```

The __Last-Modified__ header shows when the string `Hello World!` was
created. As Java strings are immutable, yada is able to deduce that the
string's creation date is also the last time it could have been
modified.

Next we have a header telling us the media-type of the string's
state. yada is able to determine that the media-type is text, but
without more clues it must default to
`text/plain`.

It is, however, able to offer the body in numerous character set
encodings, thanks to the Java platform. The default character set of the
Java platform serving this resource is UTF-8, so yada inherits that as a
default.

```http
Content-Type: text/plain;charset=utf-8
```

Since the Java platform can encode a string in other charsets, yada uses the _Vary_ header to signal to the user-agent (and caches) that the body could change if a request contained an _Accept-Charset_ header.

```http
Vary: accept-charset
```

Next we are given the length of the body, in bytes.

```http
Content-Length: 13
```

In this case, the count includes a newline.

Finally we see our response body.

```nohighlight
Hello World!
```

### A conditional request

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
request was unacceptable. The is also a __Allow__ header, telling us
which methods are allowed. One of these methods is OPTIONS. Let's try this.

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
(yada (atom "Hello World!"))
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

### A HEAD request

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

(Note that unlike Ring's implementation of `HEAD`
in `ring.middleware.head/wrap-head`, yada's implementation does not cause a
response body to be generated and then truncated. This means that HEAD requests in yada are fast and inexpensive.)

### Parameterized Hello World!

### An attempt to get the string gzip compressed

[todo]

### An attempt to get the string in Chinese

[todo - well, yada isn't _that_ clever, at least not from just a string. But let's see how we would code it using Google Translate - defer this to another chapter introducing async, and we can offer the Chinese version in Shift_JIS encoding too!]

### Swagger

[todo]

### Summary

This simple example demonstrated how a rich and functional HTTP resource
was created with a tiny amount of code.

We get so much behaviour for free, simply by raising our programming
model abstraction.

But this example has barely scratched the surface of what yada can do,
as you will learn in the next few chapters.

## Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "{{yada.version}}"]
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


## Functions

```clojure
(yada (fn [ctx] ...))
```

## Async

[Hello World! in Chinese]

## Resources - under the hood

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

(extend-protocol ResourceConstructor
  String
  (make-resource [s]
    (->StringResource s (to-date (now)))))
```

Recall the _Hello World!_ example.

```clojure
(yada "Hello World!")
```

yada calls `make-resource` on the argument. This declaration causes a new instance of the `StringResource` record to be created.

```clojure
(extend-protocol ResourceConstructor
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

---

### Dynamic responses

The values in resource description can be constant values, as we have already seen. But typically the body of a response will be created dynamically, on a per-request basis, depending on the current state of the resource and/or parameters passed in the request. In this case, a Clojure function is used.

```clojure
(def ^{:doc "A handler to greet the world!"}
  hello
  (yada {:body (fn [ctx] "Hello World!")}))
```

The function takes a single argument known as the _request context_. It is an ordinary Clojure map containing various data relating to the request. Request contexts will be covered in more detail later on.

<example ref="DynamicHelloWorld"/>

### Feature list



That's the end of the introduction. The following chapters explain yada in more depth :-

<toc drop="1"/>

## Parameters

Many web requests contain parameters, which affect how a resource behaves. Often parameters are specified as part of the URI's query string. But parameters can also be inferred from the URI's path. It's also possible for a request to contain parameters in its headers or body, as we'll see later.

For example, let's imagine we have a fictional URI to access the transactions of a bank account.

```nohighlight
https://bigbank.com/accounts/1234/transactions?since=tuesday
```

There are 2 parameters here. The first, `1234`, is contained in the
path `/accounts/1234/transactions`. We call this a _path parameter_.

The second, `tuesday`, is embedded in the URI's query string (after
the `?` symbol). We call this a _query parameter_.

yada allows you to declare both these and other types of parameter via the __:parameters__ entry in the resource description.

Parameters must be specified for each method that the resource supports. The reason for this is because parameters can, and often do, differ depending on the method used.

For example, below we have a resource description that defines the parameters for requests to a resource representing a bank account. For `GET` requests, there is both a path parameter and query parameter, for `POST` requests there is the same path parameter and a body.

We define parameter types in the style of [Prismatic](https://prismatic.com)'s
excellent [schema](https://github.com/prismatic/schema) library.

```clojure
(require [schema.core :refer (defschema)]

(defschema Transaction
  {:payee String
   :description String
   :amount Double}

{:parameters
  {:get {:path {:account Long}
         :query {:since String}}
   :post {:path {:account Long}
          :body Transaction}}}
```

But for `POST` requests, there is a body parameter, which defines the entity body that must be sent with the request. This might be used, for example, to post a new transaction to a bank account.

We can declare the parameter in the resource description's __:parameters__ entry. At runtime, these parameters are extracted from a request and  added as the __:parameters__ entry of the _request context_.

Let's show this with an example.

<example ref="ParameterDeclaredPathQueryWithGet"/>

<!-- <example ref="PathParameterRequired"/> -->

Now let's show how this same resource responds to a `POST` request.

<example ref="ParameterDeclaredPathQueryWithPost"/>

### Form parameters

<example ref="FormParameter"/>

### Header parameters

<example ref="HeaderParameter"/>

### old

Let's show how these types work with another example :-

<!-- <example ref="PathParameterCoerced"/> -->

If we try to coerce a parameter into a type that it cannot be coerced into, the parameter will be given a null value. If the parameter is specified as required, this will represent an error and produce a 400 response. Let's see this happen with an example :-

<!-- <example ref="PathParameterCoercedError"/> -->
Query parameters can be defined in much the same way as path parameters.
The difference is that while path parameters distinguish between
resources, query parameters are used to influence the representation
produced from the same resource.

Query parameters are encoding in the URI's _query string_ (the
part after the `?` character).

<!-- <example ref="QueryParameter"/> -->

<include type="note" ref="ring-middleware"/>

<!-- <example ref="QueryParameterDeclared"/> -->

We can declare a query that query parameter is required, or otherwise accept when it isn't given (which is the default case).

<!-- <example ref="QueryParameterRequired"/> -->

<!-- <example ref="QueryParameterNotRequired"/> -->

<!-- <example ref="QueryParameterCoerced"/> -->

Parameter validation is one of a number of strategies to defend against
user agents sending malformed requests. Using yada's parameter coercion,
validation can actually reduce the amount of code in your implementation
since you don't have to code type transformations.

### Bypassing

Finally, let's see how we could extract a path parameter without declaring it.

<example ref="PathParameterUndeclared"/>

### Benefits to declarative parameter declaration

Declaring your parameters in resource descriptions comes with numerous advantages.

- Parameters are declared with types, which are automatically coerced thereby eliminating error-prone conversion code.

- The parameter value will be automatically coerced to the given type. This eliminates the need to write error-prone code to parse and convert parameters into their desired type.

- Parameters are pre-validated on every request, providing some defence against injection attacks. If the request if found to be invalid, a 400 response is returned.

- Parameter declarations can help to document the API — this will be covered later in the chapter on [Swagger](#Swagger).

## Asynchronous responses

Under normal circumstances, with Clojure running on a JVM, each request can be processed by a separate thread.

However, sometimes the production of the response body involves making requests
to data-sources and other activities which may be _I/O-bound_. This means the thread handling the request has to block, waiting on the data to arrive from the IO system.

For heavily loaded or high-throughput web APIs, this is an inefficient
use of precious resources. In recent years, this problem has been
addressed by using a asynchronous I/O. The request thread
is able to make a request for data via I/O, and then is free to carry out
further work (such as processing another web request). When the data
requested arrives on the I/O channel, another thread carries on when the
original thread left off.

As a developer, yada gives you fine-grained control over when to use a synchronous
programming model and when to use an asynchronous one.

<include type="note" ref="kv-args"/>

#### Deferred values

A deferred value is simply a value that may not yet be known. Examples
include Clojure's futures, delays and promises. Deferred values are
built into yada. For further details, see Zach Tellman's
[manifold](https://github.com/ztellman/manifold) library.

In almost all cases, it is possible to specify _deferred values_ in a
resource description.

Let's see this in action with another example :-

<example ref="AsyncHelloWorld"/>

The sleep is exaggerated but the delay that the web client would
experience is real. In a real-world application, however, the ability to
use an asynchronous model is very useful for techniques to improve
scalability. For example, in a heavily loaded server, I/O operations can
be queued and batched together. Performance may be slightly worse for
each individual request, but the overall throughput of the web server
can be significantly improved.

Normally, exploiting asynchronous operations in handling web requests is
difficult and requires advanced knowledge of asynchronous programming
techniques. In yada, however, it's easy.

<include type="note" ref="ratpack"/>

## Content Negotiation

<div class="warning"><p>Deprecated, see State section below</p></div>

HTTP has a content negotiation feature which allows a user agent
(browser, device) and a web server to establish the best representation
for a given resource. This may include the mime-type of the content, its
language, charset and transfer encoding.

We shall focus on the mime-type of the content.

There are multiple ways to indicate which content-types can be provided by an implementation. However, as we are discussing the __:body__ entry, we'll first introduce how the implementation can use a map between content-types and bodies.

<example ref="BodyContentTypeNegotiation"/>
<example ref="BodyContentTypeNegotiation2"/>

## State

REST is about resources which have state, and representations that are
negotiated between the user agent and the server to transfer that state.

In fact, we can think of the world-wide web as a being a solution to a
single problem: the distribution of state.

[Explain Clojure's model of quantised state - state changes are modelled as snapshots which are wholly replaced with new versions as time progresses.]

[Explain yada's extensible state protocol, and how it is satisfied by default implementations for java.io.File, clojure.lang.Atom (with watchers) - clojure.lang.Ref, and possibly by datomic.Database]

[Explain the role of schema in constraining the state of a resource, and its role in influencing the defaulting of parameters that yada resources expect]

### Retrieving state

Until now, when we have constructed bodies we have done so explicitly.
While it is possible to explicitly specify the body of a response, doing
so assumes you are prepared to format the content according to the
negotiated content-type.

Instead you can delegate that job to yada, and focus on returning the
logical state of a resource, rather than formatting the content of its
physical representation. Doing this will mean it is easier to support additional content-types.

We provide a __:state__ entry in the map we return from the
__:resource__. This is the resource's state.

If there is no __:body__ entry, the state is automatically serialized
into a suitable format (according to the usual rules for determining
the content's type).

<example ref="State"/>

Of course, if you want full control over the rendering of the body, you
can add a __body__ entry. If you do, you can access the state of the resource returned by the __state__ entry which is available from the `ctx` argument.

If you provide a __body__ entry like this, then you should indicate the
content-type that it produces, since it can't inferred automatically (as
you may choose to override it), and no __Content-Type__ header will be set
otherwise.

<example ref="StateWithBody"/>

A resource which has a non-nil `:state` entry is naturally assumed to
exist. It is therefore unnecessary to provide a non-nil `:resource`
entry, unless you wish to communicate a resource's meta-data.

<example ref="StateWithFile"/>

### Declaring available content-types

You can use the __:produces__ declaration to declare the content types that state can produce.

(This is a different approach that the one we've seen before, with a __:body__ map. It is likely that the __:body__ map approach will be deprecated in a future release.)

<example ref="ProducesContentTypeNegotiationJson"/>

<example ref="ProducesContentTypeNegotiationEdn"/>

### Changing state

HTTP specifies a number of methods which mutate a resource's state.

#### POSTs

Let's start with the `POST` method and review the spec :-

> The `POST` method requests that the target resource process the
representation enclosed in the request according to the resource's own
specific semantics.

This means that we can decide to define whatever semantics we want, or
in other words, do anything we like when processing the request.

Let's see this with an example :-

<example ref="PostCounter"/>

As we have seen, processing of `POST` requests is achieved by adding an __:post__ entry to the resource description. If the value is a function, it will be called with the request context as an argument, and return a value. We can also specify the value as a constant. Whichever we choose, the table below shows how the return value is interpretted.

<table class="table">
<thead>
<tr>
<th>Return value</th>
<th>Interpretation</th>
</tr>
</thead>
<tbody>
<tr><td>true</td><td>Processing succeeded</td></tr>
<tr><td>false</td><td>Processing failed</td></tr>
<tr><td>String</td><td>The path of a newly created resource</td></tr>
<tr><td>Map</td><td>A modified request context</td></tr>
<tr><td></td><td></td></tr>
</tbody>
</table>

When returning a modified request context, a __:location__ entry will be
interpretted as the location of the newly created resource (or location
of the primary resource if multiple resources are created). This
location will be returned as the value of the __Location__ header of the
HTTP response.

#### PUTs

`PUT` a resource. The resource-map returns a resource with an etag which
matches the value of the 'If-Match' header in the request. This means
the `PUT` can proceed.

> If-Match is most often used with state-changing methods (e.g., `POST`, `PUT`, `DELETE`) to prevent accidental overwrites when multiple user agents might be acting in parallel on the same resource (i.e., to the \"lost update\" problem).

<example ref="PutResourceMatchedEtag"/>

<example ref="PutResourceUnmatchedEtag"/>

#### DELETEs

[TODO: Explain how `PUT`s and `DELETE`s return '202 Accepted' on deferred responses]


## Conditional Requests

The Last-Modified header indicates to the user agent the time that the resource was last modified.

This is part of a resource's meta-data which can be return as a Date.

<example ref="LastModifiedHeader"/>

It's perfectly OK to return a number rather than a date for the
resource's __:last-modified__ entry, and the header will still be returned to the user agent as a date.

The number will be interpretted as the number of milliseconds since the epoch (Jan 1st, 1970, UTC).

<example ref="LastModifiedHeaderAsLong"/>

<example ref="LastModifiedHeaderAsDeferred"/>

<example ref="IfModifiedSince"/>

## Service Availability

<example ref="ServiceUnavailable"/>
<example ref="ServiceUnavailableAsync"/>
<example ref="ServiceUnavailableRetryAfter"/>
<example ref="ServiceUnavailableRetryAfter2"/>
<example ref="ServiceUnavailableRetryAfter3"/>

## Validation

<example ref="DisallowedPost"/>
<example ref="DisallowedGet"/>
<example ref="DisallowedPut"/>
<example ref="DisallowedDelete"/>

## Range requests

[This section is a stub, more to follow.]

## Access control

Sometimes we want to protect resources from unauthorized access.

Access to a resource is determined by the __:authorization__ entry.

<example ref="AccessForbiddenToAll"/>

It is more common to determine access based on the incoming request. For example, we may want to forbid unencrypted access to a resource.

<example ref="AccessForbiddenToSomeRequests"/>
<example ref="AccessAllowedToOtherRequests"/>

Sometimes there it is useful to signal to the user-agent that authorization could not be attempted because the user-agent has not supplied authentication details. This allows the user-agent to retry the request with those details added.

To do this, simply return __:not-authorized__ in the __:authorization__ entry.

<example ref="NotAuthorized"/>

The meaning of a 401 depends on the authorization schemes in use. HTTP includes [Basic Access Authentication](http://en.wikipedia.org/wiki/Basic_access_authenticationBasic) to achieve authentication using standard HTTP headers.

To enable this on your resource, you should add a __:security__ entry which declares the type (or types) of security scheme that you want to use to protect your resource. Security schemes are maps which must contain a __:type__ entry. If you use a value of __:basic__, yada will do the rest.

<example ref="BasicAccessAuthentication"/>

Of course, as you should now be expecting from yada, the __:authorization__ function can return a deferred value, such as a future or promise, should you need to check the credentials against a remote database.

## Cross-Origin Resource Sharing

If you are planning to serve your API resources from on server, and have
them consumed from a web application coming from another server, you
will hit a security restriction built in to modern browsers known
[CORS](http://www.w3.org/TR/cors/). CORS requires your API resources to
explicitly state which other origins (other web applications) are
allowed to access your API. This is to ensure your API cannot be
exploited using scripting attacks that use a user's credentials with her
knowledge.

yada supports has built-in support for CORS.

Some resources, such as Google Web Fonts, are designed to be fully public and available to scripts from any origin. For these types of resources, set the __:allow-origin__ entry to true.

<example ref="CorsAll"/>

If you're going to open your resource to all scripts, browsers will not send on any cookies, credentials or anything that would identify the user, since that would be open to abuse by any script on any website.

However, if you want to receive cookies or user credentials, you'll need check the origin provided in the request is from a website you're willing to accept requests from.

<example ref="CorsCheckOrigin"/>

### Preflight requests

If you try to do a mutable action, such as a `PUT`, on server of different origin than your website, then the browser will 'pre-flight' the request.

<example ref="CorsPreflight"/>

## Server sent events

[Server sent events](http://en.wikipedia.org/Server_sent_events) are a useful way of providing real-time notications to clients. Although notifications are one-way, there are numerous benefits if you don't need fully bi-directional socket communication between the user-agent and server. For instance, server sent events are layered over HTTP, so importantly inherit the security aspects of your system, including cookie propagation and CORS protection, as well as content negotiation and error handling.

Server sent events are built into all modern browsers. Server-sent event sources are created in JavaScript like this:-

```javascript
var es =
  new EventSource("{{prefix}}/examples/ServerSentEvents");
```

Once you have access to an event source, you can add listeners to it in exactly the same way you would add listeners to keyboard, mouse and other events in JavaScript.

```javascript
es.onmessage = function(ev) {
  console.log(ev);
};
```

Creating the resource to provide the events to the user-agent is super-easy with yada.

The only thing to do is set the content-type to __text/event-stream__

<example ref="ServerSentEvents"/>

The example above showed a contrived example where the events are static. More usually, you will specify a function returning a _stream_ of events.

A stream can be anything that can produce events, such as a lazy sequence or (usefully) a core.async channel.

Let's demonstrate this with another example.

<example ref="ServerSentEventsWithCoreAsyncChannel"/>

It's also possible to use the __:state__ entry instead of
__:body__. This has the advantage of defaulting to a content-type of
__text/event-stream__ without has having to specify it explicitly.

<example ref="ServerSentEventsDefaultContentType"/>

Of course, all the parts of yada you've learned up until now, such as
access control, can be added to the resource description. (Contrast that with
web-sockets, where you'd have to design and implement your own bespoke
security system.)

## Custom responses

It's usually better to allow yada to set the status and headers automatically, but there are times when you need this control. Therefore, it's possible in a resource description to specify the status directly, with the __:status__ entry, which can be a constant or function.

<example ref="CustomStatus"/>

Setting headers is achieved in the same way, using the __:headers__ entry, and these are merged with the headers that yada automatically includes.

<example ref="CustomHeader"/>

<include type="note" ref="i-am-a-teapot"/>

## Routing

Web applications combine multiple resources to form a website or API
service (or both).

When a web application accepts an HTTP request it extracts the
URI. While part of the URI has been used to locate the application
itself, usually the application will use the rest of the URI to
determine which resource should handle the request. This process is
often referred to as _routing_ or URI _dispatch_.

It is useful to model your service as a hierarchical _route structure_,
where individual resources representing the leaves and the routes to
those resources representing the branches.

Until now, we have made no assumption about the routing library used to route requests to yada handlers. In fact, everything described in previous chapters can be used with any routing library.

<include type="note" ref="web-frameworks"/>

### Integration with Compojure

Here is how you might use yada with Compojure :-

```clojure
(require
  '[compojure.core :refer (GET)]
  '[yada.yada :refer (yada)])

(GET "/users/:userid" [userid]
  (yada
    :body (fn [_]
            (print-str "This is the user id:" userid))))
```

### Integration with bidi

Bidi describes a syntax for a route structure which has certain
advantages, the most obvious being that the logic already exists, in a
tried-and-tested form, to process the route structure and use it to
dispatch to handlers.

Let's show how to integrate yada's resource descriptions into a route structure.

Imagine we have yada-created handler that responds to every request with
a constant body.

```clojure
(yada {:body "API working!"})
```

Suppose we want this yada handler to respond only to requests with the URI `/api/1.0/status`. We can combine this inside a bidi route structure like this:

```clojure
(require 'bidi.bidi 'bidi.ring '[yada.yada :as yada])

;; Define our route structure
(def api
  ["/api/1.0"
    {"/status" (yada/resource {:body "API working!"})
     "/show-ctx" (yada/resource {:body (fn [ctx] (str ctx))})}
])

;; Turn the route structure into a Ring handler
(defn handler (bidi.ring/make-handler api))
```

The `handler` we have created can be used as an argument to various
choices of Ring-compatible web server.

Now, when we send a request to `/api/1.0/status`, the handler will dispatch the request to our handler.

To see this code in action, create a new project based on the modular lein template

```
lein new modular yada-demo yada
cd yada-demo
lein run
```

#### Declaring common resource-map entries

Sometimes, you would like all your resources to share a set of
resource-map entries and it can be tedious and error-prone to declare
the common entries for every resource in the service.

At other times, a sub-tree of resources in the hierarchical routing
structure can be determined which share a common set of resource-map
entries, so you'd like to declare these entries for the group as a
whole, at one of the branches of the routing tree.

Let's suppose we have a couple of entries we want to add to every handler fixed below a certain point in our route structure. In this example, we'll pretend we want to secure part of a website, so we might define these partial resource-map entries here.

```clojure
(def security
  {:security {:type :basic :realm "Protected"}
   :authorization (fn [ctx]
                    (or
                     (when-let [auth (:authentication ctx)]
                       (= ((juxt :user :password) auth)
                          ["alice" "password"]))
                     :not-authorized))})
```

Now when we create our resource structure, we declare these partial entries using the `yada/partial` wrapper.

```clojure
(require '[yada.bidi :refer (resource)]
)

(defn secure [routes]
  (yada.bidi/partial security routes))

(def api
  ["/api"
   {"/status" (resource {:body "API working!"})
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure ; this is all we need to secure
                   {"/a" (yada/resource :body "Secret area A")
                   "/b" (yada/resource :body "Secret area B")})}])
```

Note that we have also replace our usual call to `yada` with its
bidi-compatible version `yada.bidi/resource`. The reason for this is so that
any `yada.bidi/partial` route-map declarations higher up in the route
structure are merged to form the complete resource-map.

`yada.bidi/resource` has another property which makes it useful to use
as part of larger data structure. Rather than returning a function as
`yada` would do, it returns a record instance. This can still be
invoked, just like a function, but the resource-map remains accessible
if the resource is part of a the larger data-structure, as we will see
in the next section.

## Swagger

Given that yada resources are declared as maps, it is useful to compose
multiple yada resources together as part of a larger data structure, one
that also declares the route structure.

If [bidi](https://github.com/juxt/bidi) is used as the routing library,
yada and bidi data can be combined to form enough meta-data about a
resource for create a [Swagger](http://swagger.io/) resource. This is
achieved by wrapper the routes that make up an API with a
`yada.swagger/Swagger` record (or by using the convenience function,
`swaggered`).

Below is a bidi route structure which demonstrates how bidi and yada fit
together.

```clojure
(require '[yada.swagger :refer (swaggered)])

["/api"
    (swaggered
     {:info {:title "User API"
             :version "0.0.1"
             :description "Example user API"}
      :basePath "/api"}
     {"/users"
      {""
       (resource
        ^{:swagger {:get {:summary "Get users"
                          :description "Get a list of all known users"}}}
        {:state (:users db)})

       ["/" :username]
       {"" (resource
            ^{:swagger {:get {:summary "Get user"
                              :description "Get the details of a known user"
                              }}}
            {:state (fn [ctx]
                      (when-let [user (get {"bob" {:name "Bob"}}
                                           (-> ctx :parameters :username))]
                        {:user user}))
             :parameters {:get {:path {:username s/Str}}}})

        "/posts" (resource
                  ^{:swagger {:post {:summary "Create a new post"}}}
                  {:state "Posts"
                   :post (fn [ctx] nil)}

                  )}}})]
```

Clojure metadata is used to annotate yada resources with additional
metadata required by the Swagger spec.

Note how relatively uncluttered the overall data structure is,
considering how it fully defines both routing information and resource
behavior. Note also that there are no clever macros or other magic here,
(except for the record wrappers which just introduce Clojure records
which can be treated as maps).

The entire data structure can be printed, walked, sequenced, navigated
with zippers, and otherwise manipulated by a Clojure's wide array of
functions.

n<include type="note" ref="swagger-implementation"/>

The `swaggered` wrapper introduces a record, `yada.swagger.Swagger`,
which takes part in bidi's route resolution process (thanks to bidi's protocol-based extensibility).

When `/swagger.json` is appending to the path, the record handlers the
request itself, returning a Swagger 2.0 specification, generated by
Tommi Reiman's wonderful
[ring-swagger](https://github.com/metosin/ring-swagger) library.

The specification meets a standard defined by the folks at
[Swagger](http:/swagger.io) which encourages interoperability between
RESTful web APIs and tools.

The
[built-in Swagger UI tool](/swagger-ui/index.html?url=/api/swagger.json)
demonstrates this.

## Testing

Once you have built your API you should want to test it to ensure it is working just as you expect, and there are no nasty surprises lurking in your code.

<div class="warning"><p>Forward looking feature... Coming soon!</p></div>

The data-oriented approach of yada makes it possible to generate traffic designed to exercise your code in ways that will try to cause it to error.

To create tests, add the following dependency to the dependencies in your project's `project.clj` (ideally in the test scope)

```clojure
(defproject
...
  :profiles
    {:test
      {:dependencies
        [
          [yada/test "{{yada.version}}"]
        ]}})
```

Here is an example of what to write in your project's test code.

```clojure
(def my-resource {:body "Hello World!"})

(deftest api-test
  (yada/test my-resource
             {:trials 2000
              :protocol :http
              }))
```




## Alternatives

[this is a stub, to discuss how yada differs from other alternative libraries and approaches]

### Differences with Ring

Most Clojure web applications are composed of 'plain old' Ring handlers with 'plain old' Ring middleware.

Ring middleware is remarkably simple and powerful.

[history: webrick, etc.]

[ring middleware]

### Differences with Liberator
### Differences with compojure-api
### Differences with Pedestal
### Differences with fn-house

## Concluding remarks

In this user-manual we have seen how yada can help create powerful
RESTful APIs with a declarative data-centric syntax, and how the
adoption of a declarative data format (rather than functional
composition) allows us to easily extend yada's functionality in various
ways.

Although yada is flexible, it is also powerful. Asynchronous operation
can be enabled wherever required, with fine-grained control residing
with the user, using futures and promises, avoiding the need for
deeply-nested code full of callback functions.
