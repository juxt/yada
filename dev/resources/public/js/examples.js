clearIt = function(title) {
    var id = "response-"+title;

    // Clear
    $("div#"+id+" .status").text("");
    $("div#"+id+" .headers").text("");
    $("div#"+id+" .body").val("");
}

tryIt = function(meth, u, title, headers) {
    clearIt(title);

    var id = "response-"+title;
    $("div#"+id+" .status").html("Waiting&#8230;");

    $.ajax({type: meth,
            url: u,
            headers: headers})
        .done(function(msg, textStatus, jqXHR) {
            $("div#"+id+" .status").text(jqXHR.status + " " + jqXHR.statusText);
            // $("div#"+id+" .headers").text(jqXHR.getAllResponseHeaders());

            $("div#"+id+" .headers").html(jqXHR.getAllResponseHeaders());
            $("div#"+id+" .body").val(jqXHR.responseText);
        })
        .fail(function(jqXHR) {
            $("div#"+id+" .status").text(jqXHR.status);
            $("div#"+id+" .headers").text(jqXHR.getAllResponseHeaders());
            $("div#"+id+" .body").val(jqXHR.responseText);
        })
}
