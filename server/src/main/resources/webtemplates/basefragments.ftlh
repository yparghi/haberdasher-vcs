<#macro pageStart title noindex=false>
<!DOCTYPE html>
<html>
    <head>
        <title>${title}</title>
        <link rel="stylesheet" href="/static/base.css" />
        <#if stylesheetThisPage??><link rel="stylesheet" href="${stylesheetThisPage}" /></#if>
        <#if javascriptIncludesThisPage??>
            <#list javascriptIncludesThisPage as jsInclude>
                <script type="text/javascript" src="${jsInclude}"></script>
            </#list>
        </#if>

        <meta name="viewport" content="width=device-width,initial-scale=1" />
        <#if noindex>
            <meta name="robots" content="noindex" />
        </#if>
    </head>

    <body>
        <div id="mainContainer">
</#macro>


<#macro topNavBar>
    <div id="topNavBar">

        <span id="topBarLeft">
            <#if navBar_isLanding!false>
                <a href="#pricing">Pricing</a>
            </#if>
            <a href="/docs">Docs</a>
        </span>

        <span id="topBarCenter">
            <a id="navBarTopLink" href="<#if navBar_isLoggedIn!false>/home<#else>/</#if>">
                <img src="/static/hdlogo.svg" style="height: 35px;">
                <span id="navBarCenterTitle">haberdasher</span>
            </a>
        </span>

        <span id="topBarRight">
            <#if navBar_isLoggedIn!false>
                <a href="/download">Download</a>
                <a href="/settings">Settings</a>
                <a href="/logout">Logout</a>
            <#else>
                <a href="/login">Login</a>
                <a href="/signup">Sign up</a>
            </#if>
        </span>

    </div>
</#macro>


<#macro vcsContentStart>
<div id="vcsContentDiv">
</#macro>

<#macro vcsContentEnd>
</div> <!-- vcsContentDiv -->
</#macro>

<#macro footer>
    <div id="footer">
        <div id="footerLogoDiv"><img src="/static/hdlogo_white.svg" style="height: 35px;"><span id="footerLogoText">haberdasher</span></div>
        <a class="footerLink" href="mailto:hello@haberdashervcs.com">hello@haberdashervcs.com</a>
        <a class="footerLink" href="https://twitter.com/haberdashervcs">@haberdashervcs</a>
        <a class="footerLink" href="/blog">Blog</a>
        <a class="footerLink" href="https://groups.google.com/g/haberdasher-discuss">Forum</a>
        <a class="footerLink" href="/terms">Terms</a>
        <a class="footerLink" href="/privacy">Privacy</a>
        <span id="footerCopyright">Copyright 2022 Haberdasher LLC</span>
    </div>
</#macro>


<#macro pageEnd>
        </div> <!-- mainContainer -->
    </body>
</html>
</#macro>
