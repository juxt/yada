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


## Asynchronous responses


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
      [""
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

                  )}}}])]
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
