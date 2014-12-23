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
/*					Creating UI for user preferences						*/
/****************************************************************************/

chrome.runtime.onMessage.addListener(
	function(request, sender, sendResponse) {
	if (request.type == "delete") {
		chrome.storage.local.get("sendroidDB", function(result) {
			console.log("Accessing Storage for deleting device_" + request.id);
			result.sendroidDB.splice(request.id, 1);
			chrome.storage.local.set({ "sendroidDB": result.sendroidDB }, function() {
				console.log("Saving Storage after deleting");
				updateContextMenu();
			});
		});
	} else if (request.type == "add") {
		chrome.storage.local.get("sendroidDB", function(result) {
			console.log("Accessing Storage for adding new device");
			result.sendroidDB.push(request.device);
			chrome.storage.local.set({ "sendroidDB": result.sendroidDB }, function() {
				console.log("Saving Storage after Adding");
				updateContextMenu();
			});
		});
	} else if (request.type == "edit") {		
		chrome.storage.local.get("sendroidDB", function(result) {
			console.log("Accessing Storage for editing device_" + request.id);
			result.sendroidDB[request.id].name = request.device.name;
			result.sendroidDB[request.id].regid = request.device.regid;
			result.sendroidDB[request.id].status = request.device.status;
			chrome.storage.local.set({ "sendroidDB": result.sendroidDB }, function() {
				console.log("Saving Storage after Editing");
				updateContextMenu();
			});
		});
	}
	sendResponse({ type: "show" });
});

/**********   notification constructor for error notifications	**********/
function sheyarNotify(error, body, showUserPref) {
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

function updateContextMenu () {
	chrome.contextMenus.removeAll();
	chrome.contextMenus.create({
		"title":	"Send to android",
		"id":		"top",
		"contexts":	["image", "link", "selection", "page"]
	});

	chrome.storage.local.get("sendroidDB", function(result) {
		console.log("Creating context menu item list.");
		for (var i = 0; i < result.sendroidDB.length; i++) {
			chrome.contextMenus.create({
				"title":	result.sendroidDB[i].name,
				"id":		i.toString(),
				"parentId":	"top"
			});
		};
	});
}

/* onClick listener for context menu */
chrome.contextMenus.onClicked.addListener(checkContext);

/* template for creating sheyar message */
var sheyarData = {
	id: 0,			/* for separate android notification	*/
	type: "type", 	/* data type : image/text/URL			*/
	body: "body"	/* actual data to be shared				*/
};

/* check context type and populate sheyarData accordingly */
function checkContext(info, tab) {
	console.log("item " + info.menuItemId + " was clicked");
	sheyarData.id = Math.floor((1 + Math.random()) * 0x10000);	/* random ID */
	if(info.srcUrl != null) {
		sheyarData.type = "img";
		sheyarData.body = info.srcUrl;
	} else if (info.selectionText != null) {
		sheyarData.type = "txt";
		sheyarData.body = info.selectionText;
	} else if (info.linkUrl != null) {
		sheyarData.type = "url";
		sheyarData.body = info.linkUrl;
	} else if (info.pageUrl != null) {
		sheyarData.type = "url";
		sheyarData.body = info.pageUrl;
	}
	
	console.log("type : " + sheyarData.type + " (" + sheyarData.id + ")");
	console.log("body : " + sheyarData.body);
	createAndSendMessage(info.menuItemId);
};

/* handle context menu click */
function createAndSendMessage(toDevice) {	
	/* restrict message body length to 2048 characters */
	if (sheyarData.body.length > 2048) {
		console.log("You can't send more than 2048 characters");
		return;
	}
		
	/* if everything is okay proceed with message creation */
	console.log(sheyarData.type + " : " + sheyarData.body.substring(0, 64));

	chrome.storage.local.get("sendroidDB", function(result) {
			var sheyarMessage = JSON.stringify({
			time_to_live: 60,			/* message life in GCM server */
			delay_while_idle: false,	/* T = wait | F = deliver immediately */
			data: {
				id: sheyarData.id,
				type: sheyarData.type,
				body: sheyarData.body
			},
			registration_ids: [result.sendroidDB[toDevice].regid]
		});

		sendMessage(sheyarMessage, toDevice);
	});
}

/* send the message to GCM server using http POST */
function sendMessage(message, toDevice) {
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
			//sheyarStorage.storage.devices[toDevice].status = 1;
		}
	/* handle errors in sending message to GCM server */
	} else if (response.status === 400) {
		/* JSON parsing errors : invalid/missing fields */
		console.log("Error : " + response.status + " (" + response.text + ")");
		sheyarNotify("JSON Parsing Error", 
			"Please mail the developer mentioning this error.",
			false);
	} else if (response.status === 401) {
		/* authentication error */
		console.log("Error : " + response.status + " (" + response.text + ")");
		sheyarNotify("Authentication Error", 
			"Please update Sendroid Chrome extension.\n" + 
			"If the error persists, mail the developer mentioning this error.",
			false);
	} else {
		console.log("Error : " + response.status + " (" + response.text + ")");
		/* TODO : Retry */
		sheyarNotify("Server Error", "Please retry after some time", false);
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
			 * update it in local Sheyar storage too */
			//sheyarStorage.storage.devices[toDevice].regid = 
			//			resJSON.results[0].registration_id;
		}
	} else {
		if (resJSON.results[0].error === "Unavailable") {
			console.log("Error : Server Unavailable");
			sheyarNotify("Server Unavailable", 
				"Please retry after some time",
				false);
		} else if (resJSON.results[0].error === "NotRegistered") {
			console.log("Info : Device not registered. Removing device");
			//sheyarStorage.storage.devices.splice(toDevice, 1);
			sheyarNotify("Unregistered Device", 
				"It seems your device has been unregistered or has not been properly " +
				"registered. Please register the device again using the " + 
				"Sheyar android application. We are removing the device " +
				"from Sheyar Firefox database for now.",
				false);
		} else if (resJSON.results[0].error === "InvalidRegistration") {
			console.log("Info : Invalid registration. Update required.");
			sheyarNotify("Invalid registration", 
				"Registration ID of your device is not Valid. " +
				"Update device registration ID in Sheyar Firefox database.", 
				true);
			//sheyarStorage.storage.devices[toDevice].status = 0;
		} else if (resJSON.results[0].error === "MismatchSenderId") {
			console.log("Info : May be because of updates?");
			/* FUTURE : May be because of updates? | See GCM docs  */
			sheyarNotify("Mismatched Sender Id", 
				"Please mail the developer mentioning this error.",
				false);
		} else if (resJSON.results[0].error === "InternalServerError") {
			sheyarNotify("Internal Server Error", 
				"Please retry after some time",
				false);
		} else if (resJSON.results[0].error === "DeviceMessageRateExceeded") {
			console.log("Error: DeviceMessageRateExceeded");
			sheyarNotify("Device MessageRate Exceeded", 
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
			sheyarNotify(resJSON.results[0].error, 
				"Please mail the developer mentioning this error.",
				false);
		}
	}
}
