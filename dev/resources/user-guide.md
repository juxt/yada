# The yada user guide

Welcome to the yada user guide!

This guide is ideal if you are new to yada and is suitable for anyone
who is interested in building robust RESTful web APIs quickly and
easily. Previous experience of web development in Clojure may be helpful
but is not mandatory.

### yada is simple, but also easy!

If you follow this guide carefully you will learn how to take advantage
of the many features yada has to offer.

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

## Forward

With the emergence and dominance of HTTP, and the increasing importance
to the world's economy of APIs built on it, creating high-quality APIs
rapidly and reliably has become crucially important.

Clojure makes an ideal choice for writing solid software, but most
existing web libraries and frameworks for Clojure are bound to the
synchronous, 'one thread per request' model. This is now becoming a
limitation, and in the future, with HTTP/2 around the corner, this
constraint will need to be relaxed in order to achieve the scale and
throughput required for some applications.

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

A deferred value is simply a value that may not yet be known. Examples
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


## Parameters

Many web requests contain parameters, which affect how a resource behaves. Often parameters are specified at the end of a URI, in the query string. But parameters can also be inferred from the path.

For example, let's imagine we have a fictional URI to access the transactions of a bank account.

```nohighlight
https://bigbank.com/accounts/1234/transactions?since=tuesday
```

There are 2 parameters here. The first, `1234`, is contained in the
path `/accounts/1234/transactions`. We call this a _path parameter_.

The second, `tuesday`, is embedded in the URI's query string (after
the `?` symbol). We call this a _query parameter_.

yada allows you to declare both these and other types of parameter via the __:parameters__ entry in the resource map.

Parameters must be specified for each method that the resource supports. The reason for this is because parameters can, and often do, differ depending on the method used.

For example, below we have a resource map that defines the parameters for requests to a resource representing a bank account. For GET requests, there is both a path parameter and query parameter, for POST requests there is the same path parameter and a body.

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

But for POST requests, there is a body parameter, which defines the entity body that must be sent with the request. This might be used, for example, to post a new transaction to a bank account.

We can declare the parameter in the resource map's __:parameters__ entry. At runtime, these parameters are extracted from a request and  added as the __:parameters__ entry of the _request context_.

Let's show this with an example.

<example ref="ParameterDeclaredPathQueryWithGet"/>

<!-- <example ref="PathParameterRequired"/> -->

Now let's show how this same resource responds to a POST request.

<example ref="ParameterDeclaredPathQueryWithPost"/>

### Form parameters

<example ref="FormParameter"/>

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

Declaring your parameters in resource maps comes with numerous advantages.

- Parameters are declared with types, which are automatically coerced thereby eliminating error-prone conversion code.

- The parameter value will be automatically coerced to the given type. This eliminates the need to write error-prone code to parse and convert parameters into their desired type.

- Parameters are pre-validated on every request, providing some defence against injection attacks. If the request if found to be invalid, a 400 response is returned.

- Parameter declarations can help to document the API — this will be covered later in the chapter on [Swagger](#Swagger).

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
access control, can be added to the resource map. (Contrast that with
web-sockets, where you'd have to design and implement your own bespoke
security system.)

## Misc

<example ref="StatusAndHeaders"/>

<include type="note" ref="kv-args"/>

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

Let's show how to integrate yada's resource maps into a route structure.

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

<include type="note" ref="swagger-implementation"/>

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

In this user-guide we have seen how yada can help create powerful
RESTful APIs with a declarative data-centric syntax, and how the
adoption of a declarative data format (rather than functional
composition) allows us to easily extend yada's functionality in various
ways.

Although yada is flexible, it is also powerful. Asynchronous operation
can be enabled wherever required, with fine-grained control residing
with the user, using futures and promises, avoiding the need for
deeply-nested code full of callback functions.
