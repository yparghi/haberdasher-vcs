<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if errorMessages??>
    <#list errorMessages as message>
        <p class="alert errorAlert">${message}</p>
    </#list>
</#if>


<div id="inviteFormContainer">
    <h2>Sign up</h2>

    <p>You've been invited to join the repo <b>${repoName}</b>.

    <form id="inviteForm" method="post" action="/acceptInvitation?token=${taskTokenForFormPost?url}">
        <p class="fieldLabel">Email:</p>
        <p class="textField">${inviteEmail}</p>

        <p class="fieldLabel">Your password:</p>
        <p><input class="textField" type="password" name="password"/></p>
        <p class="fieldMessage">6+ characters long</p>

        <p class="fieldLabel">Confirm password:</p>
        <p><input class="textField" type="password" name="confirmPassword"/></p>

        <p><input type="checkbox" name="agreeToTerms" value="yes"><label for="agreeToTerms">(Required) I agree to the Haberdasher <a href="/terms">Terms of service</a> and the <a href="/privacy">Privacy policy</a>.</label></p>
        <p><input type="checkbox" name="agreeToProductEmails" value="yes"><label for="agreeToProductEmails">(Optional) I'd like to receive emails with product updates. (Once a month or less. You'll still receive administrative emails.)</label></p>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Sign up &raquo;" /></div>
    </form>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
