(ns yada.status)

;; Copied from https://github.com/metosin/ring-http-response
(def status
  "Maps status to name and description"
  {
   100 {:name "Continue"
        :description "The server has received the request headers and the client should proceed to send the request body."}
   101 {:name "Switching Protocols"
        :description "The server is switching protocols because the client requested the switch."}
   102 {:name "Processing"
        :description "The server is processing the request but no response is available yet."}
   200 {:name "OK"
        :description "OK"}
   201 {:name "Created"
        :description "The request has been fulfilled and resulted in a new resource being created."}
   202 {:name "Accepted"
        :description "The request has been accepted for processing but the processing has not been completed."}
   203 {:name "Non-Authoritative Information"
        :description "The server successfully processed the request but is returning information that may be from another source."}
   204 {:name "No Content"
        :description "The server successfully processed the request, but is not returning any content. Usually used as a response to a successful delete request."}
   205 {:name "Reset Content"
        :description "The server successfully processed the request but is not returning any content. Unlike a 204 response, this response requires that the requester reset the document view."}
   206 {:name "Partial Content"
        :description "The server is delivering only part of the resource due to a range header sent by the client."}
   207 {:name "Multi-Status"
        :description "The message body that follows is an XML message and can contain a number of separate response codes depending on how many sub-requests were made."}
   208 {:name "Already Reported"
        :description "The members of a DAV binding have already been enumerated in a previous reply to this request and are not being included again."}
   226 {:name "IM Used"
        :description "The server has fulfilled a GET request for the resource and the response is a representation of the result of one or more instance-manipulations applied to the current instance."}
   300 {:name "Multiple Choices"
        :description "There are multiple options for the resource that the client may follow."}
   301 {:name "Moved Permanently"
        :description "This and all future requests should be directed to the given URI."}
   302 {:name "Found"
        :description "The resource was found but at a different URI."}
   303 {:name "See Other"
        :description "The response to the request can be found under another URI using a GET method."}
   304 {:name "Not Modified"
        :description "The resource has not been modified since last requested."}
   305 {:name "Use Proxy"
        :description "This single request is to be repeated via the proxy given by the Location field."}
   307 {:name "Temporary Redirect"
        :description "The request should be repeated with another URI but future requests can still use the original URI."}
   308 {:name "Permanent Redirect"
        :description "The request and all future requests should be repeated using another URI."}
   400 {:name "Bad Request"
        :description "The request contains bad syntax or cannot be fulfilled."}
   401 {:name "Unauthorized"
        :description "Authentication is possible but has failed or not yet been provided."}
   402 {:name "Payment Required"
        :description "Reserved for future use."}
   403 {:name "Forbidden"
        :description "The request was a legal request but the server is refusing to respond to it."}
   404 {:name "Not Found"
        :description "The requested resource could not be found but may be available again in the future."}
   405 {:name "Method Not Allowed"
        :description "A request was made of a resource using a request method not supported by that resource;"}
   406 {:name "Not Acceptable"
        :description "The requested resource is only capable of generating content not acceptable according to the Accept headers sent in the request."}
   407 {:name "Proxy Authentication Required"
        :description "Proxy authentication is required to access the requested resource."}
   408 {:name "Request Timeout"
        :description "The server timed out waiting for the request."}
   409 {:name "Conflict"
        :description "The request could not be processed because of conflict in the request such as an edit conflict."}
   410 {:name "Gone"
        :description "The resource requested is no longer available and will not be available again."}
   411 {:name "Length Required"
        :description "The request did not specify the length of its content which is required by the requested resource."}
   412 {:name "Precondition Failed"
        :description "The server does not meet one of the preconditions that the requester put on the request."}
   413 {:name "Request Entity Too Large"
        :description "The request is larger than the server is willing or able to process."}
   414 {:name "Request-URI Too Long"
        :description "The URI provided was too long for the server to process."}
   415 {:name "Unsupported Media Type"
        :description "The request entity has a media type which the server or resource does not support."}
   416 {:name "Requested Range Not Satisfiable"
        :description "The client has asked for a portion of the file but the server cannot supply that portion."}
   417 {:name "Expectation Failed"
        :description "The server cannot meet the requirements of the Expect request-header field."}
   420 {:name "Enhance Your Calm"
        :description "You are being rate-limited."}
   422 {:name "Unprocessable Entity"
        :description "The request was well-formed but was unable to be followed due to semantic errors."}
   423 {:name "Locked"
        :description "The resource that is being accessed is locked."}
   424 {:name "Failed Dependency"
        :description "The request failed due to failure of a previous request."}
   425 {:name "Unordered Collection"
        :description "The collection is unordered."}
   426 {:name "Upgrade Required"
        :description "The client should switch to a different protocol."}
   428 {:name "Precondition Required"
        :description "The server requires the request to be conditional."}
   429 {:name "Too Many Requests"
        :description "The user has sent too many requests in a given amount of time."}
   431 {:name "Request Header Fields Too Large"
        :description "The server is unwilling to process the request because either an individual header field or all the header fields collectively are too large."}
   449 {:name "Retry With"
        :description "The request should be retried after doing the appropriate action."}
   450 {:name "Blocked by Windows Parental Controls"
        :description "Windows Parental Controls are turned on and are blocking access to the given webpage."}
   451 {:name "Unavailable For Legal Reasons"
        :description "Resource access is denied for legal reasons."}
   500 {:name "Internal Server Error"
        :description "There was an internal server error."}
   501 {:name "Not Implemented"
        :description "The server either does not recognize the request method or it lacks the ability to fulfill the request."}
   502 {:name "Bad Gateway"
        :description "The server was acting as a gateway or proxy and received an invalid response from the upstream server."}
   503 {:name "Service Unavailable"
        :description "The server is currently unavailable (because it is overloaded or down for maintenance)."}
   504 {:name "Gateway Timeout"
        :description "The server was acting as a gateway or proxy and did not receive a timely request from the upstream server."}
   505 {:name "HTTP Version Not Supported"
        :description "The server does not support the HTTP protocol version used in the request."}
   506 {:name "Variant Also Negotiates"
        :description "Transparent content negotiation for the request results in a circular reference."}
   507 {:name "Insufficient Storage"
        :description "Insufficient storage to complete the request."}
   508 {:name "Loop Detected"
        :description "The server detected an infinite loop while processing the request."}
   509 {:name "Bandwidth Limit Exceeded"
        :description "Bandwidth limit has been exceeded."}
   510 {:name "Not Extended"
        :description "Further extensions to the request are required for the server to fulfill it."}
   511 {:name "Network Authentication Required"
        :description "The client needs to authenticate to gain network access."}
   598 {:name "Network read timeout"
        :description ""}
   599 {:name "Network connect timeout"
        :description ""}
   })
