# Testing

Using `yada.response-for`, you can create a response from a bidi route structure containing yada resources for testing purposes:

```clojure
(response-for ["/foo" (yada "hello")] :get "/foo")
```
