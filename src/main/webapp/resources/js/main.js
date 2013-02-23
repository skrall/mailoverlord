
function getSelectedMessageIds() {
    var ids = [];
    $("input[name=messageCheckBox]:checked:enabled").each(function () {
        ids.push($(this).val());
    });
    return ids;
}

function performDelete() {
    var ids = getSelectedMessageIds();
    if(ids.length == 0)
        return;
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
                  location.reload();
              })
        .fail(function () {
                  console.log("Error...");
              });
}

$(document).ready(function () {

    $("#selectAll").click(function () {
        var checkBoxes = $("input[name=messageCheckBox]");
        checkBoxes.prop("checked", $("#selectAll").prop("checked"));
    });

    $("#deleteButton").click(function () {
        $("yesNoDialogYesButton").off();
        $("#yesNoDialogYesButton").on("click", function() {
            performDelete();
            $('#yesNoModel').modal('hide');
        });
        $("#yesNoModel").modal('show');
    });

    $("#releaseButton").click(function () {
        var ids = getSelectedMessageIds();
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
