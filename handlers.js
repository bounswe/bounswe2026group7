

/**
 * BUTTON CLICK HANDLERS
 *
 * Each button on the homepage has its own handler function below.
 * Replace the placeholder console.log with your own implementation.
 */

function onButton1Click() {
  window.open("weather.html", "_blank");
} 

function onButton4Click() {
  window.open("joke.html", "_blank");
}



// b 2
let img = document.getElementById("manifestImage");

  if (!img) {
    img = document.createElement("img");
    img.id = "manifestImage";
    img.style.marginTop = "20px";
    img.style.maxWidth = "400px";
    document.body.appendChild(img);
  }

  // Her basışta yeni foto
  img.src = "https://picsum.photos/400?random=" + new Date().getTime();

function onButton2Click() {
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


function onButton6Click() {
  console.log("Button 6 clicked -- implement me!");
}

function onButton7Click() {
  window.location.href = "monkeys.html";
}
  // Fetch data from the highly stable Random User API
  fetch('https://randomuser.me/api/')
    .then(response => response.json())
    .then(data => {
      // Get the generated user data
      const user = data.results[0];
      
      // Open a new blank page
      const newWindow = window.open('', '_blank');
      
      // Write the description and the API response to the new page
      newWindow.document.write('<h2 style="font-family: sans-serif;">🌍 Random Global Citizen</h2>');
      newWindow.document.write('<p style="font-family: sans-serif;"><strong>Description:</strong> This public API returns a randomly generated user profile from different countries around the world.</p>');
      
      // Add their picture
      newWindow.document.write('<img src="' + user.picture.large + '" style="border-radius: 50%; border: 3px solid #483D8B; margin-top: 10px;">');
      
      // Add their info
      newWindow.document.write('<h3 style="font-family: sans-serif;">Name: ' + user.name.first + ' ' + user.name.last + '</h3>');
      newWindow.document.write('<ul style="font-family: sans-serif; font-size: 18px; color: #333;">');
      newWindow.document.write('<li><strong>Country:</strong> ' + user.location.country + '</li>');
      newWindow.document.write('<li><strong>City:</strong> ' + user.location.city + '</li>');
      newWindow.document.write('<li><strong>Timezone:</strong> ' + user.location.timezone.description + '</li>');
      newWindow.document.write('</ul>');
    })
    .catch(error => {
      console.error('Error fetching API:', error);
      alert('Failed to load global citizen data!');
    });
}
