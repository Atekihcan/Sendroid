var self = require("sdk/self");
var panel = require("sdk/panel");
var request = require("sdk/request");
var contextMenu = require("sdk/context-menu");
var notifications = require("sdk/notifications");
var sendroidDB = require("sdk/simple-storage");
var toggleButton = require("sdk/ui/button/toggle");

var APP_NAME	= "Sendroid"
var APP_ICON_18 = self.data.url("assets/logo_18.png");
var APP_ICON_32 = self.data.url("assets/logo_32.png");
var APP_ICON_36 = self.data.url("assets/logo_36.png");
var APP_ICON_64 = self.data.url("assets/logo_64.png");
var GCM_API_KEY = "GCM_API_KEY";

/* checking storage */
if (!sendroidDB.storage.devices) {
	sendroidDB.storage.devices = [];
}

/****************************************************************************/
/*					Creating UI for user preferences						*/
/****************************************************************************/

/* construct a panel for entering user preferences */
var sendroidUserPrefs = panel.Panel({
	width: 250,
	height: 350,
	contentURL: self.data.url("user_prefs.html"),
	contentScriptFile: self.data.url("user_prefs.js"),
	onHide: function() {
		sendroidButton.state('window', {checked: false});
	}
});

/* function to show user preference panel as we'll be doing it a lot */
function showUserPrefsPanel() {
	sendroidUserPrefs.show({
		position: sendroidButton
	});
}

/* construct a panel for adding a new device */
var sendroidAddDevice = panel.Panel({
	width: 250,
	height: 350,
	contentURL: self.data.url("add_device.html"),
	contentScriptFile: self.data.url("add_device.js"),
	onHide: showUserPrefsPanel
});

/* for button/image tooltip via 'title' attributes */
require('sdk/view/core').getActiveView(sendroidUserPrefs)
	.setAttribute('tooltip', 'aHTMLTooltip');
require('sdk/view/core').getActiveView(sendroidAddDevice)
	.setAttribute('tooltip', 'aHTMLTooltip');
	
/* create a button to show the user preference panel */
var sendroidButton = toggleButton.ToggleButton({
	id: "sendroid-settings",
	label: "Sendroid Settings",
	icon: {
		"18": APP_ICON_18,
		"32": APP_ICON_32,
		"36": APP_ICON_36,
		"64": APP_ICON_64
	},
	onClick: showUserPrefsPanel
});

/* prepare the user preference panel for display. */
sendroidUserPrefs.on("show", function() {
	sendroidUserPrefs.port.emit("show", sendroidDB.storage.devices);
});

/* prepare the add device panel for display. */
sendroidAddDevice.on("show", function() {
	sendroidAddDevice.port.emit("show");
});

/*****************	handle user preference panel events		*****************/
/* handle add_device : show add_device panel */
sendroidUserPrefs.port.on("add_device", function () {
	console.log("Adding new device");
	sendroidUserPrefs.hide();
	sendroidAddDevice.show({
		position: sendroidButton
	});
});

/* handle edit_device : show add_device panel with pre-filled values */
sendroidUserPrefs.port.on("edit_device", function (deviceID) {
	console.log("Editing device_" + deviceID + " : " 
					+ sendroidDB.storage.devices[deviceID].name);
	sendroidAddDevice.show({
		position: sendroidButton
	});
	sendroidAddDevice.port.emit("edit", deviceID, sendroidDB.storage.devices);
});

/* handle delete_device : remove device from storage and update context menu*/
sendroidUserPrefs.port.on("delete_device", function (deviceID) {
	console.log("Deleting device_" + deviceID + " : " 
					+ sendroidDB.storage.devices[deviceID].name);
	sendroidDB.storage.devices.splice(deviceID, 1);
	updatesendroidMenu();
	sendroidUserPrefs.port.emit("show", sendroidDB.storage.devices);
});

/******************	handle add/edit device panel events		*****************/
/* handle device addition : add device to storage and update context menu */
sendroidAddDevice.port.on("new_device", function (device) {
	console.log("[New] Name : " + device.name);
	console.log("[New] Reg ID : " + device.regid);	
	if (device.name && device.regid) {
		/* if device name and regid are valid save the device in storage */
		sendroidDB.storage.devices.push(device);
		updatesendroidMenu();
	}

	sendroidAddDevice.hide();
});

/* handle device update : update storage and context menu item label */
sendroidAddDevice.port.on("update_device", function (device, deviceID) {
	console.log("[Update] Name : " + device.name);
	console.log("[Update] Reg ID : " + device.regid);
	if (device.name && device.regid) {
		/* update only if device name and regid are valid */
		sendroidDB.storage.devices[deviceID].name = device.name;
		sendroidDB.storage.devices[deviceID].regid = device.regid;
		sendroidDB.storage.devices[deviceID].status = device.status;
		
		sendroidMenu.items[deviceID].label = device.name;
	}

	sendroidAddDevice.hide();
});

/* handle add/edit cancel */
sendroidAddDevice.port.on("cancel_device", function () {
	console.log("Cancelling add/edit");

	sendroidAddDevice.hide();
});

/**********   notification constructor for error notifications	**********/
function sendroidNotify(error, body, showUserPref) {
	notifications.notify({
		title: APP_NAME + " : " + error,
		text: body,
		iconURL: APP_ICON_64,
		onClick: function() {
			if (showUserPref) {
				showUserPrefsPanel();
			}
		}
	});
}

/****************************************************************************/
/*			Creating context menus and sending data to GCM server			*/
/****************************************************************************/
/* create context menu */
var sendroidMenu = contextMenu.Menu({
	label: "Send to android",
	context: contextMenu.PredicateContext(checkContext),
	contentScriptFile: self.data.url("context_menu.js"),
	image: APP_ICON_32,
	onMessage: createAndSendMessage,
	items: []
});

/* update context menu items using saved device names */
function updatesendroidMenu() {
	/* remove all the menu items */
	sendroidMenu.items = [];
	
	/* repopulate the menu using device list from storage */
	if (sendroidDB.storage.devices.length) {
		for (var i = 0; i < sendroidDB.storage.devices.length; i++) {
			sendroidMenu.addItem(contextMenu.Item({
				label: sendroidDB.storage.devices[i].name,
				data: i.toString(),
				context: contextMenu.PredicateContext(checkContext),
				contentScriptFile: self.data.url("context_menu_item.js"),
				onMessage: createAndSendMessage
			}));
			console.log("Added " + sendroidMenu.items[i].label);
		}
	} else {
		/* if no device is stored, show default context menu item */
		sendroidMenu.addItem(contextMenu.Item({
			label: "Add a device",
			data: "-1"
		}));
	}
}

/* initialize context menu */
updatesendroidMenu();

/* template for creating sendroid message */
var sendroidData = {
	id: 0,			/* for separate android notification	*/
	type: "type", 	/* data type : image/text/URL			*/
	body: "body"	/* actual data to be shared				*/
};

/* global variable to store target device id */
var toDevice = -1;

/* check context type and populate sendroidData accordingly */
function checkContext(context) {
	sendroidData.id = Math.floor((1 + Math.random()) * 0x10000); /* random ID */
	if(context.targetName === 'img') {
		sendroidData.type = "img";
		sendroidData.body = context.srcURL;
		return true;
	} else if (context.selectionText != null) {
		sendroidData.type = "txt";
		sendroidData.body = context.selectionText;
		return true;
	} else if (context.linkURL != null) {
		sendroidData.type = "url";
		sendroidData.body = context.linkURL;
		return true;
	} else if (context.documentURL != null) {
		sendroidData.type = "url";
		sendroidData.body = context.documentURL;
		return true;
	} else {
		return false;
	}
}

/* handle context menu click */
function createAndSendMessage(deviceID) {
	toDevice = parseInt(deviceID, 10);
	console.log("createAndSendMessage : " + deviceID);
	/* if no device has been added, show user preference panel */
	if (toDevice < 0 || !sendroidDB.storage.devices.length) {
		showUserPrefsPanel();
		return;
	}
	
	/* restrict message body length to 2048 characters */
	if (sendroidData.body.length > 2048) {
		console.log("You can't send more than 2048 characters");
		sendroidNotify("Message is too long", 
			"Remember Sendroid cannot send more than 2048 characters.",
			false);
		return;
	}
	
	/* FUTURE : if something is wrong from Firefox add-on side,
	 * this is the last place to correct that */
	
	/* if everything is okay proceed with message creation */
	console.log(sendroidData.type + " : " + sendroidData.body.substring(0, 64));
	
	var sendroidMessage = JSON.stringify({
		time_to_live: 60,			/* message life in GCM server | 1 minute */
		delay_while_idle: false,	/* T = wait | F = deliver immediately */
		data: {
			id: sendroidData.id,
			type: sendroidData.type,
			body: sendroidData.body
		},
		registration_ids: [sendroidDB.storage.devices[toDevice].regid]
	});

	sendMessage(sendroidMessage); 
}

/* send the message to GCM server using http POST */
function sendMessage(message) {
	var sendRequest = request.Request({
		url: 'https://android.googleapis.com/gcm/send',
		headers: {
			Authorization: 'key=' + GCM_API_KEY
		},
		contentType: "application/json",
		content: message,
		onComplete: handleResponse
	});
	sendRequest.post();
}

/* handle the response received from GCM server
 * Note : A successful send request (http POST response status 200) does not 
 * mean the message has been successfully delivered to the android device. 
 * It just means message has been delivered successfully to the GCM server.*/
function handleResponse(response) {
	if (response.status === 200) {
		/* handle errors in pushing message from GCM server to device */
		if (response.json.failure || response.json.canonical_ids) {
			handleSendSuccess(response);
		} else {
			console.log("Everything is awesome");
			/* everything is okay, so update device status in storage */
			sendroidDB.storage.devices[toDevice].status = 1;
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
			"Please update Sendroid Firefox extension.\n" + 
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
function handleSendSuccess(res) {
	if(res.json.results[0].message_id) {
		if(res.json.results[0].registration_id) {
			console.log("Info : Updating registration ID for " + 
						sendroidDB.storage.devices[toDevice].name);
			/* FUTURE : should we notify the user? */
			/* device registration id has changed in the server
			 * update it in local Sendroid storage too */
			sendroidDB.storage.devices[toDevice].regid = 
						res.json.results[0].registration_id;
		}
	} else {
		if (res.json.results[0].error === "Unavailable") {
			console.log("Error : Server Unavailable");
			sendroidNotify("Server Unavailable", 
				"Please retry after some time",
				false);
		} else if (res.json.results[0].error === "NotRegistered") {
			console.log("Info : Device not registered. Removing device");
			sendroidNotify("Unregistered Device", 
				"It seems '" + 
				sendroidDB.storage.devices[toDevice].name +
				"' has been unregistered or has not been registered yet. " +
                "We are removing the device from Sendroid Firefox " +
                "database for now. Please add it again with proper " +
                "registration ID.",
				false);
			sendroidDB.storage.devices.splice(toDevice, 1);
		} else if (res.json.results[0].error === "InvalidRegistration") {
			console.log("Info : Invalid registration. Update required.");
			sendroidNotify("Invalid registration", 
				"Registration ID of '" + 
				sendroidDB.storage.devices[toDevice].name +
				"' is not Valid. Update registration ID in Sendroid " +
                "Firefox database.", 
				true);
			sendroidDB.storage.devices[toDevice].status = 0;
		} else if (res.json.results[0].error === "MismatchSenderId") {
			console.log("Info : May be because of updates?");
			/* FUTURE : May be because of updates? | See GCM docs  */
			sendroidNotify("Mismatched Sender Id", 
				"Please mail the developer mentioning this error.",
				false);
		} else if (res.json.results[0].error === "InternalServerError") {
			sendroidNotify("Internal Server Error", 
				"Please retry after some time",
				false);
		} else if (res.json.results[0].error === "DeviceMessageRateExceeded") {
			console.log("Error: DeviceMessageRateExceeded");
			sendroidNotify("Device MessageRate Exceeded", 
				"Slow down, Cowboy!\n" +
				"Please send do not send messages so frequently to a device.",
				false);
		} else {
			console.log("Error: " + res.json.results[0].error + 
						" (Shouldn't happen)");
			console.log("response.status : " + res.status);
			console.log("response.json.success : " + res.json.success);
			console.log("response.json.failure : " + res.json.failure);
			console.log("response.json.canonical_ids : " + 
						res.json.canonical_ids);
			console.log("response.json.results.message_id : " + 
						res.json.results.message_id);
			console.log("response.json.results.registration_id : " + 
						res.json.results.registration_id);
			sendroidNotify(res.json.results[0].error, 
				"Please mail the developer mentioning this error.",
				false);
		}
	}
}
