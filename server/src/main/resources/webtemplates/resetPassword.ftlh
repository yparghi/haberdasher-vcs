<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />


<#if resetJustSent!false>
<p class="alert successAlert">A reset link was sent to your email address.</p>
</#if>


<h3>Reset password</h3>


<#if doActualReset!false>

    <div id="formContainer">
        <form method="post" action="/resetPassword?resetToken=${resetToken?url}">
            <p>New password:</p>
            <p><input type="password" id="passwordInput" name="password"/></p>
            <p>Confirm password:</p>
            <p><input type="password" id="confirmPasswordInput" name="confirmPassword"/></p>
            <p><input type="submit" value="Reset password"/></p>
        </form>
    </div>

<#else>

    <div id="formContainer">
        <form method="post" action="/resetPassword">
            <p>Email:</p>
            <p><input type="text" id="emailInput" name="email"/></p>
            <p><input type="submit" value="Send Reset Email"/></p>
        </form>
    </div>

</#if>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />
