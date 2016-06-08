# Interceptors

The interceptor chain, established on the creation of a resource. A
resource's interceptor chain can be modified from the defaults.

## Core interceptors

### available?
### known-method?
### uri-too-long?
### TRACE
### method-allowed?
### parse-parameters
### authenticate
### get-properties
### authorize
### process-request-body
### check-modification-time
### select-representation
### if-match
### if-none-match
### invoke-method
### get-new-properties
### compute-etag
### access-control-headers
### create-response
### logging
### return


## Modifying interceptor chains

Say you want to modify the interceptor chain for a given resource.

You might want to put your interceptor(s) at the front.

```clojure
(yada.resource/prepend-interceptor
  resource
  my-interceptor-a
  my-interceptor-b)
```

Alternatively, you might want to replace some existing core interceptors:

```clojure
(update resource
  :interceptor-chain
  (partial replace {yada.interceptors/logging my-logging
                    yada.security/authorize my-authorize}))
```

Or you may want to insert some of your own before a given interceptor:

```clojure
(yada.resource/insert-interceptor
  resource yada.security/authorize my-pre-authorize)
```

You can also append interceptors after a given interceptor:

```clojure
(yada.resource/append-interceptor
  resource yada.security/authorize my-post-authorize)
```
