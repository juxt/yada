Let's assume the path in the request URI is `/PathParameter/1234`. Let's also assume that our routing library has extracted the account number (`1234`) and provided it in the Ring request's `:route-params` entry :-

```clojure
{
  :account "1234"
}
```

Let's add a __:body__ entry with a function that extracts the parameter from the request context and uses it in a formatted string.

<handler/>

The code in __:body__ can reach into the _request context_ for the Ring request, and from that extract the `:route-params` entry for `:account`.

Now, let's send a request with a parameter in the path :-

<request/>

We expect to see the string "Account number is 1234" returned in the body.

<response/>
