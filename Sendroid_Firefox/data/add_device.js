var nameArea = document.getElementById("name");
var regidArea = document.getElementById("regid");
var saveButton = document.getElementById("save");
var cancelButton = document.getElementById("cancel");

var updateID =  -1;
var updateDevice = false;

var newDevice = {
	name: "",
	regid: "",
	status: 2
};

/* handle save button click */
saveButton.addEventListener('click', function onClick() {
	newDevice.name = nameArea.value.replace(/(\r\n|\n|\r)/gm, "");
	newDevice.regid = regidArea.value.replace(/(\r\n|\n|\r)/gm, "");
	newDevice.status = 2;
	/* pass inputs to addon only if both inputs are valid */
	if (newDevice.name && newDevice.regid) {
		nameArea.value = '';
		regidArea.value = '';
		/* for update event, pass new values as well as the device id */
		if (updateDevice) {
			self.port.emit("update_device", newDevice, updateID);
			updateID = -1;
			updateDevice = false;		
		/* for add event, pass only new values */
		} else {
			self.port.emit("new_device", newDevice);
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
	nameArea.value = '';
	regidArea.value = '';
	self.port.emit("cancel_device");
}, false);

/* ready the add_device panel for showing */
self.port.on("show", onShow);
self.port.on("edit", onShow);

/* ready the add_device panel for showing */
function onShow(deviceID, devices) {
	console.log("Showing add/edit device panel");
	nameArea.focus();
	/* TODO : Check why show event is happening after edit */
	if (deviceID + 1) {
		nameArea.value = devices[deviceID].name;
		regidArea.value = devices[deviceID].regid;
		updateID = deviceID;
		updateDevice = true;
	}
	nameArea.style.border = "";
	regidArea.style.border = "";
}
