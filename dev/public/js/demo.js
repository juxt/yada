clearIt = function(ix) {
    var id = "response-"+ix;

    // Clear
    $("div#"+id+" .status").html("");
    $("div#"+id+" .body").val("");
}

tryIt = function(meth, u, ix) {
    clearIt(ix);

    var id = "response-"+ix;
    $("div#"+id+" .status").html("Waiting&#8230;");

    $.ajax({type: meth, url: u})
        .done(function(msg, textStatus, jqXHR) {
            $("div#"+id+" .status").text(jqXHR.status);
            $("div#"+id+" .body").val(msg);
        })
        .fail(function(jqXHR) {
            $("div#"+id+" .status").text(jqXHR.status);
        })
}
