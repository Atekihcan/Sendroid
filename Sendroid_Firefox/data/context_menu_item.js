self.on("click", function (node, data) {
	console.log("data : " + data);
	self.postMessage(data);
});