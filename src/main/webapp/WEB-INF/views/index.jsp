<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--@elvariable id="messageJspData" type="org.mailoverlord.server.model.MessageJspData"--%>
<%--@elvariable id="pagination" type="org.mailoverlord.server.model.Pagination"--%>
<!DOCTYPE html>
<!--[if lt IE 7]>      <html class="no-js lt-ie9 lt-ie8 lt-ie7"> <![endif]-->
<!--[if IE 7]>         <html class="no-js lt-ie9 lt-ie8"> <![endif]-->
<!--[if IE 8]>         <html class="no-js lt-ie9"> <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js"> <!--<![endif]-->
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <title></title>
    <meta name="description" content="">
    <meta name="viewport" content="width=device-width">

    <link rel="stylesheet" href="<c:url value='/resources/css/bootstrap.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/resources/css/bootstrap-responsive.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/resources/css/main.css'/>">

    <script src="<c:url value='/resources/js/vendor/modernizr-2.6.2-respond-1.1.0.min.js'/>"></script>
    <script type="text/javascript">
        contextRoot = <c:url value='/'/>;
    </script>
</head>
<body>
<!--[if lt IE 7]>
<p class="chromeframe">You are using an <strong>outdated</strong> browser. Please <a href="http://browsehappy.com/">upgrade your browser</a> or <a href="http://www.google.com/chromeframe/?redirect=true">activate Google Chrome Frame</a> to improve your experience.</p>
<![endif]-->

<!-- This code is taken from http://twitter.github.com/bootstrap/examples/hero.html -->

<div class="navbar navbar-inverse navbar-fixed-top">
    <div class="navbar-inner">
        <div class="container">
            <a class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </a>
            <a class="brand" href="#">Mail Overlord</a>
        </div>
    </div>
</div>

<div class="container">

    <!-- Main hero unit for a primary marketing messageIds or call to action -->
    <div class="hero-unit">
        <table class="table">
            <thead>
            <tr>
                <th>From Address</th>
                <th>To Addresses</th>
                <th>Received Time</th>
                <th><input type="checkbox" name="selectAll" id="selectAll"/> Select All</th>
            </tr>
            </thead>
            <tbody>
                <c:forEach var="message" items="${messageJspData.page.content}">
                <tr>
                    <td>${message.from}</td>
                    <td>
                        <div class="from">${message.to}</div>
                    </td>
                    <td>${message.receivedTimestamp}</td>
                    <td><input type="checkbox" name="messageCheckBox" value="${message.id}"/></td>
                </tr>
                </c:forEach>
            </tbody>
            <tfoot>
            <tr>
                <td colspan="5">
                    <div id="buttons" class="pull-right">
                        <button id="releaseButton" class="btn btn-small" data-toggle="tooltip"
                                title="Release email as is, without changing to or from address.">Release</button>
                        <button id="modifyButton" class="btn btn-small btn-info" data-toggle="tooltip"
                                title="Release the email with a chance to change to the to and or from address">Modif
                            y</button>
                        <button id="deleteButton" class="btn btn-small btn-danger" data-toggle="tool-tip"
                                title="Delete the message permanently.">Delete</button>
                    </div>
                </td>
            </tr>
            </tfoot>
        </table>
        <div class="pagination pagination-right">
            <ul>
                <c:choose>
                    <c:when test="${pagination.displayPreviousPageLink}">
                        <li><a href="<c:url value='/?page.page=${pagination.previousPageLinkNumber}'/>">Prev</a></li>
                    </c:when>
                    <c:otherwise>
                        <li class="disabled"><a href="#">Prev</a></li>
                    </c:otherwise>
                </c:choose>
                <c:forEach begin="${pagination.startPageNumber}" end="${pagination.endPageNumber}" step="1"
                           var="index">
                    <c:choose>
                        <c:when test="${pagination.currentPageNumber == index}">
                            <li class="disabled"><a href="#">${index}</a></li>
                        </c:when>
                        <c:otherwise>
                            <li><a href="<c:url value='/?page.page=${index}'/>">${index}</a></li>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
                <c:choose>
                    <c:when test="${pagination.displayNextPageLink}">
                        <li><a href="<c:url value='/?page.page=${pagination.nextPageLinkNumber}'/>">Next</a></li>
                    </c:when>
                    <c:otherwise>
                        <li class="disabled"><a href="#">Next</a></li>
                    </c:otherwise>
                </c:choose>
            </ul>
        </div>
    </div>

    <footer>
        <p>Mail Overlord</p>
    </footer>

</div> <!-- /container -->

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
<script>window.jQuery ||
        document.write('<script src="<c:url value='/resources/js/vendor/jquery-1.9.1.min.js'/>"><\/script>')</script>

<script src="<c:url value='/resources/js/vendor/bootstrap.min.js'/>"></script>

<script src="<c:url value='/resources/js/plugins.js'/>"></script>
<script src="<c:url value='/resources/js/main.js'/>"></script>

<%--
<script>
    var _gaq=[['_setAccount','UA-XXXXX-X'],['_trackPageview']];
    (function(d,t){var g=d.createElement(t),s=d.getElementsByTagName(t)[0];
        g.src=('https:'==location.protocol?'//ssl':'//www')+'.google-analytics.com/ga.js';
        s.parentNode.insertBefore(g,s)}(document,'script'));
</script>
--%>

<!-- Yes / No Modal -->
<div id="yesNoModel" class="modal hide fade" tabindex="-1" role="dialog" aria-labelledby="yesNoModelLabel"
     aria-hidden="true">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h3 id="yesNoModelLabel">..... Put model title in here ....</h3>
    </div>
    <div class="modal-body">
        <p id="yesNoModelBody">.... Put message in here ....</p>
    </div>
    <div class="modal-footer">
        <button class="btn" data-dismiss="modal" aria-hidden="true">No</button>
        <button id="yesNoDialogYesButton" class="btn">Yes</button>
    </div>
</div>

<!-- Modify Model -->
<div id="modifyModal" class="modal hide fade" tabindex="-1" role="dialog" aria-labelledby="modifyModelLabel"
     aria-hidden="true">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h3 id="modifyModelLabel">Modify and release message</h3>
    </div>
    <div class="modal-body">
        <form>
            <fieldset>
                <legend>Override Email Addresses</legend>
                <label class="checkbox">
                    <input id="overrideFromCheckbox" type="checkbox"> Override From
                </label>
                <label for="overrideFromInput">Override From Address</label>
                <input id="overrideFromInput" type="text" placeholder="user@asdf.com" disabled>
                <span class="help-block">Example block-level help text here.</span>
                <label class="checkbox">
                    <input id="overrideToCheckbox" type="checkbox"> Override To
                </label>
                <label for="overrideToInput">Override To Addresses</label>
                <textarea id="overrideToInput" rows="5" cols="50" disabled>user1@asdf.com user2@asdf.com</textarea>
                <span class="help-block">Separate multiple addresses with white space or a comma.</span>
            </fieldset>
        </form>
    </div>
    <div class="modal-footer">
        <button class="btn" data-dismiss="modal" aria-hidden="true">Cancel</button>
        <button id="modifyDialogYesButton" class="btn">Release</button>
    </div>
</div>

<!-- Spinner Model -->
<div id="spinnerModal" class="modal hide fade" tabindex="-1" role="dialog" aria-labelledby="spinnerModelLabel"
     aria-hidden="true">
    <div class="modal-header">
        <h3 id="spinnerModelLabel">...  Replace with title ...</h3>
    </div>
    <div class="modal-body">
        <div class="pagination-centered">
            <img src="<c:url value='/resources/img/spinner.gif'/>">
        </div>
    </div>
</div>

</body>
</html>
