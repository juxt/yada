### Keyword arguments

yada's `yada` function takes a single arg, the response map. But if you want, you can call the `yada` function with kv-args, like this :-

```clojure
(yada :status 200 :body "Hello World")
```
