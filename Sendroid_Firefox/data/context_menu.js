self.on("click", function (node, data) {
	console.log("data : " + data);
	if (parseInt(data) < 0)
		self.postMessage(data);
});