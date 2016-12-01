var MAX_DEVICE = 4;
var DEV_STATUS = ["Update Registration ID", "Good to go", "Unknown"];

var noDevice = document.getElementById("no_device");
var addButton = document.getElementById("add_device");
var deviceList = document.getElementById("device_list");
var editButton = [document.getElementById("edit_device_0"),
				  document.getElementById("edit_device_1"),
				  document.getElementById("edit_device_2"), 
				  document.getElementById("edit_device_3")];

var deleteButton = [document.getElementById("delete_device_0"),
					document.getElementById("delete_device_1"),
					document.getElementById("delete_device_2"), 
					document.getElementById("delete_device_3")];

/* handle add_device button click */
addButton.addEventListener('click', function onAddDeviceClick() {
	self.port.emit("add_device");
}, false);

/* handle edit button clicks */
function onEdit(deviceID) {
	self.port.emit("edit_device", deviceID);
}
editButton[0].addEventListener('click', function() { onEdit(0) }, false);
editButton[1].addEventListener('click', function() { onEdit(1) }, false);
editButton[2].addEventListener('click', function() { onEdit(2) }, false);
editButton[3].addEventListener('click', function() { onEdit(3) }, false);

/* handle delete button clicks */
function onDelete(deviceID) {
	self.port.emit("delete_device", deviceID);
}
deleteButton[0].addEventListener('click', function() { onDelete(0) }, false);
deleteButton[1].addEventListener('click', function() { onDelete(1) }, false);
deleteButton[2].addEventListener('click', function() { onDelete(2) }, false);
deleteButton[3].addEventListener('click', function() { onDelete(3) }, false);

/* ready the panel for showing */
self.port.on("show", onShow);

function onShow(devices) {
	/* reset the panel to clear device details to handle deletion */
	deviceList.style.display = "none";
	for (var i = 0; i < MAX_DEVICE; i++) {
		var t_name = document.getElementById("device_" + i);
		t_name.children[0].firstChild.src = "";
		t_name.children[1].firstChild.nodeValue = "";
		t_name.style.display = "none";
	}

	/* update the table fields with proper values */
	if (devices.length > 0) {
		deviceList.style.display = "table";
		noDevice.children[0].firstChild.nodeValue = "";
		noDevice.children[1].firstChild.nodeValue = "";
		
		for (var i = 0; i < devices.length; i++) {
			var t_name = document.getElementById("device_" + i);
			t_name.children[0].firstChild.src = "assets/ic_status_" + 
												devices[i].status + ".png";
			t_name.children[0].firstChild.title = DEV_STATUS[devices[i].status];
			t_name.children[1].firstChild.nodeValue = devices[i].name;
			t_name.style.display = "table-row";
		}
		
		if (devices.length === MAX_DEVICE) {
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
}
