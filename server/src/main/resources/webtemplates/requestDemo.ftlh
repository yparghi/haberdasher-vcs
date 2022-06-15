<#import "basefragments.ftlh" as basefragments>
<@basefragments.pageStart title />
<@basefragments.topNavBar />


<#if missingFields>
    <p class="alert errorAlert">All fields are required (except company)</p>
</#if>

<#if demoEmailSent>
    <p class="alert successAlert">Thank you! Our team has been notified and we'll reach out by email within a business day.</p>
</#if>


<h1 id="heading">Request a demo</h1>

<div id="robotDiv"><img id="robotImg" src="/static/landing/robot.jpg"></div>


<div id="wholeDiv">

<div id="copyDiv">
    <h2>What to expect</h2>
    <p>We'll explain the basic Haberdasher workflow and compare & contrast it to other common version control systems. Then we'll show you an example repository and commands to demonstrate how Haberdasher:</p>
    <ul>
        <li>Simplifies dependency management (checking everything in)</li>
        <li>Handles large files</li>
        <li>Scales monorepos</li>
    </ul>
    <p>Then we can discuss your use cases and answer any questions. The demo lasts anywhere from 15-30 minutes depending on your preference.</p>
</div>


<div id="demoFormContainer">
    <form id="demoForm" method="post" action="/requestDemo">
        <p class="fieldLabel">Your name*</p>
        <p class="textField"><input type="text" name="name"></p>

        <p class="fieldLabel">Email*</p>
        <p class="textField"><input type="text" name="email"></p>

        <p class="fieldLabel">Company (optional)</p>
        <p class="textField"><input type="text" name="company"></p>

        <p class="fieldLabel">What would you like to learn about Haberdasher?*</p>
        <p class="textField"><textarea name="interest" rows="3"></textarea></p>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Request a demo &raquo;" /></div>
    </form>
</div>

</div> <!-- wholeDiv -->


<@basefragments.footer />
<@basefragments.pageEnd />
