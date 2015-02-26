It's perfectly OK to return a number rather than a date for the
resource's __:last-modified__ entry, and the header will still be returned to the user agent as a date.

The number will be interpretted as the number of milliseconds since the epoch (Jan 1st, 1970, UTC).
