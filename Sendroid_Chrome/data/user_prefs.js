var MAX_DEVICE = 4;
var DEV_STATUS = ["Update Registration ID", "Good to go", "Unknown"];

/* get all the DOM references */
var addDiv = document.getElementById("add_device_div");
var prefDiv = document.getElementById("user_prefs_div");
var noDevice = document.getElementById("no_device");
var deviceList = document.getElementById("device_list");

var editButton = [document.getElementById("edit_device_0"),
				 document.getElementById("edit_device_1"),
				 document.getElementById("edit_device_2"), 
				 document.getElementById("edit_device_3")];

var deleteButton = [document.getElementById("delete_device_0"),
				 document.getElementById("delete_device_1"),
				 document.getElementById("delete_device_2"), 
				 document.getElementById("delete_device_3")];

var addButton = document.getElementById("add_device");
var saveButton = document.getElementById("save");
var cancelButton = document.getElementById("cancel");
				 
var nameArea = document.getElementById("name");
var regidArea = document.getElementById("regid");

var updateID =  -1;
var updateDevice = false;

/****************************************************************************/
/*						User Preference Panel Handling						*/
/****************************************************************************/
/* handle add_device button click */
addButton.addEventListener('click', function onAddDeviceClick() {
	console.log("Adding device");
	/* reset update flags */
	updateID =  -1;
	updateDevice = false;
	prefDiv.style.display = "none";
	addDiv.style.display = "block";
}, false);

/* handle edit button clicks */
function onEdit(deviceID) {
	console.log("Editing device_" + deviceID);
	updateID =  deviceID;
	updateDevice = true;
	chrome.storage.local.get("sendroidDB", function(result) {
		console.log("Accessing Storage from content script");
		nameArea.value = result.sendroidDB[deviceID].name;
		regidArea.value = result.sendroidDB[deviceID].regid;
	});
	prefDiv.style.display = "none";
	addDiv.style.display = "block";
}
editButton[0].addEventListener('click', function() { onEdit(0) }, false);
editButton[1].addEventListener('click', function() { onEdit(1) }, false);
editButton[2].addEventListener('click', function() { onEdit(2) }, false);
editButton[3].addEventListener('click', function() { onEdit(3) }, false);

/* handle delete button clicks */
function onDelete(deviceID) {
	console.log("Deleting device_" + deviceID);
	chrome.runtime.sendMessage({type: "delete", id: deviceID}, function(response) {
		if (response.type == "show") {
			showUserPrefs();
		}
	});
}
deleteButton[0].addEventListener('click', function() { onDelete(0) }, false);
deleteButton[1].addEventListener('click', function() { onDelete(1) }, false);
deleteButton[2].addEventListener('click', function() { onDelete(2) }, false);
deleteButton[3].addEventListener('click', function() { onDelete(3) }, false);

showUserPrefs();

function showUserPrefs() {	
	/* reset the panel to clear device details to handle deletion */
	deviceList.style.display = "none";
	for (var i = 0; i < MAX_DEVICE; i++) {
		var t_name = document.getElementById("device_" + i);
		t_name.children[0].firstChild.src = "";
		t_name.children[1].firstChild.nodeValue = "";
		t_name.style.display = "none";
	}
	
	chrome.storage.local.get("sendroidDB", function(result) {
		console.log("Accessing Storage from content script");
		console.log("CS : " + result.sendroidDB);
		/* update the table fields with proper values */
		if (result.sendroidDB.length > 0) {
			deviceList.style.display = "table";
			noDevice.children[0].firstChild.nodeValue = "";
			noDevice.children[1].firstChild.nodeValue = "";
			
			for (var i = 0; i < result.sendroidDB.length; i++) {
				var t_name = document.getElementById("device_" + i);
				t_name.children[0].firstChild.src = "assets/ic_status_" + 
													result.sendroidDB[i].status + ".png";
				t_name.children[0].firstChild.title = DEV_STATUS[result.sendroidDB[i].status];
				t_name.children[1].firstChild.nodeValue = result.sendroidDB[i].name;
				t_name.style.display = "block";
			}
			
			if (result.sendroidDB.length === MAX_DEVICE) {
				noDevice.children[0].firstChild.nodeValue = 
										"Maximum device limit reached.";
				noDevice.children[1].firstChild.nodeValue = 
										"Delete some device to add a new one.";
				addButton.style.display = "none";
			} else {
				addButton.style.display = "block";
			}
		} else {
			noDevice.children[0].firstChild.nodeValue = 
									"No android device found.";
			noDevice.children[1].firstChild.nodeValue = 
									"Sendroid needs at least one device to work.";
		}
	});

	/* reset text areas and panel display properties */
	nameArea.value = '';
	regidArea.value = '';	
	nameArea.style.border = "";
	regidArea.style.border = "";
	addDiv.style.display = "none";
	prefDiv.style.display = "block";
}

/****************************************************************************/
/*					Add/Edit Device Panel Handling							*/
/****************************************************************************/

/* handle save button click */
saveButton.addEventListener('click', function onClick() {
	var devices = [];
	var newDevice = {
		name: nameArea.value.replace(/(\r\n|\n|\r)/gm, ""),
		regid: regidArea.value.replace(/(\r\n|\n|\r)/gm, ""),
		status: 2
	};
	
	/* pass inputs to addon only if both inputs are valid */
	if (newDevice.name && newDevice.regid) {
		console.log(newDevice);
		console.log(updateDevice + " and " + updateID);
		/* for update event, update storage */
		if (updateDevice) {
			chrome.runtime.sendMessage({type: "edit", id: updateID, device: newDevice}, function(response) {
				if (response.type == "show") {
					showUserPrefs();
				}
			});

		/* for add event, add new device to storage */
		} else {
			chrome.runtime.sendMessage({type: "add", device: newDevice}, function(response) {
				if (response.type == "show") {
					showUserPrefs();
				}
			});
		}
	/* if inputs are empty, mark input boxes in red */
	} else {
		if (newDevice.name) {
			nameArea.style.border = "";
		} else {
			nameArea.style.border = "2px solid #c00";
		}		
		if (newDevice.regid) {
			regidArea.style.border = "";
		} else {
			regidArea.style.border = "2px solid #c00";
		}
	}
}, false);

/* handle cancel button click */
cancelButton.addEventListener('click', function onClick() {
	console.log("Cancelling add/edit");
	showUserPrefs();
}, false);
