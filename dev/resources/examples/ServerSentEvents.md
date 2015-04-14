For example, here we simply specify a vector of events, creating a resource map with a single __:events__ entry.

<resource-map/>

That's it, because yada takes care of all the other details.

We can test the resource on the console by using the following curl command.

<curl/>

Or see the result in the browser.

<response/>

Note that the __Content-Type__ header is set to `text/event-stream` which tells the browser to keep the connection open, awaiting more notifications.

Server sent events are very simple to create. Each event is prefixed with `data:` and terminated with two newlines.
