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
    <h2>Invite a user to your repo</h2>

    <form id="inviteForm" method="post" action="/invite">
        <p class="fieldLabel">Email:</p>
        <p><input class="textField" type="text" name="email"/></p>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Invite &raquo;" /></div>
    </form>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
