$(document).ready(function() {

    $("#selectAll").click(function() {
        var checkBoxes = $("input[name=messageCheckBox]");
        checkBoxes.prop("checked", $("#selectAll").prop("checked"));
    });

});
