# The yada manual

Welcome to the yada manual!

This manual corresponds with version 1.0.0-20150903.093751-9

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

State is everywhere. The world is moving and we need to keep up. We need
our computers to help us stay up-to-date with the latest information,
chats, trends, stock-prices, news and weather updates, and other
important stuff.

The web is primarily a means to move state around. You have some state
here, and you want it over there. Or it's over there, but you want it
over here.

For two decades or more, the pre-dominant model for web programming has
ignored state, instead requiring developers to work at the level of the
HTTP protocol itself.

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
frameworks in many languages, old and new (Python, Ruby, Go,
Clojure...). It has survived because it's so flexible, as many
low-level programming models are.

But there are significant downsides to this model too. HTTP is a big
specification, and it's unreasonable to expect developers to have the
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
standard. For ephemeral prototypes, this 'fake it' approach to HTTP is
acceptable. But HTTP is designed for long-lived systems with lifetimes
measured in decades, that must cross departmental and even
organisational boundaries, and adapt to ongoing changes in technology.

It's time for a fresh approach. We need our libraries to do more work
for us. For this to happen, we need to move from the _de-facto_
'operational' view of web 'services' to a strong _data-oriented_
approach, focussing on what a web _resource_ is really about: _state_.

## Introduction

### What is yada?

yada is a Clojure library that lets you create powerful Ring
handlers that are fully compliant with HTTP specifications.

Underlying yada is a number of Clojure protocols. Any Clojure data type
that satisfies one or more of these protocols can be used to build a
Ring handler. You can use the built-in types (strings, files,
collections, atoms, etc.), create your own or re-use ones written by
others.

This approach has a number of advantages. Many things you would expect
to have to code yourself are taken care of automatically, such as
request validation, content negotiation, conditional requests, HEAD,
OPTIONS and TRACE methods, cache-control, CORS and much more, leaving
you time to focus on the functional parts of your application and
leaving you with far less handler code to write and maintain.

yada is built on a fully asynchronous core, allowing you to
exploit the asynchronous features of modern web servers, to achieve
greater scalability for your Clojure-powered websites and APIs.

yada is data-centric, letting you specify your web resources
as _data_. This has some compelling advantages, such as being able to
dynamically generate parts of your application, or transform that data
into other formats, such as [Swagger](http://swagger.io) specifications
for API documentation.

However, yada is not a fully-fledged 'batteries-included' web
'framework'. It does not offer URI routing and link formation, nor does
it offer views and templating. It does, however, integrate seamlessly
with its sibling library [bidi](https://github.com/juxt/bidi (for URI
routing and formation) and other routing libraries. It can be integrated
with the many template libraries available for Clojure and Java, so you
can build your own web-framework from yada and other libraries.

yada is also agnostic to how you want to build your app. It is designed
to be easy to use for HTML content and web APIs, in whatever style you
decide is right for you (Swagger documented, hypermedia driven, ROCA, jsonapi, real-time, etc). The only constraint is that yada tries to comply as far
as possible with current HTTP specifications, to promote richer, more
interoperable and interconnected systems which are built to last.

### An introductory example: Hello World!

Let's introduce yada properly by writing some code. Let's start with some state, a string: `Hello World!`. We'll be able to give an overview of many of yada's features using this simple example.

We pass the string as a single argument to yada's `yada` function, and yada returns a Ring handler.

```clojure
(require '[yada.yada :refer [yada]])

(yada "Hello World!\n")
```

This web resource can be used as a Ring handler. By combining this handler with a web-server, we can start the service.

Here's how you can start the service using
[Aleph](https://github.com/ztellman/aleph). (Note that Aleph is the only
web server that yada currently supports).

```clojure
(require '[yada.yada :refer [yada]]
         '[aleph.http :refer [start-server]])

(start-server
  (yada "Hello World!\n")
  {:port 3000})
```

Alternatively, you can following along by referencing and experimenting with the code in `dev/src/yada/dev/hello.clj`. See the [installation](#Installation) chapter.

Once we have bound this handler to the path `/hello`, we're able to make
the following HTTP request :-

```nohighlight
curl -i http://localhost:8090/hello
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
  ["/hello-api"
      (yada/swaggered
        {:info {:title "Hello World!"
                :version "1.0"
                :description "Demonstrating yada + swagger"}
                :basePath "/hello-api"}
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

The `yada/swaggered` wrapper provides a Swagger specification, in JSON, derived from its arguments. This specification can be used to drive a [Swagger UI](http://localhost:8090/swagger-ui/index.html?url=/hello-api/swagger.json).

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

### Mutation

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
which tells us that `PUT` isn't possible. This makes sense, because we can't mutate a Java string.

We could, however, wrap the Java string with a Clojure reference which
could be changed to point at different Java strings.

To demonstrate this, yada contains support for atoms. Let's add a new
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
wanted. To ensure we don't override someone's change, we could have set
the __If-Match__ header using the __ETag__ value.

Let's test this now, using the ETag value we got before we sent our
`PUT` request.

```nohighlight
curl -i http://localhost:8090/hello -X PUT -H "If-Match: 1462348343" -d "Hello Dolly!"
```

[fill out]


Before reverting our code back to the original, without the atom, let's see the Swagger UI again.

![Swagger](static/img/mutable-hello-swagger.png)

We now have a few more methods. [See for yourself](http://localhost:8090/swagger-ui/index.html?url=/hello-atom-api/swagger.json).

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

The response does not have a body, but tells us the headers we would get
if we were to try a `GET` request.

For more details about HEAD queries, see [insert reference here].

#### Parameters

Often, a resource's state will not be constant, but depend in some way on the request itself. Let's say we want to pass a parameter to the resource, via a query parameter.

First, let's call name our query parameter `p`. Since the state is
sensitive to the request, we specify a function rather than a value. The
function takes a single parameter called the _request context_, denoted
by the symbol `ctx`.

```clojure
(yada
  (fn [ctx] (format "Hello %s!\n" (get-in ctx [:parameters :p])))
  :parameters {:get {:query {:p String}}})
```

Parameters are declared using additional key-value arguments after the first argument. They are declared on a per-method, per-type basis.

If the request correctly contains the parameter, it will be available in
the request context, via the __:parameters__ key.

Let's see this in action

```nohighlight
curl -i http://localhost:8090/hello-parameters?p=Ken
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
(yada
  (fn [ctx]
    (case (yada/language ctx)
      "zh-ch" "你好世界!\n"
      "en" "Hello World!\n"))
  :language ["zh-ch" "en"])
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

### Summary

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
[yada "1.0.0-20150903.093751-9"]
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

### Benefits to declarative parameter declaration

Declaring your parameters in resource descriptions comes with numerous advantages.

- Parameters are declared with types, which are automatically coerced thereby eliminating error-prone conversion code.

- The parameter value will be automatically coerced to the given type. This eliminates the need to write error-prone code to parse and convert parameters into their desired type.

- Parameters are pre-validated on every request, providing some defence against injection attacks. If the request if found to be invalid, a 400 response is returned.

- Parameter declarations can help to document the API, for example,
  automatic generation of Swagger specifications.

## Representations

If you think of a web resource in MVC terms, representations are the
different views of a resource. Representations are the means by which a
resource's state can be transferred to different parts of the web.

The actual content of a representation is determined by the outcome of a
process known as _content negotiation_.

Content negotiation is an important feature of HTTP, allowing clients and
servers to agree on how a resource can be represented to best meet the
availability, compatibility and preferences of both parties.

There are 2 types of
[content negotiation](/spec/rfc7231#section-3.4)
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

Recall our `Hello World!` example. Let's extend this by specifying 2
sets of possible representation.

```clojure
(yada
  (fn [ctx]
    (case (get-in ctx [:response :representation :language])
            "zh-ch" "你好世界!\n"
            "en" "Hello World!\n"))
  :representations [{:media-type "text/plain"
                     :language #{"en" "zh-ch"}
                     :charset "UTF-8"}
                    {:media-type "text/plain"
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

The second type of negotiation is termed _reactive negotiation_ where the
agent chooses from a list of representations provided by the server.

## Resources

Different types of resources are added to yada by defining Clojure types
that satisfy one or more of yada's [protocols](#Protocols). The core request
handling logic determines whether the resource type satisfies a protocol
and uses the results of applying these protocol functions in determining
the response.

Let's delve a little deeper into how the _Hello World!_ example works.

Here is the actual code that tells yada about Java strings. The
namespace declaration and comments have been removed, but otherwise this
is all the code that is required to adapt Java strings into yada
resources.

```clojure
(defrecord StringResource [s last-modified]

  Representations
  (representations [_]
    [{:media-type "text/plain" :charset platform-charsets}])

  ResourceModification
  (last-modified [_ _] last-modified)

  ResourceVersion
  (version [_ _] s)

  Get
  (GET [_ _] s))

(extend-protocol ResourceCoercion
  String
  (as-resource [s]
    (->StringResource s (to-date (now)))))
```

Recall the _Hello World!_ example.

```clojure
(yada "Hello World!")
```

yada calls `as-resource` on the argument. This declaration causes a
new instance of the `StringResource` record to be created.

```clojure
(extend-protocol ResourceCoercion
  String
  (as-resource [s]
  (->StringResource s (to-date (now)))))
```

The original string (`Hello World!`) and the current date is captured
and provided to the `StringResource` record. (The only reason for using
`clj-time` rather than `java.util.Date` is to facilitate testing).

The `StringResource` resource satisfies the `Representations`
protocol, which means it can specify which types of representation it is
able to generate. The `representations` function must return a list of
_representation declarations_, which declare all the possible
combinations of media-type, charset, encoding and language. In this
case, we just have one representation declaration which specifies
`text/plain` and the charsets available (all those supported on the Java
platform we are on).

### Custom resources

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

The `yada.resources.file-resourceDirectoryResource

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
built into yada. For further details, see Zach Tellman's
[manifold](https://github.com/ztellman/manifold) library.

In almost all cases, it is possible to return a _deferred value_ from
any of the functions that make up our resource record or handler options.

For example, suppose our resource retrieves its state from another internal web API. This would be a common pattern with µ-services. Let's assume you are using an HTTP client library that is asynchronous, and requires you provide a _callback_ function that will be called when the HTTP response is ready.

On sending the request, we could create a promise which is given to the callback function and returned to yada as the return value of our function.

Some time later the callback function will be called, and its implementation will _deliver_ the promise with the response value. That will cause the continuation of processing of the original request. Note that _at no time is any thread blocked by I/O_.

![Async](static/img/hello-async.png)

Actually, if we use Aleph or http-kit as our HTTP client the code is
even simpler, since each return promises from their request functions.

```clojure
(require '[yada.resource :refer [Get]])

(defrecord MyResource []
  Get
  (GET [_ ctx] (aleph.http/get "http://users.example.org")))
```

In a real-world application, the ability to
use an asynchronous model is very useful for techniques to improve
scalability. For example, in a heavily loaded server, I/O operations can
be queued and batched together. Performance may be slightly worse for
each individual request, but the overall throughput of the web server
can be significantly improved.

Normally, exploiting asynchronous operations in handling web requests is
difficult and requires advanced knowledge of asynchronous programming
techniques. In yada, however, it's easy.

For a fuller explanation as to why asynchronous programming models are
beneficial, see the [Ratpack](http://ratpack.io) documentation. Note
that yada provides all the features of Ratpack, and more, but native to
Clojure. The combination of Clojure and yada significantly reduces the
amount of code you have to write to create scalable web APIs for your
applications.

## Routing

Since the `yada` function returns a Ring-compatible
handler, it is compatible with any Clojure URI router.

If you use a data-structure for your routes, you can walk the tree to
find yada resources which, as data, can be augmented according to any
policies you may have for a particular sub-tree of routes.

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
data, they are amenable to programmatic transformation, making your
future options virtually limitless.

## Options

Options can be given in the option map given as the optional second
argument to yada's `resource` function.

```clojure
(yada resource {:request-uri-too-long? 8192
                :last-modified (fn [ctx] (.lastModified file))})
```

Some options are used to override values that would have otherwise come
from the resource. In these cases, options always take precedence.

### :allowed-methods

The set of methods that the handler should support.

```clojure
{:allowed-methods #{:get :put}}
```

The methods `:head` and `:options` are added implicitly, if yada is able
to support them on the resource. If you don't want this, use
`:all-allowed-methods`.

### :all-allowed-methods (advanced)

Use this is you want to specify exactly which methods are allowed on the resource. This should not be used in conjunction with the :allowed-methods id, but if both are specified, `:all-allowed-methods` will take precedence over `:allowed-methods`.

```clojure
{:all-allowed-methods #{:get :put :options :trace}} ; no :head
```

This option is for advanced users only. Usually you should use
`:allowed-methods` and let yada add additional methods it is able to
support.

### :authorization
### :charset
### :encoding
### :headers
### :id
### :language
### :last-modified
### :journal
### :journal-browser-path
### :media-type
### :parameters

Specify, or override, the parameters of a resource. Since parameters often apply to a method's implementation, these are given on a method by method basis. Each entry in the map must be keyed with the method. Each method parameters is itself a map, keyed by the parameter type.

```clojure
{:parameters {:get {:query {:p String}
                    :header {"X-Version" Integer}}}}
```

The example above declares both a _query_ and _header_ parameter. The
query parameter is called p and is a String. The header
parameter is the value of the HTTP request's `X-Version` header and is coerced to an integer.

### :representation
### :representations
### :request-uri-too-long?
### :security
### :service-available?
### :status
### :trace

## Protocols

yada defines a number of protocols. Existing Clojure types and records
can be extended with these protocols to adapt them to use with yada.

### `yada.protocols.ResourceCoercion`

Deprecated - any _define-time_ logic should be removed to the
single-arity form of `properties` (see below).

### `yada.protocols.Properties`

Defines a `properties` function of 2 arites.

```clojure
(properties [resource])
(properties [resource ctx])
```

The first form takes only the resource itself as an argument and is
called only once by the yada function when it is building the resource
data.

The second form is called on every request. It takes a second argument, the request context, which contains the Ring request, and resource data, including the properties returned by the single arity call to `properties`.

A resource should return a map from these `properties`
functions, containing the entries described later. Other entries can be
returned, as long as the keys are namespaced keywords (non-namespaced
keywords in this case are reserved by yada).

### `yada.protocols.ETag`

Extend this protocol to override yada's built-in entity-tag generation
facility.

### `yada.methods.Method`

Every HTTP method is implemented as a type which extends the yada.methods.Method protocols. This way, new HTTP methods can be added to yada. Each type must implement the correct semantics for the method, although yada comes with a number of built-in methods for each of the most common HTTP methods.

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

## Properties

By satisfying the `Properties` protocol, each resource can
provide data back to yada in the form of a map from
`properties`. Here is a list of entries the returned map may
contain.

### `:allowed-methods`

A set of keywords indicating the methods allowed on the resource.

### `:parameters`

A map of parameters, keyed by method and then parameter type.

### `:representations`

A collection of representations. Usually returned from the single-arity
form, without the context. This is so that the representation set forms
part of the resources's data model, and can be exploited by
introspection tools such as Swagger prior to handling requests.

### `:last-modified`

The last modified date of the resource.

### `:version`

The resource version. Used for entity-tag construction.

### `:collection?`

Whether the resource is actually serving a collection of sub-resources.

## Resource model

Once a resource has been built, it can be treated as a map with the
following entries.

### `:allowed-methods`

A set of keywords indicating the methods supported by the resource.

### `:authorization`

[todo]

### `:base`

The original uncoerced first argument passed to yada's `resource`
function (or it's `yada` alias).

### `:id`

A unique identifier for the individual resource. Defaults to a (random) type-4 UUID but may be overridden with the `:id` option.

### `:interceptor-chain`

The sequence of interceptors that will process the request. Mutate this
if you need to weave in custom functionality.

### `:journal`

The database that will store journal entries (one entry per
request). Can be nil.

### `:known-methods`

A set of keywords indicating all the methods that are known by
yada. This includes custom methods that have been added via protocol
extension. This set is usually a superset of the methods that are
allowed on the resource, see __:allowed-methods__. If a request
containing a method that is not known results in a 501 status code.

### `:options`

The resource options, as given to yada's `resource` function (or it's `yada` alias).

### `:parameters`

The resource parameters, as provided by, in order of precedence :-

* the `:parameters` option
* the `:parameters` entry of the result of the call to the resource's `properties` function.

### `:representations`

The resource representations, as provided by, in order of precedence :-

* the `:representations` option
* the `:representation` option
* the `:media-type`, `:charset`, `:encoding` and `:language` option
* the `:representations` entry of the result of the call to the resource's `properties` function.

### `:resource`

The resource (possibly after coercion by the `as-resource` function of the `ResourceCoercion` protocol.

### `:security`

[todo]

### `:vary`

The set of axes that can vary during proactive content negotiation

```clojure
{:vary #{:media-type :language}}
```

## The default interceptor chain

The interceptor chain, established on the creation of a handler, is a vector.

### `available?`
### `known-method?`
### `uri-too-long?`
### `TRACE`
### `method-allowed?`
### `malformed?`
### `check-modification-time`
### select-representation
### if-match
### if-none-match
### invoke-method
### get-new-properties
### compute-etag
### access-control-headers
### create-response

## Comparison guide

It is often easier to understand a technology in relation to
another. How does yada compare with other libraries?

> "I know Ring, how does yada compare to that?"

So let's start with Ring.

### Ring

Ring is by far the most popular way of building websites and HTTP
services in Clojure. Ring encompasses two ideas. First, Ring specifies
the contract between Clojure programs and the web servers they are
served by, which are often written in Java. The contract involves
specifying nature of the interface (request/response) and the structure
of the maps are used to represent the request and response.

In addition, Ring offers a set of modular and composable higher-order
functions, called Ring middleware, from which a single Ring-compatible
handler can be composed featuring a rich set of behaviour. It is also
straight-forward to create bespoke middleware for specialised
requirements.

While Ring is incredibly flexible, the fine-grained modularity it offers
comes with some trade-offs.

#### Everything always from the ground up

As a developer, Ring provides you with the raw HTTP request. The rest is
up to you. You've got to figure out how to take that data and turn it
into a response. There are some support functions, called Ring
middleware, which can help in common tasks. However, it's up to you
which Ring middleware to use, and in which order to apply it. You have
figure this out for every service you write.

Generally speaking, a web service written with Ring starts at zero
functionality and builds up. In contrast, a web service written with
yada starts you off at full HTTP functionality.

Ring is optimised for implementing _bare-bones_ web services
quickly. Since that is what developers are so often asked to do, it is
no wonder Ring is so popular.

There do exist projects (such as noir and ring-defaults) that offer
pre-constructed stacks of Ring middleware which provide a reasonable
approximation of HTTP and other important functionality.

But from the perspective of supporting HTTP fully, there are more
entrenched problems with Ring as we shall see.

#### Synchronous only

A key problem with applying Ring middleware in this way is that the
tower needs to be executed by the request thread, which precludes the
option to run middleware asynchronously. The stack of Ring middleware
maps directly onto the call stack over the executing thread.

With Ring 1.3, the Ring middleware that is packaged with Ring has been
refactored to expose the functionality to a wider set of contexts. It is
no longer necessary to compose functions together to exploit the mature
proven HTTP functionality embedded in the Ring library. However, this
does mean that the main Ring design feature, composition of higher-order
functions, just isn't possible in asynchronous contexts, a fact which
severely limits its usefulness.

#### Inefficiencies

The fine-grained modularity of the Ring middleware design means that
each Ring middleware function is strictly isolated from other middleware
functions. This is generally a good thing. Individual middleware cannot
make assumptions as to other Ring middleware in the stack. However, this
leads to a number of inefficiencies.

For example, Ring offers middleware, namely
`ring.middleware.head/wrap-head`, to support the implementation of HEAD
requests. However, the implementation requires that a full GET request
is made, from which the response body is truncated. Therefore, HEAD
requests are always at least as expensive as GET requests. This is
certainly not what the authors of HTTP had in mind.

Similarly, Ring's `wrap-not-modified` function only runs _after_ the
response has been fully formed. The whole point of this HTTP feature is
to remove load from the origin server. However, if the entire response
has to be recreated each time, there are no advantages to using this
feature.

For these reasons, we can see that Ring merely offers a
_smoke-and-mirrors_ approach to implementing certain features of HTTP.

#### How yada compares

The design of yada differs from the modular approach taken by Ring, and
instead offers something of a monolith. Arguably, this approach results
is a more complete and accurate implementation of HTTP.

### Pedestal

Cognitect's Pedestal was an early attempt to establish a complete
front-to-back stack for development of rich applications calling
APIs. Over time, Pedestal's front-end piece has been replaced by popular
ClojureScript frameworks, many derived from Facebook's React library.

However, Pedestal's service-based back-end has undergone continual
improvement for a long time and currently represents a more mature stack
than yada's.

There are some significant architectural differences. In keeping with
Ring's middleware approach, Pedestal strives to be modular and asks the
user to compose interceptor chains to add functionality. In comparison,
yada attempts to do more 'out of the box', with features such as Swagger
and content-negotiation built-in rather than implemented as
extensions. Arguably this make things easier for the developer.

yada is based on Zach Tellman's Aleph, which is ultimately based on
Netty. It also uses manifold chains for async. In comparison, Pedestal
is focussed more towards J2EE servlet containers and uses core.async directly.

It could be argued that Pedestal is tuned more towards pragmatic
development of useable APIs, rather than yada's prioritisation on HTTP
compliance. Both are very much targetted for use in the kinds of
real-world production systems we build at JUXT.

There are, however, numerous stylistic difference between the two
libraries which come down to personal choice.

### Liberator

yada and Liberator are both designed to confer proper HTTP semantics on
their services, offering sensible defaults in the absence of developer
intervention.

Liberator is inspired by the Erlang library, WebMachine, which
introduced the concept of using a flow-chart to drive the HTTP request
processing. One disadvantage of Liberator stems from the fact that this
flow-chart is wired into the code, in such a way as to make it difficult
to change or support new features in HTTP. This also means that request
processing is tied to the thread of execution, forcing the process to be
synchronous. Any I/O bound activity in the processing of the request
necessitates blocking of the request thread, leading to resource
starvation when the service is under heavy load.

Another problem is that the data model is shaped around the needs of the
decision-based steps in the flow-chart, rather than providing an
agnostic _canonical_ description of the service.

That said, Liberator is a mature and capable library that is well suited
to building HTTP services. Many of the differences between Liberator and
yada are stylistic and therefore subject to personal bias.

## Concluding remarks

In this user-manual we have seen how yada can help create flexible HTTP
services with a data-oriented syntax, and how the adoption
of a declarative data format (rather than functional composition) allows
us to easily extend yada's functionality in various ways.

Asynchronous operation can be exploited wherever required, with
fine-grained control residing with the user, using futures and promises,
avoiding the need for deeply-nested code full of callback functions.
