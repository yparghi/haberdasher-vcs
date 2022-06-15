<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if unknownUserPass>
<p class="alert errorAlert">Unknown email and password combination.</p>
</#if>

<#if passwordResetSuccessful>
<p class="alert successAlert">Password reset successful. Please log in with your new password.</p>
</#if>

<#if createUserSuccessful>
<p class="alert successAlert">You've been signed up! Please log in with your email and password.</p>
</#if>

<#if invitationAlreadyAccepted>
<p class="alert successAlert">You've already accepted this invitation. Please log in with your email and password.</p>
</#if>


<div id="loginFormContainer">
    <h2>Login</h2>

    <form id="loginForm" method="post" action="/login">
        <p class="fieldLabel">Email:</p>
        <p><input class="textField" type="text" id="usernameInput" name="username"/></p>
        <p class="fieldLabel">Password:</p>
        <p><input class="textField" type="password" id="passwordInput" name="password"/></p>

        <div class="buttonLinkDiv"><input class="buttonLink" type="submit" value="Login &raquo;" /></div>
    </form>

    <p><a href="/resetPassword">Forgot password?</a></p>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
