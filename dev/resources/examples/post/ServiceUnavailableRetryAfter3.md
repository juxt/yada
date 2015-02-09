It might also be useful to wait for some short period of time before
responding with the 503 status code. This would help provide
back-pressure to the user-agent, especially if the number of connections
from it were also limited at the OS level, which would help prevent any
given user-agent from causing excess load on the service.
