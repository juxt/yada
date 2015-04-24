# The yada user guide

Welcome to the yada user guide!

This guide is ideal if you are new to yada and is suitable for anyone
who is interested in building robust RESTful web APIs quickly and
easily. Previous experience of web development in Clojure may be helpful
but is not mandatory.

### yada is simple, but also easy!

If you follow this guide carefully you will learn how to take advantage
of the many features yada has to offer.

This is a BIG guide. If you are feeling a little overwhelmed at how much
there is to learn, don't worry, yada really is easy! Start by going
through the introduction at a gentle pace and take on the subsequent
chapters one at a time.

If you are a more experienced Clojure or REST developer and would like
to get involved in influencing yada's future, please join our
[yada-discuss](https://groups.google.com/forum/#!forum/yada-discuss)
discussion group. Regardless of your experience, everyone is more than
welcome to join the list. List members will do their best to answer any
questions you might have.

### Spot an error?

If you spot a typo, misspelling, grammar problem, confusing text, or
anything you feel you could improve, please go-ahead and
[edit the source](https://github.com/juxt/yada/edit/master/dev/resources/user-guide.md). If
your contribution is accepted you will have our eternal gratitude, help
future readers and be forever acknowledged in the yada documentation as
a contributor!

[Back to index](/)

### Table of Contents

<toc drop="0"/>

## Introduction

A web API is a set of web resources that are accessed by clients, called
_user agents_, using the HTTP protocol. Typically, a developer will
build a web API to expose the functionality of a software system to
users and other agents across the web.

HTTP is a large and powerful protocol — yada is a library that helps
reduce the amount of coding required for meeting its requirements, while
leaving you as the developer in full control.

### Where yada fits

There is a great number of libraries available to Clojure programmers to
help develop web applications. Let's explain where yada fits into a
Clojure web application and why you might want to use it in yours.

During a web request, a browser (or other user agent) establishes a
connection with a web server and sends a request encoded according to
the HTTP standard. The server decodes the request and builds a ordinary
Clojure map, called a _request map_, which it passes as an argument to a
Clojure function, called a _handler_. The
[Ring](https://github.com/ring) project establishes a standard set of
keywords so that numerous compatible web servers can fulfill this rôle,
either natively (e.g. aleph, http-kit) or via a bridge (e.g. Jetty, or
any Java servlet container).

#### First we route to the target handler...

Typically, the handler that the web server calls looks at the URI
contained in the request and delegates the request to another
handler. We say that the handler _dispatches_ the request, based on the
URI. While this routing function can be written by the Clojure
developer, there exist a number of libraries dedicated to this task
which can be used with yada.

The target handler is responsible for returning a ordinary Clojure map,
called a _response map_. The Ring standard states that such responses should contain a __:status__ entry indicating the HTTP response code, an optional __:headers__ entry containing the response headers, and an optional __:body__ entry containing the response's entity body.

#### Then we code the response...

Usually, the target handler is developed by the application developer. But with yada, the application developer passes a ordinary Clojure map, called a _resource map_ to a special yada function, `yada/handler`, which returns the target handler.

If you are unfamiliar with web development in Clojure, let's explain
that again using some basic Clojure code. Here is a simple Ring-compatible handler function :-

```clojure
(defn hello "A handler to greet the world!" [req]
  {:status 200, :body "Hello World!"})
```

This declares a function (in the var `hello`) that accepts a single argument (`req`) known as the _request map_. The implementation of the function returns a literal map, using Clojure's `{}` syntax. (By the way, commas are optional in Clojure).

Compare this with using yada to create a handler :-

```clojure
(require '[yada.yada :refer (yada)])

(def ^{:doc "A handler to greet the world!"}
  hello
  (yada {:body "Hello World!"}))
```

The code above calls a function built-in to yada called `yada`, with
a single argument known as a _resource map_. In this case, the
resource map looks strikingly similar to a Ring response map but don't
be deceived — there is a lot more going on under the hood as we shall
soon see.

<include type="note" ref="modular-template-intro"/>

### Resource maps

Let's look in more detail at the _resource map_ you pass to yada's `yada` function to create a Ring handler. This map is an ordinary Clojure map. Let's demonstrate using the example we have already seen.

<example ref="HelloWorld"/>

We'll use more examples like that to show different resource maps that demonstrate all the features of yada. (If you want to experiment further, why not take a minute to [create](#modular-template-intro) your own test project?)

<include type="note" ref="liberator"/>

Resources maps are the key to understanding yada. They are used to
define all the ways that a handler should respond to a web request.

### Dynamic responses

In the previous example, the __:body__ entry was declared as a string value. But often the body will be created dynamically, depending on the current state of the resource and/or parameters passed in the request. We need some way to vary the body at runtime. We simply replace the string value in the resource map with a function.

<example ref="DynamicHelloWorld"/>

The function in the example above takes a single argument known as the _request context_. It is an ordinary Clojure map containing various data relating to the request. Request contexts will be covered in more detail later on.

### Asynchronous responses

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

#### Deferred values

A deferred value is a simply value that may not yet be known. Examples
include Clojure's futures, delays and promises. Deferred values are
built into yada. For further details, see Zach Tellman's
[manifold](https://github.com/ztellman/manifold) library.

In almost all cases, it is possible to specify _deferred values_ in a
resource map.

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

Here is a list of yada's features :-

- Easy to create RESTful API and content services
- Support for both synchronous and asynchronous programming approaches
- Parameter validation and coercion
- Content negotiation
- Cache control
- Automatic rendering of resources into representations
- Support for [Swagger](http:/swagger.io)
- High performance

With yada, many things you would expect to have to code yourself and taken care of automatically, leaving you to focus on other aspects of your application.

That's the end of the introduction. The following chapters explain yada in more depth :-

<toc drop="1"/>

## Installation

yada is a Clojure library and if you are using it directly from a
Clojure application with a Leiningen `project.clj` file, include the
following in the file's __:dependencies__ section.

```clojure
[yada "{{yada.version}}"]
```

## Parameters

Parameters are an integral part of many web requests. Since APIs form
the basis of integration between software, it is useful to be able to
declare parameter expectations.

These expectations form the basis of a contract between a user-agent and
server. If a user-agent does not meet the contract set by the API, the
server can respond with a 400 status code, indicating to the user-agent
that the request was malformed.

### Path parameters

Let's start with path parameters, which are values that are extracted
from the URI's path.

<example ref="PathParameter"/>

We can also declare the parameter in the resource map by adding a __:params__ entry whose value is a map between keys and parameter definitions.

Each value in the map of parameter definitions is a map which must declare where a parameter is found, under a __:in__ entry. Valid values are __:path__, __:query__ and __:form__.

When types are declared, they are extracted from the request and made
available via the __:params__ entry in the _request context_.

<example ref="PathParameterDeclared"/>

If we declare that parameter is required, yada will check that the
parameter exists and return a 400 (Malformed Request) status if it does
not.

We specify that a parameter is required by setting __:required__ to `true` in the parameter's definition map.

<example ref="PathParameterRequired"/>

By default, parameters are extracted as strings. However, it is often useful to declare the type of a parameter by including a __:type__ entry in the parameter's definition map.

The value of __:type__ is interpreted by Prismatic's Schema library. Below is a table listing examples.

<table class="table">
<thead>
<tr>
<th>:type</th>
<th>Java type</th>
</tr>
</thead>
<tbody>
<tr><td>schema.core/Str</td><td>java.lang.String (the default)</td></tr>
<tr><td>schema.core/Int</td><td>java.lang.Integer</td></tr>
<tr><td>schema.core/Keyword</td><td>clojure.lang.Keyword</td></tr>
<tr><td>Long</td><td>java.lang.Long</td></tr>
<tr><td>schema.core/Inst</td><td>java.util.Date</td></tr>
</tbody>
</table>

Remember, Clojure automatically boxes and unboxes Java primitives, so you can treat a `java.lang.Integer` value as a Java `int` primitive.

Let's show how these types work with another example :-

<example ref="PathParameterCoerced"/>

If we try to coerce a parameter into a type that it cannot be coerced into, the parameter will be given a null value. If the parameter is specified as required, this will represent an error and produce a 400 response. Let's see this happen with an example :-

<example ref="PathParameterCoercedError"/>

### Query parameters

Query parameters can be defined in much the same way as path parameters.
The difference is that while path parameters distinguish between
resources, query parameters are used to influence the representation
produced from the same resource.

Query parameters are encoding in the URI's _query string_ (the
part after the `?` character).

<example ref="QueryParameter"/>

<include type="note" ref="ring-middleware"/>

<example ref="QueryParameterDeclared"/>

<example ref="QueryParameterCoerced"/>

Parameter validation is one of a number of strategies to defend against
user agents sending malformed requests. Using yada's parameter coercion,
validation can actually reduce the amount of code in your implementation
since you don't have to code type transformations.

In summary, there are a number of excellent reasons to declare parameters :-

- the parameter will undergo a validity check, to ensure the client is sending something that can be turned into the declared type.
- the parameter value will be automatically coerced to the given type. This means less code to write.
- the parameter declaration can be used in the publication of API documentation — this will be covered later in the chapter on [Swagger](#Swagger).

## Resource Metadata

The __:resource__ entry allows us to return meta-data about a resource.

The most basic meta-data we can indicate about a resource is whether it exists. We can do this by returning a simple boolean.

<example ref="ResourceExists"/>

In some cases it is possible to say whether a given resource exists. This is especially true for _singleton_ resources.

There are many cases, however, where the existence of a resource can only be determined with reference to some parameters in the request.

<example ref="ResourceFunction"/>

<example ref="ResourceExistsAsync"/>
<example ref="ResourceDoesNotExist"/>
<example ref="ResourceDoesNotExistAsync"/>

We can also return other resource meta-data to let yada construct the
proper HTTP response headers in order to exploit opportunities for
caching and other HTTP features.

## Content Negotiation

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

<example ref="ResourceState"/>

Of course, if you want full control over the rendering of the body, you
can add a __body__ entry. If you do, you can access the state of the resource returned by the __state__ entry which is available from the `ctx` argument.

If you provide a __body__ entry like this, then you should indicate the
content-type that it produces, since it can't inferred automatically (as
you may choose to override it), and no __Content-Type__ header will be set
otherwise.

<example ref="ResourceStateWithBody"/>

A resource which has a non-nil `:state` entry is naturally assumed to
exist. It is therefore unnecessary to provide a non-nil `:resource`
entry, unless you wish to communicate a resource's meta-data.

<example ref="ResourceStateTopLevel"/>

### Changing state

HTTP specifies a number of methods which mutate a resource's state.

#### POSTs

Let's start with the POST method and review the spec :-

> The POST method requests that the target resource process the
representation enclosed in the request according to the resource's own
specific semantics.

This means that we can decide to define whatever semantics we want, or
in other words, do anything we like when processing the request.

Let's see this with an example :-

<example ref="PostCounter"/>

As we have seen, processing of POST requests is achieved by adding an __:post__ entry to the resource map. If the value is a function, it will be called with the request context as an argument, and return a value. We can also specify the value as a constant. Whichever we choose, the table below shows how the return value is interpretted.

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

PUT a resource. The resource-map returns a resource with an etag which
matches the value of the 'If-Match' header in the request. This means
the PUT can proceed.

> If-Match is most often used with state-changing methods (e.g., POST, PUT, DELETE) to prevent accidental overwrites when multiple user agents might be acting in parallel on the same resource (i.e., to the \"lost update\" problem).

<example ref="PutResourceMatchedEtag"/>

<example ref="PutResourceUnmatchedEtag"/>

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

If you try to do a mutable action, such as a PUT, on server of different origin than your website, then the browser will 'pre-flight' the request.

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

Instead of specifying a __:body__ entry, we use an __:events__ entry.

<example ref="ServerSentEvents"/>

The example above showed a contrived example where the events are static. More usually, you will specify a function returning a _stream_ of events.

A stream can be anything that can produce events, such as a lazy sequence, core.async channel or reactive stream. In fact, you can use anything that is supported by the underlying manifold library, so check [manifold](https://github.com/ztellman/manifold) for full details.

Let's demonstrate this with another example.

<example ref="ServerSentEventsWithCoreAsyncChannel"/>

Of course, all the parts of yada you've learned up until now, such as
access control, can be added to the resource map. Contrast that with
web-sockets, where you'd have to design and implement your own bespoke
security system.

The only difference between a resource providing a stream of events and a resource providing content is that we specify __:events__ instead of __:body__.

## Swagger

If bidi is used as the routing library, yada and bidi data can be combined to form enough meta-data about a resource for create a [Swagger](http://swagger.io/) resource.

This [is demonstrated](/swagger-ui/index.html?url=/api/1.0.0/swagger.json) and further details can be found in the demo code.

This chapter is a stub. More documentation will be forthcoming.

## Misc

<example ref="StatusAndHeaders"/>

<include type="note" ref="kv-args"/>
