# Example 1: Hello World!

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

## Hello Swagger!

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

![Swagger](hello-swagger.png)

But we're getting ahead of ourselves here. Let's delve a bit deeper in our `Hello World!` resource.

## A conditional request

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

## Mutation

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

![Swagger](mutable-hello-swagger.png)

We now have a few more methods. [See for yourself](http://localhost:8090/swagger-ui/index.html?url=/hello-atom-swagger/swagger.json).

## A HEAD request

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

## Parameters

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

## Content negotiation

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

## Summary

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
