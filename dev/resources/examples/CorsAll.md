If we want to indicate that our resource is public and accessible to any script, no matter where that script originates, we simply set the resource map's __:allow-origin__ entry to `true`.

<handler/>

All requests to the resource will receive a response header where the __Access-Control-Allow-Origin__ header is set to `*`.

<response/>
