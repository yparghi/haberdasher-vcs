class HdSettingsHelper {
    constructor() {
        this.hostUrl = window.location.origin;
    }

    getNewCliToken() {
        var xhr = new XMLHttpRequest();
        xhr.open("POST", this.hostUrl + "/newCliToken", true);
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.responseType = "text";
        xhr.onreadystatechange = function(e) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    let parsedJson = JSON.parse(xhr.responseText);
                    document.getElementById("cliTokenHolder").textContent = parsedJson["newTokenId"];
                } else {
                    console.log("Error in xhr", xhr);
                }
            }
        };
        xhr.send("{}");
    }
}

let hdSettingsHelper = new HdSettingsHelper();
