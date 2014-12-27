var APP_NAME	= "Sendroid"
var APP_ICON_18 = "data/assets/logo_18.png";
var APP_ICON_32 = "data/assets/logo_32.png";
var APP_ICON_36 = "data/assets/logo_36.png";
var APP_ICON_64 = "data/assets/logo_64.png";
var GCM_API_KEY = "GCM_API_KEY";

/* checking storage */
var devices = chrome.storage.local.get("sendroidDB", function() {
	console.log("Accessing Storage");
});

if (!devices) {
	devices = [];
	
	chrome.storage.local.set({ "sendroidDB": devices }, function() {
		console.log("Saving Storage");
	});
}

/****************************************************************************/
/*					Hnadling UI for user preferences						*/
/****************************************************************************/

/* listens to device information changes and updates storage */
chrome.runtime.onMessage.addListener(
	function(request, sender, sendResponse) {
		chrome.storage.local.get("sendroidDB", function(result) {
			if (request.type == "delete") {
				console.log("Deleting device_" + request.id);
				result.sendroidDB.splice(request.id, 1);
			} else if (request.type == "add") {
				console.log("[storage] : adding new device");
				result.sendroidDB.push(request.device);
			} else if (request.type == "edit") {
				console.log("[storage] : editing device_" + request.id);
				result.sendroidDB[request.id].name = request.device.name;
				result.sendroidDB[request.id].regid = request.device.regid;
				result.sendroidDB[request.id].status = request.device.status;
			}
			chrome.storage.local.set(
				{ "sendroidDB": result.sendroidDB }, 
				function() {
					console.log("[storage] : saving after modification");
					updateContextMenu();
				});
		});
});

/**********   notification constructor for error notifications	**********/
function sendroidNotify(error, body, showUserPref) {
	chrome.notifications.create(
		"" , {   
			type: "basic", 
			iconUrl: APP_ICON_64, 
			title: APP_NAME + " : " + error, 
			message: body
		},
		function() { } 
	);
}

/****************************************************************************/
/*			Creating context menus and sending data to GCM server			*/
/****************************************************************************/
/* create context menu tree at install time */
chrome.runtime.onInstalled.addListener(function() {
	updateContextMenu();
});

/* update context menu items using saved device names */
function updateContextMenu () {
	/* remove all the menu items */
	chrome.contextMenus.removeAll();
	chrome.contextMenus.create({
		"title":	"Send to android",
		"id":		"top",
		"contexts":	["image", "link", "selection", "page"]
	});

	/* repopulate the menu using device list from storage */
	chrome.storage.local.get("sendroidDB", function(result) {
		console.log("Creating context menu item list.");
		for (var i = 0; i < result.sendroidDB.length; i++) {
			chrome.contextMenus.create({
				"title":	result.sendroidDB[i].name,
				"id":		i.toString(),
                "contexts": ["image", "link", "selection", "page"], 
				"parentId":	"top"
			});
		};
	});
}

/* onClick listener for context menu */
chrome.contextMenus.onClicked.addListener(checkContext);

/* template for creating sendroid message */
var sendroidData = {
	id: 0,			/* for separate android notification	*/
	type: "type", 	/* data type : image/text/URL			*/
	body: "body"	/* actual data to be shared				*/
};

/* global variable to store target device id */
var toDevice = -1;

/* check context type and populate sendroidData accordingly */
function checkContext(info, tab) {
	console.log("item " + info.menuItemId + " was clicked");
	if (info.menuItemId === "top") {
		sendroidNotify("Please Add a Device", 
			"Please add at least one device in the Sendroid settings panel. " +
			"Click the Sendroid button on top-right corner of menu bar.",
			false);
		return;
	}
	toDevice = parseInt(info.menuItemId, 10);
	sendroidData.id = Math.floor((1 + Math.random()) * 0x10000); /* random ID */
	if(info.srcUrl != null) {
		sendroidData.type = "img";
		sendroidData.body = info.srcUrl;
	} else if (info.selectionText != null) {
		sendroidData.type = "txt";
		sendroidData.body = info.selectionText;
	} else if (info.linkUrl != null) {
		sendroidData.type = "url";
		sendroidData.body = info.linkUrl;
	} else if (info.pageUrl != null) {
		sendroidData.type = "url";
		sendroidData.body = info.pageUrl;
	}
	
	console.log("type : " + sendroidData.type + " (" + sendroidData.id + ")");
	console.log("body : " + sendroidData.body);
	createAndSendMessage();
};

/* handle context menu click */
function createAndSendMessage() {	
	/* restrict message body length to 2048 characters */
	if (sendroidData.body.length > 2048) {
		console.log("You can't send more than 2048 characters");
		sendroidNotify("Message is too long", 
			"Remember Sendroid cannot send more than 2048 characters.",
			false);
		return;
	}
	
	/* FUTURE : if something is wrong from Chrome add-on side,
	 * probably this is the last place to correct that */
	
	/* if everything is okay proceed with message creation */
	console.log(sendroidData.type + " : " + sendroidData.body.substring(0, 64));

	chrome.storage.local.get("sendroidDB", function(result) {
			var sendroidMessage = JSON.stringify({
			time_to_live: 60,			/* message life in GCM server */
			delay_while_idle: false,	/* T = wait | F = deliver immediately */
			data: {
				id: sendroidData.id,
				type: sendroidData.type,
				body: sendroidData.body
			},
			registration_ids: [result.sendroidDB[toDevice].regid]
		});

		sendMessage(sendroidMessage);
	});
}

/* send the message to GCM server using http POST */
function sendMessage(message) {
	var sendRequest = new XMLHttpRequest();
    sendRequest.open('POST', 'https://android.googleapis.com/gcm/send', true);
	sendRequest.setRequestHeader('Content-type', 'application/json');
	sendRequest.setRequestHeader('Authorization', 'key=' + GCM_API_KEY);
	
	// Handle request state change events
    sendRequest.onreadystatechange = function() {
		if (sendRequest.readyState === 4) {
			handleResponse(sendRequest);
		}
	};
	sendRequest.send(message);
}

/* handle the response received from GCM server
 * Note : A successful send request (http POST response status 200) does not 
 * mean the message has been successfully delivered to the android device. 
 * It just means message has been delivered successfully to the GCM server.*/
function handleResponse(response) {
	if (response.status === 200) {
		var responseJSON = JSON.parse(response.responseText);
		console.log(responseJSON);
		/* handle errors in pushing message from GCM server to device */
		if (responseJSON.failure || responseJSON.canonical_ids) {
			handleSendSuccess(responseJSON);
		} else {
			console.log("Everything is awesome");
			/* everything is okay, so update device status in storage */
			chrome.storage.local.get("sendroidDB", function(result) {
				console.log("Accessing Storage for updating status");
				result.sendroidDB[toDevice].status = 1;
				chrome.storage.local.set(
					{ "sendroidDB": result.sendroidDB }, 
					function() {
						console.log("Saving Storage after updating status");
					});
			});
		}
	/* handle errors in sending message to GCM server */
	} else if (response.status === 400) {
		/* JSON parsing errors : invalid/missing fields */
		console.log("Error : " + response.status + " (" + response.text + ")");
		sendroidNotify("JSON Parsing Error", 
			"Please mail the developer mentioning this error.",
			false);
	} else if (response.status === 401) {
		/* authentication error */
		console.log("Error : " + response.status + " (" + response.text + ")");
		sendroidNotify("Authentication Error", 
			"Please update Sendroid Chrome extension.\n" + 
			"If the error persists, mail the developer mentioning this error.",
			false);
	} else {
		console.log("Error : " + response.status + " (" + response.text + ")");
		/* TODO : Retry */
		sendroidNotify("Server Error", "Please retry after some time", false);
	}
}

/* handling the errors that might occur while pushing messages from GCM server 
 * to android devices following the logic described in GCM documentation
 * at http://developer.android.com/google/gcm/http.html#success */
function handleSendSuccess(resJSON) {
	if(resJSON.results[0].message_id) {
		if(resJSON.results[0].registration_id) {
			console.log("Info : Updating registration ID");
			/* FUTURE : should we notify the user? */
			/* device registration id has changed in the server
			 * update it in local Sendroid storage too */
			chrome.storage.local.get("sendroidDB", function(result) {
				console.log("Accessing Storage for updating regid");
				result.sendroidDB[toDevice].regid = 
						resJSON.results[0].registration_id;
				chrome.storage.local.set(
					{ "sendroidDB": result.sendroidDB }, 
					function() {
						console.log("Saving Storage after updating regid");
					});
			});
		}
	} else {
		if (resJSON.results[0].error === "Unavailable") {
			console.log("Error : Server Unavailable");
			sendroidNotify("Server Unavailable", 
				"Please retry after some time",
				false);
		} else if (resJSON.results[0].error === "NotRegistered") {
			console.log("Info : Device not registered. Removing device");
			chrome.storage.local.get("sendroidDB", function(result) {
				console.log("Accessing Storage for NotRegistered");
				sendroidNotify("Unregistered Device", 
					"It seems " + result.sendroidDB[toDevice].name + 
					"has been unregistered or has not been properly " +
					"registered. Please register the device again using the " + 
					"Sendroid android application. We are removing the device " +
					"from Sendroid Chrome database for now.",
					false);
				result.sendroidDB.splice(toDevice, 1);
				chrome.storage.local.set(
					{ "sendroidDB": result.sendroidDB }, 
					function() {
						console.log("Saving Storage after NotRegistered");
					});
			});
		} else if (resJSON.results[0].error === "InvalidRegistration") {
			console.log("Info : Invalid registration. Update required.");
			chrome.storage.local.get("sendroidDB", function(result) {
				console.log("Accessing Storage for NotRegistered");
				sendroidNotify("Invalid registration", 
					"Registration ID of " + result.sendroidDB[toDevice].name +
					" is not Valid. Update it in Sendroid Chrome database.", 
					true);
				result.sendroidDB[toDevice].status = 0;
				chrome.storage.local.set(
					{ "sendroidDB": result.sendroidDB }, 
					function() {
						console.log("Saving Storage after NotRegistered");
					});
			});
		} else if (resJSON.results[0].error === "MismatchSenderId") {
			console.log("Info : May be because of updates?");
			/* FUTURE : May be because of updates? | See GCM docs  */
			sendroidNotify("Mismatched Sender Id", 
				"Please mail the developer mentioning this error.",
				false);
		} else if (resJSON.results[0].error === "InternalServerError") {
			sendroidNotify("Internal Server Error", 
				"Please retry after some time",
				false);
		} else if (resJSON.results[0].error === "DeviceMessageRateExceeded") {
			console.log("Error: DeviceMessageRateExceeded");
			sendroidNotify("Device MessageRate Exceeded", 
				"Slow down, Cowboy!\n" +
				"Please send do not send messages so frequently to a device.",
				false);
		} else {
			console.log("Error: " + resJSON.results[0].error + 
						" (Shouldn't happen)");
			console.log("response.status : " + res.status);
			console.log("response.json.success : " + resJSON.success);
			console.log("response.json.failure : " + resJSON.failure);
			console.log("response.json.canonical_ids : " + 
						resJSON.canonical_ids);
			console.log("response.json.results.message_id : " + 
						resJSON.results.message_id);
			console.log("response.json.results.registration_id : " + 
						resJSON.results.registration_id);
			sendroidNotify(resJSON.results[0].error, 
				"Please mail the developer mentioning this error.",
				false);
		}
	}
}
