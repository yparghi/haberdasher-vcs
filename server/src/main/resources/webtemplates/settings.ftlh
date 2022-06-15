<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if inviteSent>
    <p class="alert successAlert">The invitation email was sent!</p>
</#if>

<#if changePlanSucceeded>
    <p class="alert successAlert">Your subscription plan was successfully changed.</p>
</#if>

<#if changePlanFailed>
    <p class="alert errorAlert">Sorry, there was an error changing your plan! Please try again later, or contact support@haberdashervcs.com</p>
</#if>

<#if cancelPlanSucceeded>
    <p class="alert successAlert">Your subscription plan was successfully cancelled.</p>
</#if>

<#if upgradeSuccessful>
    <p class="alert successAlert">Your upgrade request is being processed. You'll receive an email when it's complete.</p>
</#if>

<#if freeTrialEnded>
    <p class="alert errorAlert">Your free trial has ended. Please upgrade below if you'd like to continue using Haberdasher.</p>
</#if>

<#if paidSubEnded>
    <p class="alert errorAlert">Your subscription has ended. Please re-subscribe below if you'd like to continue using Haberdasher.</p>
</#if>

<#if settingsSaved>
    <p class="alert successAlert">Your settings have been saved.</p>
</#if>


<p>Your CLI token is:</p>
<p id="cliTokenHolder">${initialTokenText}</p>
<button id="generateButton" onclick="hdSettingsHelper.getNewCliToken();">Generate New Token</button>
<p id="generateExplanation">A new token will replace your current one. It will only be shown here once, so save it somewhere safe!</p>


<hr/>
<div id="settingsFormDiv">
<form id="userSettingsForm" method="post" action="/saveUserSettings">

<p>
    <input type="checkbox" name="agreeToProductEmails" value="yes" <#if agreeToProductEmailsCurrently>checked</#if>>
    <label for="agreeToProductEmails">I'd like to receive emails with product updates. (Once a month or less. You'll still receive administrative emails.)</label>
</p>

<div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Save settings"></div>
</form>
</div>

<#if displayUsers??>
    <hr/>
    <h3>Users</h3>

    <#if allowInvites>
    <p><a href="/invite">+ Invite user</a></p>
    </#if>

    <#list displayUsers as displayUser>
        <p> <span>${displayUser.email}</span> <span>${displayUser.role}</span> </p>
    </#list>
</#if>


<#if displayBilling??>
    <hr/>
    <h3>Billing</h3>
    <p>Subscription status: <i>${billing.state}</i></p>
    <p><a href="/changePlan">Change or cancel plan</a></p>
</#if>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
