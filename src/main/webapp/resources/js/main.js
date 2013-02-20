$(document).ready(function () {

    $("#selectAll").click(function () {
        var checkBoxes = $("input[name=messageCheckBox]");
        checkBoxes.prop("checked", $("#selectAll").prop("checked"));
    });

    $("#deleteButton").click(function (index) {
        var ids = new Array();
        $("input[name=messageCheckBox]:checked:enabled").each(function () {
            ids.push($(this).val());
        });
        var messageDeleteRequest = {messageIds: ids};
        $.ajax({
                   url: contextRoot + "messages/delete",
                   contentType: "application/json",
                   type: "POST",
                   data: JSON.stringify(messageDeleteRequest),
                   processData: false
               })
            .done(function () {
                      console.log("Success...");
                  })
            .fail(function () {
                      console.log("Error...");
                  });
    });

    $("#releaseButton").click(function (index) {
        var ids = new Array();
        $("input[name=messageCheckBox]:checked:enabled").each(function () {
            ids.push($(this).val());
        });
        var messageReleaseRequest = {messageIds: ids};
        $.ajax({
                   url: contextRoot + "messages/release",
                   contentType: "application/json",
                   type: "POST",
                   data: JSON.stringify(messageReleaseRequest),
                   processData: false
               })
            .done(function () {
                      console.log("Success...");
                  })
            .fail(function () {
                      console.log("Error...");
                  });
    });
});
