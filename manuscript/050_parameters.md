# Parameters

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
[schema](https://github.com/prismatic/schema) library. This allows us
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

## Capturing multi-value parameters

Occasionally, you may have multiple values associated with a given
parameter. Query strings and HTML form data both allow for the same
parameter to be specified multiple times.

```
/search?accno=1234&accno=1235
```

To capture all values in a vector, declare your parameter type as a
vector type:

```clojure
{:parameters {:query {:accno [Long]}}}}
```

## Capturing large request bodies

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
