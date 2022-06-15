<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if errorMessages??>
    <#list errorMessages as message>
        <p class="alert errorAlert">${message}</p>
    </#list>
</#if>


<div class="settingsFormContainer">
    <h2>Change subscription</h2>

    <p>Changes to your plan are prorated. If you switch halfway through the month, then at the end of the month you'll be charged for one half-month on the old plan, and one half-month on the new plan.</p>

    <form class="settingsForm" method="post" action="/doChangePlan">

        <div>
            <input type="radio" name="plan" id="small" value="small">
            <label for="small">Small (max 50 GB) &mdash; $4.99 / month / user</label>
        </div>

        <div>
            <input type="radio" name="plan" id="medium" value="medium">
            <label for="medium">Medium (max 200 GB) &mdash; $9.99 / month / user</label>
        </div>

        <div>
            <input type="radio" name="plan" id="large" value="large">
            <label for="large">Large (max 1000 GB) &mdash; $19.99 / month / user</label>
        </div>

        <div>
            <input type="radio" name="plan" id="cancel" value="cancel">
            <label for="cancel">Cancel subscription</label>
            <p>Cancelling your subscription will end read and write access for all users. You can re-subscribe at any time.</p>
        </div>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Change plan &raquo;" /></div>
    </form>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
