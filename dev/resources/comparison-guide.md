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
real-world production systems we build at JUXT, and we have deployed
Pedestal successfully in numerous projects.

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
