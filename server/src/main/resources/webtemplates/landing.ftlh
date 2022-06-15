<#import "basefragments.ftlh" as basefragments>
<@basefragments.pageStart title />
<@basefragments.topNavBar />


<div id="headDiv">
<div id="headDivInner">

    <div id="headTextDiv">
        <p id="headBlurb">Millions of files.<br>Millions of commits.<br>Limitless version control.</p>
        <p class="blurbText">Haberdasher is the modern version control system for huge codebases and huge teams. With a simple, Git-like interface for branching and pushing, Haberdasher combines modern development workflows with limitless scale.</p>
        <div class="buttonLinkDiv buttonLinkDivLeft">
            <!--<a class="buttonLink" href="/signup">Create a repo</a>-->
            <a class="buttonLink buttonLinkLeft" href="/requestDemo">Request a demo</a>
        </div>
    </div>


    <div id="terminalDiv">
        <div class=fakeMenu>
          <div class="fakeButtons fakeClose"></div>
          <div class="fakeButtons fakeMinimize"></div>
          <div class="fakeButtons fakeZoom"></div>
        </div>
        <div class="fakeScreen">
          <p class="line1">$ cp -r /usr/local/lib .</p>
          <p class="line2">$ hd commit "Save dependencies"</p>
          <p class="line3">$ hd push</p>
        </div>
    </div>

</div>
</div>


<div class="concaveTop"></div>
<div id="branchUiDiv">
    <div id="branchUiDivInner">
        <h2 class="sectionHeader">A Git-like workflow, at scale</h2>
        <p class="blurbText blurbTextLight">Commit and push, locally and remotely, using large branches or small. Work the way that's right for your team. <b>Supports files up to 1 GB.</b></p>
        <img class="screenshotImg" src="/static/landing/hdhome.png">
    </div>
</div>
<div class="convexBottom"></div>


<div id="codeReviewDiv">
    <div id="codeReviewDivInner">
        <h2 class="sectionHeader">A modern diff and code review UI</h2>
        <p class="blurbText">A familiar code review UI that "just works" with your branches.</p>
        <img class="screenshotImg" src="/static/landing/review.png">
    </div>
</div>


<div class="concaveTop"></div>
<div id="featuresDiv">
    <div id="featuresDivInner">

        <h2 class="sectionHeader">Features</h2>


        <div id="newFeatureDiv">
            <div class="featureRow">
                <div class="featureSquare" style="visibility: hidden;">Blank</div><div class="featureSquare featureWhite">Haberdasher</div><div class="featureSquare featureWhite">Git</div><div class="featureSquare featureWhite">Perforce</div>
            </div>
            <div class="featureRow">
                <div class="featureSquare featureWhite">Terabyte repos?</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No (1-2 GB)</div><div class="featureSquare featureGreen">Yes</div>
            </div>
            <div class="featureRow">
                <div class="featureSquare featureWhite">Local commits & branches?</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No</div>
            </div>
            <div class="featureRow">
                <div class="featureSquare featureWhite">Large files?</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">Extension only</div><div class="featureSquare featureGreen">Yes</div>
            </div>
            <div class="featureRow">
                <div class="featureSquare featureWhite">Partial checkouts?</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureYellow">Incomplete</div><div class="featureSquare featureGreen">Yes</div>
            </div>
            <div class="featureRow">
                <div class="featureSquare featureWhite">Free self-hosting?</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No</div>
            </div>
        </div>


        <div id="newFeatureDivMobile">
            <div class="featureRow">
                <div class="featureSquare featureWhite">HD</div><div class="featureSquare featureWhite">Git</div><div class="featureSquare featureWhite">Perforce</div>
            </div>

            <div class="featureRow"><div class="featureSquare featureWhite">Terabyte repos?</div></div>
            <div class="featureRow">
                <div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No (1-2 GB)</div><div class="featureSquare featureGreen">Yes</div>
            </div>

            <div class="featureRow"><div class="featureSquare featureWhite">Local commits & branches?</div></div>
            <div class="featureRow">
                <div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No</div>
            </div>

            <div class="featureRow"><div class="featureSquare featureWhite">Large files?</div></div>
            <div class="featureRow">
                <div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">Extension only</div><div class="featureSquare featureGreen">Yes</div>
            </div>

            <div class="featureRow"><div class="featureSquare featureWhite">Partial checkouts?</div></div>
            <div class="featureRow">
                <div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureYellow">Incomplete</div><div class="featureSquare featureGreen">Yes</div>
            </div>

            <div class="featureRow"><div class="featureSquare featureWhite">Free self-hosting?</div></div>
            <div class="featureRow">
                <div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureGreen">Yes</div><div class="featureSquare featureRed">No</div>
            </div>
        </div>

    </div>
</div>
<div class="convexBottom"></div>


<div id="pricingDiv">
    <div id="pricingDivInner">

        <h2 id="pricing" class="sectionHeader">Pricing</h2>
        <p style="text-align: center;">Free for 30 days, no credit card required.<br>Upgrade or cancel at any time.</p>

        <div class="priceBoxesDiv">
            <div class="priceBox">
                <p class="priceName">Small</p>
                <p class="priceSize">50 GB</p>
                <p class="actualPrice">$4.99</p>
                <p class="priceDetail">per monthly user</p>
            </div>

            <div class="priceBox">
                <p class="priceName">Medium</p>
                <p class="priceSize">200 GB</p>
                <p class="actualPrice">$9.99</p>
                <p class="priceDetail">per monthly user</p>
            </div>

            <div class="priceBox">
                <p class="priceName">Large</p>
                <p class="priceSize">1000 GB</p>
                <p class="actualPrice">$19.99</p>
                <p class="priceDetail">per monthly user</p>
            </div>
        </div>

        <div class="buttonLinkDiv"><a class="buttonLink" href="/signup">Start a 30-day free trial</a></div>
        <#--<div class="buttonLinkDiv"><a class="buttonLink" href="/requestDemo">Request a demo</a></div>-->

    </div>
</div>



<@basefragments.footer />
<@basefragments.pageEnd />
