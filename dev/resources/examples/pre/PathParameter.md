Before describing how to dynamically produce responses, let's take a
moment to describe how to specify path parameters in a
[bidi](https://github.com/juxt/bidi) route (bidi is the routing library
used for these examples). We will assume knowledge of how to do this in
later examples, so if you're unfamiliar with bidi (or even if you are),
make sure you understand how arameters are extracted from the URI of a
request.

In _this_ example, the bidi route is a pair, containing a pattern and
the matched target (typically a Ring handler).

The pattern looks like this

```clojure
["/PathParameter/" :account]
```

As the pattern is a vector, it is treated as a sequence of _path segments_, (strings, keywords, regular-expressions, etc.). A keyword signifies a path
parameter which is extracted from the URI and added to the Ring requests
`:route-params` entry, with the keyword as the key. So this route
matches the path `/PathParameter/1234`, and the `:route-params` map
ends up like this

```clojure
{
  :account "1234"
  ...
}`
```

As a slight variation, bidi allows you to qualifiy the parameter type, by substituting the keyword with a `[type keyword]` pair.

```clojure
["/PathParameter/" [long :account]]
```

The value is now a `long`, rather than a `String`. The resulting `:route-params` map  looks like this

```clojure
{
  :account 1234
  ...
}
```
