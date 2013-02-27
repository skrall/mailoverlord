
function getSelectedMessageIds() {
    var ids = [];
    $("input[name=messageCheckBox]:checked:enabled").each(function () {
        ids.push($(this).val());
    });
    return ids;
}

function performDelete() {
    var ids = getSelectedMessageIds();
    $('#yesNoModel').modal('hide');
    if(ids.length == 0)
        return;
    displaySpinnerDialog('Deleting messages...');
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
                  $('#spinnerModel').modal('hide');
              });

}

function performRelease() {
    $('#yesNoModel').modal('hide');
    displaySpinnerDialog('Releasing messages...');
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
                  $('#spinnerModel').modal('hide');
                    });
}

function performModify() {
    var overrideFrom = $("#overrideFromCheckbox").is(':checked');
    var overrideFromAddress = $("#overrideFromInput").val();
    var overrideTo = $("#overrideToCheckbox").is(':checked');
    var overrideToAddresses = $("#overrideToInput").val();
    $('#modifyModal').modal('hide');
    displaySpinnerDialog("Releasing messages....");
    var ids = getSelectedMessageIds();
    var messageModifyRequest = { messageIds: ids,
                                 overrideTo: overrideTo,
                                 overrideToAddresses: overrideToAddresses,
                                 overrideFrom: overrideFrom,
                                 overrideFromAddress: overrideFromAddress};
    $.ajax({
               url: contextRoot + "messages/release",
               contentType: "application/json",
               type: "POST",
               data: JSON.stringify(messageModifyRequest),
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
                    $('#spinnerModel').modal('hide');
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

function displaySpinnerDialog(title) {
    $("#spinnerModelLabel").text(title);
    $("#spinnerModal").modal('show');
}

$(document).ready(function () {

    $("#selectAll").click(function () {
        var checkBoxes = $("input[name=messageCheckBox]");
        checkBoxes.prop("checked", $("#selectAll").prop("checked"));
    });

    $("#deleteButton").click(function () {
        displayYesNoDialog(performDelete, "Delete selected messages?", "Do you want to remove the selected messages permanently?")
    });
    var tooltipOptions = { delay: { show: 1500, hide: 100 } };
    $("#deleteButton").tooltip(tooltipOptions);

    $("#releaseButton").click(function () {
        displayYesNoDialog(performRelease, "Release selected messages?", "Do you want to release the selected messages?");
    });
    $("#releaseButton").tooltip(tooltipOptions);

    $("#modifyButton").click(function() {
        $("#modifyModal").modal('show');
    });
    $("#modifyButton").tooltip(tooltipOptions);

    $("#overrideFromCheckbox").click(function() {
        if ($("#overrideFromCheckbox").is(':checked')) {
            $("#overrideFromInput").prop("disabled", false);
        } else {
            $("#overrideFromInput").prop("disabled", true);
        }
    });

    $("#overrideToCheckbox").click(function() {
        if ($("#overrideToCheckbox").is(':checked')) {
            $("#overrideToInput").prop("disabled", false);
        } else {
            $("#overrideToInput").prop("disabled", true);
        }
    });

    $("#modifyDialogYesButton").click(function() {
        performModify();
    });
});
