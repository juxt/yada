In this example, we declare in the resource-map that both __:account__ and __:account-type__ query parameters much be present in the query string.

<resource-map/>

However, we construct a query string where the __:account-type__ query parameter is missing.

<request/>

This results in a 400, indicating to the user-agent that the request was bad.

<response/>
