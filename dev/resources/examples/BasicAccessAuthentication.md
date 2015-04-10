Here's a fun example to demonstrate Basic Access Authentication.

First we must add a __:security__ entry, with __:type__ set to `:basic` and a __:realm__ specified.

In the __:authorization__ function, we can pull out the credentials from the request context's __:authentication__ entry. For basic access authentication, this is a normal map containing the user and password provided by the user.

<resource-map/>

Examine the code above. Note that __:not-authorized__ is returned if no
authentication credentials exist, or if the credentials are wrong. This
is not a very sophisticated policy and a better implementation would
rate limit the number of incorrect attempts from each remote IP address.

Let's try the request. A dialog will pop up. See if you can guess the credentials! (Hint: look at the code above!)

<response/>
