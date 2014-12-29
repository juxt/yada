# yada

## Design goals

### Async support

REST APIs built on Ring and Liberator are built on a synchronous
abstraction which requires each API request to be serviced entirely by a
single thread.

In some situations this places limits to scalability. For example, when
information about a resource needs to be loaded from a remote datastore
and the I/O latency is significant.

### Error handling

### Easy debugging

Avoid long Liberator stack traces

### Data driven

Both Pedestal and bidi have shown the benefits that come from building
on data rather than functions or macros. For example, compare the amount
of code required to adapt bidi to Swagger compared to the same for
Compojure.

### Swagger compatibility

Documentation is a critical component of any web API, support for it
should be built in. Today, it seems like Swagger has built up a leading
role in defining what API documentation should feel like. It feels
pragmatic to treat Swagger as first-class, not as an after-thought.

### Security/CORS/CSRF support

These days, security needs to come by default, not as an exercise left
for the reader.

### Schema validation and coercions

Prismatic Schema has encourages the more widespread use of data
validation and coercion. This is particularly important for web APIs
which have to contend with externally presented data, injection attacks
and other malicious behaviour.

Also, there is now an explosion of data formats which might be necessary
to support, beyond JSON: bson, binary json, hessian, edn, yaml, json/ld,
json/xml. It is important that the data is agnostic to the format, and
coercions can eliminate the potential for programmer error by providing
default 'renderings' of a given data structure.

## Design choices

The project uses deferreds from @ztellman's repo:manifold. This provides
async options in the places in the HTTP implementation where it is most
needed: the determination of resource existence and the loading of the
metadata and data associated with a resource.

Unlike promises, deferreds also provide a way of signalling errors,
which is just what we need to handle and communicate errors properly.

## What's wrong with Liberator?

The original webmachine design using a state transition diagram has
served us well. However, there are a number of problems. I feel the
flowchart abstraction,  is /too/ abstract - it makes complex things
possible but inelegant at times, relying on tricks such as knowing which
decision functions to hijack, knowing what parts of the response
(e.g. response content type) have been determined when. For advanced
uses it requires initimate knowledge of the underlying flowchart. This
flowchart gives Liberator its flexibility, but the trade-off is that it
is tricky to do certain things, like adding CORS support, that should be
easier in a REST library.

Much the same can be said for solutions based on Ring middleware. The
abstraction underlying Ring middleware is the higher-order
function. Like Liberator, ordering issues come in to play, which break
the composeability.

- debugging: stack traces are unwieldly
- async is very difficult

## Comparison with Pedestal

### Similarities

Pedestal is built to support async.

### Differences

Pedestal combines routing with request handling. This library uses
[bidi](https://github.com/juxt/bidi) for the former and focusses on the
latter.

Pedestal uses an interceptor approach

This library has first-class support for Swagger.
