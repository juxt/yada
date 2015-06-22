Here we create a core.async channel and start a CSP process which loops
10 times. Each time we put a message containing the current time to the
channel, and pause for a second. After 10 tries we close the core.async
channel. Upon closing this channel, yada (actually manifold!)
automatically closes the connection with the user-agent.

<handler/>

<curl/>

<response/>
