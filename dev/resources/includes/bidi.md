Resources should be independent from URI routing. However, in REST services, representations should contain hyperlinks to other resources, as URIs.

Due to the importance of creating links to resources that don't break
when the resource is relocated to another URI, it is better to use yada
with a routing library that features support for creating links from
resources. Core yada functionality is supported for us with any routing
library, but there are some extra features available when yada is
combined with [bidi](https://github.com/juxt/bidi).
