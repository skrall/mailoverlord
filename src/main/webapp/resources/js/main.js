
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
              })
        .always(function() {
                  $('#yesNoModel').modal('hide');
              });

}

function performRelease() {
    $("#yesNoModelBody").html("<img src='" + contextRoot + "resources/img/spinner.gif'>");
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
                  location.reload();
              })
        .fail(function () {
                  console.log("Error...");
              })
        .always(function() {
                  $('#yesNoModel').modal('hide');
                    });
}

function displayYesNoDialog(operation, title, body) {
    $("#yesNoModelLabel").text(title);
    $("#yesNoModelBody").text(body);
    $("yesNoDialogYesButton").off();
    $("#yesNoDialogYesButton").on("click", function () {
        operation();
    });
    $("#yesNoModel").modal('show');
}

$(document).ready(function () {

    $("#selectAll").click(function () {
        var checkBoxes = $("input[name=messageCheckBox]");
        checkBoxes.prop("checked", $("#selectAll").prop("checked"));
    });

    $("#deleteButton").click(function () {
        displayYesNoDialog(performDelete, "Delete selected messages?", "Do you want to remove the selected messages permanently?")
    });

    $("#releaseButton").click(function () {
        displayYesNoDialog(performRelease, "Release selected messages?", "Do you want to release the selected messages?");
    });
});
