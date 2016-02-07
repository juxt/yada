# Security

Built-in to the library, yada offers a complete standards-based set of
security features for today's secure applications and content
delivery.

## Security is part of the resource, not the route

In yada, resources are self-contained and are individually protected
from unauthorized access. We agree with the HTTP standards authors
when we consider security to be integral to the definition of the
resource itself, and not an extra to be bolted on afterwards. Nor
should it be complected with routing. The process of identifying up a
resource from its URI is independent of how that resource should
behave, and shouldn't be coupled to it.

Building security into each resource yields other benefits, such as
making it easier to test the resource as a unit in isolation of other
resources and the router.

As in all other areas, yada aims for 100% compliance with core HTTP
standards when it comes to security, notably
[RFC 7235](https://tools.ietf.org/html/rfc7235). Also, since HTTP APIs
are nowadays used to facilitate transactional integration between
systems via the user's browser, it is critically important that yada
fully supports systems that offer APIs to other applications, across
origins, as standardised by [CORS](http://www.w3.org/TR/cors/).

## The `:access-control` entry

All security aspects for a resource are specified in its model's
`:access-control` entry.

## Authentication

Let's look at authentication first. Authentication is the process of
establishing and verifying the identity and credentials of the user,
with reasonable confidence that the user is not an impostor.

In yada, authentication happens after any request parameters have been
processed, so if necessary they can be used to establish the identity
of the user. However, it is important to remember that authentication
happens before the resource's properties have been loaded, since
credentials do not as it has do with the actual resource. Thus, if the
user is not genuine, we might well save a wasted trip to the
resource's data-store.

In HTTP, resources can exist inside a _protection space_ determined by
one or more _realms_. Each resource declares the realm (or realms) it
is protected by, as part of the __:access-control__ entry of its
__resource-model__.

### Authentication schemes

Each realm declares one or more _authentication schemes_
governing how requests are authenticated.

yada supports numerous authentication schemes, include custom ones you
can provide yourself.

Each scheme has a verifier, depending on the scheme this is usually a
function. The verifier is used to extract or otherwise establish the
credentials in the request, ensuring they are authentic and true, in
which case it returns these credentials as a value to be stored in the
request context. These credentials may contain information such as the
user's identity, roles and privileges. If no credentials are found, the verifier should return nil.

If no credentials are found by any of the schemes, a 401 response is
returned containing a WWW-Authenticate header.

### Basic authentication

Here is an example of a resource which uses Basic authentication
described in [RFC 2617](https://www.ietf.org/rfc/rfc2617.txt)

```clojure
{:access-control
  {:realm "accounts"
   :scheme "Basic"
   :verify (fn [[user password]] …}}
```

There are 3 entries here. The first specifies the _realm_, which is defaults to `default` in Basic Authentication, but if specified is contained in the dialog the browser presents to the user.

The second declares we are using Basic authentication.

The last entry is the verify function. In Basic Authentication, the verify function takes a single argument which is a vector of two entries: the username and password.

If the user/password pair correctly identifies an authentic user, your function should return credentials.

```clojure
(fn [[user password]]
  …
  {:email "bob@acme.com"
   :roles #{:admin}})
```

If the password is wrong, you may choose to return either an empty map or nil. If you return an empty map (a truthy value) and the resource requires credentials that aren't in the map, a 403 Forbidden response will be returned. However, if you return nil, this will be treated as no credentials being sent and a 401 Unauthorized response will be returned.

From a UX perspective there is a difference. If the user-agent is a browser, returning nil will mean that the password dialog will reappear for every failed login attempt. If you return truthy, it will show the 403 Forbidden response.

You may choose to limit the number of times a failed 'login' attempt is tolerated by setting a cookie or other means.

### Digest authentication

[coming soon]

### Cookie authentication

We can also use cookies to present authentication credentials. The advantage of cookies is that they can be set by the server based on custom authentication interaction with the user, such as the submission of a login-form.

To protect a site with cookies:

```clojure
{:access-control
  {:scheme :cookie
   :cookie "session"
   :verify (fn [cookie] …}}
```

### JWT authentication

[coming soon]

### Form-based logins

Basic Authentication has a number of weaknesses, such as the difficulty of logging out and the lack of control that a website has over the fields presented to a human.  Therefore, the vast majority of websites prefer to use a custom login form generated in HTML.

You can think of a login form as a resource that lets the user present one set of credentials in order to acquire additional ones. The credentials the user presents, via a form, are verified and if they are true, a cookie is generated that certifies this. This cookie provides the certification to subsequent requests in which it is sent.

Let's start by building this login resource that will provide a login form page to browsers and verify the form data when that form is submitted.

Here's a simplistic but viable resource model for the two methods involved:

```clojure
(require
 '[buddy.sign.jws :as jws]
 '[schema.core :as s]
 '[hiccup.core :refer [html])

{:methods
 {:post
  {:consumes "application/x-www-form-urlencoded"
   :parameters {:form
                {:user s/Str :password s/Str}}

   :response
   (fn [ctx]
     (let [{:keys [user password]} (get-in ctx [:parameters :form])]
       (if (valid-user user password)
         (assoc (:response ctx)
                :cookies {"session"
                          {:value
                           (jws/sign {:user user} "lp0fTc2JMtx8")}})
         "Try again!")))}
  :get
  {:produces "text/html"
   :response (html
              [:form
               [:input {:name "user" :type :text}]
               [:input {:name "password" :type :password}]
               [:input {:type :submit}]])}}}
```

The POST method method consumes incoming URL-encoded data (the classic way a browser sends form data). It de-structures the two parameters (user and password) from the form parameters.

We then determine if the user and password are valid (we don't explain here how this is done, but assume a `valid-user` function exists that can tell us). If the user is valid we associate a new cookie called "session" with the response. By starting with the `:response` value of the request context, we ensure yada interprets our return value as a Ring response rather than some other value.

We use Buddy's `sign` function to sign and encoded the cookie's value as a JSON string. We only specify the credentials as `{:user user}` in this case, but we could put much more into that map. The `sign` function requires us to provide a secret symmetric key that we can use for both signing and verification, but the library does allow us asymmetric key options too.

The other method, GET, simply produces a form for user-agents that can render HTML (browsers, typically) to post back. For reasons of cohesion, it's a good idea to provide these two methods in the same resource to encapsulate and dedupe the fields which are relevant to both the GET and the POST.

### Protecting resources

[Coming soon]

### Logout

The recommended way of logging out is to remove the session.

### Bearer authentication (OAuth2)

[coming soon]

### Multifactor authentication

[coming soon]

## Authorization

Authorization is the process of allowing a user access to a resource. This may require knowledge about the user only (for example, in [Role-based access control](https://en.wikipedia.org/wiki/Role-based_access_control)). Authorization may also depend on properties of the resource identified by the HTTP request's URI (as part of an [Attribute-based access control](https://en.wikipedia.org/wiki/Attribute-based_access_control) authorization scheme).

In either case, we assume that the user has already been authenticated, and we are confident that their credentials are genuine.

In yada, authorization happens _after_ the resource's properties has been loaded, because it may be necessary to check some aspect of the resource itself as part of the authorization process.

Any method can be protected by declaring a role or set of roles in its model.

```clojure
{:methods {:post :accounts/create-transaction}}
```

If multiple roles are involved, they can be composed inside vectors using simple predicate logic.

```clojure
{:methods {:post [:or [:and :accounts/user
                            :accounts/create-transaction]
                      :superuser}}
```

Only the simple boolean 'operators' of `:and`, `:or` and `:not` are allowed in this authorization scheme. This keeps the role definitions declarative and easy to extract and process by other tooling.

Of course, authentication information is available in the request context when a method is invoked, so any method may apply its own custom authorization logic as necessary. However, yada encourages developers to adopt a declarative approach to resources wherever possible, to maximise the integration opportunities with other libraries and tools.

## Realms

yada supports multiple realms.

## Cross-Origin Resource Sharing (CORS)

[coming soon]
