clearIt = function(ix) {
    var id = "test-"+ix;

    // Clear
    $("#"+id+" .status").text("");
    $("#"+id+" .headers").text("");
    $("#"+id+" .body").val("");
}

testResult = function(jqXHR, expectation) {
    if (jqXHR.status == expectation.status) {
        return "<span class='green'>PASS</span>";
    } else {
        return "<span class='red'>FAIL</span>";
    }
}

testIt = function(meth, u, ix, headers, expectation) {
    clearIt(ix);

    var id = "test-"+ix;
    $("#"+id+" .status").html("Waiting&#8230;");

    $.ajax({type: meth,
            url: u,
            headers: headers})
        .done(function(msg, textStatus, jqXHR) {
            $("#"+id+" .status").text(jqXHR.status + " " + jqXHR.statusText);
            $("#"+id+" .headers").html(jqXHR.getAllResponseHeaders());
            $("#"+id+" .body").val(jqXHR.responseText);
            $("#"+id+" .result").html(testResult(jqXHR, expectation));
        })
        .fail(function(jqXHR) {
            $("#"+id+" .status").text(jqXHR.status);
            $("#"+id+" .headers").text(jqXHR.getAllResponseHeaders());
            $("#"+id+" .body").val(jqXHR.responseText);
            $("#"+id+" .result").html(testResult(jqXHR, expectation));
        })
}

testAll = function() {
    $("button.test").click();
}

testAll();
