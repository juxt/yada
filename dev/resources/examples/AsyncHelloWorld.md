The __:body__ is a function which returns a future. The body of the
future puts the thread to sleep for a second, simulating the
effect of a long-running operation.

Note that the function returns a value immediately. The receiving thread sees
that the value is deferred, so parks the job so that it can be freed up
to work on another task.

<resource-map/>

When the sleep is over, a new thread will pick up the job and create the body that will go into the response.

<response/>
