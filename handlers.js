function onButton6Click() {
  fetch("https://dog.ceo/api/breeds/image/random")
    .then(function (response) {
      return response.json();
    })
    .then(function (data) {
      var newTab = window.open();
      newTab.document.write(`
        <html>
          <head><title>Random Dog Photo</title></head>
          <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 60px auto; padding: 20px; background: #f4f4f4; text-align: center;">
            <div style="background: white; border-radius: 12px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
              <h1>🐶 Random Dog Photo</h1>
              <img src="${data.message}" style="width: 100%; border-radius: 12px; margin-top: 20px;">
              <p style="margin-top: 15px; color: #888;">Powered by <a href="https://dog.ceo/dog-api/">Dog CEO API</a> — returns random dog images by breed.</p>
            </div>
          </body>
        </html>
      `);
    })
    .catch(function (error) {
      alert("Failed to fetch dog photo. Please try again.");
    });
}