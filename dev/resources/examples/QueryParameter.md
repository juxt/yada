Let's start by showing how to access an undeclared query parameter in
the traditional way, using ring middleware.

The path in the URI is `/QueryParameter?month=2014-09`. Let's assume that
we have added some Ring middleware to process the query string adding a
__:query-params__ to the Ring request.

```clojure
{
  "month" "2014-09"
}
```

Let's add a __:body__ entry with a function that extracts the parameter from the request context and uses it in a formatted string.

<handler/>

The code in __:body__ can reach into the _request context_ for the Ring request, and from that extract the `:query-params` entry for `:month`.

Now, let's send a request with a parameter in the path :-

<request/>

We expect to see the string "Showing transactions in month 2014-09" returned in the body.

<response/>
