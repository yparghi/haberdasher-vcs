<#import "basefragments.ftlh" as basefragments>
<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if errorMessages??>
    <#list errorMessages as message>
        <p class="alert errorAlert">${message}</p>
    </#list>
</#if>


<div id="signupFormContainer">
    <h2>Sign Up</h2>

    <form id="signupForm" method="post" action="/signup">
        <p class="fieldLabel">Email:</p>
        <p class="textField"><input type="text" name="email"/></p>

        <p class="fieldLabel">Repository name:</p>
        <p class="textField"><input type="text" name="repoName"/></p>
        <p class="fieldMessage">4+ characters long: A-Z, a-z, 0-9, hyphens (-) or underscores (_)</p>

        <p class="fieldLabel">Your password:</p>
        <p class="textField"><input type="password" name="password"/></p>
        <p class="fieldMessage">6+ characters long</p>

        <p class="fieldLabel">Confirm password:</p>
        <p class="textField"><input type="password" name="confirmPassword"/></p>

        <p><input type="checkbox" name="agreeToTerms" value="yes"><label for="agreeToTerms">(Required) I agree to the Haberdasher <a href="/terms">Terms of service</a> and the <a href="/privacy">Privacy policy</a>.</label></p>
        <p><input type="checkbox" name="agreeToProductEmails" value="yes"><label for="agreeToProductEmails">(Optional) I'd like to receive emails with product updates. (Once a month or less. You'll still receive administrative emails.)</label></p>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Sign Up &raquo;" /></div>
    </form>

    <p>You'll receive a confirmation email to complete sign-up.</p>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
