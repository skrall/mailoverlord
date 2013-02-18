<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="messageJspData" class="org.mailoverlord.server.model.MessageJspData" scope="request"/>
<jsp:useBean id="pagination" class="org.mailoverlord.server.model.Pagination" scope="request"/>
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
    <style>
        body {
            padding-top: 60px;
            padding-bottom: 40px;
        }
    </style>
    <link rel="stylesheet" href="<c:url value='/resources/css/bootstrap-responsive.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/resources/css/main.css'/>">

    <script src="<c:url value='/resources/js/vendor/modernizr-2.6.2-respond-1.1.0.min.js'/>"></script>
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
                <c:forEach var="messageIds" items="${messageJspData.page.content}">
                <tr>
                    <td>${message.from}</td>
                    <td>${message.to}</td>
                    <td>${message.receivedTimestamp}</td>
                    <td><input type="checkbox" name="messageCheckBox"/></td>
                </tr>
                </c:forEach>
            </tbody>
            <tfoot>
            <tr>
                <td colspan="5">
                    <div id="buttons" class="pull-right">
                        <button class="btn btn-small">Release</button>
                        <button class="btn btn-small btn-info">Modify</button>
                        <button class="btn btn-small btn-danger">Delete</button>
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

<script src="//ajax.googleapis.com/ajax/libs/jquery/1.9.0/jquery.min.js"></script>
<script>window.jQuery ||
        document.write('<script src="<c:url value='/resources/js/vendor/jquery-1.9.0.min.js'/>"><\/script>')</script>

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
</body>
</html>
